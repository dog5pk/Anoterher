package com.anotether.ui

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anotether.data.AppPreferences
import com.anotether.session.SessionState
import com.anotether.ui.screens.*

/**
 * Navigation root for the Anotether app.
 *
 * We use a simple sealed class route system rather than a NavHost.
 * The app has a linear flow with very few branches — NavGraph would be overkill.
 *
 * Route transitions are driven by [SessionState] changes in [MainViewModel]
 * combined with local UI state for the join screen.
 */
sealed class AppRoute {
    object PrivacyDisclosure : AppRoute()
    object Home : AppRoute()
    object Token : AppRoute()     // Initiator waiting for peer
    object Join : AppRoute()      // Joiner entering code
    object Chat : AppRoute()
    data class SessionEnded(val reason: SessionEndReason) : AppRoute()
}

@Composable
fun AnotetherApp(
    preferences: AppPreferences,
    viewModel: MainViewModel = viewModel(),
) {
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val joinError by viewModel.joinError.collectAsStateWithLifecycle()
    val isJoining by viewModel.isJoining.collectAsStateWithLifecycle()
    val hasSeenDisclosure by preferences.hasSeenPrivacyDisclosure.collectAsStateWithLifecycle(
        initialValue = false
    )

    // Local route state — starts at disclosure if first launch, else home
    var currentRoute by rememberSaveable {
        mutableStateOf<AppRoute>(AppRoute.Home)
    }

    // Watch for disclosure flag on startup
    LaunchedEffect(hasSeenDisclosure) {
        if (!hasSeenDisclosure) {
            currentRoute = AppRoute.PrivacyDisclosure
        }
    }

    // Drive navigation from session state changes
    LaunchedEffect(sessionState) {
        when (sessionState) {
            is SessionState.WaitingForPeer -> {
                currentRoute = AppRoute.Token
            }
            is SessionState.Active -> {
                currentRoute = AppRoute.Chat
            }
            is SessionState.Expired -> {
                currentRoute = AppRoute.SessionEnded(SessionEndReason.Expired)
            }
            is SessionState.Closed -> {
                currentRoute = AppRoute.SessionEnded(SessionEndReason.Closed)
            }
            else -> { /* no automatic navigation */ }
        }
    }

    // Render current route
    when (val route = currentRoute) {
        is AppRoute.PrivacyDisclosure -> {
            PrivacyDisclosureScreen(
                onAccept = {
                    kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        preferences.markPrivacyDisclosureSeen()
                    }
                    currentRoute = AppRoute.Home
                },
            )
        }

        is AppRoute.Home -> {
            HomeScreen(
                onCreateSession = {
                    viewModel.createSession()
                    // Route change driven by sessionState → WaitingForPeer
                },
                onJoinSession = {
                    currentRoute = AppRoute.Join
                },
            )
        }

        is AppRoute.Token -> {
            val state = sessionState as? SessionState.WaitingForPeer
            if (state != null) {
                TokenScreen(
                    token = state.token,
                    expiresAt = state.expiresAt,
                    peerJoined = false, // transitions to Chat via sessionState → Active
                    onBack = {
                        viewModel.endSession()
                        currentRoute = AppRoute.Home
                    },
                )
            }
        }

        is AppRoute.Join -> {
            JoinScreen(
                isJoining = isJoining,
                errorMessage = joinError,
                onJoin = { token -> viewModel.joinSession(token) },
                onBack = { currentRoute = AppRoute.Home },
            )
        }

        is AppRoute.Chat -> {
            val state = sessionState as? SessionState.Active
            if (state != null) {
                ChatScreen(
                    token = state.token,
                    messages = messages,
                    expiresAt = state.expiresAt,
                    onSendMessage = { text -> viewModel.sendMessage(text) },
                    onEndSession = { viewModel.endSession() },
                )
            }
        }

        is AppRoute.SessionEnded -> {
            SessionEndedScreen(
                reason = route.reason,
                onStartNew = {
                    currentRoute = AppRoute.Home
                },
            )
        }
    }
}

// Temporary: allow calling coroutines from non-suspend context in onAccept
// This is only used for preferences — acceptable usage
private fun kotlinx.coroutines.CoroutineScope.launch(
    context: kotlin.coroutines.CoroutineContext,
    block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit
): kotlinx.coroutines.Job = kotlinx.coroutines.launch(context = context, block = block)
