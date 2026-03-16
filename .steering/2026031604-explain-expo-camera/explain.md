# Expo Camera Android実装の詳細説明

## 概要

`modules/expo-camera/android/src/main/java/expo/modules/camera`には、Androidプラットフォーム向けのExpo Cameraモジュールの実装が含まれています。CameraXライブラリを活用し、カメラプレビュー、写真撮影、動画録画、QRコード/バーコードスキャンなどの機能を提供します。

---

## 1. カメラの起動フロー

### 1.1 概要

カメラの起動は`ExpoCameraView.kt`の`createCamera()`メソッドで実行されます。このプロセスはコルーチン上で非同期に動作し、CameraXの`ProcessCameraProvider`を使用してカメラライフサイクルを管理します。

### 1.2 起動シーケンス

```kotlin
// ExpoCameraView.kt
private suspend fun createCamera() {
    if (!shouldCreateCamera || previewPaused) {
        return
    }
    shouldCreateCamera = false
    val cameraProvider = ProcessCameraProvider.awaitInstance(context)

    // ... 設定処理
}
```

#### ステップ1: ProcessCameraProviderの取得

- `ProcessCameraProvider.awaitInstance(context)` を使用してカメラプロバイダーを取得
- このプロバイダーがカメラライフサイクル全体を管理

#### ステップ2: UseCaseの構築

カメラモードに応じて複数のUseCaseを構築:

1. **Preview UseCase** - プレビュー表示用

   ```kotlin
   val preview = Preview.Builder()
       .setResolutionSelector(resolutionSelector)
       .build()
       .also {
           it.surfaceProvider = previewView.surfaceProvider
       }
   ```

2. **ImageCapture UseCase** - 静止画撮影用（PICTUREモード）

   ```kotlin
   imageCaptureUseCase = ImageCapture.Builder()
       .setResolutionSelector(resolutionSelector)
       .setFlashMode(currentFlashMode.mapToLens())
       .build()
   ```

3. **ImageAnalysis UseCase** - QRコード/バーコードスキャン用

   ```kotlin
   imageAnalysisUseCase = ImageAnalysis.Builder()
       .setResolutionSelector(...)
       .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
       .build()
   ```

4. **VideoCapture UseCase** - 動画録画用（VIDEOモード）
   ```kotlin
   val videoCapture = createVideoCapture()
   ```

#### ステップ3: CameraSelectorの構築

前面/背面カメラの選択:

```kotlin
val cameraSelector = CameraSelector.Builder()
    .requireLensFacing(lensFacing.mapToCharacteristic())
    .build()
```

#### ステップ4: UseCaseのバインド

```kotlin
val useCases = UseCaseGroup.Builder().apply {
    addUseCase(preview)
    if (cameraMode == CameraMode.PICTURE) {
        imageCaptureUseCase?.let { addUseCase(it) }
        imageAnalysisUseCase?.let { addUseCase(it) }
    } else {
        addUseCase(videoCapture)
    }
}.build()

cameraProvider.unbindAll()
camera = cameraProvider.bindToLifecycle(currentActivity, cameraSelector, useCases)
```

#### ステップ5: カメラ状態の監視

```kotlin
camera?.let {
    observeCameraState(it.cameraInfo)
}

private fun observeCameraState(cameraInfo: CameraInfo) {
    cameraInfo.cameraState.observe(currentActivity) {
        when (it.type) {
            CameraState.Type.OPEN -> {
                onCameraReady(Unit)  // React Nativeにイベント送信
                setTorchEnabled(enableTorch)
            }
            else -> {}
        }
    }
}
```

### 1.3 起動トリガー

カメラの再作成は以下の場合に発生:

- `lensFacing`（前面/背面）変更時
- `cameraMode`（写真/動画）変更時
- `videoQuality`変更時
- `ratio`（アスペクト比）変更時
- `pictureSize`変更時
- `mirror`設定変更時
- `recreateCamera()`メソッド呼び出し時

これらのプロパティが変更されると`shouldCreateCamera = true`がセットされ、次回の`createCamera()`呼び出しで再構築されます。

---

