package com.tcl.automute.scootermac

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Button
import kotlinx.coroutines.*
import android.widget.EditText
import android.widget.Toast

class DeviceListActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var refreshBtn: Button
    private lateinit var manualBtn: Button
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_list)
        listView = findViewById(R.id.deviceList)
        refreshBtn = findViewById(R.id.refreshBtn)
        manualBtn = findViewById(R.id.manualBtn)

        refreshBtn.setOnClickListener { discover() }
        manualBtn.setOnClickListener { showManualEntry() }
        discover()
    }

    private fun showManualEntry() {
        val input = EditText(this)
        val dlg = AlertDialog.Builder(this)
            .setTitle("Enter TV IP")
            .setView(input)
            .setPositiveButton("OK") { d, _ ->
                val ip = input.text.toString().trim()
                if (ip.matches(Regex("^(?:[0-9]{1,3}\.){3}[0-9]{1,3}$"))) {
                    val i = Intent()
                    i.putExtra("selected_ip", ip)
                    i.putExtra("selected_name", ip)
                    setResult(RESULT_OK, i)
                    finish()
                } else {
                    Toast.makeText(this, "Invalid IP", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
        dlg.show()
    }

    private fun discover() {
        refreshBtn.isEnabled = false
        refreshBtn.text = "Searching..."
        scope.launch {
            val devices = withContext(Dispatchers.IO) { RokuDiscovery.discoverAll(5000) }
            val names = devices.map { "${'$'}{it.name} — ${'$'}{it.ip}" }.toMutableList()
            if (names.isEmpty()) names.add("No devices found")
            val adapter = ArrayAdapter(this@DeviceListActivity, android.R.layout.simple_list_item_1, names)
            listView.adapter = adapter
            listView.setOnItemClickListener { _, _, pos, _ ->
                if (devices.isNotEmpty() && pos < devices.size) {
                    val d = devices[pos]
                    val i = Intent()
                    i.putExtra("selected_ip", d.ip)
                    i.putExtra("selected_name", d.name)
                    setResult(RESULT_OK, i)
                    finish()
                }
            }
            refreshBtn.isEnabled = true
            refreshBtn.text = "Refresh"
        }
    }
}
