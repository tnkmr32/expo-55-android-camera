package expo.modules.qrcode

import android.util.Log
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class QRCodeModule : Module() {
  companion object {
    private const val TAG = "QRCodeModule"
  }

  override fun definition() = ModuleDefinition {
    Name("ExpoQRCode")

    Log.d(TAG, "QRCodeModule definition() called")

    // 非同期関数：名前を受け取って挨拶を返す
    AsyncFunction("sayHello") { name: String ->
      Log.d(TAG, "sayHello() called with name=$name")
      "Hello, $name! (from Expo Module)"
    }

    // 同期関数：モジュール情報を返す
    Function("getModuleInfo") {
      Log.d(TAG, "getModuleInfo() called")
      mapOf(
        "version" to "1.0.0",
        "buildDate" to System.currentTimeMillis().toString(),
        "platform" to "Android"
      )
    }

    // 非同期関数：エラーをシミュレート
    AsyncFunction("simulateError") {
      Log.d(TAG, "simulateError() called")
      throw Exception("This is a simulated error from native module")
    }

    // CameraViewの登録
    View(CameraView::class) {
      Log.d(TAG, "CameraView registration")

      // Events
      Events("onCameraReady", "onCameraError", "onPermissionDenied")

      // Props
      Prop("enabled") { view: CameraView, enabled: Boolean ->
        Log.d(TAG, "CameraView enabled prop set to $enabled")
        view.setCameraEnabled(enabled)
      }

      // Lifecycle callbacks
      OnViewDidUpdateProps { view: CameraView ->
        Log.d(TAG, "CameraView props updated")
      }

      OnViewDestroys { view: CameraView ->
        Log.d(TAG, "CameraView destroying")
        view.onHostDestroy()
      }
    }
  }
}