## 2. カメラ権限の管理

### 2.1 概要

カメラ権限の管理は`CameraViewModule.kt`で実装されています。Expo Modules APIの`Permissions`ユーティリティを使用して、React Native層からの権限要求を処理します。

### 2.2 必要な権限

```kotlin
// CameraViewModule.kt
val cameraPermissions = if (VRUtilities.isQuest()) {
    arrayOf(
        Manifest.permission.CAMERA,
        VRUtilities.HZOS_CAMERA_PERMISSION  // Quest VRデバイス用
    )
} else {
    arrayOf(Manifest.permission.CAMERA)
}
```

一般的なAndroidデバイスでは`Manifest.permission.CAMERA`のみ必要ですが、Quest VRデバイスでは追加の権限が必要です。

### 2.3 権限関連メソッド

#### カメラ権限の確認

```kotlin
AsyncFunction("getCameraPermissionsAsync") { promise: Promise ->
    Permissions.getPermissionsWithPermissionsManager(
        permissionsManager,
        promise,
        *cameraPermissions
    )
}
```

- 現在の権限状態を確認
- 結果はPromiseとして返される

#### カメラ権限の要求

```kotlin
AsyncFunction("requestCameraPermissionsAsync") { promise: Promise ->
    Permissions.askForPermissionsWithPermissionsManager(
        permissionsManager,
        promise,
        *cameraPermissions
    )
}
```

- ユーザーに権限ダイアログを表示
- ユーザーの応答結果をPromiseで返す

#### マイク権限（動画録音用）

```kotlin
AsyncFunction("requestMicrophonePermissionsAsync") { promise: Promise ->
    Permissions.askForPermissionsWithPermissionsManager(
        permissionsManager,
        promise,
        Manifest.permission.RECORD_AUDIO
    )
}

AsyncFunction("getMicrophonePermissionsAsync") { promise: Promise ->
    Permissions.getPermissionsWithPermissionsManager(
        permissionsManager,
        promise,
        Manifest.permission.RECORD_AUDIO
    )
}
```

### 2.4 動画録画時の権限チェック

```kotlin
// ExpoCameraView.kt - record()メソッド内
if (!mute && ActivityCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
    ) != PackageManager.PERMISSION_GRANTED
) {
    promise.reject(Exceptions.MissingPermissions(Manifest.permission.RECORD_AUDIO))
    return
}
```

音声付き動画録画時には`RECORD_AUDIO`権限を実行時にチェックします。

---

## 3. プレビューの表示フロー

### 3.1 概要

プレビュー表示には`PreviewView`（CameraXのコンポーネント）を使用し、`ExpoCameraView`がそのラッパーとして機能します。

### 3.2 PreviewViewの初期化

```kotlin
// ExpoCameraView.kt - クラスレベルのプロパティ
private var previewView = PreviewView(context).apply {
    elevation = 0f
}

// init ブロック内
init {
    orientationEventListener.enable()
    previewView.setOnHierarchyChangeListener(object : OnHierarchyChangeListener {
        override fun onChildViewRemoved(parent: View?, child: View?) = Unit
        override fun onChildViewAdded(parent: View?, child: View?) {
            parent?.measure(
                MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY)
            )
            parent?.layout(0, 0, parent.measuredWidth, parent.measuredHeight)
        }
    })
    addView(
        previewView,
        ViewGroup.LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
        )
    )
}
```

### 3.3 Preview UseCaseの作成とバインド

```kotlin
private suspend fun createCamera() {
    // ...

    // アスペクト比に応じたScaleTypeの設定
    ratio?.let {
        previewView.scaleType =
            if (ratio == CameraRatio.FOUR_THREE || ratio == CameraRatio.SIXTEEN_NINE) {
                PreviewView.ScaleType.FIT_CENTER
            } else {
                PreviewView.ScaleType.FILL_CENTER
            }
    }

    // ResolutionSelectorの構築
    val resolutionSelector = buildResolutionSelector()

    // Preview UseCaseの作成
    val preview = Preview.Builder()
        .setResolutionSelector(resolutionSelector)
        .build()
        .also {
            it.surfaceProvider = previewView.surfaceProvider
        }

    // ...
}
```

