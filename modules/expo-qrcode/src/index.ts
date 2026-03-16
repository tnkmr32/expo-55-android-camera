import { requireNativeModule } from 'expo-modules-core';

// ネイティブモジュールの型定義
export interface QRCodeModuleType {
  sayHello(name: string): Promise<string>;
  getModuleInfo(): { version: string; buildDate: string; platform: string };
  simulateError(): Promise<void>;
}

// ネイティブモジュールの取得
const ExpoQRCode: QRCodeModuleType = requireNativeModule('ExpoQRCode');

// エクスポート
export default ExpoQRCode;
