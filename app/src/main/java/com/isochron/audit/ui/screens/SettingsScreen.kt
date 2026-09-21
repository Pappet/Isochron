package com.isochron.audit.ui.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.isochron.audit.BuildConfig
import com.isochron.audit.R
import com.isochron.audit.data.repository.DeviceRepository
import com.isochron.audit.ui.UiMessageBus
import com.isochron.audit.ui.components.HairlineHorizontal
import com.isochron.audit.ui.components.SpectrumFilterChip
import com.isochron.audit.ui.components.SpectrumKicker
import com.isochron.audit.ui.theme.InterFamily
import com.isochron.audit.ui.theme.JetBrainsMonoFamily
import com.isochron.audit.ui.theme.Spectrum
import com.isochron.audit.util.AppPrefs
import com.isochron.audit.util.openAppSettings
import kotlinx.coroutines.launch

/**
 * Full-screen overlay reachable from every tab's gear (audit F4): permissions,
 * onboarding replay, default monitor interval, wipe all data (G2), attribution.
 */
@Composable
fun SettingsScreen(
    onClose: () -> Unit,
    onReplayOnboarding: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences(AppPrefs.NAME, Context.MODE_PRIVATE) }
    val repository = remember { DeviceRepository(context.applicationContext) }

    var interval by remember {
        mutableIntStateOf(prefs.getInt(AppPrefs.KEY_MONITOR_INTERVAL, AppPrefs.DEFAULT_MONITOR_INTERVAL))
    }
    var confirmWipe by remember { mutableStateOf(false) }

    BackHandler { onClose() }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            containerColor = Spectrum.SurfaceRaised,
            titleContentColor = Spectrum.OnSurface,
            textContentColor = Spectrum.OnSurfaceDim,
            title = { Text(stringResource(R.string.settings_wipe_confirm_title)) },
            text = { Text(stringResource(R.string.settings_wipe_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmWipe = false
                    scope.launch {
                        try {
                            repository.deleteAllData()
                            UiMessageBus.post(R.string.settings_wipe_done)
                        } catch (e: Exception) {
                            UiMessageBus.postError(R.string.settings_wipe_failed, e)
                        }
                    }
                }) {
                    Text(stringResource(R.string.settings_wipe_confirm_btn), color = Spectrum.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmWipe = false }) {
                    Text(stringResource(R.string.btn_cancel), color = Spectrum.OnSurface)
                }
            },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Spectrum.Surface)
            // Drawn over the pager: claim every pointer so a swipe on the header or
            // a blank area cannot reach the pages underneath.
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        // Header
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .size(30.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .border(1.dp, Spectrum.GridLine, RoundedCornerShape(4.dp))
                    .clickable(role = Role.Button) { onClose() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.btn_close), tint = Spectrum.OnSurface, modifier = Modifier.size(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.settings_kicker),
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 11.sp,
                    color = Spectrum.OnSurfaceDim,
                    letterSpacing = 0.18.em,
                )
                Text(
                    stringResource(R.string.settings_title),
                    fontFamily = InterFamily,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    color = Spectrum.OnSurface,
                )
            }
        }
        HairlineHorizontal()

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // Permissions
            SettingsSection(stringResource(R.string.settings_section_permissions)) {
                SettingsAction(
                    title = stringResource(R.string.settings_manage_permissions),
                    subtitle = stringResource(R.string.settings_manage_permissions_desc),
                    onClick = { context.openAppSettings() },
                )
                SettingsAction(
                    title = stringResource(R.string.settings_replay_onboarding),
                    subtitle = stringResource(R.string.settings_replay_onboarding_desc),
                    onClick = {
                        prefs.edit().putBoolean(AppPrefs.KEY_ONBOARDING_COMPLETE, false).apply()
                        onReplayOnboarding()
                    },
                )
            }

            // Monitoring default
            SettingsSection(stringResource(R.string.settings_section_monitoring)) {
                Text(
                    stringResource(R.string.settings_interval_desc),
                    fontFamily = InterFamily,
                    fontSize = 13.sp,
                    color = Spectrum.OnSurfaceDim,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        5 to stringResource(R.string.mon_interval_5s),
                        10 to stringResource(R.string.mon_interval_10s),
                        30 to stringResource(R.string.mon_interval_30s),
                        60 to stringResource(R.string.mon_interval_1m),
                    ).forEach { (seconds, label) ->
                        SpectrumFilterChip(
                            label = label,
                            selected = interval == seconds,
                            onClick = {
                                interval = seconds
                                prefs.edit().putInt(AppPrefs.KEY_MONITOR_INTERVAL, seconds).apply()
                            },
                        )
                    }
                }
            }

            // Data
            SettingsSection(stringResource(R.string.settings_section_data)) {
                SettingsAction(
                    title = stringResource(R.string.settings_wipe),
                    subtitle = stringResource(R.string.settings_wipe_desc),
                    danger = true,
                    onClick = { confirmWipe = true },
                )
            }

            // About
            SettingsSection(stringResource(R.string.settings_section_about)) {
                Text(
                    stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 11.sp,
                    color = Spectrum.OnSurfaceDim,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.settings_attribution),
                    fontFamily = InterFamily,
                    fontSize = 13.sp,
                    color = Spectrum.OnSurfaceDim,
                    lineHeight = 19.sp,
                )
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
        SpectrumKicker(title, color = Spectrum.OnSurfaceDim)
        Spacer(Modifier.height(10.dp))
        content()
    }
    HairlineHorizontal()
}

@Composable
private fun SettingsAction(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Text(
            title,
            fontFamily = InterFamily,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = if (danger) Spectrum.Danger else Spectrum.OnSurface,
        )
        Text(
            subtitle,
            fontFamily = InterFamily,
            fontSize = 13.sp,
            color = Spectrum.OnSurfaceDim,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
