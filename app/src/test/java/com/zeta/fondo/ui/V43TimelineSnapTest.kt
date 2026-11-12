package com.zeta.fondo.ui

import com.zeta.fondo.model.EffectClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * v43: imán de la línea de tiempo y bloques más largos que la pantalla.
 * Valores tomados de la captura del usuario: 214 px por segundo (con zoom), pantalla de
 * 576 px, línea blanca en el centro (288 px) a los 2,3 s.
 */
class V43TimelineSnapTest {
    private val pps = 214f
    private val snapSec = 12f * 2.625f / pps          // 12 dp en un teléfono de 420 dpi

    @Test fun trimmedEdgeSticksToTheWhiteLine() {
        val anchors = snapAnchors(emptyList(), null, 6f, playheadSec = 2.3f)
        val (edge, guide) = snapEdge(2.36f, anchors, snapSec)      // a 13 px de la línea
        assertEquals(2.3f, edge, 1e-6f)
        assertEquals(2.3f, guide!!, 1e-6f)
        val (free, none) = snapEdge(2.6f, anchors, snapSec)         // lejos: no se pega
        assertEquals(2.6f, free, 1e-6f)
        assertNull(none)
    }

    @Test fun edgeSticksToOtherBlocksButNotToItself() {
        val clips = listOf(EffectClip(1, "snow", 0f, 3.8f), EffectClip(2, "flame", 4f, 2f))
        val anchors = snapAnchors(clips, exceptUid = 1, total = 6f, playheadSec = null)
        assertEquals(4f, snapEdge(3.95f, anchors, snapSec).first, 1e-6f)
    }

    @Test fun longBlockKeepsItsRealEndWhenZoomed() {
        // Nieve de 0 a 3,8 s con la línea a 2,3 s: empieza fuera de la pantalla (−204 px)
        // y termina a 609 px, también fuera. Se dibuja de 0 a 576 (la v42 lo acababa a 372).
        val x0 = 288f + (0f - 2.3f) * pps
        val x1 = x0 + 3.8f * pps
        assertEquals(0 to 576, visibleSpan(x0, x1, 576))
        // Recortado hasta la línea blanca: su final queda exactamente en el centro.
        val (left, w) = visibleSpan(x0, x0 + 2.3f * pps, 576)
        assertEquals(288, left + w)
        // Entero fuera de la pantalla: no ocupa nada.
        assertEquals(0, visibleSpan(-500f, -10f, 576).second)
    }
}
