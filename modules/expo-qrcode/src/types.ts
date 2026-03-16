/**
 * カメラ関連の型定義
 */

import { ViewStyle } from "react-native";

/**
 * カメラエラー情報
 */
export interface CameraError {
  code: string;
  message: string;
}

/**
 * CameraViewコンポーネントのProps
 */
export interface CameraViewProps {
  /**
   * カメラの有効/無効
   * @default false
   */
  enabled?: boolean;

  /**
   * カメラ準備完了イベント
   */
  onCameraReady?: () => void;

  /**
   * カメラエラーイベント
   */
  onCameraError?: (event: { nativeEvent: CameraError }) => void;

  /**
   * パーミッション拒否イベント
   */
  onPermissionDenied?: () => void;

  /**
   * スタイル
   */
  style?: ViewStyle;
}

/**
 * カメラエラーコード
 */
export const CameraErrorCode = {
  PERMISSION_DENIED: "PERMISSION_DENIED",
  CAMERA_UNAVAILABLE: "CAMERA_UNAVAILABLE",
  CAMERA_INITIALIZATION_FAILED: "CAMERA_INITIALIZATION_FAILED",
  CAMERA_BIND_FAILED: "CAMERA_BIND_FAILED",
  CAMERA_ERROR: "CAMERA_ERROR",
  UNKNOWN_ERROR: "UNKNOWN_ERROR",
} as const;

export type CameraErrorCodeType =
  (typeof CameraErrorCode)[keyof typeof CameraErrorCode];
