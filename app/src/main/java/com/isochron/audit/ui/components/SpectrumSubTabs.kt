package com.isochron.audit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.isochron.audit.R
import com.isochron.audit.ui.theme.JetBrainsMonoFamily
import com.isochron.audit.ui.theme.Spectrum

/**
 * Second navigation level under a bottom-nav destination: uppercase mono labels with
 * the accent underline of the bottom nav, plus the settings gear at the right edge
 * so every screen has the same way into the settings (audit F1/F4).
 */
@Composable
fun SpectrumSubTabs(
    tabs: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().background(Spectrum.Surface)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, label ->
                val isSel = index == selected
                Box(
                    Modifier
                        .minimumInteractiveComponentSize()
                        .selectable(selected = isSel, onClick = { onSelect(index) }, role = Role.Tab)
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            label,
                            color = if (isSel) Spectrum.Accent else Spectrum.OnSurfaceDim,
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            letterSpacing = 0.12.em,
                        )
                        Box(
                            Modifier
                                .padding(top = 4.dp)
                                .width(18.dp)
                                .height(2.dp)
                                .background(if (isSel) Spectrum.Accent else Spectrum.Surface),
                        )
                    }
                }
            }
            Box(Modifier.weight(1f))
            Box(
                Modifier
                    .minimumInteractiveComponentSize()
                    .clickable(role = Role.Button, onClick = onOpenSettings),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Settings,
                    contentDescription = stringResource(R.string.settings_title),
                    tint = Spectrum.OnSurfaceDim,
                    modifier = Modifier.width(18.dp).height(18.dp),
                )
            }
        }
        HairlineHorizontal()
    }
}
