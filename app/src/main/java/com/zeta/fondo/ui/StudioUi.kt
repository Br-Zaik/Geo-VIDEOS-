package com.zeta.fondo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val StudioPink = Color(0xFFF64297)
val StudioPurple = Color(0xFF9B65FB)
val StudioOrange = Color(0xFFFF9044)
val StudioPanel = Color(0xFF1B1C23)
val StudioTile = Color(0xFF252630)
val StudioText = Color(0xFFF8F8FA)
val StudioMuted = Color(0xFFADADB9)
val StudioGradient = Brush.horizontalGradient(listOf(StudioPink, StudioPurple))

@Composable
fun StudioButton(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp))
            .background(if (active) StudioPurple.copy(alpha = .15f) else StudioTile)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = if (active) StudioPurple else StudioText, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = if (active) StudioPurple else StudioText, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Medium,
            maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
fun StudioSmallButton(label: String, icon: ImageVector, modifier: Modifier = Modifier,
                      selected: Boolean = false, onClick: () -> Unit) {
    Row(modifier.heightIn(min = 53.dp).clip(RoundedCornerShape(14.dp))
        .background(if (selected) StudioPurple else StudioTile)
        .clickable(onClick = onClick).padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center) {
        Icon(icon, null, tint = if (selected) Color.Black else StudioText, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(7.dp))
        Text(label, color = if (selected) Color.Black else StudioText, fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun StudioChoice(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(13.dp))
        .background(if (selected) StudioGradient else Brush.horizontalGradient(listOf(StudioTile, StudioTile)))
        .clickable(onClick = onClick).padding(horizontal = 11.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        Text(label, color = StudioText, fontSize = 14.sp, maxLines = 1, textAlign = TextAlign.Center,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

/** Acción compacta: el texto vive en la descripción accesible, no sobre el lienzo. */
@Composable
fun StudioIconAction(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier.heightIn(min = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) StudioGradient else Brush.horizontalGradient(listOf(StudioTile, StudioTile)))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, tint = StudioText, modifier = Modifier.size(23.dp))
    }
}

@Composable
fun StudioPrimary(label: String, icon: ImageVector = Icons.Filled.Add, modifier: Modifier = Modifier,
                  enabled: Boolean = true, onClick: () -> Unit) {
    Row(modifier.heightIn(min = 56.dp).clip(RoundedCornerShape(18.dp))
        .background(if (enabled) StudioGradient else Brush.horizontalGradient(listOf(StudioTile, StudioTile)))
        .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = StudioText, modifier = Modifier.size(25.dp))
        Spacer(Modifier.width(11.dp))
        Text(label, color = StudioText, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1)
    }
}
