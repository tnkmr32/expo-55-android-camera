# Expo Module APIを使用したAndroidネイティブコードの動作確認

## 1. 要件の確認

### 機能概要

Expo Module APIを使用してAndroidネイティブコードを実装し、React Native側から呼び出せることを確認する基本モジュールを作成します。

### 達成すべきゴール

1. Expo Module APIを使用したAndroidネイティブコードの実装
2. Androidネイティブコードの正常動作
3. React Native側からのAndroidネイティブコードの呼び出し
4. React Native側でのAndroidネイティブコードの結果受け取り
5. React Native側でのAndroidネイティブコードのエラー受け取り

### 動作要件

- シンプルなメソッド呼び出し（同期・非同期）
- 成功時のレスポンス返却
- エラー時の例外処理とエラー情報の伝達
- TypeScriptの型定義サポート

## 2. 影響範囲の特定

### 新規作成が必要なファイル

#### ローカルExpoモジュールの作成

Expo Module APIを使用する場合、プロジェクトルートに`modules/`フォルダを作成し、その配下にローカルモジュールを配置します。

##### モジュール設定ファイル

- `modules/expo-qrcode/expo-module.config.json`
  - モジュール名、プラットフォーム設定
  - autolinkingの設定

- `modules/expo-qrcode/package.json`（オプション）
  - モジュールのメタデータ

##### Androidネイティブコード

- `modules/expo-qrcode/android/src/main/java/expo/modules/qrcode/QRCodeModule.kt`
  - Expo Module定義
  - サンプルメソッド実装
  - @Module アノテーションによるモジュール宣言

##### iOSネイティブコード（将来対応）

- `modules/expo-qrcode/ios/QRCodeModule.swift`
  - iOSモジュール実装（後回し可）

##### TypeScript/React Native側

- `modules/expo-qrcode/src/index.ts`
  - ネイティブモジュールのインポート
  - TypeScript型定義
  - エクスポート関数

- `modules/expo-qrcode/index.ts`
  - モジュールのエントリーポイント
  - src/index.ts の再エクスポート

##### テスト画面

- `src/app/test-module.tsx`
  - モジュール動作確認用画面
  - 成功・エラーケースのテスト

### 変更が必要な既存ファイル

#### package.json（確認のみ）

- `package.json`
  - expoパッケージがインストールされていることを確認（既存）
  - expo-modules-coreとexpo-modules-autolinkingは自動的に含まれる

#### ビルド設定（確認のみ）

- `android/build.gradle`
  - `apply plugin: "expo-root-project"` の確認（既存）
- `android/app/build.gradle`
  - `autolinkLibrariesWithApp()` の確認（既存）
  - Kotlinサポートの確認（既存）

**注意**: 上記の設定は既にプロジェクトに含まれているため、追加設定は不要です。Expo Module APIのautolinking機能が、`modules/`フォルダ内のモジュールを自動的に検出して登録します。

#### ルーティング（オプション）

- `src/app/_layout.tsx`
  - テスト画面へのナビゲーション追加（必要に応じて）

### 影響を受けないコンポーネント

- 既存の画面コンポーネント（explore.tsx, index.tsx）
- UI関連コンポーネント（components/ui/）
- テーマ設定（constants/theme.ts）

## 3. 設計の策定

### アーキテクチャ概要

```
React Native Layer (TypeScript)
    ↓ (呼び出し: modules/expo-qrcode/src/index.ts)
Expo Modules API Bridge (autolinking)
    ↓
Android Native Layer (Kotlin)
    ↓ (結果返却)
React Native Layer (TypeScript)
```

**フォルダ構成**:

```
project-root/
├── modules/
│   └── expo-qrcode/              # ローカルExpoモジュール
│       ├── android/
│       │   └── src/
│       │       └── main/
│       │           └── java/
│       │               └── expo/
│       │                   └── modules/
│       │                       └── qrcode/
│       │                           └── QRCodeModule.kt
│       ├── ios/                  # (将来対応)
│       ├── src/
│       │   └── index.ts          # TypeScript実装
│       ├── index.ts               # エントリーポイント
│       └── expo-module.config.json
├── src/
│   └── app/
│       └── test-module.tsx       # テスト画面
├── android/
├── ios/
└── package.json
```

**autolinkingの動作**:

1. `expo-modules-autolinking`が`modules/`フォルダをスキャン
2. `expo-module.config.json`を持つモジュールを検出
3. ビルド時に自動的にネイティブコードをリンク
4. React Native側から`NativeModules`経由でアクセス可能

### クラス設計

