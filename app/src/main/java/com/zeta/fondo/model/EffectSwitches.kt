package com.zeta.fondo.model

/** Only effect activation switches: never masks, animation paths or color values. */
object EffectSwitches {
    private val keys = Effects.all.filter { it.id !in setOf("flow", "zonemotion") }
        .flatMap { listOf("${it.id}.on", "${it.id}.copy.on") }.toSet() + (0..3).map { "element$it.on" }
    fun active(settings: Map<String, Float>): Map<String, Float> = settings.filter { (k,v) -> k in keys && v > .5f }
    fun disabled(settings: Map<String, Float>): Map<String, Float> = active(settings).mapValues { 0f }
}
