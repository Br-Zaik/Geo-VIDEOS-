package com.zeta.fondo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.zeta.fondo.AppViewModel

/**
 * v44.25 · Al pedir «Detrás» sin ningún personaje pintado en Proteger, el efecto no tendría nada
 * detrás de lo que pasar. Antes la app creaba sola una máscara (y en un paisaje tapaba el efecto
 * entero); ahora pregunta.
 */
@Composable
fun BehindAskDialog(vm: AppViewModel) {
    Dialog(onDismissRequest = { vm.behindKeepFront() }) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(StudioPanel).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("¿Detrás de qué?", color = StudioText, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text("Proteger está vacío: no hay ningún personaje para que el efecto pase por detrás.",
                color = StudioMuted, fontSize = 13.sp, lineHeight = 18.sp)
            StudioSmallButton("Buscar personaje solo", Icons.Filled.PersonSearch, Modifier.fillMaxWidth()) {
                vm.behindFindCharacter()
            }
            StudioSmallButton("Pintarlo yo", Icons.Filled.Brush, Modifier.fillMaxWidth()) { vm.behindPaintMyself() }
            StudioSmallButton("Dejarlo delante", Icons.Filled.ArrowUpward, Modifier.fillMaxWidth()) { vm.behindKeepFront() }
        }
    }
}
