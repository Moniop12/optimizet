package com.monai.optimizer.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.monai.optimizer.R
import com.monai.optimizer.optimizer.MonitorEngine
import com.monai.optimizer.optimizer.OptProfile
import kotlin.math.abs

/**
 * V4 — Floating Overlay UI (System Alert Window / TYPE_APPLICATION_OVERLAY).
 *
 * Muncul saat notifikasi 1-baris diklik. Berisi detail lengkap:
 *  - Baterai % + status charge/discharge + suhu
 *  - RAM bebas & CPU % (dari MonitorEngine)
 *  - Tombol profil PERF / BALANCED / SAVER
 *  - Tombol Stop (matikan live service) & Close (sembunyikan overlay)
 *  - Bisa di-drag bebas ke posisi mana pun
 *
 * Overlay otomatis hilang ketika MonAiService mati (ACTION_HIDE dikirim
 * dari onDestroy service) atau saat user menekan Stop/Close.
 */
class OverlayService : Service() {

    companion object {
        const val ACTION_SHOW = "ACTION_SHOW_OVERLAY"
        const val ACTION_HIDE = "ACTION_HIDE_OVERLAY"

        // Warna palet pastel blue konsisten tema V2
        private const val COLOR_BG = 0xF2FFFFFF.toInt()      // putih 95% transparan
        private const val COLOR_TITLE = 0xFF14171F.toInt()   // dark charcoal
        private const val COLOR_SUB = 0xFF5A6072.toInt()     // slate
        private const val COLOR_ACCENT = 0xFF5B8DEF.toInt()  // pastel blue
        private const val COLOR_GREEN = 0xFF0F9F59.toInt()
        private const val COLOR_ORANGE = 0xFFD97706.toInt()
        private const val COLOR_RED = 0xFFDC2626.toInt()
        private const val COLOR_TRACK = 0x1A5B8DEF.toInt()
        private const val COLOR_CHIP_BG = 0x0A14171F.toInt()
        private const val COLOR_CHIP_STROKE = 0x14141B20.toInt()
        private const val COLOR_DIVIDER = 0x14141B20.toInt()
    }

    private lateinit var wm: WindowManager
    private var overlayView: View? = null
    private var overlayParams: WindowManager.LayoutParams? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var refreshRunnable: Runnable? = null

    // View fields — dibuat di onCreate() (context sudah valid). DILARANG inisialisasi
    // di field initializer: Service belum di-attach saat itu, `this` masih null
    // context → NPE getResources() (crash OverlayService.<init>).
    private lateinit var batteryTxt: TextView
    private lateinit var tempTxt: TextView
    private lateinit var powerTxt: TextView
    private lateinit var ramTxt: TextView
    private lateinit var cpuTxt: TextView
    private lateinit var batteryBar: ProgressBar

