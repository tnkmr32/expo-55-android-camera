# パフォーマンス測定機能の設計書

作成日: 2026年3月16日
対象モジュール: `modules/expo-camera`

## 1. 要件の確認

### 目的

expo-cameraのQRコード認識処理におけるパフォーマンスボトルネックを特定するため、各処理段階の実行時間を計測する機能を追加する。

### 測定対象

以下の4つの処理段階の実行時間を計測する：

1. **フレームのキャプチャ**: CameraXの`ImageAnalysis`のコールバック内で、フレームがキャプチャされたタイムスタンプを記録
2. **フレームの変換**: `InputImage`への変換前後でタイムスタンプを記録し、変換にかかった時間を計測
3. **QRコードの解析**: ML KitのBarcode Scanner APIの呼び出し前後でタイムスタンプを記録し、解析にかかった時間を計測
4. **結果の処理**: JavaScript層で`onBarcodeScanned`イベントが発火したタイムスタンプを記録し、全体の処理時間を計測

### 要求仕様

- 測定はオプション機能として実装し、開発・デバッグ時のみ有効化できるようにする
- 測定結果はAndroidのLogcatに出力する
- 測定結果は適切なフォーマットで出力し、解析しやすくする
- 本番環境では測定コードのオーバーヘッドを最小限に抑える

## 2. 影響範囲の特定

### 変更対象ファイル

#### 主要変更

1. **BarcodeAnalyzer.kt** (`modules/expo-camera/android/src/main/java/expo/modules/camera/analyzers/`)
   - `analyze(imageProxy: ImageProxy)`メソッドに計測ポイントを追加
   - フレームキャプチャ、変換、解析の各時点でタイムスタンプを記録
   - 計測結果をログ出力

2. **ExpoCameraView.kt** (`modules/expo-camera/android/src/main/java/expo/modules/camera/`)
   - `onBarcodeScanned`メソッドに計測ポイントを追加
   - JavaScript層へのイベント送信時刻を記録
   - 全体の処理時間を計算してログ出力
   - 設定APIを追加（パフォーマンス測定の有効/無効切り替え）

#### 新規作成

3. **PerformanceLogger.kt** (新規)
   - パフォーマンス測定用のユーティリティクラス
   - タイムスタンプの記録とフォーマット
   - ログ出力の共通処理
   - 測定の有効/無効を管理

4. **CameraRecords.kt** (追加)
   - パフォーマンス測定の設定を表すRecordを追加

### 既存機能への影響

- 既存の動作には影響なし（測定機能がデフォルトで無効のため）
- 測定有効時もフレーム処理の流れは変更なし
- ログ出力のみの追加のため、パフォーマンスへの影響は最小限

## 3. 設計の策定

### アーキテクチャ

#### 3.1 PerformanceLoggerクラス

```
class PerformanceLogger {
  companion object {
    var isEnabled: Boolean = false

    // 測定セッションを表すデータクラス
    data class MeasurementSession(
      val frameId: Long,
      val frameCapturedAt: Long,
      val conversionStartAt: Long? = null,
      val conversionEndAt: Long? = null,
      val analysisStartAt: Long? = null,
      val analysisEndAt: Long? = null,
      val resultProcessedAt: Long? = null
    )

    // セッション管理
    private val sessions = ConcurrentHashMap<Long, MeasurementSession>()

    // API
    fun startFrameCapture(frameId: Long): Long
    fun recordConversionStart(frameId: Long)
    fun recordConversionEnd(frameId: Long)
    fun recordAnalysisStart(frameId: Long)
    fun recordAnalysisEnd(frameId: Long)
    fun recordResultProcessed(frameId: Long)
    fun logSummary(frameId: Long)
  }
}
```

#### 3.2 BarcodeAnalyzerの変更点

