package com.dumblauncher.app.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dumblauncher.app.HomeUiState
import com.dumblauncher.app.data.LaunchableApp
import com.dumblauncher.app.ui.theme.EInkBlack
import com.dumblauncher.app.ui.theme.EInkWhite
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onLaunch: (LaunchableApp) -> Unit,
    onOpenAllApps: () -> Unit,
    onOpenSettings: () -> Unit,
    onRequestUsageAccess: () -> Unit,
) {
    var dragAccum by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EInkWhite)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (abs(dragAccum) > 120f) {
                            onOpenAllApps()
                        }
                        dragAccum = 0f
                    },
                    onDragCancel = { dragAccum = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        dragAccum += dragAmount
                    },
                )
            }
            .combinedClickable(
                onClick = {},
                onLongClick = onOpenSettings,
            )
            .padding(horizontal = 28.dp, vertical = 24.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = state.clock.timeText,
                    color = EInkBlack,
                    fontSize = 56.sp,
                    lineHeight = 60.sp,
                )
                if (state.hasUsageAccess) {
                    Text(
                        text = state.screenTimeText ?: "0m",
                        color = EInkBlack,
                        fontSize = 22.sp,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                } else {
                    Text(
                        text = "Tap for screen time",
                        color = EInkBlack,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .clickable(onClick = onRequestUsageAccess),
                    )
                }
            }

            Text(
                text = "${state.clock.dateText}, ${state.batteryPercent}%",
                color = EInkBlack,
                fontSize = 16.sp,
            )

            if (!state.hasUsageAccess) {
                Text(
                    text = "Tap to grant usage access for screen time",
                    color = EInkBlack,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clickable(onClick = onRequestUsageAccess),
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (state.favorites.isEmpty()) {
                    Text(
                        text = "Long-press for settings",
                        color = EInkBlack,
                        fontSize = 22.sp,
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        state.favorites.forEach { app ->
                            Text(
                                text = app.label,
                                color = EInkBlack,
                                fontSize = 28.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = { onLaunch(app) },
                                        onLongClick = onOpenSettings,
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}
