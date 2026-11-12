package com.zeta.fondo.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.zeta.fondo.AppViewModel
import com.zeta.fondo.engine.UserSounds
import com.zeta.fondo.model.SoundCatalog
import com.zeta.fondo.model.SoundClips

/**
 * v44 · Sonidos por escenas. Tocar uno lo añade como BLOQUE en la línea de tiempo (en el
 * cabezal); ahí se mueve, recorta, divide, duplica, silencia o aísla como un efecto.
 */
@Composable
fun SoundLibrary(vm: AppViewModel) {
    val cats = SoundCatalog.categories
    val cat = vm.soundCategory.takeIf { it in cats } ?: cats.first()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.importUserSound(it) } }
    Text("Sonidos (bloques en la línea de tiempo)", color = StudioText, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    Text("Toca un sonido para probarlo: tocar otro lo cambia (no se suman). ✓ lo deja. Para sumar otro sonido, vuelve a abrir Audio.",
        color = StudioMuted, fontSize = 12.sp)
    TextTabs(cats, cats.indexOf(cat), Modifier.fillMaxWidth().height(40.dp)) { vm.soundCategory = cats[it] }
    if (cat == "Mis sonidos") {
        val dir = vm.meta?.dir
        val mine = if (dir != null) UserSounds.list(dir) else emptyList()
        StudioSmallButton("Importar grabación (hasta 60 s)", Icons.Filled.Upload, Modifier.fillMaxWidth()) {
            picker.launch(arrayOf("audio/*"))
        }
        mine.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { info -> Box(Modifier.weight(1f)) { SoundTile(info.name, vm.isTrialSound(info.id)) { vm.trySound(info.id) } } }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (mine.isEmpty()) Text("Tus grabaciones se guardan dentro del proyecto y se pueden usar en cualquier momento del vídeo.",
            color = StudioMuted, fontSize = 12.sp)
    } else {
        SoundCatalog.byCategory[cat].orEmpty().chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { snd ->
                    val on = vm.isTrialSound(snd.id) || vm.effectClips.any { it.effectId == SoundClips.clipId(snd.id) }
                    Box(Modifier.weight(1f)) { SoundTile(snd.name + if (snd.event) " ⚡" else "", on) { vm.trySound(snd.id) } }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Text("Sintetizados por la app (sin grabaciones de terceros). Las grabaciones reales aún no están incluidas: ver Licencias.",
            color = StudioMuted, fontSize = 11.sp)
    }
}

@Composable
private fun SoundTile(name: String, active: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(Modifier.fillMaxWidth().height(46.dp).clip(shape)
        .background(if (active) StudioPurple.copy(alpha = .16f) else StudioTile)
        .then(if (active) Modifier.border(1.5.dp, StudioGradient, shape) else Modifier)
        .clickable(onClick = onClick).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.GraphicEq, null, tint = if (active) StudioPink else StudioMuted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(name, color = StudioText, fontSize = 11.sp, lineHeight = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
    }
}

/** Ajustar un bloque de sonido: volumen y panorama (animables con ◆), fundidos y silencio. */
@Composable
fun SoundClipPanel(vm: AppViewModel, uid: Long) {
    val clip = vm.effectClips.firstOrNull { it.uid == uid } ?: run { vm.closeSoundClip(true); return }
    val id = clip.effectId
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(48.dp).padding(start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clickable { vm.closeSoundClip(false) }, contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Close, "Descartar", tint = StudioText, modifier = Modifier.size(24.dp))
            }
            Text(timelineTitle(id), color = StudioText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Box(Modifier.size(44.dp).clickable { vm.closeSoundClip(true) }, contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, "Listo", tint = StudioText, modifier = Modifier.size(26.dp))
            }
        }
        PanelScroll {
            @Suppress("UNUSED_VARIABLE") val tick = vm.scrubT
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    LabeledSlider("Volumen", vm.soundValue(clip, "vol"), 0f, 1.5f, false) { vm.setSoundValue(uid, "vol", it) }
                }
                KeyDiamond(vm, "$id.vol")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    LabeledSlider("Panorama (izq. · dcha.)", vm.soundValue(clip, "pan"), -1f, 1f, false) { vm.setSoundValue(uid, "pan", it) }
                }
                KeyDiamond(vm, "$id.pan")
            }
            LabeledSlider("Fundido de entrada", vm.soundValue(clip, "fadein"), 0f, 3f, false, suffix = " s") { vm.setSoundValue(uid, "fadein", it) }
            LabeledSlider("Fundido de salida", vm.soundValue(clip, "fadeout"), 0f, 3f, false, suffix = " s") { vm.setSoundValue(uid, "fadeout", it) }
            SwitchRow("Silenciado", clip.hidden) { vm.toggleClipHidden(uid) }
            Text("Usa ◆ para que el volumen o el panorama cambien durante el vídeo (por ejemplo, la lluvia que se acerca).",
                color = StudioMuted, fontSize = 11.sp)
        }
    }
}

