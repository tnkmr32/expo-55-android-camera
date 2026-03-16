package expo.modules.qrcode

import android.content.Context
import android.util.Log
import androidx.camera.view.PreviewView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import expo.modules.kotlin.AppContext
import expo.modules.kotlin.exception.Exceptions
import expo.modules.kotlin.viewevent.EventDispatcher
import expo.modules.kotlin.views.ExpoView

/**
 * カメラプレビューを表示するViewコンポーネント
 */
class CameraView(context: Context, appContext: AppContext) : ExpoView(context, appContext) {
  companion object {
    private const val TAG = "CameraView"
  }

  private val previewView: PreviewView
  private val cameraManager: CameraManager
  private var isEnabled: Boolean = false
  private var isCameraReady: Boolean = false
  private var isPermissionRequesting: Boolean = false
  private var lifecycleObserver: DefaultLifecycleObserver? = null

  // イベントディスパッチャー
  private val onCameraReady by EventDispatcher()
  private val onCameraError by EventDispatcher<Map<String, Any?>>()
  private val onPermissionDenied by EventDispatcher()

  init {
    Log.d(TAG, "CameraView init")
    
    // CameraView自体のレイアウトパラメータを設定
    layoutParams = LayoutParams(
      LayoutParams.MATCH_PARENT,
      LayoutParams.MATCH_PARENT
    )
    
    // PreviewViewを作成
    previewView = PreviewView(context).apply {
      layoutParams = LayoutParams(
        LayoutParams.MATCH_PARENT,
        LayoutParams.MATCH_PARENT
      )
      // プレビューのスケールタイプを設定（親ビューを埋める）
      scaleType = PreviewView.ScaleType.FILL_CENTER
      // PERFORMANCE モードを使用（SurfaceViewベース）でSurface準備を高速化
      implementationMode = PreviewView.ImplementationMode.PERFORMANCE
      
      Log.d(TAG, "PreviewView created: scaleType=${scaleType}, implementationMode=${implementationMode}")
    }
    
    addView(previewView)
    
    // PreviewViewがレイアウト後にサイズを確認
    previewView.post {
      Log.d(TAG, "PreviewView size: width=${previewView.width}, height=${previewView.height}")
      Log.d(TAG, "PreviewView visibility: ${previewView.visibility}, isAttachedToWindow=${previewView.isAttachedToWindow}")
    }
    
    // CameraManagerを初期化
    cameraManager = CameraManager(context)
    
    // ライフサイクルオブザーバーを設定
    setupLifecycleObserver()
    
    Log.d(TAG, "CameraView initialized successfully")
  }

