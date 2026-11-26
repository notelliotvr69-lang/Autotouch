package com.vrflex.quest

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val FlexBlack = Color(0xFF101012)
private val FlexPanel = Color(0xFF1C1C20)
private val FlexOrange = Color(0xFFFF7A18)
private val FlexText = Color(0xFFF4F4F5)
private val FlexMuted = Color(0xFFAAAAB2)

@Composable
fun VRFlexLauncher() {
    val context = LocalContext.current
    val settings = remember(context) { context.getSharedPreferences("vrflex_settings", android.content.Context.MODE_PRIVATE) }
    var tab by remember { mutableStateOf("GAMES") }
    var prediction by remember { mutableFloatStateOf(settings.getFloat("prediction", 33f)) }
    var armLength by remember { mutableFloatStateOf(settings.getFloat("arm_length", 100f)) }
    var launchStatus by remember { mutableStateOf("Ready") }
    MaterialTheme {
        Column(
            modifier = Modifier.fillMaxSize().background(FlexBlack, RoundedCornerShape(22.dp)).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("VR", color = FlexOrange, fontSize = 27.sp, fontWeight = FontWeight.Black)
                Text("FLEX", color = FlexText, fontSize = 27.sp, fontWeight = FontWeight.Black)
                Text("   QUEST 3", color = FlexMuted, fontSize = 11.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("GAMES", "MOD MENU", "ARMS").forEach { item ->
                    Button(
                        onClick = { tab = item },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (tab == item) FlexOrange else FlexPanel,
                            contentColor = if (tab == item) Color.Black else FlexText
                        )
                    ) { Text(item, fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                }
            }
            when (tab) {
                "GAMES" -> {
                    Text("YOUR LIBRARY", color = FlexMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    GameLaunchButton("GORILLA TAG", "com.AnotherAxiom.GorillaTag") { launchStatus = it }
                    GameLaunchButton("GILLA", "com.Chase.Gilla") { launchStatus = it }
                    Text("Choose a game to open it. More installed apps can be added in a later pass.", color = FlexMuted, fontSize = 11.sp)
                    Text(launchStatus, color = if (launchStatus.startsWith("Could not") || launchStatus.startsWith("Not installed")) FlexOrange else FlexMuted, fontSize = 11.sp)
                }
                "MOD MENU" -> {
                    Text("CONTROLLER", color = FlexOrange, fontWeight = FontWeight.Bold)
                    Text("Prediction  •  ${prediction.toInt()}", color = FlexText)
                    Slider(value = prediction, onValueChange = { prediction = it; settings.edit().putFloat("prediction", it).apply() }, valueRange = 0f..200f)
                    Text("Arm length  •  ${armLength.toInt()}%", color = FlexText)
                    Slider(value = armLength, onValueChange = { armLength = it; settings.edit().putFloat("arm_length", it).apply() }, valueRange = 0f..200f)
                    Text("These controls currently change VRFlex's preview values only; cross-game tracking changes need a supported runtime integration.", color = FlexMuted, fontSize = 10.sp)
                }
                else -> {
                    Text("SHARED ARM LENGTH", color = FlexText)
                    Text("${armLength.toInt()}%", color = FlexOrange, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Slider(value = armLength, onValueChange = { armLength = it }, valueRange = 0f..200f)
                    Text("One shared value for both controllers. Saved on this device.", color = FlexMuted, fontSize = 11.sp)
                }
            }
            Text("VRFLEX • SPATIAL PREVIEW", color = FlexMuted, fontSize = 9.sp)
        }
    }
}

@Composable
private fun GameLaunchButton(label: String, packageName: String, onStatus: (String) -> Unit) {
    val context = LocalContext.current
    Button(
        onClick = {
            try {
                val launch = context.packageManager.getLaunchIntentForPackage(packageName)
                if (launch != null) {
                    context.startActivity(launch)
                    onStatus("Opening $label…")
                } else {
                    onStatus("Not installed or launch activity unavailable: $label")
                }
            } catch (error: Exception) {
                onStatus("Could not open $label: ${error.javaClass.simpleName}")
            }
        },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = FlexPanel, contentColor = FlexText),
        shape = RoundedCornerShape(12.dp)
    ) { Text(label, fontWeight = FontWeight.Bold) }
}
