package com.datn.datacollectv2

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat

class PermissionHelper(private val activity: Activity) {

    companion object {
        val REQUIRED: List<String> = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(Manifest.permission.ACTIVITY_RECOGNITION)
            }
        }
    }

    private var launcher: ActivityResultLauncher<Array<String>>? = null
    private var onGranted: (() -> Unit)? = null

    fun setLauncher(l: ActivityResultLauncher<Array<String>>) {
        launcher = l
    }

    fun handleResult(results: Map<String, Boolean>) {
        val denied = results.filterValues { !it }.keys
        if (denied.isEmpty()) {
            onGranted?.invoke()
        } else {
            showDeniedWarning(denied)
            onGranted?.invoke()
        }
    }

    fun checkAndRequest(onAllGranted: () -> Unit) {
        onGranted = onAllGranted
        val missing = REQUIRED.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            onAllGranted()
            return
        }
        showRationaleDialog(missing) {
            launcher?.launch(missing.toTypedArray())
                ?: run { onAllGranted() }
        }
    }

    // ── Private helpers ────────────────────────────────────────────────

    private fun showRationaleDialog(missing: List<String>, onOk: () -> Unit) {
        val lines = missing.joinToString("\n") { perm ->
            when {
                perm == Manifest.permission.POST_NOTIFICATIONS ->
                    "• Thông báo: hiển thị trạng thái thu cảm biến khi ứng dụng chạy nền"
                perm == Manifest.permission.ACTIVITY_RECOGNITION ->
                    "• Nhận diện hoạt động: đo chính xác hơn khi đi bộ / đứng / ngồi"
                else -> "• ${perm.substringAfterLast('.')}"
            }
        }

        AlertDialog.Builder(activity)
            .setTitle("Ứng dụng cần một số quyền")
            .setMessage(
                "Để thu dữ liệu cảm biến đúng cách, ứng dụng cần:\n\n$lines\n\n" +
                "Dữ liệu chỉ được lưu trên thiết bị của bạn và không chia sẻ ra ngoài."
            )
            .setPositiveButton("Cấp quyền") { _, _ -> onOk() }
            .setNegativeButton("Bỏ qua") { _, _ ->
                onGranted?.invoke()
            }
            .setCancelable(false)
            .show()
    }

    private fun showDeniedWarning(denied: Set<String>) {
        val msg = if (denied.any { it == Manifest.permission.POST_NOTIFICATIONS })
            "Bạn đã từ chối quyền Thông báo. Trạng thái thu sẽ không hiện trên thanh thông báo khi ứng dụng chạy nền."
        else
            "Một số quyền bị từ chối. Ứng dụng vẫn hoạt động nhưng có thể thiếu một số tính năng."

        AlertDialog.Builder(activity)
            .setTitle("Lưu ý")
            .setMessage(msg)
            .setPositiveButton("OK", null)
            .show()
    }
}
