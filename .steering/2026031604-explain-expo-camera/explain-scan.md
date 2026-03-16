# expo-camera QRコード・バーコード認識フロー解説

## 概要

expo-cameraモジュールにおけるカメラプレビュー表示時のQRコード／バーコード認識は、CameraXの`ImageAnalysis` APIとGoogle ML Kitの`Barcode Scanning` APIを組み合わせて実装されています。

## アーキテクチャ概要

```
カメラプレビュー
    ↓
ImageAnalysis (CameraX)
    ↓
BarcodeAnalyzer
    ↓
ML Kit Barcode Scanner
    ↓
BarCodeScannerResult
    ↓
JavaScript層 (onBarcodeScanned)
```

## 詳細フロー

### 1. 動画データを取得する

**場所**: `ExpoCameraView.kt` - `createImageAnalyzer()`メソッド

```kotlin
private fun createImageAnalyzer(): ImageAnalysis =
  ImageAnalysis.Builder()
    .setResolutionSelector(
      ResolutionSelector.Builder()
        .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
        .build()
    )
    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
    .build()
```

**ポイント**:

- CameraXの`ImageAnalysis` UseCaseを使用してカメラからのリアルタイムフレームを取得
- `HIGHEST_AVAILABLE_STRATEGY`: 利用可能な最高解像度を使用
- `STRATEGY_KEEP_ONLY_LATEST`: バックプレッシャー対策として最新のフレームのみを保持（古いフレームは破棄）
- これにより処理が追いつかない場合でもメモリ圧迫を防ぎ、常に最新の画像を解析できる

### 2. 動画データからフレームごとに解析にかける

**場所**: `BarcodeAnalyzer.kt` - `analyze()`メソッド

```kotlin
override fun analyze(imageProxy: ImageProxy) {
  val mediaImage = imageProxy.image

  if (mediaImage != null) {
    val rotationDegrees = imageProxy.imageInfo.rotationDegrees
    val image = InputImage.fromMediaImage(mediaImage, rotationDegrees)

    // 回転を考慮した実効的な幅と高さを計算
    val isRotated = rotationDegrees == 90 || rotationDegrees == 270
    val effectiveWidth = if (isRotated) imageProxy.height else imageProxy.width
    val effectiveHeight = if (isRotated) imageProxy.width else imageProxy.height

    // ML Kitへの処理を委譲...
  }
}
```

**ポイント**:

- `BarcodeAnalyzer`が`ImageAnalysis.Analyzer`インターフェースを実装
- カメラからの各フレームが自動的に`analyze()`メソッドに渡される
- `ImageProxy`から実際のカメラ画像データを取得
- デバイスの向きによる回転情報を取得し、ML Kit用の`InputImage`に変換
- 回転角度（90度または270度）に応じて幅と高さを入れ替える必要がある

### 3. 解析処理の内容（ML Kit APIの呼び出し）

**場所**: `BarcodeAnalyzer.kt` - `barcodeScanner.process()`呼び出し

```kotlin
// スキャナーの初期化
private val barcodeFormats = formats.map { it.mapToBarcode() }.reduce { acc, it ->
  acc or it
}
private var barcodeScannerOptions =
  BarcodeScannerOptions.Builder()
    .setBarcodeFormats(barcodeFormats)
    .build()
private var barcodeScanner = BarcodeScanning.getClient(barcodeScannerOptions)

// 実際のスキャン処理
barcodeScanner.process(image)
  .addOnSuccessListener { barcodes ->
    if (barcodes.isEmpty()) {
      return@addOnSuccessListener
    }
    val barcode = barcodes.first()
    // 結果を処理...
  }
  .addOnFailureListener {
    Log.d("SCANNER", it.cause?.message ?: "Barcode scanning failed")
  }
  .addOnCompleteListener {
    imageProxy.close()  // リソースを解放
  }
```

**ポイント**:

- Google ML Kitの`BarcodeScanning` APIを使用
- `BarcodeScannerOptions`で検出するバーコードフォーマット（QRコード、EAN、Code128など）を指定
- `process()`メソッドは非同期で実行され、`Task`オブジェクトを返す
- 成功時には検出されたバーコードのリスト、失敗時にはエラーが返される
- 処理完了後は必ず`imageProxy.close()`を呼び出してメモリリークを防ぐ

**ML Kitの内部処理**:

- 画像からバーコード候補領域を検出
- 各候補領域のパターンをデコード
- バーコードタイプ、データ、位置情報を抽出

### 4. 解析結果の返却

**場所**: `BarcodeAnalyzer.kt` - バーコード情報の抽出と構造化

