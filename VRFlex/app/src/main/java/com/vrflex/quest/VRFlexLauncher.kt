package com.vrflex.quest

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket

private val FlexBlack = Color(0xFF101012)
private val FlexPanel = Color(0xFF1C1C20)
private val FlexOrange = Color(0xFFFF7A18)
private val FlexText = Color(0xFFF4F4F5)
private val FlexMuted = Color(0xFFAAAAB2)

/**
 * Small TCP control channel for the phone-host protocol.
 * Protocol: one JSON object per line; phone answers HELLO with
 * {"type":"welcome","protocol":1}, and accepts {"type":"launch","game":"..."}.
 * Video/pose transport is a later milestone; this is not a streamer yet.
 */
private object PhoneHostLink {
    private var socket: Socket? = null
    private var input: BufferedReader? = null
    private var output: BufferedWriter? = null

    @Synchronized
    fun connect(host: String, port: Int = 4765): String {
        disconnect()
        val candidate = Socket()
        candidate.connect(InetSocketAddress(host.trim(), port), 2500)
        candidate.soTimeout = 2500
        val reader = BufferedReader(InputStreamReader(candidate.getInputStream(), Charsets.UTF_8))
        val writer = BufferedWriter(OutputStreamWriter(candidate.getOutputStream(), Charsets.UTF_8))
        writer.write(JSONObject().put("type", "hello").put("client", "vrflex-quest").put("protocol", 1).toString())
        writer.newLine()
        writer.flush()
        val response = reader.readLine() ?: throw IllegalStateException("Phone closed the connection")
        val json = JSONObject(response)
        if (json.optString("type") != "welcome" || json.optInt("protocol") != 1) {
            throw IllegalStateException("Phone host protocol mismatch")
        }
        socket = candidate
        input = reader
        output = writer
        return "Connected to VRFlex Mobile"
    }

    @Synchronized
    fun launch(game: String): String {
        val active = socket ?: throw IllegalStateException("Connect to the phone first")
        if (!active.isConnected || active.isClosed) throw IllegalStateException("Phone connection was lost")
        val writer = output ?: throw IllegalStateException("No phone connection")
        writer.write(JSONObject().put("type", "launch").put("game", game).toString())
        writer.newLine()
        writer.flush()
        return "Sent $game launch request to phone"
    }

    @Synchronized
    fun disconnect() {
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        input = null
        output = null
    }
}

