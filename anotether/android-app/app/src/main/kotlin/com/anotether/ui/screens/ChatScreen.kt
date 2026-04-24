package com.anotether.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anotether.messaging.Message
import com.anotether.messaging.MessageDirection
import com.anotether.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Chat Screen — the active messaging interface.
 *
 * Messages appear in a LazyColumn that auto-scrolls to the latest.
 * Outgoing messages (ours) are right-aligned, burnt orange.
 * Incoming messages are left-aligned, navy.
 *
 * The token is shown in the top bar as a subtle reminder.
 * The "End Session" action is available but not prominent — we don't want accidental taps.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    token: String,
    messages: List<Message>,
    expiresAt: Long,
    onSendMessage: (String) -> Unit,
    onEndSession: () -> Unit,
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var showEndConfirm by remember { mutableStateOf(false) }

    // Auto-scroll to bottom when messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Encrypted Session",
                            style = MaterialTheme.typography.titleMedium.copy(color = OffWhite),
                        )
                        Text(
                            text = token.chunked(3).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                color = SubtleGray,
                                letterSpacing = 1.sp,
                            ),
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { showEndConfirm = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = SubtleGray),
                    ) {
                        Text(
                            text = "End",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Navy800,
                ),
            )
        },
        containerColor = Navy900,
        bottomBar = {
            MessageInputBar(
                text = inputText,
                onTextChange = { inputText = it },
                onSend = {
                    val msg = inputText.trim()
                    if (msg.isNotEmpty()) {
                        onSendMessage(msg)
                        inputText = ""
                        scope.launch {
                            if (messages.isNotEmpty()) {
                                listState.animateScrollToItem(messages.size)
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (messages.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(SuccessGreen),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Session active",
                        style = MaterialTheme.typography.bodyMedium.copy(color = SubtleGray),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Messages are end-to-end encrypted",
                        style = MaterialTheme.typography.bodySmall.copy(color = DimGray),
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = 8.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = messages,
                    key = { it.seq },
                ) { message ->
                    MessageBubble(message = message)
                }
            }
        }
    }

    // End session confirmation dialog
    if (showEndConfirm) {
        AlertDialog(
            onDismissRequest = { showEndConfirm = false },
            containerColor = Navy800,
            title = {
                Text(
                    text = "End Session?",
                    style = MaterialTheme.typography.titleLarge.copy(color = OffWhite),
                )
            },
            text = {
                Text(
                    text = "This will close the session for both parties. All messages will be deleted.",
                    style = MaterialTheme.typography.bodyMedium.copy(color = SubtleGray),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showEndConfirm = false
                        onEndSession()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = ErrorRed),
                ) {
                    Text(
                        text = "End Session",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showEndConfirm = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = SubtleGray),
                ) {
                    Text(text = "Cancel")
                }
            },
        )
    }
}

@Composable
private fun MessageBubble(message: Message) {
    val isOutgoing = message.direction == MessageDirection.Outgoing
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val timeString = remember(message.sentAt) {
        timeFormat.format(Date(message.sentAt * 1000))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 280.dp),
            horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start,
        ) {
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isOutgoing) 16.dp else 4.dp,
                            bottomEnd = if (isOutgoing) 4.dp else 16.dp,
                        )
                    )
                    .background(if (isOutgoing) OutgoingBubble else IncomingBubble)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = if (isOutgoing) OutgoingText else IncomingText,
                        lineHeight = 22.sp,
                    ),
                )
            }

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = timeString,
                style = MaterialTheme.typography.labelSmall.copy(color = DimGray),
            )
        }
    }
}

@Composable
private fun MessageInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Surface(
        color = Navy800,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        text = "Message",
                        style = MaterialTheme.typography.bodyLarge.copy(color = DimGray),
                    )
                },
                singleLine = false,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send,
                ),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Navy600,
                    unfocusedBorderColor = Navy700,
                    focusedContainerColor = Navy900,
                    unfocusedContainerColor = Navy900,
                    cursorColor = BurntOrange,
                    focusedTextColor = OffWhite,
                    unfocusedTextColor = OffWhite,
                ),
                shape = RoundedCornerShape(24.dp),
                textStyle = MaterialTheme.typography.bodyLarge,
            )

            Spacer(modifier = Modifier.width(10.dp))

            val canSend = text.trim().isNotEmpty()
            IconButton(
                onClick = onSend,
                enabled = canSend,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (canSend) BurntOrange else Navy700),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (canSend) White else DimGray,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
