package xendroid.compose.core

import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.PowerManager
import android.view.Display
import kotlin.math.abs
import xendroid.compose.gamepad.Kc

enum class BackgroundPolicy { AUTO, MANUAL, NEVER }
data class DisplayChoice(val id: Int, val width: Int, val height: Int, val hz: Float)

/** Never switch resolution implicitly while selecting refresh rate. */
fun refreshChoices(display: Display): List<DisplayChoice> {
    val current = display.mode
    return display.supportedModes.filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
        .map { DisplayChoice(it.modeId, it.physicalWidth, it.physicalHeight, it.refreshRate) }.distinctBy { it.hz }.sortedBy { it.hz }
}

/** Apply an explicit rate or match a capped game's cadence on the activity's display. */
fun selectRefresh(activity: Activity, hz: Float?, gameFps: Int = 0): Float? {
    @Suppress("DEPRECATION") val display = if (Build.VERSION.SDK_INT >= 30) activity.display else activity.windowManager.defaultDisplay
    display ?: return null
    val choices = if (hz != null || gameFps > 0) refreshChoices(display) else emptyList()
    val automatic = if (hz == null && gameFps > 0) {
        val savingPower = activity.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
        cadenceRefreshRate(gameFps, choices.map { it.hz }, savingPower)
    } else null
    val selected = (hz ?: automatic)?.let { wanted -> choices.minByOrNull { abs(it.hz - wanted) } }
    val attrs = activity.window.attributes
    val modeId = selected?.id ?: 0
    val rate = selected?.hz ?: 0f
    if (attrs.preferredDisplayModeId != modeId || attrs.preferredRefreshRate != rate) {
        attrs.preferredDisplayModeId = modeId
        attrs.preferredRefreshRate = rate
        activity.window.attributes = attrs
    }
    return selected?.hz
}

fun sustainedPerformance(activity: Activity, enabled: Boolean): Boolean {
    val power = activity.getSystemService(PowerManager::class.java)
    if (!power.isSustainedPerformanceModeSupported) return false
    activity.window.setSustainedPerformanceMode(enabled)
    return true
}

/** Opt-in angular-velocity camera input. No absolute orientation, root or mouse injection. */
class GyroCamera(context: Context, private val emit: (Int, Boolean, Int) -> Unit) : SensorEventListener {
    private val manager = context.getSystemService(SensorManager::class.java)
    private val sensor = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    val available: Boolean get() = sensor != null
    private var enabled = false
    private val calibration = GyroCalibration()
    val calibrated: Boolean get() = calibration.ready
    fun calibrate() { calibration.reset() }
    var sensitivity = 0.35f
    fun start(): Boolean {
        if (enabled) return true
        enabled = sensor?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) } == true
        return enabled
    }
    fun stop() {
        manager.unregisterListener(this); enabled = false
        for (key in Kc.RTHUMB_LEFT..Kc.RTHUMB_DOWN) emit(key, false, 0)
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    override fun onSensorChanged(event: SensorEvent) {
        if (!enabled) return
        if (!calibration.observe(event.values)) return
        val x = (-calibration.corrected(event.values[1], 1) * sensitivity).coerceIn(-1f, 1f)
        val y = (calibration.corrected(event.values[0], 0) * sensitivity).coerceIn(-1f, 1f)
        axis(x, Kc.RTHUMB_LEFT, Kc.RTHUMB_RIGHT)
        axis(y, Kc.RTHUMB_UP, Kc.RTHUMB_DOWN)
    }
    private fun axis(value: Float, negative: Int, positive: Int) {
        emit(negative, value < 0f, if (value < 0) (value * 32768).toInt() else 0)
        emit(positive, value > 0f, if (value > 0) (value * 32767).toInt() else 0)
    }
}
