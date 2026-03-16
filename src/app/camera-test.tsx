import { useState } from "react";
import { Alert, Pressable, ScrollView, StyleSheet, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import {
  BarcodeScanningResult,
  CameraView,
  useCameraPermissions,
} from "../../modules/expo-camera";

import { ThemedText } from "@/components/themed-text";
import { ThemedView } from "@/components/themed-view";
import { Spacing } from "@/constants/theme";

type LogEntry = {
  type: "info" | "success" | "error" | "warning";
  message: string;
  timestamp: Date;
};

export default function CameraTestScreen() {
  const [cameraEnabled, setCameraEnabled] = useState(false);
  const [logs, setLogs] = useState<LogEntry[]>([]);
  const [permission, requestPermission] = useCameraPermissions();

  const addLog = (
    type: "info" | "success" | "error" | "warning",
    message: string,
  ) => {
    console.log(`[CameraTest][${type}] ${message}`);
    setLogs((prev) => [
      {
        type,
        message,
        timestamp: new Date(),
      },
      ...prev,
    ]);
  };

  const handleCameraReady = () => {
    addLog("success", "カメラが準備完了しました");
  };

  const handleBarcodeScanned = (result: BarcodeScanningResult) => {
    addLog("info", `QRコード検出: ${result.type} - ${result.data}`);
  };

  const toggleCamera = async () => {
    if (!cameraEnabled) {
      // カメラを有効化する前にパーミッションを確認
      if (!permission) {
        addLog("info", "パーミッション情報を読み込んでいます...");
        return;
      }

      if (!permission.granted) {
        addLog("info", "カメラパーミッションを要求しています...");
        const result = await requestPermission();
        if (!result.granted) {
          addLog("warning", "カメラパーミッションが拒否されました");
          Alert.alert(
            "パーミッション必要",
            "カメラを使用するにはカメラパーミッションを許可してください。",
          );
          return;
        }
        addLog("success", "カメラパーミッションが許可されました");
      }

      setCameraEnabled(true);
      addLog("info", "カメラを有効にしました");
    } else {
      setCameraEnabled(false);
      addLog("info", "カメラを無効にしました");
    }
  };

  const clearLogs = () => {
    setLogs([]);
  };

  const getLogColor = (type: LogEntry["type"]) => {
    switch (type) {
      case "success":
        return "#10b981";
      case "error":
        return "#ef4444";
      case "warning":
        return "#f59e0b";
      default:
        return "#6b7280";
    }
  };

  const formatTime = (date: Date) => {
    return date.toLocaleTimeString("ja-JP", {
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
    });
  };

  return (
    <ThemedView style={styles.container}>
      <SafeAreaView style={styles.safeArea}>
        <View style={styles.content}>
          {/* ヘッダー */}
          <ThemedView style={styles.header}>
            <ThemedText type="title" style={styles.title}>
              Camera Test
            </ThemedText>
            <ThemedText type="small" style={styles.subtitle}>
              カメラプレビュー動作確認
            </ThemedText>
          </ThemedView>

          {/* カメラプレビューエリア */}
          <ThemedView type="backgroundElement" style={styles.cameraContainer}>
            {cameraEnabled ? (
              <CameraView
                facing="back"
                onCameraReady={handleCameraReady}
                onBarcodeScanned={handleBarcodeScanned}
                barcodeScannerSettings={{
                  barcodeTypes: ["qr"],
                }}
                style={styles.camera}
              />
            ) : (
              <ThemedView style={styles.cameraPlaceholder}>
                <ThemedText style={styles.placeholderText}>
                  カメラが無効です
                </ThemedText>
                <ThemedText type="small" style={styles.placeholderSubtext}>
                  下のボタンでカメラを有効にしてください
                </ThemedText>
              </ThemedView>
            )}
          </ThemedView>

          {/* コントロールボタン */}
          <ThemedView style={styles.controls}>
            <Pressable
              style={({ pressed }) => [
                styles.button,
                cameraEnabled ? styles.dangerButton : styles.primaryButton,
                pressed && styles.buttonPressed,
              ]}
              onPress={toggleCamera}
            >
              <ThemedText style={styles.buttonText}>
                {cameraEnabled ? "カメラを停止" : "カメラを起動"}
              </ThemedText>
            </Pressable>
          </ThemedView>

          {/* ログエリア */}
          <ThemedView type="backgroundElement" style={styles.logsContainer}>
            <View style={styles.logsHeader}>
              <ThemedText type="subtitle" style={styles.logsTitle}>
                ログ
              </ThemedText>
              <Pressable onPress={clearLogs}>
                <ThemedText type="link" style={styles.clearButton}>
                  クリア
                </ThemedText>
              </Pressable>
            </View>

            <ScrollView style={styles.logsList}>
              {logs.length === 0 ? (
                <ThemedText type="small" style={styles.emptyLogs}>
                  まだログがありません
                </ThemedText>
              ) : (
                logs.map((log, index) => (
                  <View key={index} style={styles.logEntry}>
                    <View style={styles.logHeader}>
                      <View
                        style={[
                          styles.logBadge,
                          { backgroundColor: getLogColor(log.type) },
                        ]}
                      >
                        <ThemedText style={styles.logBadgeText}>
                          {log.type.toUpperCase()}
                        </ThemedText>
                      </View>
                      <ThemedText type="small" style={styles.logTime}>
                        {formatTime(log.timestamp)}
                      </ThemedText>
                    </View>
                    <ThemedText style={styles.logMessage}>
                      {log.message}
                    </ThemedText>
                  </View>
                ))
              )}
            </ScrollView>
          </ThemedView>
        </View>
      </SafeAreaView>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  safeArea: {
    flex: 1,
  },
  content: {
    flex: 1,
    padding: Spacing.three,
    gap: Spacing.three,
  },
  header: {
    gap: Spacing.one,
  },
  title: {
    fontSize: 28,
    fontWeight: "bold",
  },
  subtitle: {
    opacity: 0.7,
  },
  cameraContainer: {
    height: 300,
    borderRadius: 12,
    overflow: "hidden",
  },
  camera: {
    flex: 1,
  },
  cameraPlaceholder: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
    gap: Spacing.two,
  },
  placeholderText: {
    fontSize: 18,
    fontWeight: "600",
  },
  placeholderSubtext: {
    opacity: 0.6,
  },
  controls: {
    flexDirection: "row",
    gap: Spacing.two,
  },
  button: {
    flex: 1,
    paddingVertical: Spacing.three,
    paddingHorizontal: Spacing.four,
    borderRadius: 8,
    alignItems: "center",
    justifyContent: "center",
  },
  primaryButton: {
    backgroundColor: "#3b82f6",
  },
  dangerButton: {
    backgroundColor: "#ef4444",
  },
  buttonPressed: {
    opacity: 0.7,
  },
  buttonText: {
    color: "#ffffff",
    fontSize: 16,
    fontWeight: "600",
  },
  logsContainer: {
    flex: 1,
    borderRadius: 12,
    padding: Spacing.three,
  },
  logsHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: Spacing.two,
  },
  logsTitle: {
    fontSize: 18,
    fontWeight: "600",
  },
  clearButton: {
    fontSize: 14,
  },
  logsList: {
    flex: 1,
  },
  emptyLogs: {
    textAlign: "center",
    opacity: 0.5,
    marginTop: Spacing.four,
  },
  logEntry: {
    marginBottom: Spacing.three,
    paddingBottom: Spacing.three,
    borderBottomWidth: 1,
    borderBottomColor: "rgba(128, 128, 128, 0.2)",
  },
  logHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: Spacing.one,
  },
  logBadge: {
    paddingHorizontal: Spacing.two,
    paddingVertical: 2,
    borderRadius: 4,
  },
  logBadgeText: {
    color: "#ffffff",
    fontSize: 10,
    fontWeight: "700",
  },
  logTime: {
    opacity: 0.6,
  },
  logMessage: {
    fontSize: 14,
  },
});