### 3.4 解像度選択

```kotlin
private fun buildResolutionSelector(): ResolutionSelector {
    val strategy = if (pictureSize.isNotEmpty()) {
        val size = parseSizeSafely(pictureSize)
        size?.let {
            ResolutionStrategy(size, ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)
        } ?: ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY
    } else {
        ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY
    }

    return if (ratio == CameraRatio.ONE_ONE) {
        // 正方形（1:1）の場合：正方形の解像度のみをフィルタ
        ResolutionSelector.Builder().setResolutionFilter { supportedSizes, _ ->
            return@setResolutionFilter supportedSizes.filter {
                it.width == it.height
            }
        }.setResolutionStrategy(strategy).build()
    } else {
        // その他のアスペクト比
        ResolutionSelector.Builder().apply {
            ratio?.let {
                setAspectRatioStrategy(it.mapToStrategy())
            }
            setResolutionStrategy(strategy)
        }.build()
    }
}
```

### 3.5 ScaleTypeの動作

#### FIT_CENTER

- プレビュー全体が見えるようにスケール
- アスペクト比を維持しながら、小さい方の辺に合わせる
- 余白（レターボックス/ピラーボックス）が発生する可能性あり

#### FILL_CENTER

- プレビューがビュー全体を埋めるようにスケール
- アスペクト比を維持しながら、大きい方の辺に合わせる
- 画像の一部がクロップされる可能性あり

### 3.6 デバイス回転への対応

```kotlin
private val orientationEventListener by lazy {
    object : OrientationEventListener(appContext.throwingActivity) {
        override fun onOrientationChanged(orientation: Int) {
            if (orientation == ORIENTATION_UNKNOWN) {
                return
            }

            val rotation = when (orientation) {
                in 45 until 135 -> Surface.ROTATION_270
                in 135 until 225 -> Surface.ROTATION_180
                in 225 until 315 -> Surface.ROTATION_90
                else -> Surface.ROTATION_0
            }

            imageAnalysisUseCase?.targetRotation = rotation
            imageCaptureUseCase?.targetRotation = rotation
        }
    }
}
```

デバイスの向きに応じて、画像解析と撮影のターゲット回転を動的に更新します。

### 3.7 プレビューの一時停止/再開

```kotlin
fun pausePreview() {
    previewPaused = true
    cameraProvider?.unbindAll()
}

fun resumePreview() {
    shouldCreateCamera = true
    previewPaused = false
    scope.launch {
        createCamera()
    }
}
```

アプリがバックグラウンドに移行する際などにプレビューを一時停止し、リソースを解放できます。

---

## 4. QRコード読み取りロジック

### 4.1 概要

QRコード/バーコードの読み取りには、Google ML Kitの`BarcodeScanning` APIを使用しています。`ImageAnalysis` UseCaseと組み合わせて、リアルタイムでプレビュー画像からバーコードを検出します。

### 4.2 アーキテクチャ

1. **BarcodeAnalyzer** - `ImageAnalysis.Analyzer`の実装
2. **MLKitBarCodeScanner** - 静止画像からのバーコードスキャン用
3. **BarCodeScannerResultSerializer** - 検出結果のBundle化

### 4.3 BarcodeAnalyzerの実装

