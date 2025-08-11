package com.example.reverseclockwidget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TickerService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default)
    private var tickerJob: Job? = null
    private var isRunning = false

    companion object {
        private const val TAG = "TickerService"
        private const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "TickerService created")
        startInForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "TickerService started with startId: $startId")
        if (!isRunning) {
            startTicker()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "TickerService destroyed")
        tickerJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startInForeground() {
        val channelId = ensureChannel()
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Clock widget running")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel(): String {
        val channelId = "clock_widget_ticker"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val existing = nm.getNotificationChannel(channelId)
            if (existing == null) {
                val channel = NotificationChannel(
                    channelId,
                    "Clock Widget",
                    NotificationManager.IMPORTANCE_MIN
                ).apply {
                    setShowBadge(false)
                    enableLights(false)
                    enableVibration(false)
                    setSound(null, null)
                }
                nm.createNotificationChannel(channel)
            }
        }
        return channelId
    }

    private fun startTicker() {
        if (isRunning) return
        
        isRunning = true
        tickerJob = serviceScope.launch {
            Log.d(TAG, "Ticker started")
            
            while (isActive) {
                try {
                    val appWidgetManager = AppWidgetManager.getInstance(this@TickerService)
                    val ids = appWidgetManager.getAppWidgetIds(
                        ComponentName(this@TickerService, ClockWidget::class.java)
                    )

                    if (ids.isEmpty()) {
                        Log.d(TAG, "No widgets found, stopping service")
                        stopSelf()
                        break
                    }

                    // Update all widgets with their actual dimensions (using widget options for accuracy)
                    for (widgetId in ids) {
                        try {
                            val opts = appWidgetManager.getAppWidgetOptions(widgetId)
                            val minWdp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110)
                            val minHdp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
                            val maxWdp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, minWdp)
                            val maxHdp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, minHdp)

                            val targetWdp = Math.max(minWdp, maxWdp)
                            val targetHdp = Math.max(minHdp, maxHdp)

                            // Convert dp to pixels for Android 16 compatibility
                            val dm = resources.displayMetrics
                            val width = (targetWdp * dm.density).toInt().coerceAtLeast((110 * dm.density).toInt())
                            val height = (targetHdp * dm.density).toInt().coerceAtLeast((110 * dm.density).toInt())
                            
                            val views = android.widget.RemoteViews(packageName, R.layout.clock_widget)
                            val bitmap = ClockWidget.createClockBitmap(width, height)
                            views.setImageViewBitmap(R.id.clock_image, bitmap)
                            
                            appWidgetManager.updateAppWidget(widgetId, views)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to update widget $widgetId", e)
                        }
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "Tick failed", t)
                    // Don't break the loop, just continue
                }

                // Precise timing to next second boundary
                val now = System.currentTimeMillis()
                val sleep = 1000L - (now % 1000L)
                delay(sleep)
            }
            
            isRunning = false
            Log.d(TAG, "Ticker stopped")
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "Task removed, ensuring service continues")
        // Ensure service continues even if app is removed from recent tasks
        val restartServiceIntent = Intent(applicationContext, TickerService::class.java)
        restartServiceIntent.setPackage(packageName)
        startService(restartServiceIntent)
        super.onTaskRemoved(rootIntent)
    }
}


