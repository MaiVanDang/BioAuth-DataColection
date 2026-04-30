package com.datn.datacollectv2

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.datn.datacollectv2.view.SensorBarChartView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.io.File

class SensorCollectionActivity : AppCompatActivity(), SensorEventListener {

    // ── Sensors ────────────────────────────────────────────────────────
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope    : Sensor? = null
    private var magnetometer : Sensor? = null

    // ── Buffers ────────────────────────────────────────────────────────
    private val accBuffer       = mutableListOf<FloatArray>()
    private val gyroBuffer      = mutableListOf<FloatArray>()
    private val magBuffer       = mutableListOf<FloatArray>()
    private val accTimestampBuffer = mutableListOf<Long>()
    private var recordingUtcOffsetMs: Long = 0L

    // ── State maps ─────────────────────────────────────────────────────
    private val targetSecs    = DEFAULT_TARGET_SECS.toMutableMap()
    private val accumulatedMs = ACTIVITY_LABELS.associateWith { 0L }.toMutableMap()
    private val attemptCounts = ACTIVITY_LABELS.associateWith { 0 }.toMutableMap()

    private val MIN_SAMPLES     = 150
    private var currentActivity = ""
    private var currentCardView : MaterialCardView? = null
    private var isRecording     = false

    // ── Foreground Service ─────────────────────────────────────────────
    private var sensorService: SensorForegroundService? = null
    private var serviceBound  = false

