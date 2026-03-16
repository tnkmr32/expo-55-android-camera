package expo.modules.qrcode

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * カメラパーミッション処理クラス
 */
class PermissionHandler {
  companion object {
    private const val TAG = "PermissionHandler"
    private const val CAMERA_PERMISSION = Manifest.permission.CAMERA
    const val CAMERA_PERMISSION_REQUEST_CODE = 1001

    /**
     * カメラパーミッションが許可されているかチェック
     */
    fun hasPermission(context: Context): Boolean {
      Log.d(TAG, "hasPermission() called")
      val result = ContextCompat.checkSelfPermission(
        context,
        CAMERA_PERMISSION
      ) == PackageManager.PERMISSION_GRANTED
      
      Log.d(TAG, "hasPermission() result: $result")
      return result
    }

    /**
     * カメラパーミッションを要求
     */
    fun requestPermission(activity: Activity) {
      Log.d(TAG, "requestPermission() called")
      ActivityCompat.requestPermissions(
        activity,
        arrayOf(CAMERA_PERMISSION),
        CAMERA_PERMISSION_REQUEST_CODE
      )
    }

    /**
     * パーミッション説明を表示すべきか判定
     */
    fun shouldShowRationale(activity: Activity): Boolean {
      Log.d(TAG, "shouldShowRationale() called")
      val result = ActivityCompat.shouldShowRequestPermissionRationale(
        activity,
        CAMERA_PERMISSION
      )
      
      Log.d(TAG, "shouldShowRationale() result: $result")
      return result
    }
  }
}
