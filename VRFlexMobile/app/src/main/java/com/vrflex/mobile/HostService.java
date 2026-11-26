package com.vrflex.mobile;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.*;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.*;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * VRFlex phone host: TCP control (4765), H.264 video datagrams (4768),
 * PCM playback audio datagrams (4767), and Quest pose input (4766).
 * Datagram payloads use a 12-byte big-endian header:
 * magic 'VFX1', frame/sequence int, fragment index short, fragment count short.
 */
public final class HostService extends Service {
    public static final String ACTION_START="com.vrflex.mobile.START_HOST";
    public static final String ACTION_STOP="com.vrflex.mobile.STOP_HOST";
    public static final String EXTRA_RESULT_CODE="result_code";
    public static final String EXTRA_RESULT_DATA="result_data";
    public static final String ACTION_HOST_STATUS="com.vrflex.mobile.HOST_STATUS";
    public static final String EXTRA_HOST_STATUS="host_status";
    private static final int NOTIFICATION_ID=4765;
    private final AtomicBoolean running=new AtomicBoolean(false);
    private ServerSocket controlServer;
    private DatagramSocket videoSocket, audioSocket, poseSocket;
    private volatile InetAddress clientAddress;
    private volatile Socket activeClient;
    private volatile int latestPosePackets;
    private volatile byte[] codecConfig;
    private MediaProjection projection;
    private MediaCodec videoEncoder;
    private VirtualDisplay virtualDisplay;
    private AudioRecord audioRecord;
    private Thread controlThread, poseThread, videoThread, audioThread;