    // ── Permission helper ──────────────────────────────────────────────
    private val permHelper = PermissionHelper(this)
    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results -> permHelper.handleResult(results) }

    // ── Views ──────────────────────────────────────────────────────────
    private lateinit var btnBack           : ImageButton
    private lateinit var btnLogout         : ImageButton
    private lateinit var tvUserInfo        : TextView
    private lateinit var tvUserAvatar      : TextView
    private lateinit var tvProgressLabel   : TextView
    private lateinit var progressBarTotal  : LinearProgressIndicator
    private lateinit var cardRecording     : MaterialCardView
    private lateinit var viewRecDot        : View
    private lateinit var tvRecordingStatus : TextView
    private lateinit var tvTimer           : TextView
    private lateinit var cardSensorValues  : MaterialCardView
    private lateinit var tvAccValues       : TextView
    private lateinit var tvGyroValues      : TextView
    private lateinit var sensorBarChart    : SensorBarChartView
    private lateinit var tvStatusSmall     : TextView
    private lateinit var btnStopRecording  : MaterialButton
    private lateinit var btnGoToForm       : MaterialButton

    private var pendingStartAction: (() -> Unit)? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            sensorService = (binder as SensorForegroundService.LocalBinder).getService()
            serviceBound  = true
            attachServiceCallbacks()

            pendingStartAction?.invoke()
            pendingStartAction = null

            sensorService?.let { svc ->
                if (svc.isRecording && !isRecording) {
                    val s = svc.session ?: return
                    currentActivity = s.activityLabel
                    isRecording     = true
                    registerSensors()
                    syncUIToRecordingState(s.activityLabel)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            sensorService = null
            serviceBound  = false
        }
    }

    // ── Card ID map ────────────────────────────────────────────────────
    private val activityCardId: Map<String, Int> by lazy {
        mapOf(
            "walking"  to R.id.cardWalking,
            "standing" to R.id.cardStanding,
            "sitting"  to R.id.cardSitting,
        )
    }

    private val activityStrings: Map<String, Pair<Int, Int>> by lazy {
        mapOf(
            "walking"  to (R.string.activity_walking_icon  to R.string.activity_walking),
            "standing" to (R.string.activity_standing_icon to R.string.activity_standing),
            "sitting"  to (R.string.activity_sitting_icon  to R.string.activity_sitting)
        )
    }

    private fun cardOf(label: String): MaterialCardView? =
        activityCardId[label]?.let { findViewById(it) }

    companion object {
        private const val TARGET_STEP_SECS = 30
        private const val TARGET_MIN_SECS  = 30
        private const val TARGET_MAX_SECS  = 1800

        val ACTIVITY_LABELS = listOf("walking", "standing", "sitting")
        val DEFAULT_TARGET_SECS = mapOf(
            "walking"  to 360,
            "standing" to 360,
            "sitting"  to 360,
        )
    }

    // ── Lifecycle ──────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sensor_collection)

        // Bind views
        btnBack           = findViewById(R.id.btnBack)
        btnLogout         = findViewById(R.id.btnLogout)
        tvUserInfo        = findViewById(R.id.tvUserInfo)
        tvUserAvatar      = findViewById(R.id.tvUserAvatar)
        progressBarTotal  = findViewById(R.id.progressBar)
        tvProgressLabel   = findViewById(R.id.tvProgressLabel)
        cardRecording     = findViewById(R.id.cardRecording)
        viewRecDot        = findViewById(R.id.viewRecDot)
        tvRecordingStatus = findViewById(R.id.tvRecordingStatus)
        tvTimer           = findViewById(R.id.tvTimer)
        cardSensorValues  = findViewById(R.id.cardSensorValues)
        tvAccValues       = findViewById(R.id.tvAccValues)
        tvGyroValues      = findViewById(R.id.tvGyroValues)
        sensorBarChart    = findViewById(R.id.sensorBarChart)
        tvStatusSmall     = findViewById(R.id.tvStatusSmall)
        btnStopRecording  = findViewById(R.id.btnStopRecording)
        btnGoToForm       = findViewById(R.id.btnGoToForm)

        UserSession.getProfile(this)?.let { profile ->
            tvUserInfo.text = profile.name

            val initials = profile.name
                .trim().split("\\s+".toRegex())
                .filter { it.isNotEmpty() }
                .takeLast(2)
                .joinToString("") { it.first().uppercaseChar().toString() }
                .take(2)
                .ifEmpty { "U" }
            tvUserAvatar.text = initials
        }

        btnBack.setOnClickListener { finish() }
        btnStopRecording.setOnClickListener { if (isRecording) stopAndSave() }
        btnGoToForm.setOnClickListener {
            startActivity(Intent(this, FormActivity::class.java).apply {
                putExtra("USER_ID",    intent.getStringExtra("USER_ID"))
                putExtra("SESSION_ID", intent.getStringExtra("SESSION_ID"))
                putExtra("ROUND", 1)
            })
        }

        btnLogout.setOnClickListener { showLogoutDialog() }

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope     = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        magnetometer  = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        setupActivityCards()
        setupTargetControls()
        restoreSensorState()
        refreshAllUI()

        permHelper.setLauncher(permLauncher)

        val si = Intent(this, SensorForegroundService::class.java)
        ContextCompat.startForegroundService(this, si)
        bindService(si, serviceConnection, Context.BIND_AUTO_CREATE)

        permHelper.checkAndRequest { }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        restoreSensorState()
        refreshAllUI()
        if (serviceBound) attachServiceCallbacks()
    }

    override fun onStart() {
        super.onStart()
        if (serviceBound) attachServiceCallbacks()

        sensorService?.let { svc ->
            if (svc.isRecording && !isRecording) {
                val s = svc.session ?: return@let
                currentActivity = s.activityLabel
                isRecording     = true
                registerSensors()
                syncUIToRecordingState(s.activityLabel)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        saveSensorState()
        sensorService?.onTick = null
        if (!isRecording) sensorManager.unregisterListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        if (serviceBound) {
            sensorService?.onTick = null
            unbindService(serviceConnection)
            serviceBound = false
        }
        stopService(Intent(this, SensorForegroundService::class.java))
    }

    // ── Logout ─────────────────────────────────────────────────────────

    private fun showLogoutDialog() {
        if (isRecording) {
            tvStatusSmall.text = "Dừng thu trước khi đăng xuất"
            tvStatusSmall.setTextColor(getColor(R.color.error))
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Đăng xuất?")
            .setMessage(
                "Dữ liệu cảm biến đã thu sẽ bị xóa.\n\n" +
                        "Bạn có thể đăng nhập lại bằng tài khoản mới."
            )
            .setPositiveButton("Đăng xuất") { _, _ ->
                sensorService?.dismissNotification()
                stopService(Intent(this, SensorForegroundService::class.java))
                UserSession.logout(this)
                Intent(this, RegistrationActivity::class.java).also {
                    it.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(it)
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    // ── Service callbacks ──────────────────────────────────────────────

    private fun attachServiceCallbacks() {
        sensorService?.onTick = { elapsedMs ->
            runOnUiThread {
                if (elapsedMs >= 0) onServiceTick(elapsedMs)
            }
        }
    }

    private fun onServiceTick(elapsedMs: Long) {
        val secs  = (elapsedMs / 1000).toInt()
        val mins  = secs / 60
        val rmSec = secs % 60
        val tenth = (elapsedMs % 1000) / 100
        tvTimer.text = "%02d:%02d.%d".format(mins, rmSec, tenth)
        updateCardProgress(currentActivity, elapsedMs, isRealtime = true)
    }

    // ── Persist state ──────────────────────────────────────────────────

    private fun getPrefName(): String {
        val userId = intent.getStringExtra("USER_ID") ?: "default_user"
        return "sensor_state_$userId"
    }

    private fun saveSensorState() {
        getSharedPreferences(getPrefName(), MODE_PRIVATE).edit {
            ACTIVITY_LABELS.forEach { lbl ->
                putInt("target_$lbl",  targetSecs[lbl]    ?: 120)
                putLong("accMs_$lbl",  accumulatedMs[lbl] ?: 0L)
                putInt("attempt_$lbl", attemptCounts[lbl] ?: 0)
            }
        }
    }

    private fun restoreSensorState() {
        val p = getSharedPreferences(getPrefName(), MODE_PRIVATE)
        if (!p.contains("target_walking")) { resetAllToDefault(); return }
        ACTIVITY_LABELS.forEach { lbl ->
            targetSecs[lbl]    = p.getInt("target_$lbl",  targetSecs[lbl] ?: 120)
            accumulatedMs[lbl] = p.getLong("accMs_$lbl",  0L)
            attemptCounts[lbl] = p.getInt("attempt_$lbl", 0)
        }
    }

    private fun resetAllToDefault() {
        ACTIVITY_LABELS.forEach { lbl ->
            accumulatedMs[lbl] = 0L
            attemptCounts[lbl] = 0
            targetSecs[lbl]    = DEFAULT_TARGET_SECS[lbl] ?: 120
        }
    }

    // ── Setup cards & controls ─────────────────────────────────────────

    private fun setupActivityCards() {
        ACTIVITY_LABELS.forEach { label ->
            val card = cardOf(label) ?: return@forEach
            val (iconRes, nameRes) = activityStrings[label] ?: return@forEach
            card.findViewById<TextView>(R.id.tvActivityIcon).text = getString(iconRes)
            card.findViewById<TextView>(R.id.tvActivityName).text = getString(nameRes)

            card.setOnClickListener {
                if (isRecording) {
                    tvStatusSmall.text = "Nhấn Dừng trước khi chuyển hoạt động"
                    tvStatusSmall.setTextColor(getColor(R.color.error))
                    return@setOnClickListener
                }
                val accSec    = ((accumulatedMs[label] ?: 0L) / 1000).toInt()
                val targetSec = targetSecs[label] ?: 120
                if (accSec >= targetSec) showAlreadyDoneDialog(label, card)
                else startRecording(label, card)
            }

            card.findViewById<MaterialButton>(R.id.btnReset).setOnClickListener {
                if (isRecording && currentActivity == label) {
                    tvStatusSmall.text = "Dừng thu trước khi reset"
                    tvStatusSmall.setTextColor(getColor(R.color.error))
                    return@setOnClickListener
                }
                showResetDialog(label)
            }
        }
    }

    private fun showResetDialog(label: String) {
        val attempts = attemptCounts[label] ?: 0
        val accSec   = ((accumulatedMs[label] ?: 0L) / 1000).toInt()
        val mm = accSec / 60; val ss = accSec % 60

        if (attempts == 0) {
            tvStatusSmall.text = "Chưa có dữ liệu để reset"
            tvStatusSmall.setTextColor(getColor(R.color.secondary_text))
            return
        }

        AlertDialog.Builder(this)
            .setTitle("⚠️  Thu lại ${labelToVietnamese(label)}?")
            .setMessage(
                "Sẽ xóa $attempts lần thu (%02d:%02d dữ liệu).\nCác file CSV sẽ bị xóa vĩnh viễn.".format(mm, ss)
            )
            .setPositiveButton("Xóa & thu lại") { _, _ ->
                deleteCSVsForActivity(label)
                accumulatedMs[label] = 0L
                attemptCounts[label] = 0
                saveSensorState()
                refreshCardUI(label)
                refreshTotalProgress()
                tvStatusSmall.text = "${labelToVietnamese(label)} đã được tạo lại ✓"
                tvStatusSmall.setTextColor(getColor(R.color.secondary_text))
            }
            .setNegativeButton("Hủy") { d, _ -> d.dismiss() }
            .show()
    }

    private fun deleteCSVsForActivity(label: String) {
        val userId    = intent.getStringExtra("USER_ID") ?: "unknown"
        val sessionId = getCurrentSessionId()
        val dir = File(getExternalFilesDir(null), "$userId/$sessionId")
        if (!dir.exists()) return
        dir.listFiles { f ->
            f.name.startsWith("${label}_att") && f.name.endsWith(".csv")
        }?.forEach { it.delete() }
    }

    private fun showAlreadyDoneDialog(label: String, card: MaterialCardView) {
        AlertDialog.Builder(this)
            .setTitle("${labelToVietnamese(label)} đã đủ mục tiêu ✓")
            .setMessage("Bạn muốn thu thêm để tăng dữ liệu không?")
            .setPositiveButton("Thu thêm") { _, _ -> startRecording(label, card) }
            .setNegativeButton("Thôi") { d, _ -> d.dismiss() }
            .show()
    }

    private fun adjustTarget(label: String, delta: Int) {
        val cur = targetSecs[label] ?: 120
        val new = (cur + delta).coerceIn(TARGET_MIN_SECS, TARGET_MAX_SECS)
        if (new != cur) {
            targetSecs[label] = new
            refreshCardUI(label)
            refreshTotalProgress()
        }
    }

    private fun setupTargetControls() {
        ACTIVITY_LABELS.forEach { label ->
            val card = cardOf(label) ?: return@forEach
            card.findViewById<MaterialButton>(R.id.btnMinus).setOnClickListener { adjustTarget(label, -TARGET_STEP_SECS) }
            card.findViewById<MaterialButton>(R.id.btnPlus ).setOnClickListener { adjustTarget(label, +TARGET_STEP_SECS) }
        }
    }

    // ── Start recording ────────────────────────────────────────────────

    private fun startRecording(activityLabel: String, card: MaterialCardView) {
        currentCardView?.let { c ->
            val prevLabel = ACTIVITY_LABELS.firstOrNull { cardOf(it) == c } ?: ""
            if (prevLabel.isNotEmpty()) refreshCardUI(prevLabel)
        }

        currentActivity = activityLabel
        currentCardView = card
        isRecording     = true
        recordingUtcOffsetMs = System.currentTimeMillis() -
                android.os.SystemClock.elapsedRealtimeNanos() / 1_000_000

        val session = RecordingSession(
            activityLabel = activityLabel,
            accumulatedMs = accumulatedMs[activityLabel] ?: 0L
        )
        if (serviceBound && sensorService != null) {
            sensorService!!.startRecording(session)
        } else {
            pendingStartAction = { sensorService?.startRecording(session) }
        }

        syncUIToRecordingState(activityLabel)
        registerSensors()
    }

    private fun syncUIToRecordingState(activityLabel: String) {
        val accMs     = accumulatedMs[activityLabel] ?: 0L
        val accSec    = (accMs / 1000).toInt()
        val targetSec = targetSecs[activityLabel] ?: 120
        val remaining = (targetSec - accSec).coerceAtLeast(0)

        tvRecordingStatus.text = "Đang thu: ${labelToVietnamese(activityLabel)}"
        onServiceTick(accMs)

        tvStatusSmall.text = if (remaining > 0)
            "Cần thêm ~%02d:%02d — cầm tự nhiên, đừng giữ cố định".format(remaining / 60, remaining % 60)
        else
            "Đã đủ mục tiêu — thu thêm để tăng dữ liệu"
        tvStatusSmall.setTextColor(getColor(R.color.teal_600))

        btnStopRecording.visibility = View.VISIBLE
        btnGoToForm.visibility      = View.GONE

        viewRecDot.startAnimation(AlphaAnimation(1f, 0f).apply {
            duration = 600; repeatMode = Animation.REVERSE; repeatCount = Animation.INFINITE
        })
    }

    private fun registerSensors() {
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        sensorManager.registerListener(this, gyroscope,     SensorManager.SENSOR_DELAY_GAME)
        sensorManager.registerListener(this, magnetometer,  SensorManager.SENSOR_DELAY_GAME)
    }

    // ── Sensor events ──────────────────────────────────────────────────

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSensorChanged(event: SensorEvent) {
        if (!isRecording) return
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                accBuffer.add(event.values.clone())
                accTimestampBuffer.add(event.timestamp)
                if (accBuffer.size % 5 == 0) {
                    val v = event.values
                    runOnUiThread {
                        tvAccValues.text = "%.2f / %.2f / %.2f".format(v[0], v[1], v[2])
                        sensorBarChart.push(
                            Math.sqrt((v[0]*v[0] + v[1]*v[1] + v[2]*v[2]).toDouble()).toFloat()
                        )
                    }
                }
            }
            Sensor.TYPE_GYROSCOPE -> {
                gyroBuffer.add(event.values.clone())
                if (gyroBuffer.size % 5 == 0) {
                    val v = event.values
                    runOnUiThread { tvGyroValues.text = "%.2f / %.2f / %.2f".format(v[0], v[1], v[2]) }
                }
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                magBuffer.add(event.values.clone())
            }
        }
    }

    // ── Stop & Save ────────────────────────────────────────────────────

    private fun stopAndSave() {
        isRecording = false
        sensorManager.unregisterListener(this)

        val updatedSession = sensorService?.stopRecording()
        val finalTotalMs   = updatedSession?.accumulatedMs ?: 0L

        btnStopRecording.visibility = View.GONE

        val minSize = minOf(
            accBuffer.size,
            gyroBuffer.size,
            if (magBuffer.isEmpty()) accBuffer.size else magBuffer.size
        )

        if (minSize < MIN_SAMPLES) {
            accBuffer.clear()
            gyroBuffer.clear()
            magBuffer.clear()
            accTimestampBuffer.clear()
            runOnUiThread {
                viewRecDot.clearAnimation()
                tvRecordingStatus.text = "Chưa thu"
                tvTimer.text = "00:00.0"
                tvStatusSmall.text = "Quá ngắn ($minSize samples). Thu ít nhất 3 giây."
                tvStatusSmall.setTextColor(getColor(R.color.error))
                refreshCardUI(currentActivity)
            }
            return
        }
        accumulatedMs[currentActivity] = finalTotalMs
        val newAttempt = (attemptCounts[currentActivity] ?: 0) + 1
        attemptCounts[currentActivity] = newAttempt

        saveToCSV(minSize, newAttempt)

        accBuffer.clear()
        gyroBuffer.clear()
        magBuffer.clear()
        accTimestampBuffer.clear()

        val totalSec  = (finalTotalMs / 1000).toInt()
        val tgtSec    = targetSecs[currentActivity] ?: 120
        val pct       = (totalSec * 100 / tgtSec).coerceAtMost(100)
        val estWindows = (minSize - 150) / 75 + 1

        runOnUiThread {
            viewRecDot.clearAnimation()
            tvRecordingStatus.text = "Chưa thu"
            tvTimer.text = "00:00.0"
            refreshCardUI(currentActivity)
            refreshTotalProgress()

            tvStatusSmall.text = if (totalSec >= tgtSec)
                "${labelToVietnamese(currentActivity)} đủ mục tiêu ✓  ($pct% · ~$estWindows windows)"
            else
                "+$estWindows windows · $pct% mục tiêu — nhấn lại để tiếp tục"
            tvStatusSmall.setTextColor(getColor(
                if (totalSec >= tgtSec) R.color.teal_600 else R.color.secondary_text
            ))

            checkAllDoneAndShowButton()
        }
    }

    // ── UI helpers ─────────────────────────────────────────────────────

    private fun updateCardProgress(label: String, extraMs: Long = 0L, isRealtime: Boolean = false) {
        val card   = cardOf(label) ?: return
        val pb     = card.findViewById<LinearProgressIndicator>(R.id.pbActivity)
        val tvTime = card.findViewById<TextView>(R.id.tvTimeDone)
        val tvPct  = card.findViewById<TextView>(R.id.tvPct)

        val totalMs  = if (isRealtime) extraMs else (accumulatedMs[label] ?: 0L) + extraMs
        val totalSec = (totalMs / 1000).toInt()
        val tgtSec   = targetSecs[label] ?: 120
        val pct      = (totalSec * 100 / tgtSec).coerceAtMost(100)
        val dMins = totalSec / 60; val dSecs = totalSec % 60
        val tMins = tgtSec  / 60; val tSecs = tgtSec  % 60

        pb.progress = pct
        tvTime.text = "%02d:%02d / %02d:%02d".format(dMins, dSecs, tMins, tSecs)
        tvPct.text  = "$pct%"
    }

    private fun refreshCardUI(label: String) {
        updateCardProgress(label)
        val card     = cardOf(label) ?: return
        val totalSec = ((accumulatedMs[label] ?: 0L) / 1000).toInt()
        val tgtSec   = targetSecs[label] ?: 120

        when {
            totalSec >= tgtSec -> {
                card.strokeColor = getColor(R.color.teal_600)
                card.setCardBackgroundColor(getColor(R.color.teal_50))
            }
            totalSec > 0 -> {
                card.strokeColor = getColor(R.color.teal_400)
                card.setCardBackgroundColor(getColor(R.color.surface))
            }
            else -> {
                card.strokeColor = getColor(R.color.divider)
                card.setCardBackgroundColor(getColor(R.color.surface))
            }
        }
        card.alpha = 1f
    }

    private fun refreshTotalProgress() {
        val totalTargetSec = targetSecs.values.sum()
        val totalDoneSec   = accumulatedMs.values.sumOf { (it / 1000).toInt() }
        val pct = if (totalTargetSec > 0)
            (totalDoneSec * 100 / totalTargetSec).coerceAtMost(100) else 0
        val dMins = totalDoneSec / 60; val dSecs = totalDoneSec % 60
        val tMins = totalTargetSec / 60; val tSecs = totalTargetSec % 60

        progressBarTotal.max      = 100
        progressBarTotal.progress = pct
        tvProgressLabel.text      =
            "Tổng: %02d:%02d / %02d:%02d  ($pct%%)".format(dMins, dSecs, tMins, tSecs)
    }

    private fun refreshAllUI() {
        ACTIVITY_LABELS.forEach { refreshCardUI(it) }
        refreshTotalProgress()
        checkAllDoneAndShowButton()
    }

    private fun checkAllDoneAndShowButton() {
        val allDone = ACTIVITY_LABELS.all { label ->
            ((accumulatedMs[label] ?: 0L) / 1000).toInt() >= (targetSecs[label] ?: 1)
        }
        if (allDone) {
            btnGoToForm.visibility = View.VISIBLE
            sensorService?.dismissNotification()
            tvStatusSmall.text = "Tất cả hoàn thành! 🎉 Tiếp tục sang Form."
            tvStatusSmall.setTextColor(getColor(R.color.teal_600))
        }
    }

    // ── Save CSV ───────────────────────────────────────────────────────

    private fun saveToCSV(size: Int, attemptNum: Int) {
        val userId    = intent.getStringExtra("USER_ID") ?: "unknown"
        val sessionId = getCurrentSessionId()
        val filename  = "${currentActivity}_att${attemptNum}.csv"
        val dir = File(getExternalFilesDir(null), "$userId/$sessionId")
        dir.mkdirs()

        getSharedPreferences("session_prefs", MODE_PRIVATE).edit()
            .putString("current_session_id", sessionId)
            .apply()

        val utcOffsetMs = recordingUtcOffsetMs

        File(dir, filename).bufferedWriter().use { w ->
            w.write("timestamp_ms,acc_x,acc_y,acc_z,gyro_x,gyro_y,gyro_z,mag_x,mag_y,mag_z,activity,session_id\n")
            for (i in 0 until size) {
                val a = accBuffer[i]
                val g = if (i < gyroBuffer.size)  gyroBuffer[i]  else FloatArray(3)
                val m = if (i < magBuffer.size)    magBuffer[i]    else FloatArray(3)
                val tsMs = if (i < accTimestampBuffer.size)
                    utcOffsetMs + accTimestampBuffer[i] / 1_000_000
                else 0L
                w.write("$tsMs,${a[0]},${a[1]},${a[2]},${g[0]},${g[1]},${g[2]},${m[0]},${m[1]},${m[2]},$currentActivity,$sessionId\n")
            }
        }
    }

    private fun getCurrentSessionId(): String {
        val fromPrefs = getSharedPreferences("session_prefs", MODE_PRIVATE)
            .getString("current_session_id", null)

        val validPattern = Regex("^session_\\d+$")

        if (fromPrefs != null && validPattern.matches(fromPrefs)) return fromPrefs

        val fromIntent = intent.getStringExtra("SESSION_ID")
        if (fromIntent != null && validPattern.matches(fromIntent)) return fromIntent

        return "session_1"
    }

    private fun labelToVietnamese(label: String) = when (label) {
        "walking"  -> "Đi bộ"
        "standing" -> "Đứng"
        "sitting"  -> "Ngồi"
        else       -> label
    }
}