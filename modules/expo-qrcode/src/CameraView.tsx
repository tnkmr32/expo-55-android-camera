import { requireNativeViewManager } from "expo-modules-core";
import React from "react";
import { StyleSheet } from "react-native";
import type { CameraViewProps } from "./types";

/**
 * ネイティブCameraViewを要求
 */
const NativeCameraView: React.ComponentType<CameraViewProps> =
  requireNativeViewManager("ExpoQRCode");

/**
 * カメラプレビューを表示するコンポーネント
 *
 * @example
 * ```tsx
 * <CameraView
 *   enabled={true}
 *   onCameraReady={() => console.log('Camera ready')}
 *   onCameraError={(event) => console.error('Camera error:', event.nativeEvent)}
 *   onPermissionDenied={() => console.warn('Permission denied')}
 *   style={{ flex: 1 }}
 * />
 * ```
 */
export default function CameraView(props: CameraViewProps) {
  const {
    enabled = false,
    onCameraReady,
    onCameraError,
    onPermissionDenied,
    style,
  } = props;

  return (
    <NativeCameraView
      enabled={enabled}
      onCameraReady={onCameraReady}
      onCameraError={onCameraError}
      onPermissionDenied={onPermissionDenied}
      style={StyleSheet.flatten([styles.container, style])}
    />
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
});
