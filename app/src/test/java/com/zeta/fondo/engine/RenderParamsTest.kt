package com.zeta.fondo.engine

import com.zeta.fondo.model.Effects
import org.junit.Assert.*
import org.junit.Test

class RenderParamsTest {
    @Test fun newProjectDoesNotIntroduceEffectsOrElements() {
        val p=RenderParams.from(Effects.defaults(),.75f)
        listOf("Sky","Water","Dispersion").forEach { assertEquals(0f,p.studio.getValue("uStudio$it")[0],0f) }
        (0..3).forEach { assertEquals(0f,p.studio.getValue("uElementStyle[$it]")[0],0f) }
        assertEquals(1f,p.flow[0],0f)
    }
    @Test fun wholeImageAndIndependentMasksSurviveParameterConversion() {
        val p=RenderParams.from(Effects.defaults()+mapOf("rain.mask" to -2f,"sky.mask" to 3f,"water.mask" to 4f),1f)
        assertEquals(-2,p.effectMasks["rain"])
        assertEquals(3,p.effectMasks["sky"])
        assertEquals(4,p.effectMasks["water"])
        assertEquals(-1,p.effectMasks["heat"])
    }
    @Test fun separateElementsKeepIndependentPositionsAndOpacity() {
        val p=RenderParams.from(mapOf("element0.on" to 1f,"element0.x" to .2f,"element0.opacity" to .4f,
            "element1.on" to 1f,"element1.x" to .8f,"element1.opacity" to .9f),1f)
        assertEquals(.2f,p.studio.getValue("uElementPose[0]")[0],0f)
        assertEquals(.8f,p.studio.getValue("uElementPose[1]")[0],0f)
        assertEquals(.4f,p.studio.getValue("uElementStyle[0]")[0],0f)
        assertEquals(.9f,p.studio.getValue("uElementStyle[1]")[0],0f)
    }
    @Test fun disabledEffectRetainsItsEditableStrength() {
        val settings=mapOf("water.on" to 0f,"water.int" to .72f,"water.cycles" to 2f)
        assertEquals(0f,RenderParams.from(settings,1f).studio.getValue("uStudioWater")[0],0f)
        assertEquals(.72f,RenderParams.from(settings+mapOf("water.on" to 1f),1f).studio.getValue("uStudioWater")[0],0f)
    }
    @Test fun elementCyclesRemainIntegralForSeamlessLoops() {
        val p=RenderParams.from(mapOf("element0.on" to 1f,"element0.cycles" to 2.3f,"element0.size" to 0f),1f)
        assertEquals(2f,p.studio.getValue("uElementStyle[0]")[2],0f)
        assertTrue(p.studio.getValue("uElementPose[0]")[2]>0f)
    }
}
