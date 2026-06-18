package com.lifelineventures.pause

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeFormatTest {
    @Test fun formatsZeroAsMinuteSeconds() {
        assertEquals("0:00", TimeFormat.remainingLong(0))
    }

    @Test fun formatsSecondsWithLeadingZero() {
        assertEquals("0:05", TimeFormat.remainingLong(5))
        assertEquals("0:59", TimeFormat.remainingLong(59))
    }

    @Test fun formatsMinutesAndSeconds() {
        assertEquals("1:00", TimeFormat.remainingLong(60))
        assertEquals("9:09", TimeFormat.remainingLong(549))
        assertEquals("59:59", TimeFormat.remainingLong(3599))
    }

    @Test fun switchesToHoursAtAnHour() {
        assertEquals("1:00:00", TimeFormat.remainingLong(3600))
        assertEquals("2:05:09", TimeFormat.remainingLong(2 * 3600 + 5 * 60 + 9))
    }

    @Test fun treatsNegativeAsZero() {
        assertEquals("0:00", TimeFormat.remainingLong(-30))
    }
}

class HourglassMathTest {
    @Test fun fullProgressMapsToStartFill() {
        assertEquals(HourglassMath.START_FILL, HourglassMath.fill(1f), 1e-6f)
    }

    @Test fun emptyProgressMapsToEndFill() {
        assertEquals(HourglassMath.END_FILL, HourglassMath.fill(0f), 1e-6f)
    }

    @Test fun fillNeverReadsFullOrEmpty() {
        // Across the whole range the glyph stays strictly between empty and full.
        for (i in 0..100) {
            val fill = HourglassMath.fill(i / 100f)
            assertTrue(fill in 0.05f..0.85f)
        }
    }

    @Test fun fillIsMonotonicInProgress() {
        var previous = HourglassMath.fill(0f)
        for (i in 1..100) {
            val fill = HourglassMath.fill(i / 100f)
            assertTrue("fill should increase with progress", fill >= previous)
            previous = fill
        }
    }

    @Test fun outOfRangeProgressIsClamped() {
        assertEquals(HourglassMath.fill(0f), HourglassMath.fill(-1f), 1e-6f)
        assertEquals(HourglassMath.fill(1f), HourglassMath.fill(2f), 1e-6f)
    }

    @Test fun surfaceIsSqrtOfFill() {
        assertEquals(Math.sqrt(HourglassMath.fill(0.5f).toDouble()).toFloat(), HourglassMath.surface(0.5f), 1e-6f)
    }
}

class BubblePositionTest {
    @Test fun roundTripPreservesFractionWithinTolerance() {
        val max = 1080
        for (frac in listOf(0f, 0.25f, 0.5f, 0.7333f, 1f)) {
            val px = BubblePosition.toPixels(frac, max)
            val back = BubblePosition.toFraction(px, max)
            // Round-trips to within one pixel's worth of the original fraction.
            assertEquals(frac, back, 1f / max + 1e-4f)
        }
    }

    @Test fun pixelsClampedToBounds() {
        assertEquals(0, BubblePosition.toPixels(-0.5f, 500))
        assertEquals(500, BubblePosition.toPixels(1.5f, 500))
    }

    @Test fun zeroMaxNeverDividesByZero() {
        // A degenerate screen (e.g. before metrics settle) must not throw or yield NaN.
        assertEquals(0, BubblePosition.toPixels(0.5f, 0))
        assertEquals(0f, BubblePosition.toFraction(0, 0), 1e-6f)
    }

    @Test fun fractionClampedToUnitRange() {
        assertEquals(1f, BubblePosition.toFraction(9999, 500), 1e-6f)
        assertEquals(0f, BubblePosition.toFraction(-10, 500), 1e-6f)
    }
}

class BubblePresetsTest {
    @Test fun fixedPresetsIgnoreCustomValues() {
        assertEquals(0.122f, BubblePresets.metrics(BubblePresets.INSTAGRAM, 0.5f, 0.5f).sizeFraction, 1e-6f)
        assertEquals(0.033f, BubblePresets.metrics(BubblePresets.TIKTOK, 0.5f, 0.5f).edgeFraction, 1e-6f)
    }

    @Test fun customUsesSliderValues() {
        val m = BubblePresets.metrics(BubblePresets.CUSTOM, 0.18f, 0.05f)
        assertEquals(0.18f, m.sizeFraction, 1e-6f)
        assertEquals(0.05f, m.edgeFraction, 1e-6f)
    }

    @Test fun customClampsOutOfRange() {
        assertEquals(BubblePresets.SIZE_MAX, BubblePresets.metrics(BubblePresets.CUSTOM, 9f, 0.03f).sizeFraction, 1e-6f)
        assertEquals(BubblePresets.EDGE_MIN, BubblePresets.metrics(BubblePresets.CUSTOM, 0.15f, 0f).edgeFraction, 1e-6f)
    }

    @Test fun unknownPresetFallsBackToInstagram() {
        assertEquals(
            BubblePresets.metrics(BubblePresets.INSTAGRAM, 0f, 0f).sizeFraction,
            BubblePresets.metrics(99, 0f, 0f).sizeFraction,
            1e-6f
        )
    }
}

class SettingsRangesTest {
    @Test fun breathSecondsClampToOneToTwenty() {
        assertEquals(1, SettingsRanges.breathSeconds(0))
        assertEquals(1, SettingsRanges.breathSeconds(-99))
        assertEquals(20, SettingsRanges.breathSeconds(1000))
        assertEquals(7, SettingsRanges.breathSeconds(7))
    }

    @Test fun lockSecondsAllowZero() {
        assertEquals(0, SettingsRanges.lockSeconds(-5))
        assertEquals(60, SettingsRanges.lockSeconds(120))
        assertEquals(15, SettingsRanges.lockSeconds(15))
    }

    @Test fun blockMinutesClampToOneToOneTwenty() {
        assertEquals(1, SettingsRanges.blockMinutes(0))
        assertEquals(120, SettingsRanges.blockMinutes(99999))
        assertEquals(5, SettingsRanges.blockMinutes(5))
    }

    @Test fun snoozeMinutesClampToOneToSixty() {
        assertEquals(1, SettingsRanges.snoozeMinutes(0))
        assertEquals(60, SettingsRanges.snoozeMinutes(1000))
        assertEquals(5, SettingsRanges.snoozeMinutes(5))
    }

    @Test fun themeModeClampToValidEnum() {
        assertEquals(0, SettingsRanges.themeMode(-1))
        assertEquals(2, SettingsRanges.themeMode(9))
        assertEquals(1, SettingsRanges.themeMode(1))
    }

    @Test fun fractionClampsAndDefangsNaN() {
        assertEquals(0f, SettingsRanges.fraction(-1f), 1e-6f)
        assertEquals(1f, SettingsRanges.fraction(2f), 1e-6f)
        assertEquals(0.5f, SettingsRanges.fraction(0.5f), 1e-6f)
        assertEquals(0f, SettingsRanges.fraction(Float.NaN), 1e-6f)
    }
}
