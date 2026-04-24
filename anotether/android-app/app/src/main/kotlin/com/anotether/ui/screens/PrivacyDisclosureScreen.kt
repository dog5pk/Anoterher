package com.anotether.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
 * Privacy Disclosure Screen — shown on first app launch only.
 *
 * Purpose: be honest with users about what this app is and isn't.
 * No dark patterns, no confusing legal text.
 * Plain language disclosures.
 */
@Composable
fun PrivacyDisclosureScreen(
    onAccept: () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(150)
        visible = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Navy950),
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 4 }),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp, vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Wordmark
                Text(
                    text = "anotether",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.W200,
                        letterSpacing = 3.sp,
                        color = OffWhite,
                    ),
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "anonymous · encrypted · ephemeral",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = SubtleGray,
                        letterSpacing = 1.5.sp,
                    ),
                )

                Spacer(modifier = Modifier.height(48.dp))

                // Divider
                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(0.3f),
                    color = Navy600,
                    thickness = 1.dp,
                )

                Spacer(modifier = Modifier.height(40.dp))

                // Disclosure cards
                DisclosureSection(
                    icon = "✓",
                    title = "No accounts. Ever.",
                    body = "You don't register. There are no usernames, phone numbers, or email addresses in this app. Not now, not in a future update.",
                )

                Spacer(modifier = Modifier.height(20.dp))

                DisclosureSection(
                    icon = "✓",
                    title = "End-to-end encrypted.",
                    body = "Your messages are encrypted on your device before they leave. The relay server only ever sees ciphertext it cannot read.",
                )

                Spacer(modifier = Modifier.height(20.dp))

                DisclosureSection(
                    icon = "✓",
                    title = "Sessions are temporary.",
                    body = "Sessions expire after 24 hours. Messages are not stored after expiry. There is no message history across sessions.",
                )

                Spacer(modifier = Modifier.height(20.dp))

                DisclosureSection(
                    icon = "✓",
                    title = "No tracking. No ads.",
                    body = "No analytics. No crash reporters that identify you. No advertising networks. The app does not contact any third-party services.",
                )

                Spacer(modifier = Modifier.height(20.dp))

                DisclosureSection(
                    icon = "⚠",
                    iconColor = BurntOrangeLight,
                    title = "Understand the limits.",
                    body = "This app reduces exposure to casual surveillance and data harvesting. It is not designed to protect against targeted nation-state attacks. Use good judgment.",
                )

                Spacer(modifier = Modifier.height(48.dp))

                // Accept button
                Button(
                    onClick = onAccept,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BurntOrange,
                        contentColor = White,
                    ),
                ) {
                    Text(
                        text = "Understood — Continue",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "You will not see this screen again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DimGray,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun DisclosureSection(
    icon: String,
    title: String,
    body: String,
    iconColor: androidx.compose.ui.graphics.Color = SuccessGreen,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Navy800)
            .padding(20.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = icon,
            style = MaterialTheme.typography.bodyLarge.copy(color = iconColor),
            modifier = Modifier.padding(top = 2.dp),
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(color = OffWhite),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = SubtleGray,
                    lineHeight = 22.sp,
                ),
            )
        }
    }
}
