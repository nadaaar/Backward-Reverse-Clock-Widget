package com.example.reverseclockwidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.RemoteViews
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import android.os.Bundle
import android.util.TypedValue

class ClockWidget : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            AppWidgetManager.ACTION_APPWIDGET_UPDATE -> {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, ClockWidget::class.java))
                onUpdate(context, appWidgetManager, ids)
                ensureTickerServiceRunning(context)
            }
            AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED -> {
                // Widget was resized, ensure service keeps running
                ensureTickerServiceRunning(context)
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        ensureTickerServiceRunning(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // Stop ticker service when last widget is removed
        try {
            context.stopService(Intent(context, TickerService::class.java))
        } catch (t: Throwable) {
            android.util.Log.e("ClockWidget", "Failed to stop TickerService", t)
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        // Widget was resized, update it immediately
        updateAppWidget(context, appWidgetManager, appWidgetId)
        ensureTickerServiceRunning(context)
    }

    private fun ensureTickerServiceRunning(context: Context) {
        try {
            val serviceIntent = Intent(context, TickerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (t: Throwable) {
            android.util.Log.e("ClockWidget", "Failed to start TickerService", t)
        }
    }

    companion object {
        private fun dpToPx(context: Context, dp: Int): Int {
            return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp.toFloat(),
                context.resources.displayMetrics
            ).toInt()
        }

        private fun resolveWidgetSizePx(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ): Pair<Int, Int> {
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val minWdp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110)
            val minHdp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
            val maxWdp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, minWdp)
            val maxHdp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, minHdp)

            // Use the larger within provided range to render crisp at current size
            val targetWdp = Math.max(minWdp, maxWdp)
            val targetHdp = Math.max(minHdp, maxHdp)

            val widthPx = dpToPx(context, Math.max(targetWdp, 110))
            val heightPx = dpToPx(context, Math.max(targetHdp, 110))
            return widthPx to heightPx
        }

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.clock_widget)
            
            // Get current widget dimensions (dp) and convert to px for crisp rendering
            val (width, height) = resolveWidgetSizePx(context, appWidgetManager, appWidgetId)
            
            val bitmap = createClockBitmap(width, height)
            views.setImageViewBitmap(R.id.clock_image, bitmap)

            // Check permissions and set click to open settings if needed
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            }
            val ignoringBatteryOpts = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                powerManager.isIgnoringBatteryOptimizations(context.packageName)
            } else {
                true
            }

            var settingsIntent: Intent? = null
            if (!canScheduleExact) {
                settingsIntent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            } else if (!ignoringBatteryOpts) {
                settingsIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            }

            if (settingsIntent != null) {
                val pendingIntent = android.app.PendingIntent.getActivity(
                    context,
                    0,
                    settingsIntent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.clock_image, pendingIntent)
            } else {
                views.setOnClickPendingIntent(R.id.clock_image, null)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun createClockBitmap(width: Int, height: Int): Bitmap {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val centerX = width / 2f
            val centerY = height / 2f
            val radius = min(centerX, centerY) * 0.95f

            // Background (transparent by default) - keep clear for widget blending
            // No tick marks; only numbers as requested
            paint.style = Paint.Style.STROKE
            paint.color = Color.BLACK
            paint.strokeWidth = Math.max(2.5f, radius * 0.006f)
            canvas.drawCircle(centerX, centerY, radius, paint)

            // Draw numbers (reversed placement)
            paint.style = Paint.Style.FILL
            paint.textSize = Math.max(24f, radius * 0.18f)
            paint.textAlign = Paint.Align.CENTER
            for (i in 1..12) {
                var angle = -((i % 12) * 30f)
                if (angle < 0) angle += 360f
                val rad = Math.toRadians(angle.toDouble())
                val textRadius = radius * 0.78f
                val x = centerX + textRadius * sin(rad).toFloat()
                val y = centerY - textRadius * cos(rad).toFloat() + (paint.textSize / 3)
                canvas.drawText(i.toString(), x, y, paint)
            }

            // Get current time
            val cal = Calendar.getInstance()
            val hour = cal.get(Calendar.HOUR).toFloat()
            val minute = cal.get(Calendar.MINUTE).toFloat()
            val second = cal.get(Calendar.SECOND).toFloat()
            val millis = cal.get(Calendar.MILLISECOND).toFloat()

            // Add milliseconds for smoother second hand
            val smoothSecond = second + (millis / 1000f)

            // Hand widths proportional
            val hourWidth = Math.max(6f, radius * 0.03f)
            val minuteWidth = Math.max(4f, radius * 0.022f)
            val secondWidth = Math.max(2f, radius * 0.012f)

            // Hour hand (reversed angle)
            var hourAngle = -(hour * 30f + minute * 0.5f + smoothSecond * (0.5f / 60f))
            if (hourAngle < 0) hourAngle += 360f
            drawHand(canvas, centerX, centerY, hourAngle, radius * 0.5f, hourWidth, Color.BLACK)

            // Minute hand (reversed angle)
            var minAngle = -(minute * 6f + smoothSecond * 0.1f)
            if (minAngle < 0) minAngle += 360f
            drawHand(canvas, centerX, centerY, minAngle, radius * 0.75f, minuteWidth, Color.BLACK)

            // Second hand (reversed angle, red for visibility)
            var secAngle = -(smoothSecond * 6f)
            if (secAngle < 0) secAngle += 360f
            drawHand(canvas, centerX, centerY, secAngle, radius * 0.9f, secondWidth, Color.RED)

            // Center hub
            paint.style = Paint.Style.FILL
            paint.color = Color.BLACK
            canvas.drawCircle(centerX, centerY, Math.max(4f, radius * 0.035f), paint)

            return bitmap
        }

        private fun drawHand(
            canvas: Canvas,
            centerX: Float,
            centerY: Float,
            angle: Float,
            length: Float,
            width: Float,
            color: Int
        ) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = color
            paint.strokeWidth = width
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            val rad = Math.toRadians(angle.toDouble())
            val endX = centerX + length * sin(rad).toFloat()
            val endY = centerY - length * cos(rad).toFloat()
            canvas.drawLine(centerX, centerY, endX, endY, paint)
        }
    }
}