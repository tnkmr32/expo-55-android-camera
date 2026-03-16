#!/bin/bash

# パフォーマンスログ解析スクリプト
# 使用方法: ./analyze_performance.sh android-log.txt

LOG_FILE="${1:-.steering/2026031605-performance-measurement/android-log.txt}"

if [ ! -f "$LOG_FILE" ]; then
  echo "Error: Log file not found: $LOG_FILE"
  exit 1
fi

echo "=== Camera Performance Analysis ==="
echo "Log file: $LOG_FILE"
echo ""

# 最後のタイムスタンプを取得して15秒前を計算
LAST_TIMESTAMP=$(grep "CameraPerf" "$LOG_FILE" | tail -1 | awk '{print $2}')
if [ -z "$LAST_TIMESTAMP" ]; then
  echo "Error: No timestamps found in log file"
  exit 1
fi

# タイムスタンプをHH:MM:SS.mmmから秒に変換する関数
timestamp_to_seconds() {
  local ts=$1
  local hours=$(echo $ts | cut -d: -f1)
  local minutes=$(echo $ts | cut -d: -f2)
  local seconds=$(echo $ts | cut -d: -f3)
  
  echo "$hours * 3600 + $minutes * 60 + $seconds" | bc
}

LAST_SECONDS=$(timestamp_to_seconds "$LAST_TIMESTAMP")
CUTOFF_SECONDS=$(echo "$LAST_SECONDS - 15" | bc)

echo "Last timestamp: $LAST_TIMESTAMP (${LAST_SECONDS}s)"
echo "Cutoff time: ${CUTOFF_SECONDS}s (excluding last 15 seconds)"
echo ""

# awkスクリプトで解析（15秒前までのデータのみ）
awk -v cutoff="$CUTOFF_SECONDS" '
BEGIN {
  detected_count = 0
  no_detect_count = 0
  failed_count = 0
  total_count = 0
  excluded_count = 0
  
  # 配列の初期化
  conv_idx = 0
  analysis_idx = 0
  result_idx = 0
  total_idx = 0
  
  detected_analysis_idx = 0
  no_detect_analysis_idx = 0
}

# タイムスタンプを秒に変換
function ts_to_seconds(ts) {
  split(ts, parts, ":")
  hours = parts[1]
  minutes = parts[2]
  seconds = parts[3]
  return hours * 3600 + minutes * 60 + seconds
}

# 各行のタイムスタンプをチェック
{
  if ($0 ~ /CameraPerf/) {
    current_ts = ts_to_seconds($2)
    
    # カットオフ時刻以降は除外
    if (current_ts > cutoff) {
      in_excluded_zone = 1
      if ($0 ~ /\[PERF\] Frame/) {
        excluded_count++
      }
      next
    } else {
      in_excluded_zone = 0
    }
  }
}

# フレームヘッダーを検出してステータスを記録
/\[PERF\] Frame/ && !in_excluded_zone {
  total_count++
  if ($0 ~ /\[DETECTED\]/) {
    current_status = "DETECTED"
    detected_count++
  } else if ($0 ~ /\[NO DETECT\]/) {
    current_status = "NO_DETECT"
    no_detect_count++
  } else if ($0 ~ /\[FAILED\]/) {
    current_status = "FAILED"
    failed_count++
  }
}

# Conversionの時間を記録
/Conversion/ && !in_excluded_zone {
  if ($9 ~ /[0-9]+ms/) {
    gsub(/ms/, "", $9)
    if ($9 != "N/A" && $9 > 0) {
      conversion[conv_idx++] = $9
    }
  }
}

# Analysisの時間を記録
/Analysis/ && !in_excluded_zone {
  if ($9 ~ /[0-9]+ms/) {
    gsub(/ms/, "", $9)
    if ($9 != "N/A") {
      analysis[analysis_idx++] = $9
      
      # ステータス別に記録
      if (current_status == "DETECTED") {
        detected_analysis[detected_analysis_idx++] = $9
      } else if (current_status == "NO_DETECT") {
        no_detect_analysis[no_detect_analysis_idx++] = $9
      }
    }
  }
}

# Resultの時間を記録
/Result/ && !in_excluded_zone {
  if ($9 ~ /[0-9]+ms/) {
    gsub(/ms/, "", $9)
    if ($9 != "N/A") {
      result[result_idx++] = $9
    }
  }
}

# Totalの時間を記録
/Total/ && !in_excluded_zone {
  if ($9 ~ /[0-9]+ms/) {
    gsub(/ms/, "", $9)
    if ($9 != "N/A") {
      total[total_idx++] = $9
    }
  }
}

