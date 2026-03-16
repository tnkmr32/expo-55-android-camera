# カメラプレビュー機能の設計書

## 1. 要件の確認

### 機能要件

#### 1.1 カメラアクセス

- Android CameraXライブラリを使用してカメラへのアクセスを実装する
- デフォルトでは背面カメラを使用する

#### 1.2 パーミッション処理

- カメラパーミッション（CAMERA）の要求機能を実装する
- パーミッションの許可/拒否状態を検知する
- パーミッション拒否時のエラーハンドリングを実装する
- React Native側にパーミッション状態を通知する

#### 1.3 カメラプレビュー

- ネイティブビューとしてカメラプレビューを実装する
- Expo Module APIのViewManagerを使用する
- プレビューのアスペクト比を適切に処理する
- プレビューの表示/非表示を制御する

#### 1.4 ライフサイクル管理

- Activityのライフサイクル（onResume/onPause/onDestroy）に応じてカメラを制御する
- アプリがバックグラウンドに移行した際にカメラを停止する
- アプリがフォアグラウンドに復帰した際にカメラを再開する
- メモリリークを防ぐための適切なリソース解放を実装する

#### 1.5 React Nativeからの制御

- Props経由でカメラの設定を受け取る
  - `enabled`: カメラの有効/無効
- カメラの起動・停止をReact Native側から制御できる
- エラーイベントをReact Native側に送信する

#### 1.6 エラーハンドリング

- カメラ初期化エラーの検知と通知
- パーミッションエラーの検知と通知
- カメラ使用中のエラーの検知と通知
- エラーメッセージをReact Native側に送信する

### 非機能要件

#### 1.7 パフォーマンス

- カメラの起動時間を最小化する
- プレビューのフレームレートを適切に維持する
- 不要なリソース消費を防ぐ

#### 1.8 互換性

- Android API Level 24以上をサポートする
- CameraX 1.3.x以上を使用する

## 2. 影響範囲の特定

### 2.1 Androidネイティブコード

#### 追加されるファイル

- `modules/expo-qrcode/android/src/main/java/expo/modules/qrcode/CameraView.kt`
  - カメラプレビューを表示するViewコンポーネント
  - CameraXの初期化と制御
  - ライフサイクルの管理
- `modules/expo-qrcode/android/src/main/java/expo/modules/qrcode/CameraManager.kt`
  - カメラの起動・停止ロジック
  - CameraX UseCaseの管理
  - エラーハンドリング

- `modules/expo-qrcode/android/src/main/java/expo/modules/qrcode/PermissionHandler.kt`
  - パーミッションの要求と状態管理
  - パーミッション結果のコールバック処理

#### 変更されるファイル

- `modules/expo-qrcode/android/src/main/java/expo/modules/qrcode/QRCodeModule.kt`
  - CameraViewの登録
  - モジュール定義の拡張

- `modules/expo-qrcode/android/build.gradle`
  - CameraX依存関係の追加
  - AndroidX Core依存関係の追加

### 2.2 React Native / TypeScript

#### 追加されるファイル

- `modules/expo-qrcode/src/CameraView.tsx`
  - CameraViewコンポーネントのReact Nativeラッパー
  - Props型定義
  - イベントハンドラー

- `modules/expo-qrcode/src/types.ts`
  - カメラ関連の型定義
  - イベント型定義
  - エラー型定義

#### 変更されるファイル

- `modules/expo-qrcode/src/index.ts`
  - CameraViewのエクスポート
  - 型定義のエクスポート

### 2.3 Android設定ファイル

#### 変更されるファイル

- `android/app/src/main/AndroidManifest.xml`
  - CAMERAパーミッションの追加
  - カメラ機能の宣言

### 2.4 既存機能への影響

- 既存のQRCodeModule（sayHello等）には影響なし
- 既存のモジュール構造を維持しながら拡張
- 後続のQRコード読み取り機能のベースとなる

## 3. 設計の策定

### 3.1 アーキテクチャ概要

```
React Native Layer
    ↓ (Props/Events)
CameraView Component (TypeScript)
    ↓ (requireNativeViewManager)
CameraView (Kotlin) - Expo Module API View
    ↓ (使用)
CameraManager (Kotlin) - CameraX制御
    ↓ (依存)
CameraX Library
```

### 3.2 クラス設計

#### 3.2.1 CameraView.kt

**責務**:

