package com.zeta.fondo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeta.fondo.AppViewModel
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private data class ColorControl(val key:String,val label:String,val icon:ImageVector,val min:Float=-1f,val max:Float=1f)
private val colorControls=listOf(
    ColorControl("brightness","Brillo",Icons.Default.Brightness6,-.5f,.5f),
    ColorControl("contrast","Contraste",Icons.Default.Contrast),
    ColorControl("saturation","Saturación",Icons.Default.Palette),
    ColorControl("hue","Tono",Icons.Default.ColorLens,-180f,180f),
    ColorControl("exposure","Exposición",Icons.Default.Exposure,-2f,2f),
    ColorControl("sharpen","Nitidez",Icons.Default.Details,0f),
    ColorControl("vignette","Viñeta",Icons.Default.Vignette,0f),
    ColorControl("glow","Resplandor",Icons.Default.AutoAwesome,0f),
    ColorControl("prism","Prisma",Icons.Default.FilterHdr,0f),
    ColorControl("grain","Grano",Icons.Default.Grain,0f),
    ColorControl("fade","Desvanecer",Icons.Default.Tonality,0f),
    ColorControl("shadows","Sombras",Icons.Default.Brightness3),
    ColorControl("highlights","Luces",Icons.Default.LightMode),
    ColorControl("temperature","Temperatura",Icons.Default.Thermostat)
)
/**
 * v41 · Ajustar color: arriba la fila de ajustes (Brillo, Contraste, Nitidez…), abajo UN
 * deslizador para el elegido, «Restablecer» y ✓. Un punto rosa marca lo ya modificado.
 */
