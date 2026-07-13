package com.dumblauncher.app.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.dumblauncher.app.HomeUiState
import com.dumblauncher.app.R
import com.dumblauncher.app.admin.SystemPanelsHelper
import com.dumblauncher.app.data.LaunchableApp
import com.dumblauncher.app.ui.theme.EInkBlack
import com.dumblauncher.app.ui.theme.EInkWhite
import kotlin.math.abs

private val TopEdgeZoneHeight = 72.dp
private val PanelSwipeThreshold = 32.dp
private val AllAppsSwipeThreshold = 120.dp

private val HeaderSpacerHeight = 88.dp
private val HeaderSpacerWithUsageHintHeight = 118.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onLaunch: (LaunchableApp) -> Unit,
    onOpenAllApps: () -> Unit,
    onOpenSettings: () -> Unit,
    onRequestUsageAccess: () -> Unit,
    onOpenClock: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenScreenTime: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var dragStartY by remember { mutableFloatStateOf(0f) }
    var verticalDragAccum by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EInkWhite),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(density) {
                    val topZoneHeightPx = with(density) { TopEdgeZoneHeight.toPx() }
                    val allAppsThresholdPx = with(density) { AllAppsSwipeThreshold.toPx() }

                    detectDragGestures(
                        onDragStart = { offset ->
                            dragStartY = offset.y
                            verticalDragAccum = 0f
                        },
                        onDragEnd = {
                            if (dragStartY > topZoneHeightPx &&
                                abs(verticalDragAccum) > allAppsThresholdPx
                            ) {
                                onOpenAllApps()
                            }
                            verticalDragAccum = 0f
                        },
                        onDragCancel = {
                            verticalDragAccum = 0f
                        },
                        onDrag = { change, dragAmount ->
                            if (dragStartY <= topZoneHeightPx) {
                                return@detectDragGestures
                            }
                            verticalDragAccum += dragAmount.y
                            change.consume()
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
                Spacer(
                    modifier = Modifier.height(
                        if (state.hasUsageAccess) HeaderSpacerHeight else HeaderSpacerWithUsageHintHeight,
                    ),
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (state.favorites.isEmpty()) {
                        Text(
                            text = stringResource(R.string.home_hint),
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

        TopPanelGestureZone(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(TopEdgeZoneHeight)
                .align(Alignment.TopStart)
                .zIndex(1f),
            onOpenPanel = { SystemPanelsHelper.expandNotificationsPanel(context) },
        )

        HomeHeader(
            state = state,
            onOpenClock = onOpenClock,
            onOpenCalendar = onOpenCalendar,
            onOpenScreenTime = onOpenScreenTime,
            onRequestUsageAccess = onRequestUsageAccess,
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .zIndex(2f),
        )
    }
}

@Composable
private fun HomeHeader(
    state: HomeUiState,
    onOpenClock: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenScreenTime: () -> Unit,
    onRequestUsageAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 28.dp, vertical = 24.dp),
    ) {
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
                modifier = Modifier.silentClickable(onClick = onOpenClock),
            )
            if (state.hasUsageAccess) {
                Text(
                    text = state.screenTimeText ?: "0m",
                    color = EInkBlack,
                    fontSize = 22.sp,
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .silentClickable(onClick = onOpenScreenTime),
                )
            } else {
                Text(
                    text = "Tap for screen time",
                    color = EInkBlack,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .silentClickable(onClick = onRequestUsageAccess),
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = state.clock.dateText,
                color = EInkBlack,
                fontSize = 16.sp,
                modifier = Modifier.silentClickable(onClick = onOpenCalendar),
            )
            Text(
                text = ", ${state.batteryPercent}%",
                color = EInkBlack,
                fontSize = 16.sp,
            )
        }

        if (!state.hasUsageAccess) {
            Text(
                text = "Tap to grant usage access for screen time",
                color = EInkBlack,
                fontSize = 14.sp,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .silentClickable(onClick = onRequestUsageAccess),
            )
        }
    }
}

@Composable
private fun Modifier.silentClickable(onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return clickable(
        indication = null,
        interactionSource = interactionSource,
        onClick = onClick,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TopPanelGestureZone(
    modifier: Modifier = Modifier,
    onOpenPanel: () -> Boolean,
) {
    val density = LocalDensity.current
    var panelTriggered by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .combinedClickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = {},
                onLongClick = { onOpenPanel() },
            )
            .pointerInput(density) {
                val thresholdPx = with(density) { PanelSwipeThreshold.toPx() }

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var totalDragY = 0f
                    panelTriggered = false

                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break

                        val dragAmountY = change.position.y - change.previousPosition.y
                        totalDragY += dragAmountY

                        if (!panelTriggered && totalDragY >= thresholdPx) {
                            panelTriggered = true
                            val opened = onOpenPanel()
                            if (opened) {
                                change.consume()
                            }
                            break
                        }
                    } while (change.pressed)
                }
            },
    )
}