#### QRCodeModule.kt (modules/expo-qrcode/android/src/main/java/expo/modules/qrcode/QRCodeModule.kt)

```kotlin
package expo.modules.qrcode

import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class QRCodeModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("ExpoQRCode")  // React Native側でのモジュール名

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
```

**重要なポイント**:

- `Module()`を継承
- `definition()`メソッドで機能を定義
- `Name()`でReact Native側から参照する名前を指定
- `AsyncFunction`は非同期メソッド（Promise返却）
- `Function`は同期メソッド
- 例外をスローするとReact Native側でPromise rejectされる

#### QRCodeModule.ts (modules/expo-qrcode/src/index.ts)

```typescript
import { NativeModulesProxy } from "expo-modules-core";

// ネイティブモジュールの型定義
export interface QRCodeModuleType {
  sayHello(name: string): Promise<string>;
  getModuleInfo(): { version: string; buildDate: string; platform: string };
  simulateError(): Promise<void>;
}

// ネイティブモジュールの取得
const ExpoQRCode: QRCodeModuleType = NativeModulesProxy.ExpoQRCode;

// エクスポート
export default ExpoQRCode;
```

**重要なポイント**:

- `expo-modules-core`の`NativeModulesProxy`を使用
- モジュール名は Kotlin側の `Name()` と一致させる
- TypeScript型定義でIDE補完を有効化

### データフロー

#### 正常系フロー

1. React NativeコンポーネントからExpoQRCode.sayHello(name)を呼び出し
2. `expo-modules-core`の`NativeModulesProxy`を経由してKotlinのQRCodeModuleに到達
3. Kotlinの`AsyncFunction("sayHello")`が実行される
4. 結果が自動的にPromiseとしてwrapされてReact Native側に返却
5. UIに結果を表示

#### エラーハンドリングフロー

1. React NativeコンポーネントからExpoQRCode.simulateError()を呼び出し
2. Kotlinメソッドで例外をスロー
3. Expo Modules APIがエラーをキャッチしてPromiseをreject
4. React Native側のcatch句でエラーを受け取る
5. エラーメッセージをUIに表示

### Expo Modules APIの主要機能

#### Module定義

```kotlin
class QRCodeModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("ExpoQRCode")  // モジュール名

    // 関数定義
    AsyncFunction("methodName") { /* ... */ }
    Function("methodName") { /* ... */ }

    // 定数定義
    Constants {
      mapOf("KEY" to "value")
    }
  }
}
```

#### 関数の種類

- `AsyncFunction`: 非同期メソッド（Promise返却）
  - Kotlin側で`suspend`不要、戻り値が自動的にPromiseに
  - 例外をスローするとPromise reject
- `Function`: 同期メソッド
  - 戻り値がそのままJavaScriptに渡る
  - 軽量な操作に使用

#### 型マッピング

Expo Modules APIは自動的に型変換を行います：

- Kotlin String → TypeScript string
- Kotlin Int/Long → TypeScript number
- Kotlin Boolean → TypeScript boolean
- Kotlin Map<String, Any> → TypeScript object
- Kotlin List → TypeScript array
- Kotlin null → TypeScript null/undefined
- Promise<T> → Promise<T>

#### autolinkingの設定

`expo-module.config.json`:

```json
{
  "platforms": ["android", "ios"],
  "android": {
    "modules": ["expo.modules.qrcode.QRCodeModule"]
  }
}
```

このファイルがあることで、`expo-modules-autolinking`が自動的にモジュールを検出し、ビルド時にリンクします。

### エラーハンドリング戦略

#### Kotlin側

- try-catchによる例外捕捉
- 意味のあるエラーメッセージの設定
- エラーコードの定義（オプション）

#### TypeScript側

- Promise.catch()によるエラー処理
- エラーメッセージのユーザー表示
- エラーロギング（将来的にSentryなど）

## 4. 実装の計画

### フェーズ1: ローカルExpoモジュールの基本構造作成

1. modules/expo-qrcode/ フォルダ構造の作成
   - `modules/expo-qrcode/`ディレクトリ作成
   - `modules/expo-qrcode/android/src/main/java/expo/modules/qrcode/`ディレクトリ作成
   - `modules/expo-qrcode/src/`ディレクトリ作成

2. expo-module.config.jsonの作成
   - モジュール名の定義
   - プラットフォーム設定（android）
   - Androidモジュールクラスの指定

3. package.json (オプション)
   - モジュールのメタデータ
   - 将来的な独立パッケージ化に備える

### フェーズ2: Androidネイティブモジュールの実装

