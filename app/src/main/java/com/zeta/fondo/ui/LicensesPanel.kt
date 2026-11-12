package com.zeta.fondo.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeta.fondo.AppViewModel
import com.zeta.fondo.model.OpenSourceLicenses

/**
 * v44.24 · Ajustes › Licencias, simple: una lista con nombre, autor y licencia de cada pieza
 * de código abierto; al tocar una se abre su texto completo (como en WhatsApp o YouTube).
 */
@Composable
fun LicensesPanel(vm: AppViewModel) {
    var open by rememberSaveable { mutableStateOf(-1) }
    val item = OpenSourceLicenses.items.getOrNull(open)
    if (item != null) { LicenseText(item) { open = -1 }; return }
    Column(Modifier.fillMaxSize()) {
        CapHeader("Licencias") { vm.applyAndClose() }
        PanelScroll {
            Text("Zeta Motion usa estas piezas de código abierto. Toca una para leer su licencia.",
                color = StudioMuted, fontSize = 12.sp, lineHeight = 16.sp)
            OpenSourceLicenses.items.forEachIndexed { i, it -> LicenseRow(it) { open = i } }
            Spacer(Modifier.height(4.dp))
            Text("Sonidos", color = StudioText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text("Todos los sonidos los crea la propia app. Tus grabaciones importadas solo se guardan en tu proyecto.",
                color = StudioMuted, fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun LicenseRow(item: OpenSourceLicenses.Item, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(StudioTile)
        .clickable(onClick = onClick).padding(start = 12.dp, end = 6.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(item.name, color = StudioText, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${item.author} · ${item.license}", color = StudioMuted, fontSize = 12.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(item.use, color = StudioMuted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = StudioMuted, modifier = Modifier.size(22.dp))
    }
}

/** Texto completo de una licencia, ajustado al ancho de la pantalla. */
@Composable
private fun LicenseText(item: OpenSourceLicenses.Item, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val text = remember(item.file) {
        try { OpenSourceLicenses.reflow(ctx.assets.open(item.file).bufferedReader().use { it.readText() }) }
        catch (_: Exception) { "No se pudo leer ${item.file}." }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(48.dp).padding(start = 2.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).clickable(onClick = onBack),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.ArrowBack, "Volver", tint = StudioText, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(4.dp))
            Text(item.name, color = StudioText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp)) {
            Text("${item.author} · ${item.license}", color = StudioMuted, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Text(text, color = StudioText, fontSize = 12.sp, lineHeight = 17.sp)
            Spacer(Modifier.height(16.dp))
        }
    }
}
