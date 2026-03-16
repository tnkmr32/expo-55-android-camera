package expo.modules.qrcode

import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class QRCodeModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("ExpoQRCode")

    // 非同期関数：名前を受け取って挨拶を返す
    AsyncFunction("sayHello") { name: String ->
      "Hello, $name! (from Expo Module)"
    }

    // 同期関数：モジュール情報を返す
    Function("getModuleInfo") {
      mapOf(
        "version" to "1.0.0",
        "buildDate" to System.currentTimeMillis().toString(),
        "platform" to "Android"
      )
    }

    // 非同期関数：エラーをシミュレート
    AsyncFunction("simulateError") {
      throw Exception("This is a simulated error from native module")
    }
  }
}