- Expo Module APIのViewとして動作
- React NativeとCameraManagerの橋渡し
- ライフサイクルイベントの伝播

**主要メソッド**:

- `startCamera()`: カメラの起動処理を開始
- `stopCamera()`: カメラの停止処理を実行
- `onHostResume()`: アプリがフォアグラウンドに復帰時
- `onHostPause()`: アプリがバックグラウンドに移行時
- `onHostDestroy()`: ビューが破棄される時

**プロパティ**:

- `enabled: Boolean`: カメラの有効/無効状態
- `cameraManager: CameraManager`: CameraXの管理インスタンス

**イベント**:

- `onCameraReady`: カメラが起動完了時に発火
- `onCameraError`: エラー発生時に発火
- `onPermissionDenied`: パーミッション拒否時に発火

#### 3.2.2 CameraManager.kt

**責務**:

- CameraXの初期化と管理
- プレビューUseCaseの制御
- カメラセレクターの管理

**主要メソッド**:

- `bindCamera(lifecycleOwner, previewView)`: カメラをバインド
- `unbindCamera()`: カメラをアンバインド

**プロパティ**:

- `cameraProvider: ProcessCameraProvider?`: CameraXプロバイダー
- `preview: Preview?`: プレビューユースケース
- `camera: Camera?`: 現在のカメラインスタンス

**エラーハンドリング**:

- カメラ初期化失敗
- バインド失敗
- カメラ使用中のエラー

#### 3.2.3 PermissionHandler.kt

**責務**:

- カメラパーミッションの状態確認
- パーミッションリクエストの実行
- パーミッション結果のコールバック管理

**主要メソッド**:

- `hasPermission(context)`: パーミッション保有確認
- `requestPermission(activity, callback)`: パーミッション要求
- `shouldShowRationale(activity)`: 説明表示が必要か確認

**コールバック型**:

- `PermissionCallback`: パーミッション結果を受け取る関数型インターフェース

#### 3.2.4 QRCodeModule.kt（拡張）

**追加内容**:

- CameraViewの登録
- ViewManager定義

**変更点**:

- `View(CameraView::class)` の追加
- CameraViewのPropsとイベントの定義

### 3.3 データフロー

#### 3.3.1 カメラ起動フロー

```
1. React Native: <CameraView enabled={true} />
2. CameraView.kt: enabledプロパティが変更される
3. CameraView.kt: startCamera()を呼び出す
4. PermissionHandler: パーミッションをチェック
5. CameraManager: bindCamera()を実行
6. CameraX: プレビューを開始
7. CameraView.kt: onCameraReadyイベントを発火
8. React Native: onCameraReadyハンドラーが呼ばれる
```

#### 3.3.2 エラー発生フロー

```
1. CameraX: エラーが発生
2. CameraManager: エラーをキャッチ
3. CameraView.kt: onCameraErrorイベントを発火
4. React Native: onCameraErrorハンドラーが呼ばれる
5. React Native: エラーメッセージを表示
```

#### 3.3.3 ライフサイクルフロー

```
[アプリがバックグラウンドへ]
1. Activity: onPause()
2. CameraView.kt: onHostPause()
3. CameraManager: unbindCamera()
4. CameraX: リソース解放

[アプリがフォアグラウンドへ]
1. Activity: onResume()
2. CameraView.kt: onHostResume()
3. CameraView.kt: startCamera() (enabledがtrueの場合)
4. CameraManager: bindCamera()
5. CameraX: プレビュー再開
```

### 3.4 Props設計

#### React Nativeコンポーネントのprops

```typescript
interface CameraViewProps {
  // カメラの有効/無効
  enabled?: boolean;

  // カメラ準備完了イベント
  onCameraReady?: () => void;

  // カメラエラーイベント
  onCameraError?: (event: { message: string; code?: string }) => void;

  // パーミッション拒否イベント
  onPermissionDenied?: () => void;

  // スタイル
  style?: ViewStyle;
}
```

### 3.5 イベント設計

#### ネイティブからReact Nativeへのイベント

```kotlin
// onCameraReady
eventDispatcher?.sendEvent(
  name = "onCameraReady",
  body = mapOf()
)

// onCameraError
eventDispatcher?.sendEvent(
  name = "onCameraError",
  body = mapOf(
    "message" to errorMessage,
    "code" to errorCode
  )
)

// onPermissionDenied
eventDispatcher?.sendEvent(
  name = "onPermissionDenied",
  body = mapOf()
)
```