```kotlin
// BarcodeAnalyzer.kt
@OptIn(ExperimentalGetImage::class)
class BarcodeAnalyzer(
    formats: List<BarcodeType>,
    val onComplete: (BarCodeScannerResult) -> Unit
) : ImageAnalysis.Analyzer {

    // 検出対象のバーコードフォーマット
    private val barcodeFormats = if (formats.isEmpty()) {
        0  // すべてのフォーマット
    } else {
        formats.map { it.mapToBarcode() }.reduce { acc, it ->
            acc or it
        }
    }

    private var barcodeScannerOptions =
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(barcodeFormats)
            .build()

    private var barcodeScanner = BarcodeScanning.getClient(barcodeScannerOptions)

    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image

        if (mediaImage != null) {
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val image = InputImage.fromMediaImage(mediaImage, rotationDegrees)

            // 回転後の有効サイズを計算
            val isRotated = rotationDegrees == 90 || rotationDegrees == 270
            val effectiveWidth = if (isRotated) imageProxy.height else imageProxy.width
            val effectiveHeight = if (isRotated) imageProxy.width else imageProxy.height

            barcodeScanner.process(image)
                .addOnSuccessListener { barcodes ->
                    if (barcodes.isEmpty()) {
                        return@addOnSuccessListener
                    }

                    // 最初に検出されたバーコードを処理
                    val barcode = barcodes.first()
                    val raw = barcode.rawValue ?: barcode.rawBytes?.let { String(it) }

                    // コーナーポイントの抽出
                    val cornerPoints = barcode.cornerPoints?.let { points ->
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
                            barcode.format,
                            barcode.displayValue,
                            raw,
                            extra,
                            cornerPoints,
                            effectiveHeight,
                            effectiveWidth
                        )
                    )
                }
                .addOnFailureListener {
                    Log.d("SCANNER", it.cause?.message ?: "Barcode scanning failed")
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        }
    }
}
```

### 4.4 ImageAnalysis UseCaseの設定

```kotlin
// ExpoCameraView.kt
private fun createImageAnalyzer(): ImageAnalysis =
    ImageAnalysis.Builder()
        .setResolutionSelector(
            ResolutionSelector.Builder()
                .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                .build()
        )
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .build()
        .also { analyzer ->
            if (shouldScanBarcodes && CameraUtils.isMLKitBarcodeScannerAvailable()) {
                try {
                    analyzer.setAnalyzer(
                        ContextCompat.getMainExecutor(context),
                        BarcodeAnalyzer(barcodeFormats) {
                            onBarcodeScanned(it)
                        }
                    )
                } catch (e: Exception) {
                    Log.e(CameraViewModule.TAG, "Failed to initialize BarcodeAnalyzer: ${e.message}")
                }
            }
        }
```

### 4.5 座標変換ロジック

検出されたバーコードの座標は画像座標系で返されるため、プレビュービューの座標系に変換する必要があります:

```kotlin
private fun transformBarcodeScannerResultToViewCoordinates(barcode: BarCodeScannerResult) {
    val cornerPoints = barcode.cornerPoints
    val previewWidth = previewView.width.toFloat()
    val previewHeight = previewView.height.toFloat()
    val imageWidth = barcode.width.toFloat()
    val imageHeight = barcode.height.toFloat()

    if (previewWidth <= 0 || previewHeight <= 0 || imageWidth <= 0 || imageHeight <= 0) {
        return
    }

    val scaleX: Float
    val scaleY: Float
    var offsetX = 0f
    var offsetY = 0f

    when (previewView.scaleType) {
        PreviewView.ScaleType.FIT_CENTER -> {
            val previewAspectRatio = previewWidth / previewHeight
            val imageAspectRatio = imageWidth / imageHeight

            if (previewAspectRatio > imageAspectRatio) {
                scaleY = previewHeight / imageHeight
                scaleX = scaleY
                offsetX = (previewWidth - imageWidth * scaleX) / 2f
            } else {
                scaleX = previewWidth / imageWidth
                scaleY = scaleX
                offsetY = (previewHeight - imageHeight * scaleY) / 2f
            }
        }
        PreviewView.ScaleType.FILL_CENTER -> {
            val previewAspectRatio = previewWidth / previewHeight
            val imageAspectRatio = imageWidth / imageHeight

            if (previewAspectRatio > imageAspectRatio) {
                scaleX = previewWidth / imageWidth
                scaleY = scaleX
                offsetY = (previewHeight - imageHeight * scaleY) / 2f
            } else {
                scaleY = previewHeight / imageHeight
                scaleX = scaleY
                offsetX = (previewWidth - imageWidth * scaleX) / 2f
            }
        }
        else -> {
            scaleX = previewWidth / imageWidth
            scaleY = previewHeight / imageHeight
        }
    }

    // コーナーポイントの変換
    cornerPoints.mapX { index ->
        val originalX = cornerPoints[index]
        (originalX * scaleX + offsetX).roundToInt()
    }

    cornerPoints.mapY { index ->
        val originalY = cornerPoints[index]
        (originalY * scaleY + offsetY).roundToInt()
    }

    barcode.cornerPoints = cornerPoints
    barcode.height = previewHeight.toInt()
    barcode.width = previewWidth.toInt()
}
```

