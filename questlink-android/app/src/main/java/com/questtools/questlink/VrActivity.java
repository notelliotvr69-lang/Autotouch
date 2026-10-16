package com.questtools.questlink;

import android.app.NativeActivity;
import android.graphics.SurfaceTexture;
import android.view.Surface;
import android.widget.Toast;

/** OpenXR owns the display. This class only bridges MediaCodec's output texture. */
public class VrActivity extends NativeActivity {
    private SurfaceTexture texture;
    private Surface surface;
    private volatile boolean pending;

    // Called on the native render thread while its EGL context is current.
    public Surface createVideoSurface(int textureId) {
        texture = new SurfaceTexture(textureId);
        texture.setOnFrameAvailableListener(t -> pending = true);
        surface = new Surface(texture);
        return surface;
    }
    public long latchVideo(float[] matrix) {
        if (!pending || texture == null) return 0;
        pending = false;
        texture.updateTexImage();
        texture.getTransformMatrix(matrix);
        return texture.getTimestamp();
    }
    public void releaseVideoSurface() {
        if (surface != null) { surface.release(); surface = null; }
        if (texture != null) { texture.release(); texture = null; }
        pending = false;
    }
    public void reportError(String error) {
        runOnUiThread(() -> Toast.makeText(this, "QuestLink: " + error, Toast.LENGTH_LONG).show());
    }
}