1. QRCodeModule.ktの作成
   - Module()クラスを継承
   - definition()メソッドの実装
   - Name()でモジュール名定義
   - sayHelloメソッド実装（AsyncFunction）
   - getModuleInfoメソッド実装（Function）
   - simulateErrorメソッド実装（AsyncFunction）

### フェーズ3: TypeScript側の実装

1. modules/expo-qrcode/src/index.tsの作成
   - `expo-modules-core`から`NativeModulesProxy`をインポート
   - TypeScript型定義（QRCodeModuleTypeインターフェース）
   - ネイティブモジュールの取得とエクスポート

2. modules/expo-qrcode/index.tsの作成
   - src/index.tsの再エクスポート

### フェーズ4: テスト画面の実装

1. src/app/test-module.tsxの作成
   - `modules/expo-qrcode`をインポート
   - 基本UIレイアウト
   - sayHelloメソッドのテストボタン
   - getModuleInfoメソッドのテストボタン
   - simulateErrorメソッドのテストボタン
   - 結果表示エリア
   - エラー表示エリア

2. ルーティングの追加（必要に応じて）
   - \_layout.tsxへのルート追加

### フェーズ5: ビルドと動作確認

1. Androidプロジェクトのprebuild
   - `npm run prebuild:android`
   - autolinkingによるモジュール検出確認
   - ビルドエラーの修正

2. 実機/エミュレータでの動作確認
   - `npm run android`
   - モジュールの読み込み確認
   - 各メソッドの動作確認
   - コンソールログの確認

3. エラーハンドリングの確認
   - エラーが正しくReact Native側に伝わるか
   - エラーメッセージが適切か

### フェーズ6: ドキュメントとコードレビュー

1. 実装メモの作成
   - 実装内容の記録
   - autolinkingの動作確認結果
   - ハマりポイントの記録
   - 参考資料へのリンク

2. コードレビューポイント
   - エラーハンドリングの適切性
   - 型定義の正確性
   - コードの可読性
   - expo-module.config.jsonの設定確認

## 参考資料

### Expo Modules API

- [Expo Modules API Documentation](https://docs.expo.dev/modules/overview/)
- [Creating Native Modules](https://docs.expo.dev/modules/native-module-tutorial/)
- [Module API Reference](https://docs.expo.dev/modules/module-api/)
- [Local Expo Modules](https://docs.expo.dev/modules/use-standalone-expo-module-in-your-project/)
- [Expo Autolinking](https://docs.expo.dev/modules/autolinking/)

### Kotlin DSL API

- [Module Definition DSL](https://docs.expo.dev/modules/module-api/#module-definition)
- [Functions](https://docs.expo.dev/modules/module-api/#functions)
- [AsyncFunctions](https://docs.expo.dev/modules/module-api/#async-functions)
- [Constants](https://docs.expo.dev/modules/module-api/#constants)

### expo-cameraの実装

- [expo-camera GitHub](https://github.com/expo/expo/tree/main/packages/expo-camera)
- [expo-camera Android Implementation](https://github.com/expo/expo/tree/main/packages/expo-camera/android)
- Module定義の参考実装
- expo-module.config.jsonの設定例
- パッケージ構成の参考

### フォルダ構成の参考

Expo SDK内の各モジュールは以下のような構成になっています：

```
expo/packages/expo-module-name/
├── android/
│   └── src/
│       └── main/
│           └── java/
│               └── expo/
│                   └── modules/
│                       └── modulename/
├── ios/
├── src/
│   └── index.ts
├── expo-module.config.json
└── package.json
```

このプロジェクトでも同様の構成を`modules/expo-qrcode/`として作成します。

## 成功基準

### 機能面

- [ ] sayHelloメソッドが正しく動作し、文字列を返す
- [ ] getModuleInfoメソッドがモジュール情報を返す
- [ ] simulateErrorメソッドがエラーをReact Native側に伝える
- [ ] TypeScriptの型補完が効く

### 品質面

- [ ] ビルドエラーがない
- [ ] 実行時エラーがない
- [ ] エラーメッセージが適切
- [ ] コードが読みやすく保守しやすい

### ドキュメント面

- [ ] 実装内容が記録されている
- [ ] 次のステップ（カメラ実装）への移行が明確

## 次のステップへの準備

このモジュールが正常に動作することを確認できたら、次のステップ「AndroidネイティブコードでカメラAPIを使用してカメラのプレビューを表示する」に進みます。

この基礎実装で学んだ以下の知識を活用：

- Expo Modules APIの基本的な使い方
- TypeScriptとKotlinの型マッピング
- エラーハンドリングのパターン
- ビルドとデプロイの流れ
