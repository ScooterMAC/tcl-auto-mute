package com.tcl.automute.scootermac

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.TextView
import com.google.android.material.slider.Slider
import android.widget.ScrollView
import java.io.File

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val logView = findViewById<TextView>(R.id.logView)
        val clearBtn = findViewById<Button>(R.id.clearLogBtn)
        val sensitivity = findViewById<Slider>(R.id.sensitivitySliderSettings)

        // load log
        val fn = File(filesDir, "events.log")
        if (fn.exists()) {
            logView.text = fn.readText()
        } else {
            logView.text = "No events yet."
        }

        clearBtn.setOnClickListener {
            if (fn.exists()) fn.writeText("")
            logView.text = "No events yet."
        }

        sensitivity.value = 0.5f // placeholder; ideally load saved prefs
    }
}