```kotlin
val barcode = barcodes.first()
val raw = barcode.rawValue ?: barcode.rawBytes?.let { String(it) }

val cornerPoints = barcode.cornerPoints?.let { points ->
  // コーナーポイントを配列に変換
  IntArray(points.size * 2).apply {
    points.forEachIndexed { index, point ->
      this[index * 2] = point.x
      this[index * 2 + 1] = point.y
    }
  }.toMutableList()
} ?: mutableListOf()

val extra = BarCodeScannerResultSerializer.parseExtraDate(barcode)

onComplete(
  BarCodeScannerResult(
    barcode.format,          // バーコードタイプ（QR_CODE, EAN_13など）
    barcode.displayValue,    // 人間が読める形式のデータ
    raw,                     // 生のバイトデータ
    extra,                   // 拡張情報（連絡先、URL、Wi-Fi設定など）
    cornerPoints,            // バーコードの4隅の座標
    effectiveHeight,         // 画像の高さ
    effectiveWidth           // 画像の幅
  )
)
```

**ポイント**:

- ML Kitの`Barcode`オブジェクトから必要な情報を抽出
- `displayValue`: デコードされた文字列データ（QRコードの内容など）
- `rawValue`/`rawBytes`: 生のバイトデータ
- `cornerPoints`: バーコードの4隅の座標（UI表示に使用可能）
- `extra`: バーコードタイプに応じた拡張情報（例：URLバーコードなら完全なURL、連絡先バーコードなら名前・電話番号など）
- これらを統一された`BarCodeScannerResult`オブジェクトにマッピング

### 5. スキャン結果の返却

**場所**: `ExpoCameraView.kt` - イベントディスパッチャー

```kotlin
private val onBarcodeScanned by EventDispatcher<BarcodeScannedEvent>(
  /**
   * 各バーコードを個別にJSリスナーに報告するために、
   * 内容のハッシュ値をキーとして使用
   */
  coalescingKey = { event -> (event.data.hashCode() % Short.MAX_VALUE).toShort() }
)

// BarcodeAnalyzerのコールバック内で呼び出される
analyzer.setAnalyzer(
  ContextCompat.getMainExecutor(context),
  BarcodeAnalyzer(barcodeFormats) {
    onBarcodeScanned(it)  // JavaScript側のonBarcodeScannedコールバックを発火
  }
)
```

**ポイント**:

- `EventDispatcher`を使用してネイティブからJavaScript層にイベントを送信
- `coalescingKey`: 同じバーコードの重複検出を防ぐため、データ内容のハッシュ値をキーとして使用
- メインスレッド（UI thread）でアナライザーを実行することで、スムーズなイベント配信を保証
- JavaScript側では`onBarcodeScanned`プロパティで指定したコールバック関数が呼ばれる

**JavaScript側での受信**:

```tsx
<CameraView
  facing="back"
  onBarcodeScanned={(result: BarcodeScanningResult) => {
    console.log("検出:", result.type, result.data);
  }}
/>
```

## パフォーマンス最適化

### フレームスキップ戦略

- `STRATEGY_KEEP_ONLY_LATEST`: 処理が遅れている場合、古いフレームをスキップ
- ML Kitの処理が完了するまで次のフレームは送信されない（自然なスロットリング）

### メモリ管理

- `imageProxy.close()`を必ず呼び出してメモリリークを防止
- 使用後のBitmapやバッファは適切に解放

### スレッド管理

- ML Kitの処理は内部で非同期実行される
- `addOnCompleteListener`で確実にクリーンアップ処理を実行

## バーコードフォーマットのカスタマイズ

ユーザーは検出するバーコードタイプを指定可能：

```kotlin
// JavaScript側
<CameraView
  barcodeScannerSettings={{
    barcodeTypes: ["qr", "ean13", "code128"]
  }}
/>

// Kotlin側
val barcodeFormats = formats.map { it.mapToBarcode() }.reduce { acc, it ->
  acc or it  // ビット演算でフォーマットを結合
}
```

これにより不要なフォーマットをスキップすることで検出速度を向上できます。

## エラーハンドリング

- ML Kit APIの呼び出し失敗時は`addOnFailureListener`でキャッチ
- 解析に失敗してもアプリがクラッシュしないように、ログ出力のみで続行
- `imageProxy.close()`は必ず`addOnCompleteListener`内で実行し、成功・失敗に関わらずリソースを解放

## まとめ

expo-cameraのQRコード認識システムは以下の技術要素を組み合わせています：

1. **CameraX ImageAnalysis**: リアルタイムでカメラフレームを取得
2. **カスタムAnalyzer**: フレームを適切な形式に変換
3. **Google ML Kit**: 高精度なバーコード検出・デコード
4. **Expo Modules**: ネイティブとJavaScript間のシームレスなイベント通信

これらが協調動作することで、開発者は簡単なAPIでパワフルなバーコードスキャン機能を実装できます。
