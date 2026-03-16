import { requireNativeModule } from "expo-modules-core";

// ネイティブモジュールの型定義
export interface QRCodeModuleType {
  sayHello(name: string): Promise<string>;
  getModuleInfo(): { version: string; buildDate: string; platform: string };
  simulateError(): Promise<void>;
}

// ネイティブモジュールの取得
const ExpoQRCode: QRCodeModuleType = requireNativeModule("ExpoQRCode");

// カメラビューとカメラ関連の型をエクスポート
export { default as CameraView } from "./CameraView";
export { CameraErrorCode } from "./types";
export type {
  CameraError,
  CameraErrorCodeType,
  CameraViewProps,
} from "./types";

// エクスポート
export default ExpoQRCode;
