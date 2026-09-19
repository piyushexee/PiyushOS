package com.piyushos.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BgDark = Color(0xFF0E1424)
private val CardBg = Color(0xFF1A2338)
private val Accent = Color(0xFF4FC3F7)
private val UserBubble = Color(0xFF2563EB)
private val AgentBubble = Color(0xFF232E4A)
private val GreyText = Color(0xFF8A97B5)
private val GreenText = Color(0xFF66BB6A)
private val RedText = Color(0xFFEF5350)

@Composable
fun ChatScreen(
    connected: Boolean,
    host: String, onHost: (String) -> Unit,
    port: String, onPort: (String) -> Unit,
    token: String, onToken: (String) -> Unit,
    messages: List<ChatMsg>,
    listening: Boolean,
    crash: String?,
    onCopyCrash: () -> Unit,
    onDismissCrash: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onSend: (String) -> Unit,
    onMic: () -> Unit,
    onEnableAccessibility: () -> Unit,
    onEnableScreenshots: () -> Unit,
) {
    var input by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
        ) {
            Text(
                "🧠 PiyushOS v${BuildConfig.VERSION_NAME}",
                color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            if (connected) {
                Text("● Online", color = GreenText, fontSize = 13.sp)
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onDisconnect) { Text("Disconnect", color = RedText) }
            } else {
                Text("○ Offline", color = RedText, fontSize = 13.sp)
            }
        }

        // ---------- crash card (agar pichli baar crash hui) ----------
        if (crash != null) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF3A1520))
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("⚠️ Pichli baar app crash hui", color = RedText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    val lines = crash.lineSequence().take(10).toList()
                    SelectionContainer {
                        Text(
                            lines.joinToString("\n"),
                            color = Color(0xFFE8B4BC),
                            fontSize = 11.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF2A0F18))
                                .padding(8.dp)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onCopyCrash, colors = ButtonDefaults.buttonColors(containerColor = RedText)) {
                            Text("📋 Copy crash log")
                        }
                        OutlinedButton(onClick = onDismissCrash) { Text("Dismiss") }
                    }
                    Text("Crash log copy karke developer (AI) ko bhej do — turant fix ho jayega.", color = GreyText, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        if (!connected) {
            ConnectionPanel(host, onHost, port, onPort, token, onToken,
                onConnect, onEnableAccessibility, onEnableScreenshots)
        } else {
            ChatView(messages, input, { input = it }, listening, onSend, onMic)
        }
    }
}

@Composable
private fun ConnectionPanel(
    host: String, onHost: (String) -> Unit,
    port: String, onPort: (String) -> Unit,
    token: String, onToken: (String) -> Unit,
    onConnect: () -> Unit,
    onEnableAccessibility: () -> Unit,
    onEnableScreenshots: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg)
        ) {
            Column(
                Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("🔌 Server se Connect karo", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = host, onValueChange = onHost, singleLine = true,
                    label = { Text("IP (PC ka, e.g. 192.168.29.1)") },
                    colors = fieldColors()
                )
                OutlinedTextField(
                    value = port, onValueChange = onPort, singleLine = true,
                    label = { Text("Port (8787)") },
                    colors = fieldColors()
                )
                OutlinedTextField(
                    value = token, onValueChange = onToken, singleLine = true,
                    label = { Text("Token (.env ka DEVICE_TOKEN)") },
                    colors = fieldColors()
                )
                Button(
                    onClick = onConnect,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("CONNECT", fontSize = 16.sp) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onEnableAccessibility, modifier = Modifier.weight(1f)) {
                        Text("🦾 Accessibility ON", fontSize = 13.sp)
                    }
                    OutlinedButton(onClick = onEnableScreenshots, modifier = Modifier.weight(1f)) {
                        Text("📸 Screenshot ON", fontSize = 13.sp)
                    }
                }
                Text(
                    "💡 PC nahi hai? Brain ko phone me Termux me chalao (README me 'Termux' section).\n" +
                    "Termux se chalane par IP: 127.0.0.1 use hoga.",
                    color = GreyText, fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = Accent,
    unfocusedBorderColor = CardBg,
    focusedLabelColor = Accent,
    unfocusedLabelColor = GreyText,
)

@Composable
private fun ChatView(
    messages: List<ChatMsg>,
    input: String,
    onInput: (String) -> Unit,
    listening: Boolean,
    onSend: (String) -> Unit,
    onMic: () -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(messages, key = { it.id }) { msg -> Bubble(msg) }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = onInput,
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Bolo ya likho... (Hinglish chalta hai)", color = GreyText) },
                shape = RoundedCornerShape(24.dp),
                colors = fieldColors()
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = onMic,
                colors = if (listening)
                    IconButtonDefaults.filledIconButtonColors(containerColor = RedText)
                else
                    IconButtonDefaults.filledIconButtonColors(containerColor = CardBg)
            ) {
                Text(if (listening) "⏹" else "🎤", fontSize = 18.sp)
            }
            Spacer(Modifier.width(6.dp))
            FilledIconButton(
                onClick = { onSend(input); onInput("") },
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Accent)
            ) {
                Text("➤", fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun Bubble(msg: ChatMsg) {
    when (msg.role) {
        Role.USER -> Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            SelectionContainer {
                Text(
                    msg.text,
                    color = Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(UserBubble)
                        .widthIn(max = 290.dp)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
        Role.AGENT -> Row(Modifier.fillMaxWidth()) {
            SelectionContainer {
                Text(
                    msg.text,
                    color = Color(0xFFE8EDF7),
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(AgentBubble)
                        .widthIn(max = 310.dp)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
        Role.SYSTEM -> Text(
            msg.text,
            color = GreyText,
            fontSize = 12.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 2.dp)
        )
    }
}
