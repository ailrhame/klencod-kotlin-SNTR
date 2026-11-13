package com.sntr.fox

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.os.BatteryManager
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlin.math.abs

class MainActivity : Activity() {
    private val OVERLAY_PERMISSION_REQUEST = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
            setBackgroundColor(0xFFF5F5F5.toInt())
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val title = TextView(this).apply {
            text = "🦊 SNTR FOF"
            textSize = 28f
            setTextColor(Color.parseColor("#FF6D00"))
            setPadding(0, 0, 0, 40)
        }

        val btnPermission = Button(this).apply {
            text = "منح صلاحية الظهور فوق التطبيقات"
            setOnClickListener { requestOverlayPermission() }
        }

        val btnStart = Button(this).apply {
            text = "تشغيل الشخصية 🦊"
            setPadding(24, 24, 24, 24)
            setBackgroundColor(Color.parseColor("#FF8F00"))
            setTextColor(Color.WHITE)
            setOnClickListener { startFoxService() }
        }

        layout.addView(title)
        layout.addView(btnPermission)
        layout.addView(btnStart)
        setContentView(layout)
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST)
        }
    }

    private fun startFoxService() {
        if (Settings.canDrawOverlays(this)) {
            createNotificationChannel()
            startForegroundService(Intent(this, FoxOverlayService::class.java))
            finish()
        } else {
            requestOverlayPermission()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "FoxServiceChannel", "خدمة الشخصية",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQUEST && Settings.canDrawOverlays(this)) {
            startFoxService()
        }
    }
}

class FoxOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var foxView: FoxView? = null
    private var lastBatteryLevel = -1
    private var isCharging = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val batteryPct = (level / scale.toFloat() * 100).toInt()
            val charging = when (intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
                BatteryManager.BATTERY_STATUS_CHARGING,
                BatteryManager.BATTERY_STATUS_FULL -> true
                else -> false
            }
            
            if (batteryPct != lastBatteryLevel || charging != isCharging) {
                lastBatteryLevel = batteryPct
                isCharging = charging
                foxView?.updateBatteryState(batteryPct, charging)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        foxView = FoxView(this)
        
        val layoutParams = WindowManager.LayoutParams(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY 
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = 32
            y = 32
        }

        windowManager.addView(foxView, layoutParams)
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(batteryReceiver)
        foxView?.let { windowManager.removeView(it) }
    }
}

class FoxView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var state = State.NORMAL
    private var batteryLevel = 100
    private var isCharging = false
    private var eyeBlinkTime = 0L
    private var animationTime = 0L
    private var touchX = 0f
    private var touchY = 0f
    private var happyTime = 0L

    enum class State { NORMAL, HAPPY, SLEEPY, LOW_BATTERY, CHARGING }

    fun updateBatteryState(level: Int, charging: Boolean) {
        batteryLevel = level
        isCharging = charging
        state = when {
            charging -> State.CHARGING
            level < 15 -> State.LOW_BATTERY
            level > 85 -> State.HAPPY
            else -> State.NORMAL
        }
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            touchX = event.x
            touchY = event.y
            happyTime = System.currentTimeMillis()
            if (state == State.NORMAL || state == State.SLEEPY) {
                state = State.HAPPY
            }
            invalidate()
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = 160f
        val h = 160f
        animationTime += 16
        val time = System.currentTimeMillis()

        if (time - happyTime > 2500 && state == State.HAPPY && !isCharging && batteryLevel >= 15) {
            state = if (batteryLevel < 30) State.SLEEPY else State.NORMAL
        }

        val blinkOpen = time - eyeBlinkTime > 3000 + (Math.random() * 2000).toLong()
        if (time - eyeBlinkTime > 3500) eyeBlinkTime = time

        val bounce = when (state) {
            State.HAPPY -> kotlin.math.sin(animationTime / 150f) * 8f
            State.CHARGING -> kotlin.math.sin(animationTime / 250f) * 4f
            State.SLEEPY -> -2f
            else -> 0f
        }

        val baseY = h - 50f + bounce

        // 🦊 جسم الثعلب
        paint.color = Color.parseColor("#FF8C00")
        canvas.drawCircle(w/2, baseY + 10f, 42f, paint)
        
        // رأس
        canvas.drawCircle(w/2, baseY - 35f, 38f, paint)

        // أذن يمنى
        canvas.drawTriangle(w/2 - 30f, baseY - 55f, w/2 - 12f, baseY - 42f, w/2 - 28f, baseY - 28f, paint)
        // أذن يسار
        canvas.drawTriangle(w/2 + 30f, baseY - 55f, w/2 + 12f, baseY - 42f, w/2 + 28f, baseY - 28f, paint)

        // وجه أبيض
        paint.color = Color.WHITE
        canvas.drawOval(w/2 - 22f, baseY - 48f, w/2 + 22f, baseY - 18f, paint)

        // عيون
        paint.color = Color.BLACK
        val eyeHeight = when {
            state == State.SLEEPY -> 2f
            !blinkOpen -> 3f
            else -> 7f
        }
        canvas.drawOval(w/2 - 14f, baseY - 40f - eyeHeight/2, w/2 - 7f, baseY - 40f + eyeHeight/2, paint)
        canvas.drawOval(w/2 + 7f, baseY - 40f - eyeHeight/2, w/2 + 14f, baseY - 40f + eyeHeight/2, paint)

        // أنف
        paint.color = Color.BLACK
        canvas.drawCircle(w/2, baseY - 28f, 4f, paint)

        // فم
        paint.color = Color.BLACK
        when (state) {
            State.HAPPY -> {
                canvas.drawArc(w/2 - 10f, baseY - 28f, w/2 + 10f, baseY - 18f, 180f, 180f, false, paint)
            }
            State.LOW_BATTERY -> {
                canvas.drawArc(w/2 - 8f, baseY - 22f, w/2 + 8f, baseY - 14f, 0f, 180f, false, paint)
            }
            State.CHARGING -> {
                canvas.drawArc(w/2 - 10f, baseY - 28f, w/2 + 10f, baseY - 18f, 180f, 180f, false, paint)
                paint.color = Color.parseColor("#FFD700")
                paint.textSize = 18f
                canvas.drawText("⚡", w/2 - 9f, baseY - 60f, paint)
            }
            State.SLEEPY -> {
                paint.textSize = 14f
                canvas.drawText("💤", w/2 + 18f, baseY - 45f, paint)
            }
            else -> {
                canvas.drawLine(w/2 - 5f, baseY - 22f, w/2 + 5f, baseY - 22f, paint)
            }
        }

        // بطارية منخفضة
        if (state == State.LOW_BATTERY) {
            paint.color = Color.parseColor("#E53935")
            paint.textSize = 12f
            canvas.drawText("$batteryLevel%", w/2 - 12f, baseY + 35f, paint)
        }

        invalidate()
    }

    private fun Canvas.drawTriangle(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, paint: Paint) {
        val path = android.graphics.Path()
        path.moveTo(x1, y1)
        path.lineTo(x2, y2)
        path.lineTo(x3, y3)
        path.close()
        drawPath(path, paint)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(180, 180)
    }
}
