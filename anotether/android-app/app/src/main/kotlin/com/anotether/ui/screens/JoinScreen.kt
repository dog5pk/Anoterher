package com.anotether.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anotether.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Join Screen — where the second user enters the session token.
 *
 * The token field auto-uppercases and limits to 6 chars.
 * Show inline error if the token is rejected by the relay.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinScreen(
    isJoining: Boolean,
    errorMessage: String?,
    onJoin: (token: String) -> Unit,
    onBack: () -> Unit,
) {
    var tokenInput by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        delay(200)
        focusRequester.requestFocus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isJoining) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = SubtleGray,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Navy900),
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
            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = "Enter session code",
                style = MaterialTheme.typography.headlineMedium.copy(
                    color = OffWhite,
                    fontWeight = FontWeight.W300,
                ),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Type the 6-character code shared with you.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = SubtleGray,
                    textAlign = TextAlign.Center,
                ),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Token input field
            OutlinedTextField(
                value = tokenInput,
                onValueChange = { raw ->
                    // Uppercase, strip non-alphanumeric, limit to 6 chars
                    val cleaned = raw.uppercase()
                        .filter { it.isLetterOrDigit() }
                        .take(6)
                    tokenInput = cleaned
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                textStyle = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.W600,
                    fontSize = 32.sp,
                    letterSpacing = 8.sp,
                    textAlign = TextAlign.Center,
                    color = BurntOrangeLight,
                ),
                placeholder = {
                    Text(
                        text = "XXXXXX",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 32.sp,
                            letterSpacing = 8.sp,
                            textAlign = TextAlign.Center,
                            color = Navy600,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Go,
                    autoCorrect = false,
                ),
                keyboardActions = KeyboardActions(
                    onGo = {
                        if (tokenInput.length == 6 && !isJoining) {
                            keyboard?.hide()
                            onJoin(tokenInput)
                        }
                    },
                ),
                isError = errorMessage != null,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BurntOrange,
                    unfocusedBorderColor = Navy600,
                    errorBorderColor = ErrorRed,
                    focusedContainerColor = Navy800,
                    unfocusedContainerColor = Navy800,
                    errorContainerColor = Navy800,
                    cursorColor = BurntOrange,
                ),
                shape = RoundedCornerShape(12.dp),
            )

            // Error message
            AnimatedVisibility(visible = errorMessage != null) {
                errorMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall.copy(color = ErrorRed),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    keyboard?.hide()
                    onJoin(tokenInput)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = tokenInput.length == 6 && !isJoining,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BurntOrange,
                    contentColor = White,
                    disabledContainerColor = Navy700,
                    disabledContentColor = DimGray,
                ),
            ) {
                if (isJoining) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        text = "Join",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}
