package com.zeta.fondo.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v39/v40: lienzo según la proporción y tamaño único de vista previa. */
class V39Test {
    private val W = 392f
    private val H = 820f

    // v40: el panel de 3 estados se sustituyó por un lienzo fijo (ver V40Test).
    @Test fun partsAlwaysFitTheScreen() {
        for (a in listOf(9f / 16f, 3f / 4f, 1f, 4f / 3f, 16f / 9f, 21f / 9f)) for (sz in PreviewSize.values()) {
            val l = EditorLayout.compute(W, H, a, false, sz)
            val used = l.topBar + l.canvasH + l.controls + l.workspace + l.toolRow + l.tabBar
            assertTrue("$a $sz: $used", used <= H + .01f)
            assertTrue(l.canvasH >= EditorLayout.MIN_CANVAS - .01f)
        }
    }

    @Test fun horizontalImageLeavesLittleBlackAndGivesSpaceToTheTimeline() {
        val l = EditorLayout.compute(W, H, 16f / 9f, false)
        val imageH = (W - EditorLayout.SIDE_MARGIN) / (16f / 9f)
        assertTrue("franja negra pequeña", l.canvasH - imageH <= 12f)
        assertTrue("el espacio sobrante es para la zona de trabajo", l.workspace >= 200f)
    }

    @Test fun onlyTheFullscreenButtonMakesTheCanvasFullscreen() {
        assertEquals(H, EditorLayout.compute(W, H, 1f, true).canvasH, 0f)
        assertTrue(EditorLayout.compute(W, H, 9f / 16f, false).canvasH < H - EditorLayout.TOP)
    }

    @Test fun landscapeKeepsSideColumn() {
        val l = EditorLayout.compute(H, W, 16f / 9f, false)
        assertTrue(l.landscape)
        assertTrue(l.sideW >= 280f)
    }

    @Test fun onePreviewSizeForImportCacheAndOpen() {
        assertEquals(1600, PreviewSizing.target(true, 512))
        assertEquals(2048, PreviewSizing.target(false, 192))
        assertEquals(2560, PreviewSizing.target(false, 512))
        // La caché de 1920 de proyectos v38 sirve para un objetivo de 2048 o 2560: no se re-decodifica.
        assertTrue(PreviewSizing.cacheIsEnough(1920, 2048, 4000))
        assertTrue(PreviewSizing.cacheIsEnough(1920, 2560, 4000))
        // Una caché diminuta (miniatura) no sirve.
        assertFalse(PreviewSizing.cacheIsEnough(480, 2048, 4000))
        // Imagen original pequeña: la caché del mismo tamaño sirve.
        assertTrue(PreviewSizing.cacheIsEnough(1000, 2560, 1000))
    }
}
