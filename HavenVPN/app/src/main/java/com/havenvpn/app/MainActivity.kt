package com.havenvpn.app

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.net.VpnService
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config

class MainActivity : ComponentActivity() {
    private lateinit var backend: GoBackend
    private lateinit var status: TextView
    private lateinit var profileLabel: TextView
    private lateinit var connectButton: Button
    private var config: Config? = null
    private var connected = false

    private val tunnel = object : Tunnel {
        override fun getName() = "Haven"
        override fun onStateChange(newState: Tunnel.State) {
            runOnUiThread {
                connected = newState == Tunnel.State.UP
                renderState()
            }
        }
    }

    private val vpnPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) connectTunnel()
        else toast("VPN permission was not granted.")
    }

    private val profilePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                contentResolver.openInputStream(uri)?.use { config = Config.parse(it) }
                    ?: error("Could not open the selected file.")
                profileLabel.text = "WireGuard profile imported"
                connectButton.isEnabled = true
                toast("Profile ready.")
            } catch (e: Exception) {
                config = null
                profileLabel.text = "Profile could not be imported"
                connectButton.isEnabled = false
                toast("Invalid WireGuard profile: ${e.message}")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        backend = GoBackend(applicationContext)
        buildScreen()
        renderState()
    }

    private fun buildScreen() {
        val navy = Color.rgb(9, 16, 32)
        val panel = Color.rgb(22, 34, 57)
        val blue = Color.rgb(83, 155, 255)
        val white = Color.WHITE
        val muted = Color.rgb(174, 190, 214)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(28, 42, 28, 28)
            setBackgroundColor(navy)
        }
        root.addView(TextView(this).apply {
            text = "◈  HAVEN VPN"
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(white)
            gravity = Gravity.CENTER
        }, matchWrap())

        root.addView(TextView(this).apply {
            text = "Your internet. Your privacy."
            textSize = 14f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 40)
        }, matchWrap())

        status = TextView(this).apply {
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(white)
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 20)
        }
        root.addView(status, matchWrap())

        connectButton = Button(this).apply {
            text = "CONNECT"
            isAllCaps = false
            textSize = 18f
            setTextColor(white)
            backgroundTintList = ColorStateList.valueOf(blue)
            isEnabled = false
            setOnClickListener { onConnectPressed() }
        }
        root.addView(connectButton, LinearLayout.LayoutParams(-1, 58).apply {
            bottomMargin = 24
        })

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 18, 18, 18)
            setBackgroundColor(panel)
        }
        card.addView(TextView(this).apply {
            text = "VPN PROFILE"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(blue)
        }, matchWrap())

        profileLabel = TextView(this).apply {
            text = "No profile imported"
            textSize = 16f
            setTextColor(white)
            setPadding(0, 8, 0, 14)
        }
        card.addView(profileLabel, matchWrap())

        card.addView(Button(this).apply {
            text = "Import WireGuard profile"
            isAllCaps = false
            setOnClickListener {
                if (connected) stopTunnel()
                profilePicker.launch(arrayOf("text/plain", "application/octet-stream", "*/*"))
            }
        }, matchWrap())
        root.addView(card, matchWrap())

        root.addView(TextView(this).apply {
            text = "A valid WireGuard server profile is required. Haven does not provide VPN servers yet."
            textSize = 12f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(8, 26, 8, 0)
        }, matchWrap())

        setContentView(root)
    }

    private fun onConnectPressed() {
        if (connected) {
            stopTunnel()
            return
        }
        val intent = VpnService.prepare(this)
        if (intent != null) vpnPermission.launch(intent) else connectTunnel()
    }

    private fun connectTunnel() {
        val selected = config ?: return
        connectButton.isEnabled = false
        Thread {
            try {
                backend.setState(tunnel, Tunnel.State.UP, selected)
            } catch (e: Exception) {
                runOnUiThread {
                    toast("Connection failed: ${e.message}")
                    connectButton.isEnabled = true
                }
            }
        }.start()
    }

    private fun stopTunnel() {
        connectButton.isEnabled = false
        Thread {
            try {
                backend.setState(tunnel, Tunnel.State.DOWN, null)
            } catch (e: Exception) {
                runOnUiThread { toast("Disconnect failed: ${e.message}") }
            } finally {
                runOnUiThread { connectButton.isEnabled = config != null }
            }
        }.start()
    }

    private fun renderState() {
        status.text = if (connected) "CONNECTED" else "DISCONNECTED"
        connectButton.text = if (connected) "DISCONNECT" else "CONNECT"
        connectButton.isEnabled = config != null
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    private fun matchWrap() = LinearLayout.LayoutParams(-1, -2)
}