### 3.6 依存関係

#### build.gradleに追加する依存関係

```groovy
dependencies {
  // 既存の依存関係
  implementation project(':expo-modules-core')
  implementation "org.jetbrains.kotlin:kotlin-stdlib-jdk8"

  // 追加する依存関係
  // CameraX core library
  implementation "androidx.camera:camera-core:1.3.1"
  implementation "androidx.camera:camera-camera2:1.3.1"
  implementation "androidx.camera:camera-lifecycle:1.3.1"
  implementation "androidx.camera:camera-view:1.3.1"

  // AndroidX Core
  implementation "androidx.core:core-ktx:1.12.0"
  implementation "androidx.lifecycle:lifecycle-runtime-ktx:2.6.2"
}
```

### 3.7 パーミッション設定

#### AndroidManifest.xmlに追加

```xml
<!-- カメラパーミッション -->
<uses-permission android:name="android.permission.CAMERA" />

<!-- カメラ機能の宣言（オプショナル） -->
<uses-feature
    android:name="android.camera"
    android:required="false" />
<uses-feature
    android:name="android.camera.front"
    android:required="false" />
```

### 3.8 エラーコード設計

```kotlin
object CameraErrorCode {
  const val PERMISSION_DENIED = "PERMISSION_DENIED"
  const val CAMERA_UNAVAILABLE = "CAMERA_UNAVAILABLE"
  const val CAMERA_INITIALIZATION_FAILED = "CAMERA_INITIALIZATION_FAILED"
  const val CAMERA_BIND_FAILED = "CAMERA_BIND_FAILED"
  const val CAMERA_ERROR = "CAMERA_ERROR"
  const val UNKNOWN_ERROR = "UNKNOWN_ERROR"
}
```

## 4. 実装の計画

### 4.1 実装順序

#### フェーズ1: 基本構造の実装

1. CameraView.ktの骨格を作成
   - Expo Module APIのView定義
   - Props定義
   - イベント定義
   - 空のメソッド実装

2. CameraManager.ktの骨格を作成
   - クラス定義
   - プロパティ定義
   - 空のメソッド実装

3. build.gradleに依存関係を追加
   - CameraX依存関係
   - AndroidX依存関係

4. AndroidManifest.xmlにパーミッションを追加
   - CAMERAパーミッション
   - カメラ機能宣言

5. QRCodeModule.ktにCameraViewを登録
   - View定義の追加

#### フェーズ2: CameraManagerの実装

1. カメラ初期化ロジックの実装
   - ProcessCameraProviderの取得
   - エラーハンドリング

2. プレビューUseCaseの実装
   - Preview生成
   - SurfaceProviderの設定

3. カメラバインドの実装
   - CameraSelectorの設定
   - bindToLifecycleの呼び出し
   - エラーハンドリング

4. カメラアンバインドの実装
   - unbindAllの呼び出し
   - リソースのクリーンアップ

#### フェーズ3: PermissionHandlerの実装

1. パーミッション状態確認の実装
   - ContextCompat.checkSelfPermissionの使用

2. パーミッション要求の実装
   - ActivityCompat.requestPermissionsの使用
   - コールバックの管理

3. Rationaleチェックの実装
   - shouldShowRequestPermissionRationaleの使用

#### フェーズ4: CameraViewの実装

1. CameraManagerの統合
   - インスタンス生成
   - メソッド呼び出し

2. Props処理の実装
   - enabledプロパティの監視
   - 変更時のカメラ制御

3. ライフサイクル処理の実装
   - onHostResumeの実装
   - onHostPauseの実装
   - onHostDestroyの実装

4. イベント発火の実装
   - onCameraReadyの発火
   - onCameraErrorの発火
   - onPermissionDeniedの発火

#### フェーズ5: React Native統合

1. TypeScript型定義の作成
   - CameraViewProps型
   - イベント型
   - エラー型

2. CameraView.tsxコンポーネントの作成
   - requireNativeViewManagerの使用
   - Propsの型付け
   - イベントハンドラーの型付け

3. index.tsの更新
   - CameraViewのエクスポート
   - 型のエクスポート

#### フェーズ6: テストとデバッグ

1. 基本動作確認
   - カメラプレビューが表示されること
   - カメラの起動・停止が動作すること

2. Props動作確認
   - enabledプロパティが機能すること

