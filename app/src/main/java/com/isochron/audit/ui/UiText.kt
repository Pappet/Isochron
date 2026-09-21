package com.isochron.audit.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * A piece of display text produced outside the UI layer — a string resource plus
 * its format arguments — resolved only where a [Context] exists.
 *
 * The domain layer used to hand the UI finished German sentences (audit E2); now it
 * hands over a reference, and the resource system picks the language.
 */
class UiText(@StringRes val resId: Int, vararg args: Any) {
    /** Kept as an array so nested [UiText] arguments can be resolved recursively. */
    val args: Array<out Any> = args

    fun resolve(context: Context): String {
        val resolved = args.map { if (it is UiText) it.resolve(context) else it }.toTypedArray()
        return context.getString(resId, *resolved)
    }

    @Composable
    fun asString(): String {
        val context = androidx.compose.ui.platform.LocalContext.current
        return resolve(context)
    }

    override fun equals(other: Any?): Boolean =
        other is UiText && other.resId == resId && other.args.contentEquals(args)

    override fun hashCode(): Int = 31 * resId + args.contentHashCode()
}