    private var isDragging = false
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var initialX = 0
    private var initialY = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        initViews()
    }

    /** Buat semua View di sini — `this` sudah valid sebagai Context setelah
     *  Service di-attach ke system (dipanggil dari onCreate). */
    private fun initViews() {
        batteryTxt = TextView(this)
        tempTxt = TextView(this)
        powerTxt = TextView(this)
        ramTxt = TextView(this)
        cpuTxt = TextView(this)
        batteryBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> showOverlay()
            ACTION_HIDE -> hideOverlay()
        }
        return START_NOT_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showOverlay() {
        if (overlayView != null) return
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val density = resources.displayMetrics.density
        val width = (300 * density).toInt()
        val lp = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = ((resources.displayMetrics.widthPixels - width) / 2).coerceAtLeast(8)
            y = (resources.displayMetrics.heightPixels * 0.18f).toInt()
        }
        overlayParams = lp

        val view = buildOverlayView()
        overlayView = view
        wm.addView(view, lp)

        // Drag handling
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isDragging = false
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    initialX = lp.x
                    initialY = lp.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > 8 || abs(dy) > 8) isDragging = true
                    if (isDragging) {
                        lp.x = initialX + dx
                        lp.y = initialY + dy
                        wm.updateViewLayout(view, lp)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                    true
                }
                else -> false
            }
        }

        startRefreshing()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun buildOverlayView(): View {
        val density = resources.displayMetrics.density

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = roundedBg(COLOR_BG, dp(22), dp(1), COLOR_CHIP_STROKE)
        }

        // Header: judul + tombol close
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = "MonProject"
            setTextColor(COLOR_TITLE)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val closeBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_notification)
            setColorFilter(COLOR_SUB)
            contentDescription = "Close overlay"
            setOnClickListener { hideOverlay() }
        }
        header.addView(closeBtn, LinearLayout.LayoutParams(dp(26), dp(26)))
        root.addView(header)

        // Subtitle status service
        root.addView(TextView(this).apply {
            text = "Live Service \u2022 Tap & drag to move"
            setTextColor(COLOR_SUB)
            textSize = 11f
        })

        // Divider
        root.addView(View(this).apply {
            setBackgroundColor(COLOR_DIVIDER)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
            topMargin = dp(12)
            bottomMargin = dp(12)
        })

        // Battery bar
        batteryBar.max = 100
        batteryBar.progressDrawable = ContextCompat.getDrawable(this, R.drawable.notif_progress_drawable)
        root.addView(batteryBar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(6)
        ))

        // Row: battery% + temp
        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        batteryTxt.textSize = 26f
        batteryTxt.setTypeface(null, Typeface.BOLD)
        batteryTxt.setTextColor(COLOR_ACCENT)
        row1.addView(batteryTxt, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        tempTxt.textSize = 15f
        tempTxt.setTypeface(null, Typeface.BOLD)
        tempTxt.setTextColor(COLOR_TITLE)
        row1.addView(tempTxt)
        root.addView(row1, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })

        // Row: power stream
        powerTxt.textSize = 12f
        powerTxt.setTextColor(COLOR_SUB)
        root.addView(powerTxt, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(2) })

        // Grid 2 kolom: RAM + CPU
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val chipRam = buildChip("FREE RAM", ramTxt, COLOR_ACCENT)
        val chipCpu = buildChip("CPU USAGE", cpuTxt, COLOR_ACCENT)
        grid.addView(chipRam, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dp(6)
        })
        grid.addView(chipCpu, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(6)
        })
        root.addView(grid, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(10) })

        // Divider
        root.addView(View(this).apply {
            setBackgroundColor(COLOR_DIVIDER)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
            topMargin = dp(12)
            bottomMargin = dp(12)
        })

        // Label profil
        root.addView(TextView(this).apply {
            text = "PROFILE"
            setTextColor(COLOR_SUB)
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f
        })

        // Tombol profil PERF / BAL / SAVER
        val profileRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        profileRow.addView(buildProfileButton("PERF", OptProfile.PERFORMANCE, COLOR_ORANGE, profileRow), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dp(5)
        })
        profileRow.addView(buildProfileButton("BAL", OptProfile.BALANCED, COLOR_ACCENT, profileRow), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(5)
            marginEnd = dp(5)
        })
        profileRow.addView(buildProfileButton("SAVER", OptProfile.BATTERY, COLOR_GREEN, profileRow), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(5)
        })
        root.addView(profileRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })

        // Tombol STOP
        val stopBtn = TextView(this).apply {
            text = "\u23fb Stop Live Service"
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = roundedBg(COLOR_RED, dp(12), 0, Color.TRANSPARENT)
            setOnClickListener {
                runCatching {
                    val intent = Intent(this@OverlayService, MonAiService::class.java).apply {
                        action = MonAiService.ACTION_STOP
                    }
                    startService(intent)
                }
                hideOverlay()
            }
        }
        root.addView(stopBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(12) })

        return root
    }

    private fun buildChip(label: String, valueTv: TextView, accent: Int): LinearLayout {
        val chip = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(9), dp(12), dp(9))
            background = roundedBg(COLOR_CHIP_BG, dp(12), dp(1), COLOR_CHIP_STROKE)
        }
        chip.addView(TextView(this).apply {
            text = label
            setTextColor(COLOR_SUB)
            textSize = 9f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.05f
        })
        valueTv.textSize = 14f
        valueTv.setTypeface(null, Typeface.BOLD)
        valueTv.setTextColor(accent)
        chip.addView(valueTv)
        return chip
    }

    private fun buildProfileButton(label: String, profile: OptProfile, accent: Int, row: LinearLayout): TextView {
        return TextView(this).apply {
            text = label
            setTextColor(accent)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(9), dp(6), dp(9))
            background = roundedBg(accent and 0x00FFFFFF or 0x1F000000.toInt(), dp(10), dp(1), accent)
            setOnClickListener {
                runCatching {
                    val intent = Intent(this@OverlayService, MonAiService::class.java).apply {
                        action = MonAiService.ACTION_SET_PROFILE
                        putExtra(MonAiService.EXTRA_PROFILE, profile.name)
                    }
                    startService(intent)
                }
            }
        }
    }

    private fun roundedBg(fill: Int, radius: Int, strokeWidth: Int, strokeColor: Int): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = radius.toFloat()
            setColor(fill)
            if (strokeWidth > 0) {
                setStroke(strokeWidth, strokeColor)
            }
        }
    }

    private fun startRefreshing() {
        refreshRunnable?.let { mainHandler.removeCallbacks(it) }
        val r = object : Runnable {
            override fun run() {
                refreshData()
                mainHandler.postDelayed(this, 2000L)
            }
        }
        refreshRunnable = r
        r.run()
    }

    private fun refreshData() {
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val status = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val chargeState = status?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = chargeState == BatteryManager.BATTERY_STATUS_CHARGING || chargeState == BatteryManager.BATTERY_STATUS_FULL
        val tempRaw = status?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val tempC = tempRaw / 10.0
        val currentNow = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val currentMa = if (abs(currentNow) > 10000) currentNow / 1000 else currentNow

        val ram = MonitorEngine.getRamSnapshot()
        val cpu = MonitorEngine.getCpuUsagePercent(consumerId = "overlay")

        batteryTxt.text = "$level%"
        batteryBar.progress = level
        tempTxt.text = "%.1f\u00b0C".format(tempC)
        powerTxt.text = buildPowerText(isCharging, currentMa, tempC)
        powerTxt.setTextColor(if (isCharging) COLOR_GREEN else COLOR_SUB)
        ramTxt.text = "${ram.availMb} MB"
        cpuTxt.text = "$cpu%"
    }

    private fun buildPowerText(isCharging: Boolean, currentMa: Int, tempC: Double): String {
        val stream = when {
            currentMa == 0 -> "0 mA"
            isCharging -> "+${abs(currentMa)} mA \u00b7 Charging"
            else -> "-${abs(currentMa)} mA \u00b7 Discharging"
        }
        val powerSource = if (isCharging) "\u26a1" else "\ud83d\udd0b"
        return "$powerSource $stream"
    }

    private fun hideOverlay() {
        refreshRunnable?.let { mainHandler.removeCallbacks(it) }
        refreshRunnable = null
        overlayView?.let { view ->
            runCatching { wm.removeView(view) }
        }
        overlayView = null
        overlayParams = null
        stopSelf()
    }

    override fun onDestroy() {
        hideOverlay()
        super.onDestroy()
    }
}