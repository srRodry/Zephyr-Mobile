package xendroid.compose.gamepad

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlOptionsTest {
    private val legacy = ControlOptions(touchCamera = true, gyroAim = GyroAim.WHILE_LT, unbufferedInput = false, rumble = RumbleIntensity.HIGH)

    @Test fun valuesNeverSetComeFromTheOlderPreferences() {
        assertEquals(legacy, ControlOptionsDto().resolve(legacy))
        // Once written, the stored value wins over the older one.
        val stored = ControlOptionsDto(touchCamera = false, gyroSensitivity = "HIGH")
        val resolved = stored.resolve(legacy)
        assertFalse(resolved.touchCamera)
        assertEquals(GyroSensitivity.HIGH, resolved.gyroSensitivity)
        assertEquals(GyroAim.WHILE_LT, resolved.gyroAim)
        assertEquals(RumbleIntensity.HIGH, resolved.rumble)
    }

    @Test fun aWriteStoresEveryValueAndRoundTrips() {
        val options = ControlOptions(touchCamera = true, gyroCamera = true, gyroAim = GyroAim.WHILE_LB,
            gyroSensitivity = GyroSensitivity.LOW, unbufferedInput = false, rumble = RumbleIntensity.OFF)
        val dto = ControlOptionsDto.of(options)
        assertEquals(options, dto.resolve(ControlOptions()))
        val json = Json { encodeDefaults = true }
        assertEquals(dto, json.decodeFromString(ControlOptionsDto.serializer(), json.encodeToString(ControlOptionsDto.serializer(), dto)))
    }

    @Test fun unknownNamesOfANewerBuildReadAsTheDefaults() {
        val resolved = ControlOptionsDto(gyroAim = "WHILE_RT", gyroSensitivity = "ULTRA", rumble = "MAX").resolve(legacy)
        assertEquals(GyroAim.ALWAYS, resolved.gyroAim)
        assertEquals(GyroSensitivity.NORMAL, resolved.gyroSensitivity)
        assertEquals(RumbleIntensity.MEDIUM, resolved.rumble)
    }

    @Test fun gyroSensitivityCyclesLikeTheInGameMenu() {
        assertEquals(GyroSensitivity.HIGH, GyroSensitivity.NORMAL.next())
        assertEquals(GyroSensitivity.LOW, GyroSensitivity.HIGH.next())
        assertEquals(0.35f, GyroSensitivity.NORMAL.scale)
    }

    @Test fun aControllerGetsItsOwnIntensityOrGoesBackToTheDefault() {
        val pad = "a1b2c3d4e5f60718293a4b5c6d7e8f9012345678"
        val settings = RumbleSettings(default = RumbleIntensity.MEDIUM).withDevice(pad, RumbleIntensity.LOW)
        assertEquals(RumbleIntensity.LOW, settings.forDevice(pad))
        assertTrue(pad !in settings.withDevice(pad, null).perDevice)
        assertEquals(settings, settings.withDevice("  ", RumbleIntensity.HIGH))
        // Bounded: the controller set longest ago gives its place.
        var many = RumbleSettings()
        repeat(RumbleSettings.MAX_DEVICES + 3) { many = many.withDevice("pad$it", RumbleIntensity.HIGH) }
        assertEquals(RumbleSettings.MAX_DEVICES, many.perDevice.size)
        assertNull(many.perDevice["pad0"])
        assertEquals(RumbleIntensity.HIGH, many.perDevice["pad${RumbleSettings.MAX_DEVICES + 2}"])
    }

    @Test fun undoBringsBackEachStateBeforeAChangeAndKeepsTheNewest() {
        val history = EditHistory<Int>(limit = 3)
        assertFalse(history.canUndo)
        history.push(1); history.push(2); history.push(2); history.push(3); history.push(4)
        assertEquals(3, history.size)
        assertEquals(4, history.undo())
        assertEquals(3, history.undo())
        assertEquals(2, history.undo())
        assertNull(history.undo())
        assertFalse(history.canUndo)
    }
    @Test fun redoRestoresUndoneStateAndNewEditDiscardsFuture() {
        val history = EditHistory<Int>()
        history.push(1)
        history.push(2)
        assertEquals(2, history.undo(3))
        assertEquals(1, history.undo(2))
        assertEquals(2, history.redo(1))
        assertEquals(3, history.redo(2))
        assertFalse(history.canRedo)
        assertEquals(2, history.undo(3))
        history.push(2)
        assertFalse(history.canRedo)
        assertEquals(2, history.undo(4))
    }
}
