package io.github.pedropasinn.mobilepad.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutPresetsTest {
    /** Controller areas (long side minus cutout × short side), from small to large phones. */
    private val landscapes = listOf(720f to 360f, 760f to 360f, 780f to 360f, 800f to 384f, 840f to 393f, 870f to 400f, 900f to 411f, 940f to 432f)
    private val portraits = landscapes.map { (l, s) -> s to l }

    /** Every visible part on screen and clear of the others. */
    private fun assertFits(what: String, layout: PadLayout, w: Float, h: Float, portrait: Boolean) {
        val parts = PadGeometry.placed(layout, w, h, portrait).entries.toList()
        for ((k, b) in parts) assertTrue("$what: $k $b outside $w×$h", b.inside(w, h, margin = 4f))
        for (i in parts.indices) for (j in i + 1 until parts.size) {
            val (a, ba) = parts[i]
            val (b, bb) = parts[j]
            assertFalse("$what: $a $ba overlaps $b $bb at $w×$h", ba.overlaps(bb))
        }
    }

    @Test
    fun builtInLayoutFits() {
        for ((w, h) in landscapes) assertFits("default", PadLayout(), w, h, false)
        for ((w, h) in portraits) assertFits("default", PadLayout(), w, h, true)
    }

    @Test
    fun presetsFitTheScreenTheyWereBuiltFor() {
        for (p in LayoutPreset.entries) {
            for ((w, h) in landscapes) assertFits("$p land", p.build(w, h, false), w, h, false)
            for ((w, h) in portraits) assertFits("$p port", p.build(w, h, true), w, h, true)
        }
    }

    /**
     * The home screen only estimates the controller's area; the real one can be larger (smaller
     * cutout than assumed, or system bars left out of the estimate before Android 15).
     */
    @Test
    fun presetsStillFitWhenTheRealScreenIsLarger() {
        for (p in LayoutPreset.entries) for (extra in listOf(16f, 32f, 48f)) {
            for ((w, h) in landscapes) assertFits("$p land +$extra w", p.build(w, h, false), w + extra, h, false)
            for ((w, h) in portraits) assertFits("$p port +$extra h", p.build(w, h, true), w, h + extra, true)
        }
        // The short side is measured in full; allow for rounding of the dp size.
        for (p in LayoutPreset.entries) for (extra in listOf(4f, 8f)) {
            for ((w, h) in landscapes) assertFits("$p land +$extra h", p.build(w, h, false), w, h + extra, false)
            for ((w, h) in portraits) assertFits("$p port +$extra w", p.build(w, h, true), w + extra, h, true)
        }
    }

    @Test
    fun menuIsNeverHiddenAndOnlyKnownPartsAreUsed() {
        val known = PadGeometry.defaults(780f, 360f, false).keys
        for (p in LayoutPreset.entries) for (portrait in listOf(false, true)) {
            val l = p.build(780f, 360f, portrait)
            assertFalse("$p hides MENU", l[PadPart.MENU].hidden)
            assertTrue("$p uses unknown parts", known.containsAll(l.tweaks.keys))
        }
    }

    @Test
    fun defaultIsEmptyAndPresetsDiffer() {
        assertTrue(LayoutPreset.DEFAULT.build(800f, 384f, false).tweaks.isEmpty())
        val built = LayoutPreset.entries.map { it.build(800f, 384f, false) }
        for (i in built.indices) for (j in i + 1 until built.size) assertFalse(LayoutPreset.sameLayout(built[i], built[j]))
    }

    @Test
    fun presetsSurviveThePreferences() {
        for (p in LayoutPreset.entries) {
            val l = p.build(840f, 393f, false)
            assertTrue(LayoutPreset.sameLayout(l, PadLayout.decode(l.encode())))
        }
    }

    @Test
    fun leftyMirrorsTheSides() {
        val w = 800f
        val placed = PadGeometry.placed(LayoutPreset.LEFTY.build(w, 384f, false), w, 384f, false)
        val def = PadGeometry.defaults(w, 384f, false)
        assertEquals(w - def.getValue(PadPart.DPAD).cx, placed.getValue(PadPart.DPAD).cx, 0.1f)
        assertEquals(w - def.getValue(PadPart.FACE).cx, placed.getValue(PadPart.FACE).cx, 0.1f)
    }
}
