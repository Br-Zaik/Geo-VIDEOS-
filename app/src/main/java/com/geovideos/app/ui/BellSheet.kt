package com.geovideos.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geovideos.app.data.ChannelItem

/** Opciones de la campana como en YouTube: Todas, Personalizadas, Ninguna y Anular suscripción. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BellSheet(
    channel: ChannelItem,
    loading: Boolean,
    onSelect: (Int) -> Unit,
    onUnsubscribe: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF212121),
        contentColor = Color.White
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
            Text(
                "Notificaciones de ${channel.title}",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            if (loading) {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            } else {
                val canChange = channel.bellParams.any { it.isNotBlank() }
                BellRow(Icons.Rounded.NotificationsActive, "Todas", "Recibe todas las notificaciones", channel.bellState == 0, canChange) { onSelect(0) }
                BellRow(Icons.Rounded.NotificationsNone, "Personalizadas", "Notificaciones ocasionales de lo más destacado", channel.bellState == 1, canChange) { onSelect(1) }
                BellRow(Icons.Rounded.NotificationsOff, "Ninguna", "No recibas notificaciones", channel.bellState == 2, canChange) { onSelect(2) }
                if (!canChange) {
                    Text(
                        "YouTube no mandó las opciones de la campana para este canal.",
                        color = Color(0xFFAAAAAA),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )
                }
            }
            HorizontalDivider(color = Color(0xFF3A3A3A), modifier = Modifier.padding(vertical = 6.dp))
            BellRow(Icons.Rounded.PersonRemove, "Anular suscripción", "", false, true, onUnsubscribe)
        }
    }
}

@Composable
private fun BellRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) Color.White else Color(0xFF777777), modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, color = if (enabled) Color.White else Color(0xFF777777))
            if (subtitle.isNotBlank()) Text(subtitle, fontSize = 12.sp, color = Color(0xFFAAAAAA))
        }
        if (selected) Icon(Icons.Rounded.Check, contentDescription = "Elegida", tint = Color.White)
    }
}
