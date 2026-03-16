import { useState } from "react";
import {
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  View,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

import { ThemedText } from "@/components/themed-text";
import { ThemedView } from "@/components/themed-view";
import { BottomTabInset, MaxContentWidth, Spacing } from "@/constants/theme";

import ExpoQRCode from "@/../modules/expo-qrcode";

type TestResult = {
  type: "success" | "error";
  message: string;
  timestamp: Date;
};

export default function TestModuleScreen() {
  const [name, setName] = useState("Expo User");
  const [results, setResults] = useState<TestResult[]>([]);

  const addResult = (type: "success" | "error", message: string) => {
    setResults((prev) => [
      {
        type,
        message,
        timestamp: new Date(),
      },
      ...prev,
    ]);
  };

  const handleSayHello = async () => {
    try {
      const result = await ExpoQRCode.sayHello(name);
      addResult("success", `sayHello: ${result}`);
    } catch (error) {
      addResult("error", `sayHello failed: ${error}`);
    }
  };

  const handleGetModuleInfo = () => {
    try {
      const info = ExpoQRCode.getModuleInfo();
      addResult("success", `getModuleInfo: ${JSON.stringify(info, null, 2)}`);
    } catch (error) {
      addResult("error", `getModuleInfo failed: ${error}`);
    }
  };

  const handleSimulateError = async () => {
    try {
      await ExpoQRCode.simulateError();
      addResult("error", "simulateError: Expected error but got success!");
    } catch (error) {
      addResult(
        "success",
        `simulateError: Caught error as expected - ${error}`,
      );
    }
  };

  const clearResults = () => {
    setResults([]);
  };

  return (
    <ThemedView style={styles.container}>
      <SafeAreaView style={styles.safeArea}>
        <ScrollView
          style={styles.scrollView}
          contentContainerStyle={styles.scrollContent}
        >
          <ThemedView style={styles.header}>
            <ThemedText type="title" style={styles.title}>
              Module Test
            </ThemedText>
            <ThemedText type="small" style={styles.subtitle}>
              Expo Module API動作確認
            </ThemedText>
          </ThemedView>

          <ThemedView type="backgroundElement" style={styles.section}>
            <ThemedText type="subtitle" style={styles.sectionTitle}>
              テストメソッド
            </ThemedText>

            <View style={styles.buttonContainer}>
              <Pressable
                style={({ pressed }) => [
                  styles.button,
                  styles.primaryButton,
                  pressed && styles.buttonPressed,
                ]}
                onPress={handleSayHello}
              >
                <ThemedText style={styles.buttonText}>
                  sayHello (非同期)
                </ThemedText>
              </Pressable>

              <Pressable
                style={({ pressed }) => [
                  styles.button,
                  styles.secondaryButton,
                  pressed && styles.buttonPressed,
                ]}
                onPress={handleGetModuleInfo}
              >
                <ThemedText style={styles.buttonText}>
                  getModuleInfo (同期)
                </ThemedText>
              </Pressable>

              <Pressable
                style={({ pressed }) => [
                  styles.button,
                  styles.errorButton,
                  pressed && styles.buttonPressed,
                ]}
                onPress={handleSimulateError}
              >
                <ThemedText style={styles.buttonText}>
                  simulateError (エラー)
                </ThemedText>
              </Pressable>

              <Pressable
                style={({ pressed }) => [
                  styles.button,
                  styles.clearButton,
                  pressed && styles.buttonPressed,
                ]}
                onPress={clearResults}
              >
                <ThemedText style={styles.buttonText}>結果をクリア</ThemedText>
              </Pressable>
            </View>
          </ThemedView>

          <ThemedView type="backgroundElement" style={styles.section}>
            <ThemedText type="subtitle" style={styles.sectionTitle}>
              実行結果 ({results.length})
            </ThemedText>

            {results.length === 0 ? (
              <ThemedText type="small" style={styles.emptyText}>
                テストボタンを押して動作を確認してください
              </ThemedText>
            ) : (
              <View style={styles.resultsContainer}>
                {results.map((result, index) => (
                  <View
                    key={`${result.timestamp.getTime()}-${index}`}
                    style={[
                      styles.resultItem,
                      result.type === "success"
                        ? styles.resultSuccess
                        : styles.resultError,
                    ]}
                  >
                    <View style={styles.resultHeader}>
                      <ThemedText
                        style={[
                          styles.resultType,
                          result.type === "success"
                            ? styles.resultTypeSuccess
                            : styles.resultTypeError,
                        ]}
                      >
                        {result.type === "success" ? "✓ SUCCESS" : "✗ ERROR"}
                      </ThemedText>
                      <ThemedText type="small" style={styles.resultTime}>
                        {result.timestamp.toLocaleTimeString()}
                      </ThemedText>
                    </View>
                    <ThemedText type="code" style={styles.resultMessage}>
                      {result.message}
                    </ThemedText>
                  </View>
                ))}
              </View>
            )}
          </ThemedView>

          <ThemedView type="backgroundElement" style={styles.section}>
            <ThemedText type="subtitle" style={styles.sectionTitle}>
              プラットフォーム情報
            </ThemedText>
            <ThemedText type="small">Platform: {Platform.OS}</ThemedText>
            <ThemedText type="small">Version: {Platform.Version}</ThemedText>
          </ThemedView>
        </ScrollView>
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
    paddingBottom: BottomTabInset,
  },
  scrollView: {
    flex: 1,
  },
  scrollContent: {
    paddingHorizontal: Spacing.four,
    paddingVertical: Spacing.five,
    maxWidth: MaxContentWidth,
    alignSelf: "center",
    width: "100%",
  },
  header: {
    marginBottom: Spacing.five,
    alignItems: "center",
  },
  title: {
    marginBottom: Spacing.one,
  },
  subtitle: {
    opacity: 0.7,
  },
  section: {
    marginBottom: Spacing.four,
    padding: Spacing.four,
    borderRadius: 12,
  },
  sectionTitle: {
    marginBottom: Spacing.three,
  },
  buttonContainer: {
    gap: Spacing.three,
  },
  button: {
    padding: Spacing.three,
    borderRadius: 8,
    alignItems: "center",
  },
  buttonPressed: {
    opacity: 0.7,
  },
  primaryButton: {
    backgroundColor: "#007AFF",
  },
  secondaryButton: {
    backgroundColor: "#34C759",
  },
  errorButton: {
    backgroundColor: "#FF3B30",
  },
  clearButton: {
    backgroundColor: "#8E8E93",
  },
  buttonText: {
    color: "#FFFFFF",
    fontSize: 16,
    fontWeight: "600",
  },
  resultsContainer: {
    gap: Spacing.two,
  },
  resultItem: {
    padding: Spacing.three,
    borderRadius: 8,
    borderWidth: 1,
  },
  resultSuccess: {
    backgroundColor: "#E8F5E9",
    borderColor: "#4CAF50",
  },
  resultError: {
    backgroundColor: "#FFEBEE",
    borderColor: "#F44336",
  },
  resultHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: Spacing.one,
  },
  resultType: {
    fontSize: 12,
    fontWeight: "700",
  },
  resultTypeSuccess: {
    color: "#2E7D32",
  },
  resultTypeError: {
    color: "#C62828",
  },
  resultTime: {
    opacity: 0.6,
  },
  resultMessage: {
    fontSize: 12,
    color: "#000000",
  },
  emptyText: {
    textAlign: "center",
    opacity: 0.5,
    paddingVertical: Spacing.four,
  },
});
