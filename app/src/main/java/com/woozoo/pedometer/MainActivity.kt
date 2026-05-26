package com.woozoo.pedometer

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.woozoo.pedometer.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences
    private var sensorManager: SensorManager? = null
    private var stepSensor: Sensor? = null

    private var lastRawSteps: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("woozoo_pedometer", Context.MODE_PRIVATE)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        if (stepSensor == null) {
            binding.tvSteps.text = "센서 없음"
            binding.tvHint.text = "이 기기엔 만보기 센서가 없어요"
        }

        binding.btnGoal.setOnClickListener { showGoalDialog() }

        refreshUi(currentTodaySteps())
        ensurePermissionAndStart()
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
            } else {
                binding.tvHint.text = "권한 거부됨 — 설정에서 신체활동 권한을 켜주세요"
            }
        }
    }

    private fun registerSensor() {
        val s = stepSensor ?: return
        sensorManager?.registerListener(this, s, SensorManager.SENSOR_DELAY_NORMAL)
        binding.tvHint.text = "측정 중…"
    }

    override fun onResume() {
        super.onResume()
        if (stepSensor != null &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
             ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                == PackageManager.PERMISSION_GRANTED)) {
            registerSensor()
        }
        refreshUi(currentTodaySteps())
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val e = event ?: return
        if (e.sensor.type != Sensor.TYPE_STEP_COUNTER) return
        val raw = e.values.firstOrNull()?.toLong() ?: return
        lastRawSteps = raw

        val today = todayKey()
        val savedDay = prefs.getString(KEY_BASELINE_DAY, null)
        var baseline = prefs.getLong(KEY_BASELINE_VALUE, -1L)

        // 기기 재부팅 시 raw 가 baseline 보다 작아질 수 있음 → 재설정
        if (savedDay != today || baseline < 0 || raw < baseline) {
            baseline = raw
            prefs.edit()
                .putString(KEY_BASELINE_DAY, today)
                .putLong(KEY_BASELINE_VALUE, baseline)
                .apply()
        }

        val todaySteps = (raw - baseline).coerceAtLeast(0L)
        prefs.edit().putLong(KEY_TODAY_STEPS, todaySteps).apply()
        refreshUi(todaySteps)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { /* noop */ }

    private fun currentTodaySteps(): Long {
        val savedDay = prefs.getString(KEY_BASELINE_DAY, null)
        return if (savedDay == todayKey()) prefs.getLong(KEY_TODAY_STEPS, 0L) else 0L
    }

    private fun refreshUi(steps: Long) {
        val goal = prefs.getInt(KEY_GOAL, 8000).coerceAtLeast(1)
        binding.tvSteps.text = String.format(Locale.KOREA, "%,d", steps)
        binding.tvGoal.text = "목표 ${String.format(Locale.KOREA, "%,d", goal)} 걸음"
        val pct = ((steps.toFloat() / goal) * 100).toInt().coerceIn(0, 100)
        binding.progress.progress = pct
        binding.tvPct.text = "$pct%"
        val kcal = (steps * 0.04).toInt()
        val km = String.format(Locale.KOREA, "%.2f", steps * 0.0007)
        binding.tvKcal.text = "${kcal} kcal"
        binding.tvKm.text = "${km} km"
    }

    private fun showGoalDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(prefs.getInt(KEY_GOAL, 8000).toString())
        }
        AlertDialog.Builder(this)
            .setTitle("일일 목표")
            .setView(input)
            .setPositiveButton("저장") { _, _ ->
                val v = input.text.toString().toIntOrNull() ?: return@setPositiveButton
                prefs.edit().putInt(KEY_GOAL, v.coerceAtLeast(100)).apply()
                refreshUi(currentTodaySteps())
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun todayKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(Date())

    companion object {
        private const val REQ_PERMISSION = 1001
        private const val KEY_BASELINE_DAY = "baseline_day"
        private const val KEY_BASELINE_VALUE = "baseline_value"
        private const val KEY_TODAY_STEPS = "today_steps"
        private const val KEY_GOAL = "goal"
    }
}