- `analyze()`メソッドの各ステップで`PerformanceLogger`を呼び出す
- フレームIDは`ImageProxy`のタイムスタンプを使用
- 測定ポイント：
  1. メソッド開始時：フレームキャプチャ
  2. `InputImage.fromMediaImage()`呼び出し前後：変換時間
  3. `barcodeScanner.process()`呼び出し前後：解析時間

#### 3.3 ExpoCameraViewの変更点

- パフォーマンス測定の有効/無効を設定するプロパティを追加
- `onBarcodeScanned()`メソッドで結果処理時刻を記録
- 設定変更時に`PerformanceLogger.isEnabled`を更新

#### 3.4 ログ出力フォーマット

```
[PERF] Frame #123456789
  Capture      : 0ms
  Conversion   : 5ms
  Analysis     : 45ms
  Result       : 2ms
  Total        : 52ms
```

### データフロー

```
1. ImageAnalysis.Analyzer (BarcodeAnalyzer)
   ↓ analyze(imageProxy)
   ├─ [T0] PerformanceLogger.startFrameCapture(frameId)
   ↓
2. 変換処理
   ├─ [T1] PerformanceLogger.recordConversionStart(frameId)
   ├─ InputImage.fromMediaImage()
   └─ [T2] PerformanceLogger.recordConversionEnd(frameId)
   ↓
3. ML Kit解析
   ├─ [T3] PerformanceLogger.recordAnalysisStart(frameId)
   ├─ barcodeScanner.process(image)
   └─ [T4] PerformanceLogger.recordAnalysisEnd(frameId)
   ↓
4. 結果処理
   ├─ onComplete callback
   ├─ ExpoCameraView.onBarcodeScanned()
   ├─ [T5] PerformanceLogger.recordResultProcessed(frameId)
   └─ PerformanceLogger.logSummary(frameId)
```

### スレッド安全性

- `ConcurrentHashMap`を使用してマルチスレッド環境でのセッション管理を保証
- `ImageAnalysis`は設定されたExecutorで実行されるため、適切な同期が必要

## 4. 実装の計画

### フェーズ1: PerformanceLoggerの実装

1. `PerformanceLogger.kt`を新規作成
2. 基本的なタイムスタンプ記録機能を実装
3. ログ出力フォーマットを実装

### フェーズ2: BarcodeAnalyzerへの統合

1. `BarcodeAnalyzer.kt`の`analyze()`メソッドに計測ポイントを追加
2. フレームID（タイムスタンプ）の取得
3. 各処理段階での`PerformanceLogger`呼び出し
4. エラーハンドリングの追加（例外発生時も測定を完了）

### フェーズ3: ExpoCameraViewへの統合

1. パフォーマンス測定の有効/無効設定プロパティを追加
2. `CameraRecords.kt`に設定用Recordを追加
3. `onBarcodeScanned()`メソッドに計測ポイントを追加
4. `PerformanceLogger.isEnabled`の制御

### 実装順序

1. PerformanceLogger.kt (新規作成)
2. CameraRecords.kt (設定Record追加)
3. BarcodeAnalyzer.kt (計測ポイント追加)
4. ExpoCameraView.kt (API追加と計測ポイント追加)

## 注意事項

### パフォーマンスへの影響

- 測定無効時は、`isEnabled`のチェックのみで済むため影響は最小限
- 測定有効時でも、`System.nanoTime()`の呼び出しとHashMapへの書き込み程度のオーバーヘッド
- ログ出力は非同期または完了時のみに限定

### メモリ管理

- セッションデータは結果処理後にHashMapから削除
- フレームがスキップされた場合の古いセッションのクリーンアップ機構を追加

### デバッグの有効化

- BuildConfigまたは実行時フラグで制御可能にする
- JavaScript側からも設定できるようにするか検討（将来的なオプション）

### ログレベル

- パフォーマンス情報は`Log.d()`または`Log.i()`で出力
- エラー時は`Log.e()`を使用

## 次のステップ

1. この設計書のレビューと承認
2. フェーズ1から順次実装
3. 各フェーズでの動作確認
