package app.chailite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class ChatMessage(val text: String, val fromUser: Boolean)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ChaiLiteApp() }
    }
}

@Composable
private fun ChaiLiteApp() {
    var draft by remember { mutableStateOf("") }
    var selectedBot by remember { mutableStateOf("Your first character") }
    val messages = remember {
        mutableStateListOf(ChatMessage("Hey! Welcome to CHAI Lite. Your chats will live here.", false))
    }
    val bg = Color(0xFF101014)
    val panel = Color(0xFF1C1C24)
    val accent = Color(0xFFFF6B8A)

    MaterialTheme(colorScheme = darkColorScheme(primary = accent, background = bg, surface = panel)) {
        Column(Modifier.fillMaxSize().background(bg).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().background(panel).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("CHAI Lite", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(selectedBot, color = Color.LightGray, fontSize = 12.sp)
                }
                TextButton(onClick = { selectedBot = if (selectedBot == "Your first character") "New character" else "Your first character" }) {
                    Text("Switch bot", color = accent)
                }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(messages) { message ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start) {
                        Text(message.text, color = Color.White, modifier = Modifier.widthIn(max = 300.dp).background(if (message.fromUser) Color(0xFF63364A) else panel, RoundedCornerShape(18.dp)).padding(13.dp))
                    }
                }
            }
            Text("Prototype: chat UI is ready; the local AI engine is not connected yet.", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = draft, onValueChange = { draft = it }, modifier = Modifier.weight(1f), placeholder = { Text("Message...") }, maxLines = 4)
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    val text = draft.trim()
                    if (text.isNotEmpty()) {
                        messages.add(ChatMessage(text, true))
                        messages.add(ChatMessage("Your message is saved in this session. Connect a local model to generate character replies.", false))
                        draft = ""
                    }
                }) { Text("Send") }
            }
        }
    }
}