# 統計計算関数
function calc_stats(arr, len, name) {
  if (len == 0) {
    printf "  %s: No data\n", name
    return
  }
  
  # ソート（数値として）
  for (i = 0; i < len; i++) {
    for (j = i + 1; j < len; j++) {
      if (arr[i] + 0 > arr[j] + 0) {
        temp = arr[i]
        arr[i] = arr[j]
        arr[j] = temp
      }
    }
  }
  
  # 統計計算
  min_val = arr[0] + 0
  max_val = arr[len-1] + 0
  
  sum = 0
  for (i = 0; i < len; i++) {
    sum += arr[i]
  }
  avg = sum / len
  
  # パーセンタイル
  p50_idx = int(len * 0.50)
  p95_idx = int(len * 0.95)
  p99_idx = int(len * 0.99)
  
  p50 = arr[p50_idx] + 0
  p95 = arr[p95_idx] + 0
  p99 = arr[p99_idx] + 0
  
  printf "  %s (n=%d):\n", name, len
  printf "    Min: %dms, Max: %dms, Avg: %.1fms\n", min_val, max_val, avg
  printf "    p50: %dms, p95: %dms, p99: %dms\n", p50, p95, p99
}

END {
  print "--- Frame Count ---"
  printf "Total frames (analyzed): %d\n", total_count
  printf "Excluded frames (last 15s): %d\n", excluded_count
  printf "  Detected  : %d (%.1f%%)\n", detected_count, (total_count > 0 ? (detected_count/total_count)*100 : 0)
  printf "  No Detect : %d (%.1f%%)\n", no_detect_count, (total_count > 0 ? (no_detect_count/total_count)*100 : 0)
  printf "  Failed    : %d (%.1f%%)\n", failed_count, (total_count > 0 ? (failed_count/total_count)*100 : 0)
  print ""
  
  print "--- Overall Statistics ---"
  calc_stats(conversion, conv_idx, "Conversion")
  print ""
  calc_stats(analysis, analysis_idx, "Analysis")
  print ""
  calc_stats(result, result_idx, "Result")
  print ""
  calc_stats(total, total_idx, "Total")
  print ""
  
  print "--- Analysis by Status ---"
  calc_stats(detected_analysis, detected_analysis_idx, "Analysis (DETECTED)")
  print ""
  calc_stats(no_detect_analysis, no_detect_analysis_idx, "Analysis (NO DETECT)")
}
' "$LOG_FILE"

echo ""
echo "=== Detailed Statistics (excluding last 15s) ==="
echo ""

# 最後の15秒を除外したデータで詳細統計
TEMP_FILE=$(mktemp)
awk -v cutoff="$CUTOFF_SECONDS" '
function ts_to_seconds(ts) {
  split(ts, parts, ":")
  hours = parts[1]
  minutes = parts[2]
  seconds = parts[3]
  return hours * 3600 + minutes * 60 + seconds
}
{
  if ($0 ~ /CameraPerf/) {
    current_ts = ts_to_seconds($2)
    if (current_ts <= cutoff) {
      print $0
    }
  }
}
' "$LOG_FILE" > "$TEMP_FILE"

echo "--- Conversion Time Distribution ---"
printf "%8s  %s\n" "Count" "Time(ms)"
printf "%8s  %s\n" "-----" "--------"
grep "Conversion" "$TEMP_FILE" | awk '{gsub(/ms/, "", $9); if ($9 != "N/A") print $9}' | sort -n | uniq -c | sort -rn | head -10 | awk '{printf "%8d  %s\n", $1, $2}'

echo ""
echo "--- Analysis Time Distribution (Top 10) ---"
printf "%8s  %s\n" "Count" "Time(ms)"
printf "%8s  %s\n" "-----" "--------"
grep "Analysis" "$TEMP_FILE" | awk '{gsub(/ms/, "", $9); if ($9 != "N/A") print $9}' | sort -n | uniq -c | sort -rn | head -10 | awk '{printf "%8d  %s\n", $1, $2}'

echo ""
echo "--- Total Time Distribution (Top 10) ---"
printf "%8s  %s\n" "Count" "Time(ms)"
printf "%8s  %s\n" "-----" "--------"
grep "Total" "$TEMP_FILE" | awk '{gsub(/ms/, "", $9); if ($9 != "N/A") print $9}' | sort -n | uniq -c | sort -rn | head -10 | awk '{printf "%8d  %s\n", $1, $2}'

echo ""
echo "--- Slowest 10 Frames ---"
printf "%5s  %s\n" "Rank" "Time(ms)"
printf "%5s  %s\n" "----" "--------"
grep -A 5 "\[PERF\] Frame" "$TEMP_FILE" | grep "Total" | awk '{gsub(/ms/, "", $9); if ($9 != "N/A") print $9}' | sort -rn | head -10 | nl | awk '{printf "%5d  %s\n", $1, $2}'

# クリーンアップ
rm "$TEMP_FILE"
