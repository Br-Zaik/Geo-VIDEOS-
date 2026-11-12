package com.zeta.fondo.model

import com.zeta.fondo.engine.RenderParams
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v44.22 · Efectos retirados: fuera del catálogo, pero los proyectos viejos siguen igual. */
class V4422Test {
    private val ids = Effects.all.map { it.id }

    @Test fun retiradosNoSalenEnElCatalogo() {
        for (cat in EffectCatalog.categories) {
            val shown = EffectCatalog.idsFor(cat, ids) { false }
            for (r in Effects.retired) assertFalse("$r en $cat", r in shown)
        }
        assertFalse(AnimCatalog.all().any { it.effectId == "blink" })
        assertTrue(Effects.retired.none { it in EffectCatalog.popular })
    }

    @Test fun unProyectoQueLosUsaLosSigueViendo() {
        val shown = EffectCatalog.idsFor("Activos", ids) { it == "blink" }
        assertTrue("blink" in shown)
        val p = RenderParams.from(Effects.defaults() + Effects.retired.associate { "$it.on" to 1f }, 1f)
        for (r in Effects.retired) assertTrue("$r sigue en el shader", r in p.features)
    }
}