3. ライフサイクル確認
   - バックグラウンド/フォアグラウンド切り替えが正常に動作すること
   - メモリリークが発生しないこと

4. エラーハンドリング確認
   - パーミッション拒否が正しく処理されること
   - カメラエラーが正しく通知されること

5. パフォーマンス確認
   - カメラ起動時間が許容範囲内であること
   - フレームレートが適切であること

### 4.2 各フェーズの完了基準

#### フェーズ1完了基準

- [ ] ビルドエラーが発生しないこと
- [ ] 依存関係が正しく解決されていること
- [ ] パーミッション設定が追加されていること

#### フェーズ2完了基準

- [ ] CameraManagerが単体でカメラを起動できること
- [ ] カメラの起動・停止が正常に動作すること
- [ ] エラーハンドリングが実装されていること

#### フェーズ3完了基準

- [ ] パーミッションの状態確認ができること
- [ ] パーミッション要求が正常に動作すること
- [ ] パーミッション結果が正しく処理されること

#### フェーズ4完了基準

- [ ] CameraViewがレンダリングされること
- [ ] Propsが正しく処理されること
- [ ] ライフサイクルイベントが正しく処理されること
- [ ] イベントが正しく発火すること

#### フェーズ5完了基準

- [ ] React Nativeから CameraViewコンポーネントが使用できること
- [ ] TypeScriptの型チェックが通ること
- [ ] Propsが正しく型付けされていること

#### フェーズ6完了基準

- [ ] すべての基本機能が正常に動作すること
- [ ] エラーが適切にハンドリングされること
- [ ] メモリリークが発生しないこと
- [ ] パフォーマンスが許容範囲内であること

### 4.3 リスクと対策

#### リスク1: CameraX APIの互換性問題

**リスク内容**: 特定のAndroidデバイスでCameraXが正常に動作しない
**対策**:

- 最低API Levelを24に設定
- CameraXの安定版（1.3.x）を使用
- エラーハンドリングを充実させ、問題をユーザーに通知

#### リスク2: パーミッション処理の複雑さ

**リスク内容**: Android 6.0以降のランタイムパーミッションの処理が複雑
**対策**:

- PermissionHandlerクラスに処理をカプセル化
- expo-cameraの実装パターンを参考にする
- パーミッション拒否時の適切なフォールバック処理を実装

#### リスク3: ライフサイクル管理の複雑さ

**リスク内容**: アプリのライフサイクルとカメラの状態を同期させるのが難しい
**対策**:

- CameraXのlifecycle-awareな機能を活用
- 状態管理を明確にする
- リソース解放を確実に行う

#### リスク4: メモリリーク

**リスク内容**: カメラリソースの解放漏れによるメモリリーク
**対策**:

- onHostDestroyで確実にリソースを解放
- WeakReferenceの活用を検討
- Android Profilerでメモリ使用量を監視

### 4.4 次のステップへの準備

この設計により、次のステップ「QRコードの単独読み取り」に向けて以下の基盤が整います：

1. **カメラプレビューの安定動作**
   - QRコード読み取りの前提条件

2. **CameraManagerの拡張可能な設計**
   - ImageAnalysis UseCaseを追加してQRコード解析を実装可能

3. **イベントシステムの確立**
   - QRコード検出イベントを追加できる構造

4. **エラーハンドリングの基盤**
   - QRコード読み取りエラーにも対応可能

5. **ライフサイクル管理の確立**
   - 連続スキャンでも安定動作する基盤

## 5. 補足事項

### 5.1 expo-cameraとの違い

本実装は学習・カスタマイズ目的のため、expo-cameraをそのまま使用しない方針です。ただし、以下の点でexpo-cameraの実装パターンを参考にします：

- ViewManagerの実装パターン
- ライフサイクル管理の方法
- パーミッション処理の方法

### 5.2 将来の拡張性

この設計はQRコード読み取り機能の実装を想定した構造となっています：

- **QRコード読み取り機能**
  - ImageAnalysis UseCaseの追加
  - ML Kit Barcode Scanningの統合
  - 検出イベントの追加
  - 連続スキャン機能の実装

### 5.3 参考リソース

- [CameraX公式ドキュメント](https://developer.android.com/training/camerax)
- [Expo Module API](https://docs.expo.dev/modules/overview/)
- [expo-camera実装](https://github.com/expo/expo/tree/main/packages/expo-camera)
- [Android Runtime Permissions](https://developer.android.com/training/permissions/requesting)
