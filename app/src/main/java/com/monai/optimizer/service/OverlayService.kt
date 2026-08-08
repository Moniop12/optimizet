package com.monai.optimizer.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
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
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.monai.optimizer.R
import com.monai.optimizer.optimizer.MonitorEngine
import com.monai.optimizer.optimizer.OptProfile
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * V4.1 — Floating Overlay UI (System Alert Window / TYPE_APPLICATION_OVERLAY).
 *
 * Muncul saat notifikasi 1-baris diklik. Berisi detail lengkap:
 *  - Baterai % + bar + status charge/discharge + suhu
 *  - Nama aplikasi yang sedang dibuka (foreground app — dikembalikan)
 *  - RAM bebas & CPU % (warm-up baseline + fallback agar selalu terbaca)
 *  - Tombol profil PERF / BALANCED / SAVER (dengan efek visual aktif)
 *  - Tombol Close kecil di pojok atas (close overlay SAJA — notifikasi TETAP AKTIF)
 *  - Bisa di-drag, tetapi posisi selalu di-clamp ke dalam batas layar
 *
 * Overlay otomatis hilang ketika MonAiService mati (ACTION_HIDE dikirim
 * dari onDestroy service) atau saat user menekan tombol Close.
 */
class OverlayService : Service() {

    companion object {
        const val ACTION_SHOW = "ACTION_SHOW_OVERLAY"
        const val ACTION_HIDE = "ACTION_HIDE_OVERLAY"
        const val ACTION_TOGGLE_OVERLAY = "ACTION_TOGGLE_OVERLAY"

        private const val PREFS_NAME = "monai_overlay_prefs"
        private const val KEY_POS_X = "overlay_pos_x"
        private const val KEY_POS_Y = "overlay_pos_y"
        private const val DRAG_MARGIN_DP = 8

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
        private const val COLOR_ACTIVE_BG = 0x205B8DEF.toInt()
    }

    private lateinit var wm: WindowManager
    private lateinit var prefs: SharedPreferences
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
    private lateinit var focusTxt: TextView

    // Tombol profil — untuk efek visual state aktif (poin 8)
    private lateinit var btnPerf: TextView
    private lateinit var btnBal: TextView
    private lateinit var btnSave: TextView

    private var isDragging = false
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var initialX = 0
    private var initialY = 0

    // CPU warm-up baseline (poin 3): baca /proc/stat sekali di onCreate agar
    // delta pertama tidak dihitung dari 0 (yang membuat angka CPU% = 0).
    private var cpuWarmedUp = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        initViews()
        // Poin 3: warm-up baseline CPU di onCreate (context sudah valid)
        MonitorEngine.getCpuUsagePercent(consumerId = "overlay")
        cpuWarmedUp = true
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
        focusTxt = TextView(this)
        btnPerf = TextView(this)
        btnBal = TextView(this)
        btnSave = TextView(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> showOverlay()
            ACTION_HIDE -> hideOverlay()
            ACTION_TOGGLE_OVERLAY -> {
                if (overlayView != null) hideOverlay() else showOverlay()
            }
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
        val width = (292 * density).toInt()
        val lp = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            // Poin 4: HAPUS FLAG_LAYOUT_NO_LIMITS — overlay tidak boleh keluar layar.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // Poin 4: restore posisi tersimpan (dalam batas layar)
            val screenW = resources.displayMetrics.widthPixels
            val screenH = resources.displayMetrics.heightPixels
            val savedX = prefs.getInt(KEY_POS_X, (screenW - width) / 2)
            val savedY = prefs.getInt(KEY_POS_Y, (screenH * 0.18f).toInt())
            x = clamp(savedX, DRAG_MARGIN_DP, screenW - width - DRAG_MARGIN_DP)
            y = clamp(savedY, DRAG_MARGIN_DP, screenH - width / 2 - DRAG_MARGIN_DP)
        }
        overlayParams = lp

        val view = buildOverlayView()
        overlayView = view
        wm.addView(view, lp)

