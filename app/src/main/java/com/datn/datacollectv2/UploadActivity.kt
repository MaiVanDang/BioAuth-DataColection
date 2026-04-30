package com.datn.datacollectv2

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class UploadActivity : AppCompatActivity() {

    private lateinit var tvUserId        : TextView
    private lateinit var tvInertialValue : TextView
    private lateinit var tvTapValue      : TextView
    private lateinit var tvKeystrokeValue: TextView
    private lateinit var tvScrollValue   : TextView
    private lateinit var tvTotalSize     : TextView
    private lateinit var cardWarning     : MaterialCardView
    private lateinit var tvWarning       : TextView
    private lateinit var progressExport  : LinearProgressIndicator
    private lateinit var btnExportZip    : MaterialButton
    private lateinit var btnDone         : MaterialButton

    private lateinit var userId   : String
    private          var sessionId: String = "session_1"
    private lateinit var dataDir  : File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_upload)

        userId    = intent.getStringExtra("USER_ID") ?: "unknown"
        sessionId = getSharedPreferences("session_prefs", MODE_PRIVATE)
            .getString("current_session_id", "session_1") ?: "session_1"
        dataDir   = File(getExternalFilesDir(null), "$userId/$sessionId")

        bindViews()
        populateStats()
        setupButtons()
    }

    private fun bindViews() {
        tvUserId         = findViewById(R.id.tvUserId)
        tvInertialValue  = findViewById(R.id.tvInertialValue)
        tvTapValue       = findViewById(R.id.tvTapValue)
        tvKeystrokeValue = findViewById(R.id.tvKeystrokeValue)
        tvScrollValue    = findViewById(R.id.tvScrollValue)
        tvTotalSize      = findViewById(R.id.tvTotalSize)
        cardWarning      = findViewById(R.id.cardWarning)
        tvWarning        = findViewById(R.id.tvWarning)
        progressExport   = findViewById(R.id.progressExport)
        btnExportZip     = findViewById(R.id.btnExportZip)
        btnDone          = findViewById(R.id.btnDone)
    }

    private fun populateStats() {
        val userDir = File(getExternalFilesDir(null), userId)
        tvUserId.text = "ID: $userId  ·  Session hiện tại: $sessionId"

        if (!userDir.exists()) {
            showWarning("Không tìm thấy thư mục dữ liệu. Vui lòng thu lại.")
            return
        }

        val allFiles = userDir.walkTopDown().filter { it.isFile }.toList()

        val inertialActivities = setOf("walking", "sitting", "standing", "jogging", "upstairs", "downstairs")
        val inertialFiles  = allFiles.filter { f ->
            f.name.endsWith(".csv") && inertialActivities.any { f.name.startsWith("${it}_att") }
        }
        val tapFiles       = allFiles.filter { it.name.startsWith("tap_r")       && it.name.endsWith(".csv") }
        val keystrokeFiles = allFiles.filter { it.name.startsWith("keystroke_r") && it.name.endsWith(".csv") }
        val scrollFiles    = allFiles.filter { it.name.startsWith("scroll_r")    && it.name.endsWith(".csv") }

        fun countRows(files: List<File>): Int = files.sumOf { f ->
            try { f.bufferedReader().lineSequence().count() - 1 }
            catch (e: Exception) { 0 }
        }.coerceAtLeast(0)

        val totalBytes = allFiles.sumOf { it.length() }
        val totalSizeStr = when {
            totalBytes >= 1_048_576 -> "%.1f MB".format(totalBytes / 1_048_576.0)
            totalBytes >= 1_024     -> "%.1f KB".format(totalBytes / 1_024.0)
            else                    -> "$totalBytes B"
        }

        tvInertialValue.text  = "${inertialFiles.size} file CSV"
        tvTapValue.text       = "${countRows(tapFiles)} mẫu"
        tvKeystrokeValue.text = "${countRows(keystrokeFiles)} mẫu"
        tvScrollValue.text    = "${countRows(scrollFiles)} mẫu"
        tvTotalSize.text      = "Dung lượng: $totalSizeStr"

        if (inertialFiles.size < 3) {
            showWarning("Còn thiếu dữ liệu inertial. Nên có ít nhất 1 file cho mỗi hoạt động (đi bộ, đứng, ngồi).")
        }
    }

    private fun showWarning(msg: String) {
        cardWarning.visibility = View.VISIBLE
        tvWarning.text = msg
    }

    private fun setupButtons() {
        btnExportZip.setOnClickListener { showExportConfirmDialog() }
        btnDone.setOnClickListener { showDoneOptions() }
    }

    private fun exportAndShare() {
        btnExportZip.isEnabled    = false
        progressExport.visibility = View.VISIBLE

        Thread {
            try {
                val zipFile = createZip()
                runOnUiThread {
                    progressExport.visibility = View.GONE
                    btnExportZip.isEnabled    = true
                    if (zipFile != null) {
                        deleteAllSessionData()
                        resetCollectionState(userId)
                        populateStats()
                        shareZip(zipFile)
                    } else {
                        showErrorDialog("Không thể tạo file ZIP. Kiểm tra dung lượng bộ nhớ.")
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressExport.visibility = View.GONE
                    btnExportZip.isEnabled    = true
                    showErrorDialog("Lỗi khi xuất dữ liệu: ${e.message}")
                }
            }
        }.start()
    }

    private fun incrementSessionNumber() {
        val currentNum = sessionId.removePrefix("session_").toIntOrNull() ?: 1
        val nextSessionId = "session_${currentNum + 1}"
        getSharedPreferences("session_prefs", MODE_PRIVATE)
            .edit().putString("current_session_id", nextSessionId).apply()
        sessionId = nextSessionId
    }

    private fun createZip(): File? {
        val userDir = File(getExternalFilesDir(null), userId)
        if (!userDir.exists()) return null

        val zipDir  = File(cacheDir, "export")
        zipDir.mkdirs()
        val zipFile = File(zipDir, "${userId}_${System.currentTimeMillis()}.zip")

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            userDir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    val entryName = "$userId/${file.relativeTo(userDir)}"
                    zos.putNextEntry(ZipEntry(entryName))
                    FileInputStream(file).use { fis -> fis.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
        return zipFile
    }

    private fun deleteAllSessionData() {
        try {
            val userDir = File(getExternalFilesDir(null), userId)
            userDir.listFiles()?.forEach { f ->
                if (f.isDirectory) f.deleteRecursively()
            }
        } catch (_: Exception) { }
    }

    private fun shareZip(zipFile: File) {
        val uri: Uri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            zipFile
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type     = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "DataCollect – $userId")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Chia sẻ dữ liệu"))
    }

    private fun showDoneOptions() {
        AlertDialog.Builder(this)
            .setTitle("Hoàn tất!")
            .setMessage("Bạn muốn làm gì tiếp theo?")
            .setPositiveButton("Thu thêm dữ liệu") { _, _ ->
                val profile = UserSession.getProfile(this)
                if (profile != null) {
                    incrementSessionNumber()
                    resetCollectionState(profile.userId)
                    Intent(this, SensorCollectionActivity::class.java).also {
                        it.putExtra("USER_ID",    profile.userId)
                        it.putExtra("SESSION_ID", sessionId)
                        it.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        startActivity(it)
                        finish()
                    }
                } else {
                    startActivity(Intent(this, RegistrationActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                }
            }
            .setNegativeButton("Đăng xuất") { _, _ ->
                UserSession.logout(this)
                startActivity(Intent(this, RegistrationActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
            }
            .setNeutralButton("Ở lại", null)
            .show()
    }

    private fun showErrorDialog(msg: String) {
        AlertDialog.Builder(this)
            .setTitle("Lỗi")
            .setMessage(msg)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun resetCollectionState(userId: String) {
        getSharedPreferences("sensor_state_$userId", MODE_PRIVATE)
            .edit().clear().apply()
        getSharedPreferences("form_draft", MODE_PRIVATE)
            .edit().clear().apply()
        getSharedPreferences("session_prefs", MODE_PRIVATE)
            .edit().putString("current_session_id", sessionId).apply()
    }

    private fun showExportConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("Xác nhận xuất dữ liệu")
            .setMessage(
                "Hệ thống sẽ nén toàn bộ dữ liệu tất cả phiên thành 1 file ZIP.\n\n" +
                        "⚠️ Lưu ý: Sau khi tạo ZIP, toàn bộ dữ liệu gốc sẽ bị xóa ngay lập tức " +
                        "— kể cả khi bạn chưa gửi file đi. Hãy đảm bảo bạn sẵn sàng chia sẻ ngay sau khi tạo."
            )
            .setPositiveButton("Tạo ZIP và chia sẻ") { _, _ -> exportAndShare() }
            .setNegativeButton("Huỷ", null)
            .show()
    }
}