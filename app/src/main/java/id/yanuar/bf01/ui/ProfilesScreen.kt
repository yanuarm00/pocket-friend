package id.yanuar.bf01.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import id.yanuar.bf01.data.Field
import id.yanuar.bf01.data.Prefs.Profile
import id.yanuar.bf01.data.ProfileTypes
import id.yanuar.bf01.svc.Hub
import kotlinx.coroutines.launch

/** A logo on a white chip, so black logos stay readable in the dark theme. */
@Composable fun LogoChip(key: String, size: androidx.compose.ui.unit.Dp = 36.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(10.dp)).background(Color.White), contentAlignment = Alignment.Center) { LogoIcon(key, size - 8.dp) }
}

/** v0.4.4: Say Hi screen matches v5.13 (QR + profiles list); edit form opens on demand. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ProfilesScreen(hub: Hub) {
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf(hub.prefs.profiles()) }
    var sel by rememberSaveable { mutableIntStateOf(0) }
    var status by rememberSaveable { mutableStateOf("") }
    var editing by rememberSaveable { mutableStateOf(false) }
    var typeOpen by remember { mutableStateOf(false) }
    var iconOpen by remember { mutableStateOf(false) }
    val dev = hub.prefs.deviceName.ifBlank { "BF" }
    fun save(l: List<Profile>) { list = l; hub.prefs.setProfiles(l); status = "" }
    fun edit(f: (Profile) -> Profile) { if (sel in list.indices) save(list.toMutableList().also { it[sel] = f(it[sel]) }) }
    if (sel >= list.size) sel = (list.size - 1).coerceAtLeast(0)

    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SayHiContent(
            devName = dev,
            rows = list.mapIndexed { i, p ->
                val url = ProfileTypes.url(p)
                SayHiRow(i, ProfileTypes.displayName(p),
                    when {
                        url.isEmpty() -> "Fill in details"
                        ProfileTypes.tooLong(p) -> "Link too long for BF QR"
                        else -> ProfileTypes.label(p.type) + " · " + url.removePrefix("https://").removePrefix("mailto:")
                    }, ProfileTypes.logoKey(p))
            },
            selected = sel,
            qr = {
                val p = list.getOrNull(sel)
                if (p != null && ProfileTypes.url(p).isNotEmpty()) QrPreview(ProfileTypes.url(p), 160.dp, ProfileTypes.logoKey(p))
                else Text("QR", color = Pop.Grey)
            },
            onSelect = { sel = it; editing = false },
            onEdit = { sel = it; editing = true },
            onAdd = {
                if (list.size >= ProfileTypes.LIMIT) return@SayHiContent
                val used = list.map { it.type }.toSet()
                val t = ProfileTypes.all.map { it.key }.firstOrNull { it !in used && it != "waw" && it != ProfileTypes.OTHER } ?: ProfileTypes.OTHER
                save(list + Profile(t, "")); sel = list.lastIndex; editing = true
            },
            onShow = {
                scope.launch {
                    status = if (hub.sendNameCard().also { if (it) hub.db.addFeatureUse(id.yanuar.bf01.svc.Feature.SAY_HI) })
                        "Shown on $dev" else "Failed: BF is not connected"
                }
            },
            back = { hub.openScreen?.invoke(Screen.Pengaturan) },
        )
        if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodyMedium)

        if (editing) list.getOrNull(sel)?.let { p ->
            val t = ProfileTypes.type(p.type)
            val url = ProfileTypes.url(p)
            Section("Edit profile") {
                ExposedDropdownMenuBox(typeOpen, { typeOpen = it }) {
                    OutlinedTextField(t.label, {}, readOnly = true, label = { Text("Type") }, singleLine = true,
                        leadingIcon = { LogoChip(ProfileTypes.logoKey(p).let { if (p.type == ProfileTypes.OTHER) "link" else it }, 32.dp) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeOpen) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                    ExposedDropdownMenu(typeOpen, { typeOpen = false }) {
                        ProfileTypes.all.forEach { pt -> DropdownMenuItem({ Text(pt.label) }, { edit { it.copy(type = pt.key, msg = if (pt.field == Field.PHONE) it.msg else "") }; typeOpen = false },
                            leadingIcon = { LogoChip(if (pt.key == ProfileTypes.OTHER) "link" else pt.key, 32.dp) }) }
                    }
                }
                OutlinedTextField(p.name, { n -> edit { it.copy(name = n.take(24)) } }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Name on BF") }, placeholder = { Text(t.label) })
                OutlinedTextField(p.value, { v -> edit { it.copy(value = v) } }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(t.valueLabel) }, placeholder = { Text(t.example) },
                    keyboardOptions = KeyboardOptions(keyboardType = when (t.field) { Field.PHONE -> KeyboardType.Phone; Field.EMAIL -> KeyboardType.Email; else -> KeyboardType.Uri }))
                if (t.field == Field.PHONE)
                    OutlinedTextField(p.msg, { m -> edit { it.copy(msg = m) } }, Modifier.fillMaxWidth(), label = { Text("Opening message") }, placeholder = { Text("Hi, I'd like to have a chat") })
                if (p.type == ProfileTypes.OTHER) {
                    val cur = ProfileTypes.iconChoices.firstOrNull { it.first == p.icon }?.second ?: "Automatic"
                    ExposedDropdownMenuBox(iconOpen, { iconOpen = it }) {
                        OutlinedTextField(cur, {}, readOnly = true, label = { Text("Logo on the QR") }, singleLine = true,
                            leadingIcon = { LogoChip(ProfileTypes.logoKey(p), 32.dp) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(iconOpen) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                        ExposedDropdownMenu(iconOpen, { iconOpen = false }) {
                            ProfileTypes.iconChoices.forEach { (k, l) ->
                                DropdownMenuItem({ Text(l) }, { edit { it.copy(icon = k) }; iconOpen = false },
                                    leadingIcon = { LogoChip(if (k.isEmpty()) ProfileTypes.logoKey(p.copy(icon = "")) else if (k == "#") ProfileTypes.logoKey(p.copy(icon = "#")) else k, 32.dp) }) }
                        }
                    }
                }
                if (ProfileTypes.tooLong(p)) Text("This is too long for the QR on BF.", color = MaterialTheme.colorScheme.error)
                ActionButton("Save & send", primary = true, icon = Icons.AutoMirrored.Filled.Send) {
                    editing = false
                    scope.launch { status = if (hub.sendNameCard().also { if (it) hub.db.addFeatureUse(id.yanuar.bf01.svc.Feature.SAY_HI) }) "Shown on $dev" else "Failed: BF is not connected" }
                }
                TextButton({ save(list.toMutableList().also { it.removeAt(sel) }); editing = false }) { Text("Delete profile", color = Pop.Red) }
            }
        }
    }
}