### 4.6 React Nativeへのイベント送信

```kotlin
private val onBarcodeScanned by EventDispatcher<BarcodeScannedEvent>(
    /**
     * 異なるバーコードの内容を区別するためにハッシュ値を使用。
     * 同じ内容のイベントは結合（coalesce）される。
     */
    coalescingKey = { event -> (event.data.hashCode() % Short.MAX_VALUE).toShort() }
)

private fun onBarcodeScanned(barcode: BarCodeScannerResult) {
    if (shouldScanBarcodes) {
        transformBarcodeScannerResultToViewCoordinates(barcode)

        val (cornerPoints, boundingBox) = getCornerPointsAndBoundingBox(
            barcode.cornerPoints,
            barcode.boundingBox
        )

        onBarcodeScanned(
            BarcodeScannedEvent(
                target = id,
                data = barcode.value.toString(),
                raw = barcode.raw.toString(),
                type = BarcodeType.mapFormatToString(barcode.type),
                cornerPoints = cornerPoints,
                bounds = boundingBox,
                extra = barcode.extra
            )
        )
    }
}
```

### 4.7 静止画像からのスキャン

URLから画像を読み込んでバーコードをスキャンする機能も提供:

```kotlin
// CameraViewModule.kt
AsyncFunction("scanFromURLAsync") {
    url: String,
    barcodeTypes: List<BarcodeType>,
    promise: Promise
->
    if (!CameraUtils.isMLKitAvailable(appContext.reactContext)) {
        promise.reject(CameraExceptions.MLKitUnavailableException())
        return@AsyncFunction
    }

    appContext.service<ImageLoaderInterface>()?.loadImageForManipulationFromURL(
        url,
        object : ImageLoaderInterface.ResultListener {
            override fun onSuccess(bitmap: Bitmap) {
                try {
                    val scanner = MLKitBarCodeScanner()
                    val formats = barcodeTypes.map { it.mapToBarcode() }
                    scanner.setSettings(formats)

                    moduleScope.launch {
                        try {
                            val barcodes = scanner.scan(bitmap)
                                .filter { formats.contains(it.type) }
                                .map { BarCodeScannerResultSerializer.toBundle(it, 1.0f) }
                            promise.resolve(barcodes)
                        } catch (e: Exception) {
                            promise.reject(CameraExceptions.MLKitUnavailableException())
                        }
                    }
                } catch (e: Exception) {
                    promise.reject(CameraExceptions.MLKitUnavailableException())
                }
            }

            override fun onFailure(cause: Throwable?) {
                promise.reject(CameraExceptions.ImageRetrievalException(url))
            }
        }
    )
}
```

### 4.8 サポート対象のバーコードフォーマット

ML Kitは以下のバーコードフォーマットをサポート:

- QR Code
- Data Matrix
- PDF417
- Aztec
- Code 39
- Code 93
- Code 128
- EAN-8
- EAN-13
- UPC-A
- UPC-E
- Codabar
- ITF

これらは`BarcodeType` enumで定義され、ML Kitの形式にマッピングされます。

---

## まとめ

Expo Camera Androidモジュールは、以下の主要コンポーネントで構成されています:

1. **CameraViewModule.kt** - Expo Moduleの定義、権限管理、静止画スキャン
2. **ExpoCameraView.kt** - メインのカメラビュー実装、CameraXとの統合
3. **BarcodeAnalyzer.kt** - リアルタイムバーコードスキャン
4. **MLKitBarcodeAnalyzer.kt** - 静止画像からのバーコードスキャン

これらはCameraXライブラリとML Kit APIを使用し、モダンで高性能なカメラ機能を提供しています。各機能は適切に分離され、React Nativeとの連携にはExpo Modules APIを使用しています。
