package com.gyan.offline.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gyan.offline.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(vm: ChatViewModel = viewModel()) {
    val state by vm.uiState.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }

    // Auto-scroll to latest message
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    GyanTheme {
        Scaffold(
            topBar = { TopBar(isOnline = state.isOnline) },
            bottomBar = {
                InputBar(
                    text = inputText,
                    onTextChange = { inputText = it },
                    isLoading = state.isLoading,
                    isListening = state.isListening,
                    voiceEnabled = state.voiceEnabled,
                    isModelReady = state.isModelReady,
                    onSend = {
                        if (inputText.isNotBlank()) {
                            vm.sendTextMessage(inputText)
                            inputText = ""
                        }
                    },
                    onMicToggle = {
                        if (state.isListening) vm.stopListening()
                        else vm.startVoiceInput()
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(GyanBackground)
            ) {
                if (!state.isModelReady) {
                    ModelLoadingBanner()
                }

                if (state.messages.isEmpty() && state.isModelReady) {
                    WelcomePrompt()
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.messages, key = { it.id }) { msg ->
                        MessageBubble(message = msg)
                    }

                    if (state.isLoading) {
                        item { ThinkingIndicator() }
                    }
                    if (state.isListening) {
                        item { ListeningIndicator() }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(isOnline: Boolean) {
    TopAppBar(
        title = {
            Column {
                Text("ज्ञान", style = MaterialTheme.typography.titleLarge, color = GyanOnGreen)
                Text(
                    if (isOnline) "Online" else "Offline — Full AI active",
                    style = MaterialTheme.typography.labelSmall,
                    color = GyanOnGreen.copy(alpha = 0.8f)
                )
            }
        },
        actions = {
            if (!isOnline) {
                Icon(
                    Icons.Default.WifiOff,
                    contentDescription = "Offline",
                    tint = GyanOnGreen.copy(alpha = 0.7f),
                    modifier = Modifier.padding(end = 12.dp)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = GyanGreen)
    )
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val alignment = if (message.isUser) Alignment.End else Alignment.Start
    val bgColor   = if (message.isUser) UserBubble else AssistantBubble
    val shape = if (message.isUser)
        RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp)
    else
        RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            shape = shape,
            color = bgColor,
            shadowElevation = 1.dp,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 15.sp,
                color = if (message.isOutOfDomain) GyanGrey else Color.Unspecified,
                fontStyle = if (message.isOutOfDomain) FontStyle.Italic else FontStyle.Normal,
            )
        }
    }
}

@Composable
private fun ThinkingIndicator() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 4.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = GyanGreen
        )
        Spacer(Modifier.width(8.dp))
        Text("Thinking…", style = MaterialTheme.typography.bodySmall, color = GyanGrey)
    }
}

@Composable
private fun ListeningIndicator() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 4.dp)
    ) {
        val scale by animateFloatAsState(
            targetValue = 1.2f,
            animationSpec = tween(500),
            label = "pulse"
        )
        Icon(
            Icons.Default.Mic,
            contentDescription = null,
            tint = GyanGreen,
            modifier = Modifier.scale(scale).size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text("Listening…", style = MaterialTheme.typography.bodySmall, color = GyanGrey)
    }
}

@Composable
private fun ModelLoadingBanner() {
    LinearProgressIndicator(
        modifier = Modifier.fillMaxWidth(),
        color = GyanGreen
    )
    Text(
        "Loading AI model…",
        style = MaterialTheme.typography.labelSmall,
        color = GyanGrey,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    )
}

@Composable
private fun WelcomePrompt() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("नमस्ते! मैं ज्ञान हूँ।", style = MaterialTheme.typography.headlineSmall, color = GyanGreen)
        Spacer(Modifier.height(8.dp))
        Text(
            "Ask me about farming, UPSC, banking exams, or your studies.",
            style = MaterialTheme.typography.bodyMedium,
            color = GyanGrey
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "खेती, UPSC, बैंकिंग परीक्षा, या पढ़ाई के बारे में पूछें।",
            style = MaterialTheme.typography.bodyMedium,
            color = GyanGrey
        )
    }
}

@Composable
private fun InputBar(
    text: String,
    onTextChange: (String) -> Unit,
    isLoading: Boolean,
    isListening: Boolean,
    voiceEnabled: Boolean,
    isModelReady: Boolean,
    onSend: () -> Unit,
    onMicToggle: () -> Unit,
) {
    Surface(shadowElevation = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        if (isListening) "Listening…" else "Type in Hindi or English…",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                maxLines = 4,
                enabled = isModelReady && !isListening,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GyanGreen,
                    unfocusedBorderColor = GyanLightGrey,
                )
            )

            Spacer(Modifier.width(6.dp))

            if (voiceEnabled) {
                IconButton(
                    onClick = onMicToggle,
                    enabled = isModelReady && !isLoading,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (isListening) Color.Red else GyanGreenLight)
                ) {
                    Icon(
                        if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Voice input",
                        tint = Color.White
                    )
                }
                Spacer(Modifier.width(4.dp))
            }

            IconButton(
                onClick = onSend,
                enabled = text.isNotBlank() && isModelReady && !isLoading && !isListening,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (text.isNotBlank() && isModelReady) GyanGreen else GyanLightGrey)
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.White)
            }
        }
    }
}
