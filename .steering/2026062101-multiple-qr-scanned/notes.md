# 複数QRコード同時検出対応

## 変更ファイル

`modules/expo-camera/android/src/main/java/expo/modules/camera/analyzers/BarcodeAnalyzer.kt`

## 変更差分のサマリー

### 変更前の問題

MLKit が `List<Barcode>` を返しても、先頭1件だけを取り出して処理していた。

```kotlin
// 変更前
val barcode = barcodes.first()   // ← 1件のみ取り出し、残りは捨てる
onComplete(BarCodeScannerResult(...))
```

### 変更内容

バーコード処理ロジックを `processResult()` に抽出し、`barcodes.forEach` で全件処理するよう変更。

```diff
- val barcode = barcodes.first()
- val raw = barcode.rawValue ?: ...
- ...
- onComplete(BarCodeScannerResult(...))
+ processResult(barcodes, effectiveHeight, effectiveWidth, frameId)
```

```kotlin
// 追加した processResult()
internal fun processResult(barcodes: List<Barcode>, ...) {
    if (barcodes.isEmpty()) {
        PerformanceLogger.logSummary(frameId, detected = false)
        return
    }
    Log.d("SCANNER", "Frame #$frameId: ${barcodes.size}枚のQRコードを検出")
    barcodes.forEach { barcode ->
        // 各バーコードを BarCodeScannerResult に変換して onComplete を呼ぶ
        onComplete(BarCodeScannerResult(...))
    }
    PerformanceLogger.logSummary(frameId, detected = true)
}
```

### その他の変更点

- `PerformanceLogger.logSummary(detected = true)` をループ外に移動（重複呼び出し防止）
- `Log.d("SCANNER", ...)` で検出枚数を毎フレームログ出力（1枚以上検出時のみ）
- `import com.google.mlkit.vision.barcode.common.Barcode` を追加

---

## 1フレームで複数QRを検出したときの呼び出し側の挙動

### データフロー

```
MLKit → List<Barcode> (N件)
  └─ BarcodeAnalyzer.processResult()
       └─ forEach (N回)
            └─ onComplete(BarCodeScannerResult)
                 └─ ExpoCameraView.onBarcodeScanned(BarCodeScannerResult)
                      └─ EventDispatcher.onBarcodeScanned(BarcodeScannedEvent)
                           └─ JS: onBarcodeScanned コールバック (N回発火)
```

### React Native 側への届き方

- 1フレームで2枚検出した場合、JS の `onBarcodeScanned` コールバックが **2回個別に呼ばれる**
- 1回のコールバックに複数件がまとまった配列で来るわけではない
- 各コールバックの引数は従来通りの単一バーコードオブジェクト `{ data, raw, type, cornerPoints, bounds }`

### コアリング（イベント統合）の扱い

`EventDispatcher` の coalescingKey は `event.data.hashCode() % 32767`（`ExpoCameraView.kt:234`）。

```kotlin
// コードのコメントより
// We want every distinct barcode to be reported to the JS listener.
// So let's differentiate them with a hash of the contents (mod short's max value).
coalescingKey = { event -> (event.data.hashCode() % Short.MAX_VALUE).toShort() }
```

- 異なるデータのQRコードは異なる coalescingKey を持つため、統合されずに両方JSへ届く
- ハッシュ衝突（1/32,767 ≈ 0.003%）が起きた場合は片方が消える可能性があるが実用上無視できる水準

### 動作確認方法

```
adb logcat -s SCANNER
```

1枚以上検出したフレームで以下のようなログが出力される：

```
D/SCANNER: Frame #1234567890: 2枚のQRコードを検出
D/SCANNER: Frame #1234567891: 1枚のQRコードを検出
```

---

## JS側の実装上の注意

- 同一フレーム由来の複数バーコードは時間的に連続して届くが、フレームIDでの紐付けはできない（JS側のイベントに frameId は含まれない）
- 同じバーコードが連続フレームで検出されると繰り返し onBarcodeScanned が発火する（既存の挙動と同じ）
