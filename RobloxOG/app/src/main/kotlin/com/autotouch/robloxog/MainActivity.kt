package com.autotouch.robloxog

import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 28)
            setBackgroundColor(Color.rgb(16, 16, 16))
        }

        val title = TextView(this).apply {
            text = "ROBLOX OG UI"
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        root.addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val status = TextView(this).apply {
            text = "UI shell • no game injection"
            textSize = 14f
            setTextColor(Color.LTGRAY)
            setPadding(0, 12, 0, 24)
        }
        root.addView(status)

        listOf("Fly", "Spin", "Teleport", "ESP", "Rejoin", "Console").forEach { label ->
            val button = Button(this).apply {
                text = label
                setOnClickListener { status.text = "$label pressed (UI preview)" }
            }
            root.addView(button, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 10 })
        }

        setContentView(root)
    }
}
