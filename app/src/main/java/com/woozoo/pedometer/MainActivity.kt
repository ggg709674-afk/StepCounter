package com.woozoo.pedometer

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.woozoo.pedometer.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences
    private var sensorManager: SensorManager? = null
    private var stepSensor: Sensor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("woozoo_pedometer", Context.MODE_PRIVATE)
        if (prefs.getString(KEY_FIRST_DAY, null) == null) {
            prefs.edit().putString(KEY_FIRST_DAY, todayKey()).apply()
        }

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        binding.btnGoal.setOnClickListener { showGoalDialog() }
        binding.tvDate.text = SimpleDateFormat("yyyy. M. d. (E)", Locale.KOREA).format(Date())

        rollOverIfDayChanged()
        refreshUi()

        if (stepSensor == null) {
            binding.tvHint.text = "이 기기엔 만보기 센서가 없어요"
        } else {
            ensurePermissionAndStart()
        }
    }

    private fun ensurePermissionAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACTIVITY_RECOGNITION),
                    REQ_PERMISSION
                )
                return
            }
        }
        registerSensor()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                registerSensor()
                binding.tvHint.text = ""
            } else {
                binding.tvHint.text = "권한 거부됨 — 설정에서 신체활동 권한을 켜주세요"
            }
        }
    }

    private fun registerSensor() {
        val s = stepSensor ?: return
        sensorManager?.registerListener(this, s, SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onResume() {
        super.onResume()
        rollOverIfDayChanged()
        if (stepSensor != null &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
             ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                == PackageManager.PERMISSION_GRANTED)) {
            registerSensor()
        }
        refreshUi()
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val e = event ?: return
        if (e.sensor.type != Sensor.TYPE_STEP_COUNTER) return
        val raw = e.values.firstOrNull()?.toLong() ?: return

        val today = todayKey()
        val savedDay = prefs.getString(KEY_BASELINE_DAY, null)
        var baseline = prefs.getLong(KEY_BASELINE_VALUE, -1L)

        if (savedDay == null || savedDay != today || baseline < 0 || raw < baseline) {
            // 어제 분량 동결 후 baseline 재설정
            if (savedDay != null && savedDay != today) {
                val frozen = prefs.getLong(KEY_TODAY_STEPS, 0L)
                if (frozen > 0L) {
                    prefs.edit().putLong("$DAILY_PREFIX$savedDay", frozen).apply()
                }
            }
            baseline = raw
            prefs.edit()
                .putString(KEY_BASELINE_DAY, today)
                .putLong(KEY_BASELINE_VALUE, baseline)
                .putLong(KEY_TODAY_STEPS, 0L)
                .apply()
        }

        val todaySteps = (raw - baseline).coerceAtLeast(0L)
        prefs.edit().putLong(KEY_TODAY_STEPS, todaySteps).apply()
        refreshUi()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { /* noop */ }

    /** 앱 시작/onResume 시 자정 넘어갔는지 확인하고 어제 데이터 동결 */
    private fun rollOverIfDayChanged() {
        val today = todayKey()
        val savedDay = prefs.getString(KEY_BASELINE_DAY, null) ?: return
        if (savedDay != today) {
            val frozen = prefs.getLong(KEY_TODAY_STEPS, 0L)
            val ed = prefs.edit()
            if (frozen > 0L) {
                ed.putLong("$DAILY_PREFIX$savedDay", frozen)
            }
            ed.putString(KEY_BASELINE_DAY, today)
                .putLong(KEY_BASELINE_VALUE, -1L)
                .putLong(KEY_TODAY_STEPS, 0L)
                .apply()
        }
    }

    private fun todaySteps(): Long {
        val savedDay = prefs.getString(KEY_BASELINE_DAY, null)
        return if (savedDay == todayKey()) prefs.getLong(KEY_TODAY_STEPS, 0L) else 0L
    }

    /** 일자 → 걸음수. 오늘은 진행 중 값까지 포함. */
    private fun dailySteps(dayKey: String): Long {
        if (dayKey == todayKey()) return todaySteps()
        return prefs.getLong("$DAILY_PREFIX$dayKey", 0L)
    }

    private fun refreshUi() {
        val today = todaySteps()
        val goal = prefs.getInt(KEY_GOAL, 8000).coerceAtLeast(1)
        val nf = java.text.NumberFormat.getNumberInstance(Locale.KOREA)

        binding.tvSteps.text = nf.format(today)
        binding.tvGoal.text = "목표 ${nf.format(goal)} 걸음"
        val pct = ((today.toFloat() / goal) * 100).toInt().coerceIn(0, 100)
        binding.progress.progress = pct
        binding.tvPct.text = "$pct%"

        binding.tvKm.text = String.format(Locale.KOREA, "%.2f km", today * 0.0007)
        binding.tvKcal.text = "${(today * 0.04).toInt()} kcal"
        binding.tvMin.text = "${(today / 110).toInt()} 분"

        // 통계
        val week = sumLastNDays(7)
        val month = sumThisMonth()
        val total = sumAll()
        val recordDays = countRecordedDays()
        val avg = if (recordDays > 0) total / recordDays else 0L

        binding.tvWeek.text = nf.format(week)
        binding.tvMonth.text = nf.format(month)
        binding.tvTotal.text = nf.format(total)
        binding.tvAvg.text = nf.format(avg)
        binding.tvTotalSub.text = if (recordDays > 0) "걸음 · ${recordDays}일" else "걸음"

        renderChart()
    }

    private fun sumLastNDays(n: Int): Long {
        var sum = 0L
        val cal = Calendar.getInstance(Locale.KOREA)
        for (i in 0 until n) {
            sum += dailySteps(formatDay(cal.time))
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return sum
    }

    private fun sumThisMonth(): Long {
        val cal = Calendar.getInstance(Locale.KOREA)
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) // 0-based
        val today = cal.get(Calendar.DAY_OF_MONTH)
        var sum = 0L
        val c2 = Calendar.getInstance(Locale.KOREA)
        c2.set(year, month, 1)
        for (d in 1..today) {
            c2.set(Calendar.DAY_OF_MONTH, d)
            sum += dailySteps(formatDay(c2.time))
        }
        return sum
    }

    private fun sumAll(): Long {
        var sum = todaySteps()
        for ((k, v) in prefs.all) {
            if (k.startsWith(DAILY_PREFIX) && v is Long) sum += v
        }
        return sum
    }

    private fun countRecordedDays(): Int {
        var c = 0
        for ((k, v) in prefs.all) {
            if (k.startsWith(DAILY_PREFIX) && v is Long && v > 0) c++
        }
        if (todaySteps() > 0) c++
        return c
    }

    private fun renderChart() {
        val container = binding.chartContainer
        val labels = binding.chartLabels
        container.removeAllViews()
        labels.removeAllViews()

        val days = mutableListOf<Pair<String, Long>>() // (요일, 걸음수)
        val cal = Calendar.getInstance(Locale.KOREA)
        cal.add(Calendar.DAY_OF_YEAR, -6) // 6일 전부터
        val dayFmt = SimpleDateFormat("E", Locale.KOREA)
        val dKeys = mutableListOf<String>()
        for (i in 0..6) {
            val key = formatDay(cal.time)
            dKeys += key
            val steps = dailySteps(key)
            days += dayFmt.format(cal.time) to steps
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }

        val maxVal = (days.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(1L)
        val todayKey = todayKey()
        val nf = java.text.NumberFormat.getNumberInstance(Locale.KOREA)

        // 베스트 데이
        val best = days.withIndex().maxByOrNull { it.value.second }
        binding.tvBestDay.text = if (best != null && best.value.second > 0)
            "최고 ${nf.format(best.value.second)}" else ""

        for (i in 0..6) {
            val (lbl, steps) = days[i]
            val isToday = dKeys[i] == todayKey
            val ratio = (steps.toFloat() / maxVal).coerceIn(0.05f, 1f)

            // 막대 컨테이너
            val col = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            }

            // 숫자 라벨
            val valueLabel = TextView(this).apply {
                text = if (steps >= 1000) "${steps / 1000}k" else if (steps > 0) "$steps" else ""
                setTextColor(if (isToday) ContextCompat.getColor(this@MainActivity, R.color.primary)
                             else ContextCompat.getColor(this@MainActivity, R.color.text_hint))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                gravity = Gravity.CENTER
                if (isToday) setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            col.addView(valueLabel)

            // 막대
            val bar = View(this).apply {
                background = ContextCompat.getDrawable(this@MainActivity,
                    if (isToday) R.drawable.bg_bar_today
                    else if (steps == 0L) R.drawable.bg_bar_track
                    else R.drawable.bg_bar)
                alpha = if (isToday || steps > 0L) 1f else 0.6f
            }
            val barH = (dp(110) * ratio).toInt().coerceAtLeast(dp(6))
            val barW = dp(20)
            val lp = LinearLayout.LayoutParams(barW, barH)
            lp.topMargin = dp(2)
            bar.layoutParams = lp
            col.addView(bar)
            container.addView(col)

            // 요일 라벨
            val label = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text = lbl
                gravity = Gravity.CENTER
                setTextColor(if (isToday) ContextCompat.getColor(this@MainActivity, R.color.primary)
                             else ContextCompat.getColor(this@MainActivity, R.color.text_hint))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                if (isToday) setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            labels.addView(label)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun showGoalDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(prefs.getInt(KEY_GOAL, 8000).toString())
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        AlertDialog.Builder(this)
            .setTitle("일일 목표 (걸음)")
            .setView(input)
            .setPositiveButton("저장") { _, _ ->
                val v = input.text.toString().toIntOrNull() ?: return@setPositiveButton
                prefs.edit().putInt(KEY_GOAL, v.coerceAtLeast(100)).apply()
                refreshUi()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun todayKey(): String = formatDay(Date())
    private fun formatDay(d: Date): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(d)

    companion object {
        private const val REQ_PERMISSION = 1001
        private const val KEY_BASELINE_DAY = "baseline_day"
        private const val KEY_BASELINE_VALUE = "baseline_value"
        private const val KEY_TODAY_STEPS = "today_steps"
        private const val KEY_GOAL = "goal"
        private const val KEY_FIRST_DAY = "first_record_day"
        private const val DAILY_PREFIX = "daily:"
    }
}
