package com.facelook.pause

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent

/**
 * High-reliability Universal Media Controller.
 * Dispatches pause/play across YouTube, Netflix, Spotify, Chrome, MX Player:
 * 1. Targeted MediaSession transportControls (if active session available)
 * 2. Universal AudioManager Media Key Events (KEYCODE_MEDIA_PAUSE / KEYCODE_MEDIA_PLAY)
 */
class MediaPlaybackController(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val tag = "FaceLookMedia"

    /**
     * Pauses currently playing media across all media apps.
     */
    fun pausePlayback() {
        Log.i(tag, "Executing universal MEDIA_PAUSE dispatch")
        var handledBySession = false

        // Strategy 1: Targeted MediaSession Transport Controls
        try {
            val sessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            val sessions = sessionManager?.getActiveSessions(null)
            if (!sessions.isNullOrEmpty()) {
                for (controller in sessions) {
                    val state = controller.playbackState?.state
                    if (state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_FAST_FORWARDING) {
                        controller.transportControls.pause()
                        Log.i(tag, "Targeted MediaSession paused for: ${controller.packageName}")
                        handledBySession = true
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "MediaSession pause fallback to KeyEvent: ${e.message}")
        }

        // Strategy 2: Global Hardware Media Key Events
        if (!handledBySession) {
            try {
                val eventDown = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE)
                val eventUp = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE)
                audioManager.dispatchMediaKeyEvent(eventDown)
                audioManager.dispatchMediaKeyEvent(eventUp)
                Log.i(tag, "Dispatched KEYCODE_MEDIA_PAUSE key events")
            } catch (e: Exception) {
                Log.e(tag, "Error dispatching MEDIA_PAUSE key event: ${e.message}", e)
            }
        }
    }

    /**
     * Resumes media playback when user looks back at screen.
     */
    fun resumePlayback() {
        Log.i(tag, "Executing universal MEDIA_PLAY dispatch")
        var handledBySession = false

        // Strategy 1: Targeted MediaSession Transport Controls
        try {
            val sessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            val sessions = sessionManager?.getActiveSessions(null)
            if (!sessions.isNullOrEmpty()) {
                for (controller in sessions) {
                    val state = controller.playbackState?.state
                    if (state == PlaybackState.STATE_PAUSED || state == PlaybackState.STATE_STOPPED) {
                        controller.transportControls.play()
                        Log.i(tag, "Targeted MediaSession resumed for: ${controller.packageName}")
                        handledBySession = true
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "MediaSession resume fallback to KeyEvent: ${e.message}")
        }

        // Strategy 2: Global Hardware Media Key Events
        if (!handledBySession) {
            try {
                val eventDown = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY)
                val eventUp = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY)
                audioManager.dispatchMediaKeyEvent(eventDown)
                audioManager.dispatchMediaKeyEvent(eventUp)
                Log.i(tag, "Dispatched KEYCODE_MEDIA_PLAY key events")
            } catch (e: Exception) {
                Log.e(tag, "Error dispatching MEDIA_PLAY key event: ${e.message}", e)
            }
        }
    }

    /**
     * Checks if any audio/video stream is currently actively playing.
     */
    fun isMediaPlaying(): Boolean {
        // Check AudioManager stream state
        if (audioManager.isMusicActive) return true

        // Check MediaSession active state
        try {
            val sessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            val sessions = sessionManager?.getActiveSessions(null)
            if (!sessions.isNullOrEmpty()) {
                for (controller in sessions) {
                    val state = controller.playbackState?.state
                    if (state == PlaybackState.STATE_PLAYING) return true
                }
            }
        } catch (_: Exception) {}

        return false
    }
}
