package com.vrflex.quest

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import android.content.Intent
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
fun VRFlexLauncher(tabState: MutableState<String>) {
    val context = LocalContext.current
    val settings = remember(context) { context.getSharedPreferences("vrflex_settings", android.content.Context.MODE_PRIVATE) }
    var tab by tabState
    var prediction by remember { mutableFloatStateOf(settings.getFloat("prediction", 33f)) }
    var armLength by remember { mutableFloatStateOf(settings.getFloat("arm_length", 100f)) }
    var launchStatus by remember { mutableStateOf("Ready") }
    var profileEnabled by remember { mutableStateOf(settings.getBoolean("profile_enabled", true)) }
    var sittingMode by remember { mutableStateOf(settings.getBoolean("sitting_mode", false)) }
    var installedApps by remember { mutableStateOf(emptyList<InstalledVrApp>()) }
    fun refreshLibrary() {
        val intents = listOf(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            Intent(Intent.ACTION_MAIN).addCategory("com.oculus.intent.category.VR")
        )
        installedApps = intents.flatMap { query ->
            context.packageManager.queryIntentActivities(query, 0).mapNotNull { resolved ->
                val activity = resolved.activityInfo ?: return@mapNotNull null
                if (activity.packageName == context.packageName) return@mapNotNull null
                InstalledVrApp(
                    label = resolved.loadLabel(context.packageManager).toString(),
                    packageName = activity.packageName,
                    activityName = activity.name
                )
            }
        }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
    }
    LaunchedEffect(Unit) { refreshLibrary() }
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
                    Button(onClick = { refreshLibrary(); launchStatus = "Library refreshed" }, colors = ButtonDefaults.buttonColors(containerColor = FlexOrange, contentColor = Color.Black)) {
                        Text("REFRESH LIBRARY", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                    if (installedApps.isEmpty()) {
                        Text("No launchable apps found yet. Refresh after installing games.", color = FlexMuted, fontSize = 11.sp)
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.weight(1f)) {
                            items(installedApps, key = { it.packageName }) { app ->
                                Button(
                                    onClick = {
                                        try {
                                            val intent = Intent(Intent.ACTION_MAIN).setClassName(app.packageName, app.activityName)
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            context.startActivity(intent)
                                            launchStatus = "Opening " + app.label + "…"
                                        } catch (error: Exception) {
                                            launchStatus = "Could not open " + app.label + ": " + error.javaClass.simpleName
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = FlexPanel, contentColor = FlexText),
                                    shape = RoundedCornerShape(12.dp)
                                ) { Text(app.label, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                    Text(launchStatus, color = if (launchStatus.startsWith("Could not")) FlexOrange else FlexMuted, fontSize = 11.sp)
                }
                "MOD MENU" -> {
                    Text("PROFILE", color = FlexOrange, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Enable VRFlex profile", color = FlexText)
                        Switch(checked = profileEnabled, onCheckedChange = { profileEnabled = it; settings.edit().putBoolean("profile_enabled", it).apply() })
                    }
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Sitting mode preference", color = FlexText)
                        Switch(checked = sittingMode, onCheckedChange = { sittingMode = it; settings.edit().putBoolean("sitting_mode", it).apply() })
                    }
                    Text("CONTROLLER", color = FlexOrange, fontWeight = FontWeight.Bold)
                    Text("Prediction  •  ${prediction.toInt()}", color = FlexText)
                    Slider(value = prediction, onValueChange = { prediction = it; settings.edit().putFloat("prediction", it).apply() }, valueRange = 0f..200f)
                    Text("Arm length  •  ${armLength.toInt()}%", color = FlexText)
                    Slider(value = armLength, onValueChange = { armLength = it; settings.edit().putFloat("arm_length", it).apply() }, valueRange = 0f..200f)
                    Text("These values are saved on this headset. Changing another game's tracking requires a supported game integration.", color = FlexMuted, fontSize = 10.sp)
                    Text("POINTER", color = FlexOrange, fontWeight = FontWeight.Bold)
                    Text("Aim the controller laser or hand ray at a control. Press the trigger or pinch to select it. Spatial SDK supplies pointer interaction for this panel.", color = FlexMuted, fontSize = 10.sp)
                }
                else -> {
                    Text("SHARED ARM LENGTH", color = FlexText)
                    Text("${armLength.toInt()}%", color = FlexOrange, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Slider(value = armLength, onValueChange = { armLength = it; settings.edit().putFloat("arm_length", it).apply() }, valueRange = 0f..200f)
                    Text("One shared value for both controllers. Saved on this device.", color = FlexMuted, fontSize = 11.sp)
                }
            }
            Text("LEFT Y • MOD MENU     LEFT MENU • GAMES", color = FlexOrange, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private data class InstalledVrApp(
    val label: String,
    val packageName: String,
    val activityName: String
)
