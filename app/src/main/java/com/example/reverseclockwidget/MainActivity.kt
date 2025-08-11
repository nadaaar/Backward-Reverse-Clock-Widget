package com.example.reverseclockwidget

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : Activity() {
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("clock_prefs", Context.MODE_PRIVATE)

        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!granted) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 48)
        }

        val blackBtn = Button(this).apply {
            text = "Black"
            setOnClickListener { saveColorAndRefresh("black") }
        }
        val whiteBtn = Button(this).apply {
            text = "White"
            setOnClickListener { saveColorAndRefresh("white") }
        }

        layout.addView(blackBtn)
        layout.addView(whiteBtn)
        setContentView(layout)
    }

    private fun saveColorAndRefresh(color: String) {
        prefs.edit().putString("clock_color", color).apply()
        // Refresh all widgets
        val manager = AppWidgetManager.getInstance(this)
        val ids = manager.getAppWidgetIds(ComponentName(this, ClockWidget::class.java))
        for (id in ids) {
            ClockWidget.updateAppWidget(this, manager, id)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
}