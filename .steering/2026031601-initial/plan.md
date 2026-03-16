expo module apiを使用してAndroidネイティブコードでのカメラAPIを使用してQRコードを連続読みする実装をしたい。

## 参考資料

- [expo-camera Android実装](https://github.com/expo/expo/tree/main/packages/expo-camera/android)
  - expo module apiの使用方法
  - カメラAPIの実装パターン
  - QRコード読み取りの実装方法
  - ネイティブビューの実装方法

## ステップ・バイ・ステップで進める

1. expo module apiを使用してAndroidネイティブコードが動作することを確認する
2. AndroidネイティブコードでカメラAPIを使用してカメラのプレビューを表示する
3. カメラのプレビューからQRコードを単独で読み取る実装をする
4. カメラのプレビューからQRコードを連続で読み取る実装をする

## expo module apiを使用してAndroidネイティブコードが動作することを確認する

### ゴール

- expo module apiを使用してAndroidネイティブコードが実装されていること
- Androidネイティブコードが動作していること
- React Native側からAndroidネイティブコードが呼び出せること
- React Native側からAndroidネイティブコードの結果が受け取れること
- AndroidネイティブコードのエラーがReact Native側で受け取れること

### 参考: expo-cameraの実装

- モジュールの定義方法（Module、View、EventEmitter等）
- build.gradleの設定
- パッケージ構成

## AndroidネイティブコードでカメラAPIを使用してカメラのプレビューを表示する

### ゴール

- Android CameraXを使用してカメラへのアクセスが実装されていること
- カメラパーミッションの要求と処理が実装されていること
- カメラプレビューがネイティブビューとして実装されていること
- React Native側からカメラプレビューコンポーネントが使用できること
- カメラの起動・停止がReact Native側から制御できること
- カメラプレビューのエラーがReact Native側で受け取れること

### 参考: expo-cameraの実装

- CameraViewの実装パターン
- パーミッション処理の実装方法
- ライフサイクル管理（onResume/onPause等）
- Propsの受け渡し方法

## カメラのプレビューからQRコードを単独で読み取る実装をする

### ゴール

- QRコード読み取りライブラリ（MLKit等）が導入されていること
- カメラプレビューからQRコードを検出できること
- 検出したQRコードのデータがReact Native側に渡されること
- QRコード読み取り開始・停止がReact Native側から制御できること
- QRコード読み取りのエラーがReact Native側で受け取れること
- 読み取り成功時のフィードバックが実装されていること

### 参考: expo-cameraの実装

- BarCodeScannerの実装方法
- ML Kit Barcode Scanningの統合
- スキャン結果のイベント送信
- barCodeTypesの設定方法

## カメラのプレビューからQRコードを連続で読み取る実装をする

### ゴール

- QRコードの連続スキャンが実装されていること
- スキャン結果がReact Native側にストリーミングで送信されること
- 同一QRコードの重複検出が制御できること（デバウンス処理）
- 連続スキャンのパフォーマンスが最適化されていること
- スキャン設定（感度、頻度等）がReact Native側から調整できること
- メモリリークが発生しないこと

### 参考: expo-cameraの実装

- 連続スキャンのイベント処理
- フレーム解析のスロットリング処理
- メモリ管理とリソース解放
- barCodeScannerSettingsの実装
