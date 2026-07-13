package com.dumblauncher.app.ui.allapps

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dumblauncher.app.AllAppsUiState
import com.dumblauncher.app.data.LaunchableApp
import com.dumblauncher.app.ui.theme.EInkBlack
import com.dumblauncher.app.ui.theme.EInkWhite

/**
 * Full app list. Search field is intentionally invisible —
 * typing (soft keyboard) filters the list as characters are entered.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AllAppsScreen(
    state: AllAppsUiState,
    onQueryChange: (String) -> Unit,
    onLaunch: (LaunchableApp) -> Unit,
    onHide: (LaunchableApp) -> Unit,
    onRename: (LaunchableApp, String) -> Unit,
    onUninstall: (LaunchableApp) -> Unit,
    onBack: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    var dragAccum by remember { mutableFloatStateOf(0f) }
    var menuApp by remember { mutableStateOf<LaunchableApp?>(null) }
    var renameApp by remember { mutableStateOf<LaunchableApp?>(null) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(state.query, state.apps) {
        val query = state.query.trim()
        if (query.isNotEmpty() && state.apps.size == 1) {
            onLaunch(state.apps.first())
            onQueryChange("")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EInkWhite)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dragAccum < -120f) {
                            onBack()
                        }
                        dragAccum = 0f
                    },
                    onDragCancel = { dragAccum = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        dragAccum += dragAmount
                    },
                )
            },
    ) {
        // Hidden type-ahead input — no visible search field.
        BasicTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .size(1.dp)
                .focusRequester(focusRequester),
            textStyle = TextStyle(color = EInkWhite, fontSize = 1.sp),
            cursorBrush = SolidColor(EInkWhite),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                imeAction = ImeAction.Done,
                autoCorrectEnabled = false,
            ),
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            contentPadding = PaddingValues(top = 24.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(state.apps, key = { it.key }) { app ->
                Text(
                    text = app.label,
                    color = EInkBlack,
                    fontSize = 24.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { onLaunch(app) },
                            onLongClick = { menuApp = app },
                        ),
                )
            }
        }
    }

    menuApp?.let { app ->
        AppContextMenu(
            app = app,
            onHide = { onHide(app) },
            onRename = { renameApp = app },
            onUninstall = { onUninstall(app) },
            onDismiss = { menuApp = null },
        )
    }

    renameApp?.let { app ->
        RenameAppDialog(
            app = app,
            onConfirm = { newLabel -> onRename(app, newLabel) },
            onDismiss = { renameApp = null },
        )
    }
}
