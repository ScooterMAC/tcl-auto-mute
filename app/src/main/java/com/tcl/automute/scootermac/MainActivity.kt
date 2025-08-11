package com.tcl.automute.scootermac

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.*
import android.view.View
import android.widget.ImageButton
import android.widget.TextView

class MainActivity : AppCompatActivity() {

    private lateinit var startBtn: MaterialButton
    private lateinit var stopBtn: MaterialButton
    private lateinit var findBtn: MaterialButton
    private lateinit var muteToggleBtn: MaterialButton
    private lateinit var sensitivitySlider: Slider
    private lateinit var tvIpInput: TextInputEditText
    private lateinit var bellBtn: ImageButton
    private lateinit var gearBtn: ImageButton
    private lateinit var selectedTvLabel: TextView

    private var mutingEnabled = true
    private var silentMode = false

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                // TODO: show explanation dialog
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        startBtn = findViewById(R.id.startBtn)
        stopBtn = findViewById(R.id.stopBtn)
        findBtn = findViewById(R.id.findBtn)
        muteToggleBtn = findViewById(R.id.muteToggleBtn)
        sensitivitySlider = findViewById(R.id.sensitivitySlider)
        tvIpInput = findViewById(R.id.tvIpInput)
        bellBtn = findViewById(R.id.bellBtn)
        gearBtn = findViewById(R.id.gearBtn)
        selectedTvLabel = findViewById(R.id.selectedTvLabel)

        findBtn.setOnClickListener {
            startActivityForResult(Intent(this, DeviceListActivity::class.java), 1)
        }

        bellBtn.setOnClickListener {
            silentMode = !silentMode
            updateBellIcon()
        }

        gearBtn.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        muteToggleBtn.setOnLongClickListener {
            // Test mute
            val ip = tvIpInput.text.toString().trim()
            CoroutineScope(Dispatchers.IO).launch {
                RokuTclController(ip).mute()
            }
            true
        }

        muteToggleBtn.setOnClickListener {
            mutingEnabled = !mutingEnabled
            muteToggleBtn.text = if (mutingEnabled) "Muting: ON" else "Muting: OFF"
        }

        startBtn.setOnClickListener {
            ensureMicPermission()
            val sensitivity = sensitivitySlider.value
            val tvIp = tvIpInput.text.toString().trim()
            val intent = Intent(this, DetectService::class.java).apply {
                putExtra("sensitivity", sensitivity)
                putExtra("tv_ip", tvIp)
                putExtra("muting_enabled", mutingEnabled)
                putExtra("silent_mode", silentMode)
                action = DetectService.ACTION_START
            }
            ContextCompat.startForegroundService(this, intent)
        }

        stopBtn.setOnClickListener {
            val intent = Intent(this, DetectService::class.java).apply {
                action = DetectService.ACTION_STOP
            }
            startService(intent)
        }
    }

    private fun updateBellIcon() {
        if (silentMode) {
            bellBtn.setImageResource(android.R.drawable.ic_lock_silent_mode)
        } else {
            bellBtn.setImageResource(android.R.drawable.ic_lock_silent_mode_off)
        }
    }

    private fun ensureMicPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 && data != null) {
            val ip = data.getStringExtra("selected_ip")
            val name = data.getStringExtra("selected_name")
            if (!ip.isNullOrBlank()) {
                tvIpInput.setText(ip)
                selectedTvLabel.text = name ?: ip
            }
        }
    }
}
