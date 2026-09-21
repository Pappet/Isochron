package com.isochron.audit.ui

import android.content.Context
import androidx.annotation.StringRes
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * A user-facing message, resolved against string resources at display time so that
 * ViewModels and the foreground service can post without holding a [Context].
 */
class UiMessage(@StringRes val resId: Int, private val args: Array<out Any>) {
    fun resolve(context: Context): String = context.getString(resId, *args)
}

/**
 * Process-wide channel for error and status messages that the root Scaffold shows as a
 * Snackbar. Replaces the silent `Log.e` paths the usability audit flagged as C1.
 *
 * A single bus (instead of one flow per ViewModel) keeps the wiring trivial: the app has
 * exactly one Scaffold, and every screen is a page of it.
 */
object UiMessageBus {
    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 16)
    val messages: SharedFlow<UiMessage> = _messages

    fun post(@StringRes resId: Int, vararg args: Any) {
        _messages.tryEmit(UiMessage(resId, args))
    }

    /** Convenience for `catch` blocks: the exception's message becomes the format argument. */
    fun postError(@StringRes resId: Int, e: Throwable, fallback: String = "?") {
        post(resId, e.message?.takeIf { it.isNotBlank() } ?: fallback)
    }
}