    @Override public void onCreate(){super.onCreate(); createChannel();}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null && ACTION_STOP.equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}
        startForeground(NOTIFICATION_ID, notification("Starting VRFlex host…"), Build.VERSION.SDK_INT>=29?ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION|ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE:0);
        if(intent!=null && intent.hasExtra(EXTRA_RESULT_DATA) && !running.get()){
            int code=intent.getIntExtra(EXTRA_RESULT_CODE,Activity.RESULT_CANCELED);
            Intent data=intent.getParcelableExtra(EXTRA_RESULT_DATA);
            if(code==Activity.RESULT_OK && data!=null) startHost(code,data);
            else stopSelf();
        }
        return START_STICKY;
    }
    private void startHost(int resultCode,Intent data){
        if(!running.compareAndSet(false,true))return;
        try{
            MediaProjectionManager manager=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
            projection=manager.getMediaProjection(resultCode,data);
            if(projection==null)throw new IllegalStateException("Screen capture permission was not granted");
            projection.registerCallback(new MediaProjection.Callback(){@Override public void onStop(){stopSelf();}},new Handler(Looper.getMainLooper()));
            controlServer=new ServerSocket(4765);
            videoSocket=new DatagramSocket();
            audioSocket=new DatagramSocket();
            poseSocket=new DatagramSocket(4766);
            controlThread=new Thread(this::acceptClients,"VRFlex-Control");controlThread.start();
            poseThread=new Thread(this::receivePoses,"VRFlex-Pose");poseThread.start();
            startVideo();
            startAudio();
            updateNotification("Host ready • TCP 4765 • video 4768 • audio 4767 • pose 4766");
        }catch(Exception e){updateNotification("Host error: "+safe(e));stopSelf();}
    }
    private void acceptClients(){
        while(running.get())try{
            Socket socket=controlServer.accept();
            socket.setSoTimeout(0);
            socket.setKeepAlive(true);
            socket.setTcpNoDelay(true);
            Socket previous=activeClient;
            activeClient=socket;
            clientAddress=socket.getInetAddress();
            if(previous!=null && previous!=socket)try{previous.close();}catch(Exception ignored){}
            new Thread(()->handleClient(socket),"VRFlex-Client").start();
        }catch(Exception e){if(running.get())updateNotification("Control server: "+safe(e));}
    }
    private void handleClient(Socket socket){
        try(Socket s=socket;BufferedReader in=new BufferedReader(new InputStreamReader(s.getInputStream()));BufferedWriter out=new BufferedWriter(new OutputStreamWriter(s.getOutputStream()))){
            String line;
            while(running.get()&&(line=in.readLine())!=null){
                org.json.JSONObject msg=new org.json.JSONObject(line);
                String type=msg.optString("type");
                if("hello".equals(type)){
                    out.write(new org.json.JSONObject().put("type","welcome").put("protocol",1).put("video_port",4768).put("audio_port",4767).put("pose_port",4766).put("video_codec","h264").put("video_width",1280).put("video_height",720).toString());out.newLine();out.flush();
                    sendCodecConfig();
                    updateNotification("Quest connected • H.264 video and PCM audio stream active");
                }else if("launch".equals(type)){
                    String game=msg.optString("game");
                    String pkg=msg.optString("package");
                    if(pkg.isEmpty())pkg=packageFor(game);
                    boolean launched=launchPackage(pkg);
                    org.json.JSONObject reply=new org.json.JSONObject().put("type","launch_result").put("game",game).put("launched",launched).put("message",launched?"Android app launch requested":"App is not installed on the phone or is not a phone-compatible build");
                    out.write(reply.toString());out.newLine();out.flush();
                }else if("ping".equals(type)){
                    out.write("{\"type\":\"pong\"}");out.newLine();out.flush();
                }
            }
        }catch(Exception ignored){}finally{
            if(activeClient==socket){
                activeClient=null;
                clientAddress=null;
                if(running.get())updateNotification("Host ready • waiting for Quest connection");
            }
        }
    }
    private String packageFor(String game){
        if("Gorilla Tag".equalsIgnoreCase(game))return "com.AnotherAxiom.GorillaTag";
        if("Gilla".equalsIgnoreCase(game))return "com.Chase.Gilla";
        return "";
    }
    private boolean launchPackage(String pkg){
        if(pkg==null||pkg.trim().isEmpty())return false;
        try{
            Intent launch=getPackageManager().getLaunchIntentForPackage(pkg);
            if(launch==null)return false;
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launch);return true;
        }catch(Exception e){return false;}
    }
    private void receivePoses(){
        byte[] buf=new byte[2048];
        while(running.get())try{
            DatagramPacket packet=new DatagramPacket(buf,buf.length);poseSocket.receive(packet);
            InetAddress expected=clientAddress;
            if(expected!=null && !expected.equals(packet.getAddress()))continue;
            String payload=new String(packet.getData(),packet.getOffset(),packet.getLength(),java.nio.charset.StandardCharsets.UTF_8);
            org.json.JSONObject incoming=new org.json.JSONObject(payload);
            if(!"tracking".equals(incoming.optString("type")))continue;
            latestPosePackets++;
            // Apply saved VRFlex arm reach/position settings and publish the normalized packet
            // to the in-app runtime adapter. This does not inject input into unrelated apps.
            TrackingProcessor.process(this,incoming,latestPosePackets);
            if(latestPosePackets==1 || latestPosePackets%120==0)
                updateNotification("Host streaming • tracking packets: "+latestPosePackets);
        }catch(Exception e){if(running.get())updateNotification("Pose channel: "+safe(e));}
    }
    private void startVideo()throws Exception{
        final int width=1280,height=720,dpi=getResources().getDisplayMetrics().densityDpi;
        MediaFormat format=MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC,width,height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE,6_000_000);
        format.setInteger(MediaFormat.KEY_FRAME_RATE,60);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1);
        videoEncoder=MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        videoEncoder.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
        android.view.Surface surface=videoEncoder.createInputSurface();
        videoEncoder.start();
        virtualDisplay=projection.createVirtualDisplay("VRFlexPhoneHost",width,height,dpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,surface,null,null);
        videoThread=new Thread(this::drainVideo,"VRFlex-Video");videoThread.start();
    }
    private void drainVideo(){
        MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();int frame=0;
        while(running.get())try{
            int index=videoEncoder.dequeueOutputBuffer(info,10_000);
            if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
                MediaFormat outputFormat=videoEncoder.getOutputFormat();
                ByteBuffer sps=outputFormat.getByteBuffer("csd-0");
                ByteBuffer pps=outputFormat.getByteBuffer("csd-1");
                if(sps!=null){
                    byte[] a=new byte[sps.remaining()];sps.get(a);
                    byte[] b=pps==null?new byte[0]:new byte[pps.remaining()];if(pps!=null)pps.get(b);
                    codecConfig=new byte[a.length+b.length];System.arraycopy(a,0,codecConfig,0,a.length);System.arraycopy(b,0,codecConfig,a.length,b.length);
                    sendCodecConfig();
                }
            }else if(index>=0){
                try{
                    ByteBuffer data=videoEncoder.getOutputBuffer(index);
                    if(data!=null&&info.size>0){
                        byte[] bytes=new byte[info.size];data.position(info.offset);data.limit(info.offset+info.size);data.get(bytes);
                        if((info.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)!=0)sendFragments(videoSocket,bytes,clientAddress,4768,-1);else sendFragments(videoSocket,bytes,clientAddress,4768,frame++);
                    }
                }finally{
                    // Always return the encoder buffer, even if a UDP send fails.
                    videoEncoder.releaseOutputBuffer(index,false);
                }
            }
        }catch(Exception e){if(running.get())updateNotification("Video encoder: "+safe(e));}
    }
    private void startAudio(){
        if(Build.VERSION.SDK_INT<29)return;
        try{
            AudioPlaybackCaptureConfiguration config=new AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA).addMatchingUsage(AudioAttributes.USAGE_GAME).build();
            AudioFormat format=new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(48000).setChannelMask(AudioFormat.CHANNEL_IN_STEREO).build();
            int min=AudioRecord.getMinBufferSize(48000,AudioFormat.CHANNEL_IN_STEREO,AudioFormat.ENCODING_PCM_16BIT);
            audioRecord=new AudioRecord.Builder().setAudioFormat(format).setBufferSizeInBytes(Math.max(min,19200))
                .setAudioPlaybackCaptureConfig(config).build();
            audioRecord.startRecording();
            audioThread=new Thread(()->{
                byte[] buffer=new byte[1200];int seq=0;
                while(running.get())try{
                    int n=audioRecord.read(buffer,0,buffer.length);
                    if(n>0&&clientAddress!=null){byte[] packet=new byte[n+12];ByteBuffer h=ByteBuffer.wrap(packet).order(ByteOrder.BIG_ENDIAN);h.putInt(0x56465831).putInt(seq++).putShort((short)0).putShort((short)1);System.arraycopy(buffer,0,packet,12,n);audioSocket.send(new DatagramPacket(packet,packet.length,clientAddress,4767));}
                }catch(Exception e){if(running.get())updateNotification("Audio capture: "+safe(e));}
            },"VRFlex-Audio");audioThread.start();
        }catch(Exception e){updateNotification("Audio capture unavailable: "+safe(e));}
    }
    private void sendCodecConfig(){
        byte[] config=codecConfig;
        if(config!=null&&config.length>0)try{sendFragments(videoSocket,config,clientAddress,4768,-1);}catch(Exception ignored){}
    }
    private void sendFragments(DatagramSocket socket,byte[] bytes,InetAddress address,int port,int sequence)throws Exception{
        if(address==null)return;
        final int chunk=1180;int count=(bytes.length+chunk-1)/chunk;
        for(int i=0;i<count;i++){
            int start=i*chunk,len=Math.min(chunk,bytes.length-start);byte[] packet=new byte[12+len];
            ByteBuffer h=ByteBuffer.wrap(packet).order(ByteOrder.BIG_ENDIAN);
            h.putInt(0x56465831).putInt(sequence).putShort((short)i).putShort((short)count);
            System.arraycopy(bytes,start,packet,12,len);
            socket.send(new DatagramPacket(packet,packet.length,address,port));
        }
    }
    private void createChannel(){
        if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel("vrflex_host","VRFlex host",NotificationManager.IMPORTANCE_LOW);getSystemService(NotificationManager.class).createNotificationChannel(c);}
    }
    private Notification notification(String text){
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"vrflex_host"):new Notification.Builder(this);
        return b.setContentTitle("VRFlex Mobile host").setContentText(text).setSmallIcon(android.R.drawable.presence_video_online).setOngoing(true).build();
    }
    private void updateNotification(String text){
        if(Build.VERSION.SDK_INT>=26)getSystemService(NotificationManager.class).notify(NOTIFICATION_ID,notification(text));
        Intent status=new Intent(ACTION_HOST_STATUS).setPackage(getPackageName());
        status.putExtra(EXTRA_HOST_STATUS,text);
        sendBroadcast(status);
    }
    private String safe(Exception e){return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}
    @Override public void onDestroy(){
        running.set(false);
        try{if(controlServer!=null)controlServer.close();}catch(Exception ignored){}
        try{if(activeClient!=null)activeClient.close();}catch(Exception ignored){}
        activeClient=null;clientAddress=null;
        try{if(videoSocket!=null)videoSocket.close();if(audioSocket!=null)audioSocket.close();if(poseSocket!=null)poseSocket.close();}catch(Exception ignored){}
        try{if(virtualDisplay!=null)virtualDisplay.release();}catch(Exception ignored){}
        try{if(videoEncoder!=null){videoEncoder.stop();videoEncoder.release();}}catch(Exception ignored){}
        try{if(audioRecord!=null){audioRecord.stop();audioRecord.release();}}catch(Exception ignored){}
        try{if(projection!=null)projection.stop();}catch(Exception ignored){}
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
