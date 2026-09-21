package com.isochron.audit.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.isochron.audit.R
import com.isochron.audit.ui.theme.Spectrum
import com.isochron.audit.util.openAppSettings
import com.isochron.audit.util.openWifiSettings

/**
 * Runtime-permission state for a scan tab, with the two behaviours every tab needs
 * and used to reimplement individually:
 *
 * - Once the user denied twice, Android drops further requests silently. We remember
 *   that we already asked, so [permanentlyDenied] can route the banner to the system
 *   settings instead of leaving a dead button (audit A3).
 * - After a grant the tab used to sit idle until the user tapped "Scan" a second time.
 *   [request] marks the scan as pending, and [rememberScanPermissions] fires the
 *   `onGranted` callback as soon as all permissions are in (audit C3).
 */
@OptIn(ExperimentalPermissionsApi::class)
@Stable
class ScanPermissions internal constructor(
    val state: MultiplePermissionsState,
    private val requested: MutableState<Boolean>,
    internal val pending: MutableState<Boolean>,
) {
    val allGranted: Boolean get() = state.allPermissionsGranted

    val permanentlyDenied: Boolean
        get() = requested.value && !allGranted && !state.shouldShowRationale

    /** Asks the system; the pending action runs once everything is granted. */
    fun request() {
        requested.value = true
        pending.value = true
        state.launchMultiplePermissionRequest()
    }

    /** Runs [action] immediately when granted, otherwise asks and defers it. */
    fun runOrRequest(action: () -> Unit) {
        if (allGranted) action() else request()
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun rememberScanPermissions(
    permissions: List<String>,
    onGranted: () -> Unit,
): ScanPermissions {
    val state = rememberMultiplePermissionsState(permissions)
    val requested = rememberSaveable { mutableStateOf(false) }
    val pending = rememberSaveable { mutableStateOf(false) }
    val scanPermissions = remember(state) { ScanPermissions(state, requested, pending) }

    val currentOnGranted by rememberUpdatedState(onGranted)
    LaunchedEffect(state.allPermissionsGranted, pending.value) {
        if (pending.value && state.allPermissionsGranted) {
            pending.value = false
            currentOnGranted()
        }
    }
    return scanPermissions
}

/**
 * The standard "permission missing" strip. Shows nothing when everything is granted;
 * otherwise [text] with a request button, or the permanent-denial hint with a link to
 * the system settings.
 */
@Composable
fun PermissionBanner(
    permissions: ScanPermissions,
    text: String,
    color: androidx.compose.ui.graphics.Color = Spectrum.Danger,
) {
    if (permissions.allGranted) return
    val context = LocalContext.current
    val permanentlyDenied = permissions.permanentlyDenied
    SpectrumBanner(
        text = if (permanentlyDenied) stringResource(R.string.perm_denied_permanently) else text,
        color = color,
        action = if (permanentlyDenied) {
            stringResource(R.string.perm_open_settings)
        } else {
            stringResource(R.string.perm_grant_btn)
        },
        onAction = {
            if (permanentlyDenied) context.openAppSettings() else permissions.request()
        },
    )
}

/** "Wi-Fi is off" strip with a jump into the system Wi-Fi settings (audit C2). */
@Composable
fun WifiDisabledBanner(text: String = stringResource(R.string.wifi_disabled_warn)) {
    val context = LocalContext.current
    SpectrumBanner(
        text = text,
        color = Spectrum.Warning,
        action = stringResource(R.string.wifi_open_settings),
        onAction = { context.openWifiSettings() },
    )
}
