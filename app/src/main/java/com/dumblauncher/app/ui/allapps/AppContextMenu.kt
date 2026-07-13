package com.dumblauncher.app.ui.allapps

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dumblauncher.app.data.LaunchableApp
import com.dumblauncher.app.ui.theme.EInkBlack
import com.dumblauncher.app.ui.theme.EInkWhite

@Composable
fun AppContextMenu(
    app: LaunchableApp,
    onHide: () -> Unit,
    onRename: () -> Unit,
    onUninstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(4.dp))
                .background(EInkWhite)
                .padding(vertical = 8.dp),
        ) {
            Text(
                text = app.label,
                color = EInkBlack,
                fontSize = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )

            MenuAction(
                icon = { Icon(Icons.Outlined.VisibilityOff, contentDescription = null, tint = EInkBlack) },
                label = "Hide from app list",
                onClick = {
                    onHide()
                    onDismiss()
                },
            )
            MenuAction(
                icon = { Icon(Icons.Outlined.Edit, contentDescription = null, tint = EInkBlack) },
                label = "Rename",
                onClick = {
                    onRename()
                    onDismiss()
                },
            )
            MenuAction(
                icon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = EInkBlack) },
                label = "Uninstall",
                onClick = {
                    onUninstall()
                    onDismiss()
                },
            )
        }
    }
}

@Composable
private fun MenuAction(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            icon()
        }
        Text(
            text = label,
            color = EInkBlack,
            fontSize = 18.sp,
        )
    }
}