        // Drag handling — Poin 4: posisi di-clamp ke dalam batas layar (margin 8dp)
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
                        lp.x = clamp(initialX + dx, DRAG_MARGIN_DP, screenW - width - DRAG_MARGIN_DP)
                        lp.y = clamp(initialY + dy, DRAG_MARGIN_DP, screenH - width / 2 - DRAG_MARGIN_DP)
                        wm.updateViewLayout(view, lp)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isDragging) {
                        // Simpan posisi terakhir agar overlay kembali di posisi itu
                        prefs.edit().putInt(KEY_POS_X, lp.x).putInt(KEY_POS_Y, lp.y).apply()
                    }
                    isDragging = false
                    true
                }
                else -> false
            }
        }

        startRefreshing()
    }

    private fun clamp(value: Int, min: Int, max: Int): Int = value.coerceIn(min, max)

    private val screenW: Int get() = resources.displayMetrics.widthPixels
    private val screenH: Int get() = resources.displayMetrics.heightPixels

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun buildOverlayView(): View {
        val density = resources.displayMetrics.density

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(16))
            background = roundedBg(COLOR_BG, dp(22), dp(1), COLOR_CHIP_STROKE)
        }

        // Header: judul + tombol close kecil di pojok atas (poin 5)
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = "MonProject"
            setTextColor(COLOR_TITLE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val closeBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_notification)
            setColorFilter(COLOR_SUB)
            contentDescription = "Close overlay"
            setOnClickListener { hideOverlay() }
        }
        header.addView(closeBtn, LinearLayout.LayoutParams(dp(22), dp(22)))
        root.addView(header)

        // Subtitle: nama aplikasi yang sedang dibuka (poin 2 — dikembalikan)
        focusTxt.text = "\u2022 Dashboard Active"
        focusTxt.setTextColor(COLOR_SUB)
        focusTxt.textSize = 11f
        focusTxt.maxLines = 1
        root.addView(focusTxt)

        // Divider
        root.addView(View(this).apply {
            setBackgroundColor(COLOR_DIVIDER)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
            topMargin = dp(10)
            bottomMargin = dp(10)
        })

        // Battery bar
        batteryBar.max = 100
        batteryBar.progressDrawable = ContextCompat.getDrawable(this, R.drawable.notif_progress_drawable)
        root.addView(batteryBar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(6)
        ))

        // Row: battery% besar + temp
        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        batteryTxt.textSize = 24f
        batteryTxt.setTypeface(null, Typeface.BOLD)
        batteryTxt.setTextColor(COLOR_ACCENT)
        row1.addView(batteryTxt, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        tempTxt.textSize = 14f
        tempTxt.setTypeface(null, Typeface.BOLD)
        tempTxt.setTextColor(COLOR_TITLE)
        row1.addView(tempTxt)
        root.addView(row1, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(6) })

        // Row: power stream (kecil)
        powerTxt.textSize = 11f
        powerTxt.setTextColor(COLOR_SUB)
        root.addView(powerTxt, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(2) })

        // Grid 2 kolom: RAM + CPU (kompak — poin 7)
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

        // Tombol profil PERF / BAL / SAVER (poin 8: efek visual aktif + animasi scale)
        val profileRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        profileRow.addView(buildProfileButton(btnPerf, "PERF", OptProfile.PERFORMANCE, COLOR_ORANGE), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dp(5)
        })
        profileRow.addView(buildProfileButton(btnBal, "BAL", OptProfile.BALANCED, COLOR_ACCENT), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(5)
            marginEnd = dp(5)
        })
        profileRow.addView(buildProfileButton(btnSave, "SAVER", OptProfile.BATTERY, COLOR_GREEN), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(5)
        })
        root.addView(profileRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })

        // Tombol Close total (poin 5): sembunyikan overlay SAJA — notifikasi TETAP AKTIF.
        // User bisa memunculkan lagi dengan mengklik notifikasi.
        val closeBtn2 = TextView(this).apply {
            text = "\u2715  Close Panel"
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = roundedBg(COLOR_ACCENT, dp(12), 0, Color.TRANSPARENT)
            setOnClickListener { hideOverlay() }
        }
        root.addView(closeBtn2, LinearLayout.LayoutParams(
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

    /**
     * Poin 8: tombol profil dengan efek visual saat diklik.
     *  - State aktif: background tint + border tebal (tampak "terpilih")
     *  - Animasi scale ringan (0.92 → 1.0) saat diklik
     */
    private fun buildProfileButton(btn: TextView, label: String, profile: OptProfile, accent: Int): TextView {
        return btn.apply {
            text = label
            setTextColor(accent)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(10), dp(6), dp(10))
            background = roundedBg(
                if (MonAiService.currentActiveProfile == profile) COLOR_ACTIVE_BG else accent and 0x00FFFFFF or 0x1F000000.toInt(),
                dp(10), dp(1), accent
            )
            setOnClickListener {
                // Efek scale ringan (poin 8)
                animate().scaleX(0.92f).scaleY(0.92f).setDuration(90).withEndAction {
                    animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                }.start()

                runCatching {
                    val intent = Intent(this@OverlayService, MonAiService::class.java).apply {
                        action = MonAiService.ACTION_SET_PROFILE
                        putExtra(MonAiService.EXTRA_PROFILE, profile.name)
                    }
                    startService(intent)
                }
                // Update state aktif langsung (feedback visual instan)
                updateProfileButtonStates()
            }
        }
    }

    /** Perbarui tampilan aktif ketiga tombol profil (poin 8). */
    private fun updateProfileButtonStates() {
        val current = MonAiService.currentActiveProfile
        listOf(
            btnPerf to OptProfile.PERFORMANCE,
            btnBal to OptProfile.BALANCED,
            btnSave to OptProfile.BATTERY
        ).forEach { (btn, profile) ->
            val accent = when (profile) {
                OptProfile.PERFORMANCE -> COLOR_ORANGE
                OptProfile.BALANCED -> COLOR_ACCENT
                OptProfile.BATTERY -> COLOR_GREEN
            }
            val active = current == profile
            btn.background = roundedBg(
                if (active) COLOR_ACTIVE_BG else accent and 0x00FFFFFF or 0x1F000000.toInt(),
                dp(10), dp(1), accent
            )
            btn.setTextColor(if (active) Color.WHITE else accent)
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
        // Poin 3: CPU usage — warm-up sudah dilakukan di onCreate.
        // Fallback: jika masih 0 → tampilkan frekuensi/temp CPU agar tidak kosong.
        val cpu = if (cpuWarmedUp) {
            MonitorEngine.getCpuUsagePercent(consumerId = "overlay")
        } else {
            MonitorEngine.getCpuUsagePercent(consumerId = "overlay")
        }
        val cpuDisplay = if (cpu > 0) "$cpu%" else {
            val freq = MonitorEngine.getCpuFreqDisplay()
            if (freq != "--") freq else "--"
        }

        batteryTxt.text = "$level%"
        batteryBar.progress = level
        tempTxt.text = "%.1f\u00b0C".format(tempC)
        powerTxt.text = buildPowerText(isCharging, currentMa, tempC)
        powerTxt.setTextColor(if (isCharging) COLOR_GREEN else COLOR_SUB)
        ramTxt.text = "${ram.availMb} MB"
        cpuTxt.text = cpuDisplay

        // Nama aplikasi yang sedang dibuka (poin 2)
        updateFocusApp()
    }

    /** Baca nama aplikasi yang sedang dibuka via UsageStats & tampilkan di overlay. */
    private fun updateFocusApp() {
        val ctx = applicationContext
        val label = try {
            val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
            val end = System.currentTimeMillis()
            val begin = end - 15_000L
            val events = usm.queryEvents(begin, end)
            val event = android.app.usage.UsageEvents.Event()
            var lastPkg: String? = null
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED)
                ) {
                    lastPkg = event.packageName
                }
            }
            val pkg = lastPkg ?: return
            try {
                val pm = ctx.packageManager
                val appInfo = pm.getApplicationInfo(pkg, 0)
                pm.getApplicationLabel(appInfo).toString()
            } catch (_: Exception) {
                pkg.substringAfterLast(".").replaceFirstChar { it.uppercase() }
            }
        } catch (_: Exception) { null }

        label?.let {
            focusTxt.text = "\u2022 $it"
        }
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