@Composable
fun VRFlexLauncher(tabState: MutableState<String>) {
    var tab by tabState
    var hostAddress by remember { mutableStateOf("") }
    var connectionStatus by remember { mutableStateOf("Not connected • enter your phone's Wi-Fi IP") }
    var connected by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings = remember(context) {
        // Quest-side preferences are intentionally local; they do not alter other games' tracking.
        context.getSharedPreferences("vrflex_settings", android.content.Context.MODE_PRIVATE)
    }
    var prediction by remember { mutableFloatStateOf(settings.getFloat("prediction", 33f)) }
    var armLength by remember { mutableFloatStateOf(settings.getFloat("arm_length", 100f)) }
    var profileEnabled by remember { mutableStateOf(settings.getBoolean("profile_enabled", true)) }
    var sittingMode by remember { mutableStateOf(settings.getBoolean("sitting_mode", false)) }

    fun runConnection(action: () -> String) {
        if (busy) return
        busy = true
        Thread {
            val result = try { action() } catch (error: Exception) {
                "Connection failed: ${error.message ?: error.javaClass.simpleName}"
            }
            if (result.startsWith("Connection failed")) connected = false
            else if (result.startsWith("Connected")) connected = true
            connectionStatus = result
            busy = false
        }.start()
    }

    MaterialTheme {
        Column(
            modifier = Modifier.fillMaxSize().background(FlexBlack, RoundedCornerShape(22.dp)).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("VR", color = FlexOrange, fontSize = 27.sp, fontWeight = FontWeight.Black)
                Text("FLEX", color = FlexText, fontSize = 27.sp, fontWeight = FontWeight.Black)
                Text("   QUEST 3", color = FlexMuted, fontSize = 11.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("GAMES", "MOD MENU", "ARMS").forEach { item ->
                    Button(
                        onClick = { tab = item },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (tab == item) FlexOrange else FlexPanel,
                            contentColor = if (tab == item) Color.Black else FlexText
                        )
                    ) { Text(item, fontWeight = FontWeight.Bold, fontSize = 10.sp) }
                }
            }
            when (tab) {
                "GAMES" -> {
                    Text("PHONE HOST", color = FlexOrange, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("Enter the phone's local Wi-Fi IP. VRFlex uses the phone as the game host; this panel no longer launches Quest-installed games.", color = FlexMuted, fontSize = 10.sp)
                    TextField(
                        value = hostAddress,
                        onValueChange = { hostAddress = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Phone IP address") },
                        placeholder = { Text("192.168.1.25") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = FlexPanel,
                            unfocusedContainerColor = FlexPanel,
                            focusedTextColor = FlexText,
                            unfocusedTextColor = FlexText,
                            focusedLabelColor = FlexOrange,
                            unfocusedLabelColor = FlexMuted,
                            cursorColor = FlexOrange
                        )
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = !busy && hostAddress.isNotBlank(),
                            onClick = { runConnection { PhoneHostLink.connect(hostAddress) } },
                            colors = ButtonDefaults.buttonColors(containerColor = FlexOrange, contentColor = Color.Black)
                        ) { Text(if (busy) "CONNECTING…" else "CONNECT", fontWeight = FontWeight.Bold, fontSize = 10.sp) }
                        Button(
                            enabled = connected && !busy,
                            onClick = {
                                PhoneHostLink.disconnect()
                                connected = false
                                connectionStatus = "Disconnected"
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = FlexPanel, contentColor = FlexText)
                        ) { Text("DISCONNECT", fontSize = 10.sp) }
                    }
                    Text(connectionStatus, color = if (connected) FlexOrange else FlexMuted, fontSize = 10.sp)
                    Text("PHONE-HOSTED GAMES", color = FlexMuted, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    listOf("Gorilla Tag", "Gilla").forEach { game ->
                        Button(
                            enabled = connected && !busy,
                            onClick = { runConnection { PhoneHostLink.launch(game) } },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = FlexPanel,
                                contentColor = FlexText,
                                disabledContainerColor = FlexPanel,
                                disabledContentColor = FlexMuted
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("REQUEST • $game", fontWeight = FontWeight.Bold) }
                    }
                    Text("The phone host must implement protocol v1 on TCP port 4765. Video streaming and tracking return are not implemented yet.", color = FlexMuted, fontSize = 10.sp)
                }
                "MOD MENU" -> {
                    Text("PROFILE", color = FlexOrange, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Enable VRFlex profile", color = FlexText)
                        Switch(checked = profileEnabled, onCheckedChange = {
                            profileEnabled = it
                            settings.edit().putBoolean("profile_enabled", it).apply()
                        })
                    }
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Sitting mode preference", color = FlexText)
                        Switch(checked = sittingMode, onCheckedChange = {
                            sittingMode = it
                            settings.edit().putBoolean("sitting_mode", it).apply()
                        })
                    }
                    Text("PREDICTION • ${prediction.toInt()}", color = FlexText)
                    Slider(value = prediction, onValueChange = {
                        prediction = it
                        settings.edit().putFloat("prediction", it).apply()
                    }, valueRange = 0f..200f)
                    Text("These are saved profile preferences, not universal game modifications.", color = FlexMuted, fontSize = 10.sp)
                    Text("POINTER", color = FlexOrange, fontWeight = FontWeight.Bold)
                    Text("Point a Quest controller ray at a panel button and press the trigger. Hand-ray selection depends on the system hand-tracking pointer being available.", color = FlexMuted, fontSize = 10.sp)
                }
                else -> {
                    Text("ARM LENGTH PROFILE", color = FlexOrange, fontWeight = FontWeight.Bold)
                    Text("${armLength.toInt()}%", color = FlexText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Slider(value = armLength, onValueChange = {
                        armLength = it
                        settings.edit().putFloat("arm_length", it).apply()
                    }, valueRange = 0f..200f)
                    Text("Saved as a profile value on the Quest. It does not currently change controller positions in other games.", color = FlexMuted, fontSize = 10.sp)
                }
            }
            Text("LEFT Y • MOD MENU     LEFT MENU • GAMES", color = FlexOrange, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}
