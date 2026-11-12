package com.zeta.fondo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeta.fondo.AppViewModel
import com.zeta.fondo.model.AnimCatalog

/**
 * v44 · Catálogo de Animar (hoja completa), igual que el de Efectos:
 * ✕ descarta la prueba · categorías · ✓ confirma. Tocar una miniatura la prueba sobre tu
 * imagen y su bloque aparece en la línea de tiempo; tocarla otra vez abre Ajustar
 * (Ajustes, Estilo, Tiempo y Zona). La zona se pinta desde Ajustar › Zona.
 */
@Composable
fun AnimCatalogPanel(vm: AppViewModel) {
    val tick = rememberThumbTick()
    val glyphTime = remember { object { val value: Float get() = (tick() % 16) / 16f } }
    val cats = AnimCatalog.categories
    val category = vm.animCategory.takeIf { it in cats } ?: cats.first()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clickable { vm.discardAnim() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Close, "Descartar", tint = StudioText, modifier = Modifier.size(24.dp))
            }
            TextTabs(cats, cats.indexOf(category), Modifier.weight(1f).fillMaxHeight()) { vm.selectAnimCategory(cats[it]) }
            Box(Modifier.size(48.dp).clickable { vm.applyAndClose() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, "Aplicar", tint = StudioText, modifier = Modifier.size(26.dp))
            }
        }
        val list = AnimCatalog.entries[category].orEmpty()
        // v44.22 · preparar en segundo plano los movimientos de esta pestaña
        androidx.compose.runtime.LaunchedEffect(category) {
            vm.prewarmEffects(list.map { it.effectId }.filter { it.isNotEmpty() }.distinct())
        }
        LazyVerticalGrid(GridCells.Adaptive(78.dp), Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(list, key = { it.key }) { e ->
                val previewing = vm.animKey == e.key && vm.editFromCatalog
                val active = !previewing && AnimCatalog.isActive(e, vm.settings)
                CatalogCard(e.title, previewing, active, tint = clipColor(e.effectId.ifEmpty { "flow" }),
                    modifier = Modifier.clickable { vm.previewAnim(e) }) {
                    EffectThumb(e.thumb, animate = true, tick = tick, glyphTime = { glyphTime.value }, modifier = Modifier.fillMaxSize())
                    if (previewing) Column(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .45f)),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Filled.Tune, null, tint = Color.White, modifier = Modifier.size(22.dp))
                        Text("Ajustar", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
