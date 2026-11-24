package io.github.pedropasinn.mobilepad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pedropasinn.mobilepad.GameProfile
import io.github.pedropasinn.mobilepad.R
import io.github.pedropasinn.mobilepad.str

/**
 * Game profiles: each card is a saved setup with its miniature; a tap puts it to use. While one
 * is in use, changes made anywhere are saved into it, so it is edited simply by using it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProfilesPane(c: ControllerPanel) {
    var naming by remember { mutableStateOf<GameProfile?>(null) }   // rename
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<GameProfile?>(null) }
    val active = c.profiles.firstOrNull { it.id == c.activeProfile }

    CardGrid(buildList {
        c.profiles.forEach { prof ->
            add { m: Modifier -> ProfileCard(c, prof, prof.id == c.activeProfile, m) { c.onApplyProfile(prof.id) } }
        }
        add { m: Modifier -> NewProfileCard(m) { creating = true } }
    })
    if (active != null) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { naming = active }) { Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp)); Text(" " + str(R.string.profiles_rename)) }
            OutlinedButton(onClick = c.onDetachProfile) { Icon(Icons.Rounded.LinkOff, null, Modifier.size(18.dp)); Text(" " + str(R.string.profiles_detach)) }
            TextButton(onClick = { deleting = active }) { Icon(Icons.Rounded.Delete, null, Modifier.size(18.dp), tint = Danger); Text(" " + str(R.string.profiles_delete), color = Danger) }
        }
    }
    Note(str(R.string.profiles_note))

    if (creating) NameDialog(str(R.string.profiles_new), str(R.string.profiles_default_name), { creating = false }) { c.onNewProfile(it); creating = false }
    naming?.let { p -> NameDialog(str(R.string.profiles_rename), p.name, { naming = null }) { c.onRenameProfile(p.id, it); naming = null } }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(str(R.string.profiles_delete_confirm, p.name)) },
            confirmButton = { TextButton(onClick = { c.onDeleteProfile(p.id); deleting = null }) { Text(str(R.string.profiles_delete), color = Danger) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(str(R.string.profiles_cancel)) } },
        )
    }
}

@Composable
private fun ProfileCard(c: ControllerPanel, p: GameProfile, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val pal = LocalPalette.current
    val delta = c.skins.firstOrNull { it.dir.name == p.deltaSkin }
    val style = if (delta != null) rememberDeltaStyle(delta) else {
        val id = runCatching { SkinId.valueOf(p.skin) }.getOrDefault(SkinId.VIOLET)
        builtInStyle(id, str(id.nameRes))
    }
    Column(
        modifier.glass(RoundedCornerShape(18.dp), selected).clickable(onClick = onClick).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (style != null) ControllerPreview(style, Modifier.fillMaxWidth(), layout = PadLayout.decode(p.layoutLand), onClick = onClick)
        Text(p.name, color = pal.text, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Rounded,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
    }
}

@Composable
private fun NewProfileCard(modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(
        modifier.glass(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(780f / 360f).clip(RoundedCornerShape(14.dp)).background(p.surfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(Accent), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Add, null, tint = Color.White)
            }
        }
        Text(str(R.string.profiles_new), color = p.text, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Rounded,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
    }
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(name, { name = it }, label = { Text(str(R.string.profiles_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = { TextButton(onClick = { onDone(name.trim()) }, enabled = name.isNotBlank()) { Text(str(R.string.profiles_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(str(R.string.profiles_cancel)) } },
    )
}