  /**
   * ライフサイクルオブザーバーを設定
   */
  private fun setupLifecycleObserver() {
    val activity = appContext.currentActivity as? LifecycleOwner
    if (activity != null) {
      lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onResume(owner: LifecycleOwner) {
          Log.d(TAG, "Activity onResume")
          onHostResume()
        }

        override fun onPause(owner: LifecycleOwner) {
          Log.d(TAG, "Activity onPause")
          onHostPause()
        }
      }
      
      activity.lifecycle.addObserver(lifecycleObserver!!)
      Log.d(TAG, "Lifecycle observer registered")
    } else {
      Log.w(TAG, "Could not register lifecycle observer: Activity is not LifecycleOwner")
    }
  }

  /**
   * ライフサイクルオブザーバーを解除
   */
  private fun removeLifecycleObserver() {
    val activity = appContext.currentActivity as? LifecycleOwner
    if (activity != null && lifecycleObserver != null) {
      activity.lifecycle.removeObserver(lifecycleObserver!!)
      lifecycleObserver = null
      Log.d(TAG, "Lifecycle observer removed")
    }
  }

  /**
   * enabledプロパティが変更されたときの処理
   */
  fun setCameraEnabled(enabled: Boolean) {
    Log.d(TAG, "setCameraEnabled() called with enabled=$enabled")
    
    if (isEnabled == enabled) {
      Log.d(TAG, "Enabled state unchanged, skipping")
      return
    }
    
    isEnabled = enabled
    
    if (enabled) {
      startCamera()
    } else {
      stopCamera()
    }
  }

  /**
   * カメラを起動
   */
  private fun startCamera() {
    Log.d(TAG, "startCamera() called")

    // パーミッションチェック
    if (!PermissionHandler.hasPermission(context)) {
      Log.w(TAG, "Camera permission not granted")
      
      // 既にパーミッション要求中の場合はスキップ
      if (isPermissionRequesting) {
        Log.d(TAG, "Permission request already in progress")
        return
      }
      
      // パーミッションを要求
      val activity = appContext.currentActivity
      if (activity != null) {
        Log.d(TAG, "Requesting camera permission")
        isPermissionRequesting = true
        PermissionHandler.requestPermission(activity)
        
        // パーミッション結果は別途ハンドリングされるべき
        // ここでは即座にonPermissionDeniedを発火しない
      } else {
        Log.e(TAG, "Current activity is null, cannot request permission")
        isPermissionRequesting = false
        isEnabled = false // パーミッション要求できない場合は無効化
        sendErrorEvent(
          CameraErrorCode.CAMERA_ERROR,
          "Cannot request permission: Activity not available"
        )
        onPermissionDenied(mapOf())
      }
      return
    }
    
    // パーミッション要求が完了したのでフラグをリセット
    isPermissionRequesting = false

    // ライフサイクルオーナーを取得
    val lifecycleOwner = appContext.currentActivity as? LifecycleOwner
    
    if (lifecycleOwner == null) {
      Log.e(TAG, "LifecycleOwner not available")
      sendErrorEvent(
        CameraErrorCode.CAMERA_ERROR,
        "Could not start camera: LifecycleOwner not available"
      )
      return
    }

    Log.d(TAG, "Binding camera to lifecycle")
    Log.d(TAG, "PreviewView before bind: width=${previewView.width}, height=${previewView.height}, visibility=${previewView.visibility}")
    
    // 注: PreviewViewの内部View(TextureView/SurfaceView)は、setSurfaceProvider()が呼ばれた後に作成される
    // そのため、レイアウト完了後、すぐにカメラをバインドする
    
    if (previewView.width > 0 && previewView.height > 0) {
      // 既にレイアウト済み - カメラをバインド
      Log.d(TAG, "PreviewView already laid out, binding camera")
      bindCameraToPreview(lifecycleOwner)
    } else {
      // レイアウト完了を待つ
      Log.d(TAG, "Waiting for PreviewView layout...")
      previewView.post {
        if (previewView.width > 0 && previewView.height > 0) {
          Log.d(TAG, "PreviewView layout completed: ${previewView.width}x${previewView.height}")
          bindCameraToPreview(lifecycleOwner)
        } else {
          Log.e(TAG, "PreviewView has zero size after layout")
          sendErrorEvent(
            CameraErrorCode.CAMERA_ERROR,
            "PreviewView layout failed: size is 0x0"
          )
        }
      }
    }
  }

  /**
   * カメラをPreviewViewにバインド
   */
  private fun bindCameraToPreview(lifecycleOwner: LifecycleOwner) {
    Log.d(TAG, "bindCameraToPreview() called")
    
    // カメラをバインド
    cameraManager.bindCamera(
      lifecycleOwner = lifecycleOwner,
      previewView = previewView,
      onSuccess = {
        Log.d(TAG, "Camera started successfully")
        Log.d(TAG, "PreviewView after bind: width=${previewView.width}, height=${previewView.height}")
        isCameraReady = true
        onCameraReady(mapOf())
      },
      onError = { code, message ->
        Log.e(TAG, "Camera start failed: code=$code, message=$message")
        isCameraReady = false
        sendErrorEvent(code, message)
      }
    )
  }

  /**
   * カメラを停止
   */
  private fun stopCamera() {
    Log.d(TAG, "stopCamera() called")
    
    try {
      cameraManager.unbindCamera()
      isCameraReady = false
      Log.d(TAG, "Camera stopped successfully")
    } catch (e: Exception) {
      Log.e(TAG, "Error stopping camera", e)
      sendErrorEvent(
        CameraErrorCode.CAMERA_ERROR,
        "Failed to stop camera: ${e.message}"
      )
    }
  }

  /**
   * エremoveLifecycleObserver()
      ラーイベントを送信
   */
  private fun sendErrorEvent(code: String, message: String) {
    Log.d(TAG, "sendErrorEvent() code=$code, message=$message")
    
    onCameraError(
      mapOf(
        "code" to code,
        "message" to message
      )
    )
  }

  /**
   * ホストがResumeされたときの処理
   */
  fun onHostResume() {
    Log.d(TAG, "onHostResume() called, isEnabled=$isEnabled, isPermissionRequesting=$isPermissionRequesting")
    
    // パーミッション要求から戻ってきた場合、許可状態をチェック
    if (isPermissionRequesting) {
      if (PermissionHandler.hasPermission(context)) {
        Log.d(TAG, "Permission granted, resetting flag and starting camera")
        isPermissionRequesting = false
        if (isEnabled && !isCameraReady) {
          startCamera()
        }
      } else {
        // パーミッションが拒否された
        Log.w(TAG, "Permission denied by user")
        isPermissionRequesting = false
        isEnabled = false
        onPermissionDenied(mapOf())
      }
      return
    }
    
    // 通常のResume処理
    if (isEnabled && !isCameraReady) {
      startCamera()
    }
  }

  /**
   * ホストがPauseされたときの処理
   */
  fun onHostPause() {
    Log.d(TAG, "onHostPause() called")
    
    if (isCameraReady) {
      stopCamera()
    }
  }

  /**
   * ホストが破棄されたときの処理
   */
  fun onHostDestroy() {
    Log.d(TAG, "onHostDestroy() called")
    
    try {
      cameraManager.cleanup()
      isCameraReady = false
      Log.d(TAG, "Resources cleaned up successfully")
    } catch (e: Exception) {
      Log.e(TAG, "Error during cleanup", e)
    }
  }
}
