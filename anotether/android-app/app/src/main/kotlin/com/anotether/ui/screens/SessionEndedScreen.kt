package com.anotether.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anotether.ui.theme.*

/**
 * Shown when a session has expired or been closed.
 *
 * Clean termination screen. No history. No recovery.
 * This is by design — sessions are ephemeral.
 */
@Composable
fun SessionEndedScreen(
    reason: SessionEndReason,
    onStartNew: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Navy950),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {

            // Icon indicator
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(Navy800, RoundedCornerShape(50)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (reason == SessionEndReason.Expired) "⧗" else "✕",
                    fontSize = 28.sp,
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = when (reason) {
                    SessionEndReason.Expired -> "Session Expired"
                    SessionEndReason.Closed -> "Session Ended"
                    SessionEndReason.PeerClosed -> "Session Closed"
                },
                style = MaterialTheme.typography.headlineMedium.copy(
                    color = OffWhite,
                    fontWeight = FontWeight.W300,
                ),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = when (reason) {
                    SessionEndReason.Expired ->
                        "This session has expired. All messages have been deleted from the relay."
                    SessionEndReason.Closed ->
                        "You ended this session. All messages have been deleted."
                    SessionEndReason.PeerClosed ->
                        "The other party ended this session. All messages have been deleted."
                },
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = SubtleGray,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                ),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = onStartNew,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BurntOrange,
                    contentColor = White,
                ),
            ) {
                Text(
                    text = "Start New Session",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

enum class SessionEndReason {
    /** 24h TTL elapsed */
    Expired,
    /** This user closed the session */
    Closed,
    /** The peer closed the session */
    PeerClosed,
}
