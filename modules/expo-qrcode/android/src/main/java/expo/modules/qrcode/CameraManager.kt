package expo.modules.qrcode

import android.content.Context
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.ExecutionException

/**
 * CameraXの初期化と管理を担当するクラス
 */
class CameraManager(private val context: Context) {
  companion object {
    private const val TAG = "CameraManager"
  }

  private var cameraProvider: ProcessCameraProvider? = null
  private var preview: Preview? = null
  private var camera: Camera? = null
  private var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>? = null

  /**
   * カメラをバインドしてプレビューを開始
   */
  fun bindCamera(
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    onSuccess: () -> Unit,
    onError: (String, String) -> Unit
  ) {
    Log.d(TAG, "bindCamera() called")

    try {
      // CameraProviderを取得
      cameraProviderFuture = ProcessCameraProvider.getInstance(context)

      cameraProviderFuture?.addListener({
        try {
          Log.d(TAG, "CameraProvider listener triggered")
          
          cameraProvider = cameraProviderFuture?.get()
          
          if (cameraProvider == null) {
            Log.e(TAG, "Failed to get CameraProvider")
            onError(CameraErrorCode.CAMERA_INITIALIZATION_FAILED, "CameraProvider is null")
            return@addListener
          }

          Log.d(TAG, "CameraProvider obtained successfully")

          // 既存のバインドを解除
          cameraProvider?.unbindAll()
          Log.d(TAG, "Existing bindings unbound")

          // プレビューを設定
          preview = Preview.Builder()
            .build()
            .also { previewUseCase ->
              // プレビューのSurfaceProviderを設定
              previewUseCase.setSurfaceProvider(previewView.surfaceProvider)
            }
          Log.d(TAG, "Preview created and surface provider set")

          // カメラセレクター（背面カメラ）
          val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
          Log.d(TAG, "Camera selector set to BACK_CAMERA")

          try {
            // ライフサイクルにバインド
            camera = cameraProvider?.bindToLifecycle(
              lifecycleOwner,
              cameraSelector,
              preview
            )
            
            Log.d(TAG, "Camera bound to lifecycle successfully")
            onSuccess()
            
          } catch (e: Exception) {
            Log.e(TAG, "Error binding camera to lifecycle", e)
            onError(CameraErrorCode.CAMERA_BIND_FAILED, "Failed to bind camera: ${e.message}")
          }

        } catch (e: ExecutionException) {
          Log.e(TAG, "ExecutionException while getting CameraProvider", e)
          onError(CameraErrorCode.CAMERA_INITIALIZATION_FAILED, "Execution error: ${e.message}")
        } catch (e: InterruptedException) {
          Log.e(TAG, "InterruptedException while getting CameraProvider", e)
          onError(CameraErrorCode.CAMERA_INITIALIZATION_FAILED, "Interrupted: ${e.message}")
        } catch (e: Exception) {
          Log.e(TAG, "Unknown exception in CameraProvider listener", e)
          onError(CameraErrorCode.UNKNOWN_ERROR, "Unknown error: ${e.message}")
        }
      }, ContextCompat.getMainExecutor(context))

    } catch (e: Exception) {
      Log.e(TAG, "Error in bindCamera()", e)
      onError(CameraErrorCode.CAMERA_ERROR, "Failed to initialize camera: ${e.message}")
    }
  }

  /**
   * カメラをアンバインドしてリソースを解放
   */
  fun unbindCamera() {
    Log.d(TAG, "unbindCamera() called")
    
    try {
      cameraProvider?.unbindAll()
      camera = null
      preview = null
      Log.d(TAG, "Camera unbound successfully")
    } catch (e: Exception) {
      Log.e(TAG, "Error unbinding camera", e)
    }
  }

  /**
   * リソースをクリーンアップ
   */
  fun cleanup() {
    Log.d(TAG, "cleanup() called")
    
    try {
      unbindCamera()
      cameraProvider = null
      cameraProviderFuture = null
      Log.d(TAG, "Cleanup completed successfully")
    } catch (e: Exception) {
      Log.e(TAG, "Error during cleanup", e)
    }
  }
}
