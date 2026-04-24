package com.anotether.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anotether.ui.theme.*
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * Token Screen — shown to the session initiator while waiting for a peer.
 *
 * Displays the 6-character session code prominently.
 * Shows a countdown to session expiry.
 * Transitions automatically to chat when peer joins.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokenScreen(
    token: String,
    expiresAt: Long,
    peerJoined: Boolean,
    onBack: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    var timeRemaining by remember { mutableStateOf("") }

    // Update countdown every second
    LaunchedEffect(expiresAt) {
        while (true) {
            val nowSecs = System.currentTimeMillis() / 1000
            val remaining = max(0L, expiresAt - nowSecs)
            val hours = remaining / 3600
            val minutes = (remaining % 3600) / 60
            val seconds = remaining % 60
            timeRemaining = if (hours > 0) {
                "%02d:%02d:%02d".format(hours, minutes, seconds)
            } else {
                "%02d:%02d".format(minutes, seconds)
            }
            delay(1000)
        }
    }

    // Reset "Copied" indicator
    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = SubtleGray,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Navy900,
                ),
            )
        },
        containerColor = Navy900,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Share this code",
                style = MaterialTheme.typography.headlineMedium.copy(
                    color = OffWhite,
                    fontWeight = FontWeight.W300,
                ),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Give it to the person you want to connect with.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = SubtleGray,
                    textAlign = TextAlign.Center,
                ),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Token display — large, monospaced, tappable to copy
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Navy800)
                    .border(1.dp, Navy600, RoundedCornerShape(14.dp))
                    .clickable {
                        clipboardManager.setText(AnnotatedString(token))
                        copied = true
                    }
                    .padding(vertical = 36.dp, horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Token with wide spacing for readability
                    Text(
                        text = token.chunked(3).joinToString("  "),
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.W600,
                            fontSize = 42.sp,
                            letterSpacing = 8.sp,
                            color = BurntOrangeLight,
                        ),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    AnimatedContent(
                        targetState = copied,
                        transitionSpec = {
                            fadeIn() togetherWith fadeOut()
                        },
                        label = "copy_label",
                    ) { isCopied ->
                        Text(
                            text = if (isCopied) "Copied to clipboard" else "Tap to copy",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = if (isCopied) SuccessGreen else DimGray,
                            ),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Waiting indicator
            AnimatedContent(
                targetState = peerJoined,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "peer_status",
            ) { joined ->
                if (!joined) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Pulsing indicator
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .clip(RoundedCornerShape(4.dp)),
                            color = BurntOrange,
                            trackColor = Navy700,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Waiting for someone to join…",
                            style = MaterialTheme.typography.bodyMedium.copy(color = SubtleGray),
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(50))
                                .background(SuccessGreen),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Peer joined — entering chat",
                            style = MaterialTheme.typography.bodyMedium.copy(color = SuccessGreen),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Expiry countdown
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Navy800)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Session expires in",
                    style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray),
                )
                Text(
                    text = timeRemaining,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        color = OffWhite,
                    ),
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