@Composable
fun ColorPanel(vm:AppViewModel){
    var selected by remember { mutableIntStateOf(0) }
    val control=colorControls[selected]
    val key="adjust.${control.key}"
    val value=vm.settings[key]?:0f
    val anyChanged=colorControls.any { abs(vm.settings["adjust.${it.key}"]?:0f)>1e-4f }
    Column(Modifier.fillMaxSize()){
        Row(Modifier.fillMaxWidth().height(48.dp).padding(start=6.dp,end=4.dp),verticalAlignment=Alignment.CenterVertically){
            Row(Modifier.clip(RoundedCornerShape(10.dp)).clickable(enabled=anyChanged){vm.resetColor()}
                .padding(horizontal=8.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically){
                Icon(Icons.Default.Refresh,null,tint=if(anyChanged) StudioText else StudioMuted.copy(alpha=.5f),modifier=Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Restablecer",color=if(anyChanged) StudioText else StudioMuted.copy(alpha=.5f),fontSize=13.sp)
            }
            Text("Ajustar",color=StudioText,fontSize=15.sp,fontWeight=FontWeight.SemiBold,textAlign=TextAlign.Center,
                modifier=Modifier.weight(1f))
            Spacer(Modifier.width(60.dp))
            Box(Modifier.size(44.dp).clip(CircleShape).clickable{vm.applyAndClose()},contentAlignment=Alignment.Center){
                Icon(Icons.Default.Check,"Listo",tint=StudioText,modifier=Modifier.size(26.dp))
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=10.dp,vertical=6.dp),
            horizontalArrangement=Arrangement.spacedBy(6.dp)){
            colorControls.forEachIndexed { i,c->
                val on=i==selected
                val changed=abs(vm.settings["adjust.${c.key}"]?:0f)>1e-4f
                Column(Modifier.width(62.dp).clip(RoundedCornerShape(10.dp)).clickable{selected=i}.padding(vertical=4.dp),
                    horizontalAlignment=Alignment.CenterHorizontally){
                    Box(Modifier.size(48.dp).clip(CircleShape).background(if(on) StudioText else StudioTile),
                        contentAlignment=Alignment.Center){
                        Icon(c.icon,null,tint=if(on) Color.Black else StudioText,modifier=Modifier.size(22.dp))
                        if(changed&&!on) Box(Modifier.align(Alignment.TopEnd).padding(5.dp).size(7.dp).clip(CircleShape).background(StudioPink))
                    }
                    Text(c.label,color=if(on) StudioText else StudioMuted,fontSize=11.sp,maxLines=1,
                        modifier=Modifier.padding(top=5.dp))
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text(shownValue(value,control),color=StudioText,fontSize=14.sp,fontWeight=FontWeight.SemiBold,
            textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().padding(horizontal=18.dp),verticalAlignment=Alignment.CenterVertically){
            Slider(value=value.coerceIn(control.min,control.max),onValueChange={vm.setSetting(key,it)},
                valueRange=control.min..control.max,modifier=Modifier.weight(1f),colors=capSliderColors())
            Box(Modifier.size(40.dp).clip(CircleShape).clickable(enabled=abs(value)>1e-4f){vm.setSetting(key,0f)},
                contentAlignment=Alignment.Center){
                Icon(Icons.Default.Refresh,"Restablecer ${control.label}",
                    tint=if(abs(value)>1e-4f) StudioMuted else StudioMuted.copy(alpha=.35f),modifier=Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
    }
}

/** Valor como en un editor de vídeo: −50…50 (o grados para el tono). */
private fun shownValue(v:Float,c:ColorControl):String =
    if(c.key=="hue") "${v.roundToInt()}°"
    else { val span=maxOf(abs(c.min),abs(c.max)); (v/span*(if(c.min<0f) 50f else 100f)).roundToInt().toString() }

@Composable
fun LoopPanel(vm:AppViewModel,onBack:()->Unit){
    val mode=(vm.settings["flow.mode"]?:0f).toInt()
    val cycles=(vm.settings["flow.cycles"]?:2f).toInt().coerceAtLeast(1)
    val duration=vm.exportSettings.loopSec
    Column(Modifier.fillMaxSize().padding(horizontal=8.dp)){
        Row(Modifier.fillMaxWidth().height(36.dp),verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Volver a Animar",tint=StudioText)}
            Text("Bucle de movimiento",color=StudioText,fontSize=14.sp,modifier=Modifier.weight(1f))
        }
        // Siempre visible incluso si el usuario desplaza el resto del panel del bucle.
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment=Alignment.CenterVertically){
            Text("Velocidad",color=StudioText,fontSize=13.sp)
            Slider(value=cycles.toFloat(),onValueChange={vm.setSetting("flow.cycles",it.roundToInt().toFloat())},
                valueRange=1f..8f,steps=6,modifier=Modifier.weight(1f))
            Text("${cycles} ciclos",color=StudioMuted,fontSize=11.sp)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())){
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(5.dp)){
                listOf("Continuo","Ida y vuelta","Pulso").forEachIndexed{i,label->StudioChoice(label,mode==i){vm.setSetting("flow.mode",i.toFloat())}}
            }
            LabeledSlider("Duración",duration.toFloat(),2f,60f,true,suffix="s"){
                vm.updateExport(vm.exportSettings.copy(loopSec=it.toInt()))
            }
            SwitchRow("Bucle perfecto", (vm.settings["loop.perfect"] ?: 0f) > .5f) { vm.setPerfectLoop(it) }
            Text("Cámara, profundidad y movimientos periódicos vuelven al mismo punto al terminar.",
                color=StudioMuted,fontSize=11.sp)
            Text("${cycles} ciclos · cada ciclo: ${String.format(Locale.US,"%.2f",duration.toFloat()/cycles)} s",color=StudioMuted,fontSize=12.sp)
            if(mode==0)LabeledSlider("Suavizado del enlace",vm.settings["flow.blend"]?:1f,.05f,1f,false){vm.setSetting("flow.blend",it)}
            LabeledSlider("Punto de inicio",(vm.settings["flow.phase"]?:0f)*100f,0f,100f,true,suffix="%"){
                vm.setSetting("flow.phase",it/100f)
            }
            LabeledSlider("Repeticiones del MP4",vm.exportSettings.repeats.toFloat(),1f,10f,true){
                vm.updateExport(vm.exportSettings.copy(repeats=it.toInt()))
            }
            Text("MP4: ${duration*vm.exportSettings.repeats} s · repetir no cambia la velocidad.",color=StudioMuted,fontSize=12.sp)
        }
    }
}
