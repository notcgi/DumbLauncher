package com.dumblauncher.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dumblauncher.app.SettingsUiState
import com.dumblauncher.app.data.FavoritesStore
import com.dumblauncher.app.data.LaunchableApp
import com.dumblauncher.app.ui.theme.EInkBlack
import com.dumblauncher.app.ui.theme.EInkWhite

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onFavoriteCountChange: (Int) -> Unit,
    onHideSelfChange: (Boolean) -> Unit,
    onToggleFavorite: (LaunchableApp) -> Unit,
    onMoveFavorite: (String, Int) -> Unit,
    onUnhideApp: (String) -> Unit,
    onResetAppLabel: (String) -> Unit,
    onOpenUsageAccess: () -> Unit,
) {
    var filter by remember { mutableStateOf("") }
    val byKey = remember(state.allApps) { state.allApps.associateBy { it.key } }
    val selectedApps = state.favoriteKeys.mapNotNull { byKey[it] }
    val filteredApps = remember(state.allApps, filter) {
        val q = filter.trim().lowercase()
        if (q.isEmpty()) state.allApps
        else state.allApps.filter { it.label.lowercase().contains(q) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EInkWhite)
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Back",
                color = EInkBlack,
                fontSize = 18.sp,
                modifier = Modifier.clickable(onClick = onBack),
            )
            Text(
                text = "Settings",
                color = EInkBlack,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(1.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Favorites count: ${state.favoriteCount}",
            color = EInkBlack,
            fontSize = 16.sp,
        )
        Slider(
            value = state.favoriteCount.toFloat(),
            onValueChange = { onFavoriteCountChange(it.toInt()) },
            valueRange = FavoritesStore.MIN_COUNT.toFloat()..FavoritesStore.MAX_COUNT.toFloat(),
            steps = FavoritesStore.MAX_COUNT - FavoritesStore.MIN_COUNT - 1,
            colors = SliderDefaults.colors(
                thumbColor = EInkBlack,
                activeTrackColor = EInkBlack,
                inactiveTrackColor = EInkBlack.copy(alpha = 0.3f),
            ),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Hide DumbLauncher",
                color = EInkBlack,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = state.hideSelf,
                onCheckedChange = onHideSelfChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = EInkWhite,
                    checkedTrackColor = EInkBlack,
                    uncheckedThumbColor = EInkBlack,
                    uncheckedTrackColor = EInkWhite,
                    uncheckedBorderColor = EInkBlack,
                ),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (!state.hasUsageAccess) {
            Text(
                text = "Open usage access settings",
                color = EInkBlack,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenUsageAccess)
                    .padding(vertical = 8.dp),
            )
        } else {
            Text(
                text = "Usage access granted",
                color = EInkBlack,
                fontSize = 16.sp,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = EInkBlack)
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Favorites (${selectedApps.size}/${state.favoriteCount})",
            color = EInkBlack,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))

        selectedApps.forEachIndexed { index, app ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = app.label,
                    color = EInkBlack,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "Up",
                    color = EInkBlack,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clickable(enabled = index > 0) {
                            onMoveFavorite(app.key, -1)
                        }
                        .padding(horizontal = 8.dp),
                )
                Text(
                    text = "Down",
                    color = EInkBlack,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clickable(enabled = index < selectedApps.lastIndex) {
                            onMoveFavorite(app.key, 1)
                        }
                        .padding(horizontal = 8.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = EInkBlack)
        Spacer(modifier = Modifier.height(12.dp))

        if (state.hiddenApps.isNotEmpty()) {
            Text(
                text = "Hidden apps (tap to show)",
                color = EInkBlack,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            state.hiddenApps.forEach { app ->
                Text(
                    text = app.label,
                    color = EInkBlack,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onUnhideApp(app.key) }
                        .padding(vertical = 8.dp),
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = EInkBlack)
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (state.renamedApps.isNotEmpty()) {
            Text(
                text = "Renamed apps (tap to reset)",
                color = EInkBlack,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            state.renamedApps.forEach { app ->
                Text(
                    text = app.label,
                    color = EInkBlack,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onResetAppLabel(app.key) }
                        .padding(vertical = 8.dp),
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = EInkBlack)
            Spacer(modifier = Modifier.height(12.dp))
        }

        Text(
            text = "All apps (tap to toggle)",
            color = EInkBlack,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )

        BasicTextField(
            value = filter,
            onValueChange = { filter = it },
            singleLine = true,
            textStyle = TextStyle(color = EInkBlack, fontSize = 16.sp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            decorationBox = { inner ->
                if (filter.isEmpty()) {
                    Text("Filter…", color = EInkBlack.copy(alpha = 0.4f), fontSize = 16.sp)
                }
                inner()
            },
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(filteredApps, key = { it.key }) { app ->
                val selected = state.favoriteKeys.contains(app.key)
                val mark = if (selected) "[x] " else "[ ] "
                Text(
                    text = mark + app.label,
                    color = EInkBlack,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleFavorite(app) }
                        .padding(vertical = 8.dp),
                )
            }
        }
    }
}
