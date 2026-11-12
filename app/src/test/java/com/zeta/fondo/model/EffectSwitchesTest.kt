package com.zeta.fondo.model

import org.junit.Assert.*
import org.junit.Test

class EffectSwitchesTest {
    @Test fun removeAllKeepsAnimationMasksAndColor() {
        val original=Effects.defaults()+mapOf("rain.on" to 1f,"rain.mask" to 3f,"rain.int" to .8f,
            "zonemotion.on" to 1f,"flow.on" to 1f,"adjust.brightness" to .3f)
        val after=original+EffectSwitches.disabled(original)
        assertEquals(0f,after.getValue("rain.on"),0f)
        for(k in listOf("rain.mask","rain.int","zonemotion.on","flow.on","adjust.brightness")) assertEquals(original[k],after[k])
    }
    @Test fun independentCopiesAndElementsAreIncluded() {
        val values=mapOf("ash.copy.on" to 1f,"element0.on" to 1f,"element3.on" to 1f,"sky.on" to 1f)
        assertEquals(values.keys,EffectSwitches.disabled(values).keys)
        assertTrue(EffectSwitches.disabled(values).values.all{it==0f})
    }
    @Test fun undoRestoresOnlySwitchesAndRedoTurnsThemOff() {
        val original=mapOf("rain.on" to 1f,"element0.on" to 1f,"adjust.contrast" to .1f)
        val before=EffectSwitches.active(original);val patch=EffectSwitches.disabled(original)
        val edited=original+patch+mapOf("adjust.contrast" to .4f)
        val restored=edited+before
        assertEquals(1f,restored.getValue("rain.on"),0f)
        assertEquals(.4f,restored.getValue("adjust.contrast"),0f)
        assertTrue(EffectSwitches.active(restored+patch).isEmpty())
    }
    @Test fun inactiveProjectHasNoResetAction() {
        assertTrue(EffectSwitches.disabled(Effects.defaults()).isEmpty())
    }
}
