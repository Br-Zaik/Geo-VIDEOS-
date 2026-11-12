package com.zeta.fondo.model

/**
 * v44.25 · org.json no admite NaN ni infinitos: JSONObject.put lanza una excepción y el proyecto
 * ENTERO dejaba de guardarse («No se pudo guardar» en cada intento). Al escribir, un número
 * inválido se cambia por un valor seguro ([def]); al leer, los ajustes ausentes toman su valor
 * por defecto. Código puro (pruebas JVM).
 */
internal fun Float.jsonSafe(def: Float = 0f): Double = if (isFinite()) toDouble() else def.toDouble()
