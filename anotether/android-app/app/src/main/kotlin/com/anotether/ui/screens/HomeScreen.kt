package com.anotether.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anotether.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Home Screen.
 *
 * Two choices. Nothing else.
 * Clean, intentional, no clutter.
 */
@Composable
fun HomeScreen(
    onCreateSession: () -> Unit,
    onJoinSession: () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(100)
        visible = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Navy900),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {

                // Wordmark
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "anotether",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.W200,
                            letterSpacing = 3.sp,
                            color = OffWhite,
                        ),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // Status dot — shows app is ready
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(RoundedCornerShape(50))
                                .background(SuccessGreen),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "no account · no trace",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = SubtleGray,
                                letterSpacing = 1.sp,
                            ),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(72.dp))

                // Primary action: Create
                Button(
                    onClick = onCreateSession,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BurntOrange,
                        contentColor = White,
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                ) {
                    Text(
                        text = "Create Session",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.W600,
                        ),
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Secondary action: Join
                OutlinedButton(
                    onClick = onJoinSession,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Navy600),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = OffWhite,
                    ),
                ) {
                    Text(
                        text = "Join Session",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.W400,
                        ),
                    )
                }

                Spacer(modifier = Modifier.height(48.dp))

                Text(
                    text = "Sessions expire in 24 hours.\nNo messages are stored after expiry.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = DimGray,
                        textAlign = TextAlign.Center,
                    ),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
