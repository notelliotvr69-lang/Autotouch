package dev.autotouch.dualsenserumble

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.hardware.input.InputManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.InputDevice
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

class MainActivity : Activity(), InputManager.InputDeviceListener {
    private lateinit var inputManager: InputManager
    private lateinit var statusText: TextView
    private lateinit var intensityText: TextView
    private lateinit var intensitySlider: SeekBar
    private lateinit var startButton: Button
    private var selectedDeviceId = -1
    private var controllerVibrator: Vibrator? = null

    private val handler = Handler(Looper.getMainLooper())
    private val finishTest = Runnable {
        if (::startButton.isInitialized) {
            startButton.isEnabled = controllerVibrator?.hasVibrator() == true
        }
        if (::statusText.isInitialized) {
            statusText.text = "Test finished. Tap Start to run another short test."
            statusText.setTextColor(Color.LTGRAY)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        inputManager = getSystemService(Context.INPUT_SERVICE) as InputManager
        buildInterface()
        inputManager.registerInputDeviceListener(this, handler)
        refreshController()
    }

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(28), dp(24), dp(24))
            setBackgroundColor(Color.rgb(17, 22, 32))
        }

        val title = TextView(this).apply {
            text = "DualSense Rumble"
            textSize = 27f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        root.addView(title, matchWrap())

        val subtitle = TextView(this).apply {
            text = "Bluetooth controller vibration test"
            textSize = 14f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(24))
        }
        root.addView(subtitle, matchWrap())

        statusText = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.rgb(255, 210, 110))
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(14), dp(8), dp(18))
            text = "Looking for a connected DualSense…"
        }
        root.addView(statusText, matchWrap())

        val scanButton = Button(this).apply {
            text = "SCAN FOR CONTROLLER"
            setOnClickListener { refreshController() }
        }
        root.addView(scanButton, matchWrap())

        intensityText = TextView(this).apply {
            text = "Intensity: 100%"
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(0, dp(28), 0, dp(4))
        }
        root.addView(intensityText, matchWrap())

        intensitySlider = SeekBar(this).apply {
            max = 100
            progress = 100
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    intensityText.text = "Intensity: ${progress.coerceAtLeast(1)}%"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        root.addView(intensitySlider, matchWrap())

        startButton = Button(this).apply {
            text = "START SHORT TEST"
            isEnabled = false
            setOnClickListener { startRumbleTest() }
        }
        val startParams = matchWrap().apply { topMargin = dp(24) }
        root.addView(startButton, startParams)

        val stopButton = Button(this).apply {
            text = "STOP"
            setOnClickListener { stopTest("Test stopped.") }
        }
        root.addView(stopButton, matchWrap())

        val note = TextView(this).apply {
            text = "Each test is a brief 1.5-second burst. STOP cancels it immediately, and leaving the app also stops vibration."
            textSize = 13f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, dp(22), 0, 0)
        }
        root.addView(note, matchWrap())

        setContentView(root)
    }

    private fun refreshController() {
        stopTest()
        selectedDeviceId = -1
        controllerVibrator = null

        val devices = InputDevice.getDeviceIds()
            .mapNotNull { InputDevice.getDevice(it) }
            .filter { device ->
                device.isExternal &&
                    ((device.sources and InputDevice.SOURCE_GAMEPAD) != 0 ||
                     (device.sources and InputDevice.SOURCE_JOYSTICK) != 0)
            }

        val dualSense = devices.firstOrNull { device ->
            device.vendorId == 0x054C ||
                device.name.contains("DualSense", ignoreCase = true) ||
                device.name.contains("Wireless Controller", ignoreCase = true)
        }

        if (dualSense == null) {
            statusText.text = "No DualSense found. Pair it in Android Bluetooth settings, then tap Scan."
            statusText.setTextColor(Color.rgb(255, 210, 110))
            startButton.isEnabled = false
            return
        }

        selectedDeviceId = dualSense.id
        val deviceVibrator = try {
            dualSense.vibratorManager.defaultVibrator
        } catch (_: Exception) {
            null
        }

        if (deviceVibrator == null || !deviceVibrator.hasVibrator()) {
            statusText.text = "Android sees ${dualSense.name}, but does not expose a controller rumble motor to apps on this device."
            statusText.setTextColor(Color.rgb(255, 150, 130))
            startButton.isEnabled = false
            return
        }

        controllerVibrator = deviceVibrator
        statusText.text = "Connected: ${dualSense.name}\nController rumble is available."
        statusText.setTextColor(Color.rgb(130, 230, 160))
        if (!deviceVibrator.hasAmplitudeControl()) {
            statusText.append("\nThis controller does not report adjustable amplitude; the intensity slider may not change strength.")
        }
        startButton.isEnabled = true
    }

    private fun startRumbleTest() {
        val vibrator = controllerVibrator
        if (vibrator == null || !vibrator.hasVibrator()) {
            refreshController()
            return
        }

        val percent = intensitySlider.progress.coerceAtLeast(1)
        val amplitude = if (vibrator.hasAmplitudeControl()) {
            (percent * 255 / 100).coerceIn(1, 255)
        } else {
            VibrationEffect.DEFAULT_AMPLITUDE
        }

        try {
            vibrator.vibrate(VibrationEffect.createOneShot(1_500L, amplitude))
            startButton.isEnabled = false
            statusText.text = "Short rumble test running…"
            statusText.setTextColor(Color.rgb(130, 230, 160))
            handler.removeCallbacks(finishTest)
            handler.postDelayed(finishTest, 1_600L)
        } catch (error: Exception) {
            statusText.text = "Could not start controller rumble: ${error.message ?: "unsupported vibration request"}"
            statusText.setTextColor(Color.rgb(255, 150, 130))
        }
    }

    private fun stopTest(message: String? = null) {
        handler.removeCallbacks(finishTest)
        try {
            controllerVibrator?.cancel()
        } catch (_: Exception) {
        }
        if (::startButton.isInitialized) {
            startButton.isEnabled = controllerVibrator?.hasVibrator() == true
        }
        if (message != null && ::statusText.isInitialized) {
            statusText.text = message
            statusText.setTextColor(Color.LTGRAY)
        }
    }

    override fun onInputDeviceAdded(deviceId: Int) = refreshController()
    override fun onInputDeviceRemoved(deviceId: Int) {
        if (deviceId == selectedDeviceId) refreshController()
    }
    override fun onInputDeviceChanged(deviceId: Int) = refreshController()

    override fun onPause() {
        stopTest("Test stopped because the app went into the background.")
        super.onPause()
    }

    override fun onDestroy() {
        stopTest()
        inputManager.unregisterInputDeviceListener(this)
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )
}
