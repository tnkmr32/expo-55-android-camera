package expo.modules.camera.utils

import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * パフォーマンス測定用のユーティリティクラス
 * 
 * 各処理段階のタイムスタンプを記録し、処理時間を計測してログ出力します。
 * デフォルトでは無効になっており、開発・デバッグ時のみ有効化されます。
 */
class PerformanceLogger private constructor() {
  companion object {
    private const val TAG = "CameraPerf"
    
    /**
     * パフォーマンス測定の有効/無効を制御するフラグ
     * デフォルトは無効
     */
    @Volatile
    var isEnabled: Boolean = false
    
    /**
     * 測定セッションを表すデータクラス
     * 
     * @param frameId フレームの一意な識別子（ImageProxyのタイムスタンプを使用）
     * @param frameCapturedAt フレームがキャプチャされた時刻（ナノ秒）
     * @param conversionStartAt 変換処理開始時刻（ナノ秒）
     * @param conversionEndAt 変換処理終了時刻（ナノ秒）
     * @param analysisStartAt 解析処理開始時刻（ナノ秒）
     * @param analysisEndAt 解析処理終了時刻（ナノ秒）
     * @param resultProcessedAt 結果処理完了時刻（ナノ秒）
     */
    data class MeasurementSession(
      val frameId: Long,
      val frameCapturedAt: Long,
      var conversionStartAt: Long? = null,
      var conversionEndAt: Long? = null,
      var analysisStartAt: Long? = null,
      var analysisEndAt: Long? = null,
      var resultProcessedAt: Long? = null
    )
    
    /**
     * セッション管理用のマップ
     * フレームIDをキーとして、測定セッションを管理します
     */
    private val sessions = ConcurrentHashMap<Long, MeasurementSession>()
    
    /**
     * 古いセッションをクリーンアップするための閾値（10秒）
     */
    private const val SESSION_TIMEOUT_NS = 10_000_000_000L
    
    /**
     * フレームキャプチャの開始を記録
     * 
     * @param frameId フレームID
     * @return 記録されたタイムスタンプ
     */
    fun startFrameCapture(frameId: Long): Long {
      if (!isEnabled) return 0L
      
      val timestamp = System.nanoTime()
      val session = MeasurementSession(frameId, timestamp)
      sessions[frameId] = session
      
      // 古いセッションをクリーンアップ
      cleanupOldSessions(timestamp)
      
      return timestamp
    }
    
    /**
     * 変換処理の開始を記録
     * 
     * @param frameId フレームID
     */
    fun recordConversionStart(frameId: Long) {
      if (!isEnabled) return
      
      sessions[frameId]?.let { session ->
        session.conversionStartAt = System.nanoTime()
      }
    }
    
    /**
     * 変換処理の終了を記録
     * 
     * @param frameId フレームID
     */
    fun recordConversionEnd(frameId: Long) {
      if (!isEnabled) return
      
      sessions[frameId]?.let { session ->
        session.conversionEndAt = System.nanoTime()
      }
    }
    
    /**
     * 解析処理の開始を記録
     * 
     * @param frameId フレームID
     */
    fun recordAnalysisStart(frameId: Long) {
      if (!isEnabled) return
      
      sessions[frameId]?.let { session ->
        session.analysisStartAt = System.nanoTime()
      }
    }
    
    /**
     * 解析処理の終了を記録
     * 
     * @param frameId フレームID
     */
    fun recordAnalysisEnd(frameId: Long) {
      if (!isEnabled) return
      
      sessions[frameId]?.let { session ->
        session.analysisEndAt = System.nanoTime()
      }
    }
    
    /**
     * 結果処理の完了を記録
     * 
     * @param frameId フレームID
     */
    fun recordResultProcessed(frameId: Long) {
      if (!isEnabled) return
      
      sessions[frameId]?.let { session ->
        session.resultProcessedAt = System.nanoTime()
      }
    }
    
    /**
     * 測定結果のサマリーをログ出力し、セッションをクリーンアップ
     * 
     * @param frameId フレームID
     * @param detected バーコードが検出されたかどうか（trueの場合、ExpoCameraViewでさらに詳細ログが出力される）
     * @param failed 解析が失敗したかどうか
     */
    fun logSummary(frameId: Long, detected: Boolean = true, failed: Boolean = false) {
      if (!isEnabled) return
      
      sessions.remove(frameId)?.let { session ->
        val captureTime = session.frameCapturedAt
        
        val conversionTime = if (session.conversionStartAt != null && session.conversionEndAt != null) {
          (session.conversionEndAt!! - session.conversionStartAt!!) / 1_000_000
        } else {
          null
        }
        
        val analysisTime = if (session.analysisStartAt != null && session.analysisEndAt != null) {
          (session.analysisEndAt!! - session.analysisStartAt!!) / 1_000_000
        } else {
          null
        }
        
        val resultTime = if (session.resultProcessedAt != null && session.analysisEndAt != null) {
          (session.resultProcessedAt!! - session.analysisEndAt!!) / 1_000_000
        } else {
          null
        }
        
        val totalTime = if (session.resultProcessedAt != null) {
          (session.resultProcessedAt!! - captureTime) / 1_000_000
        } else if (session.analysisEndAt != null) {
          // 結果処理がない場合（検出なし/失敗時）は解析終了時点までの時間
          (session.analysisEndAt!! - captureTime) / 1_000_000
        } else {
          null
        }
        
        // 状態を示す文字列
        val status = when {
          failed -> "[FAILED]"
          !detected -> "[NO DETECT]"
          else -> "[DETECTED]"
        }
        
        // フォーマット済みのログを出力
        val logBuilder = StringBuilder()
        logBuilder.append("[PERF] Frame #$frameId $status\n")
        logBuilder.append("  Capture      : 0ms\n")
        
        if (conversionTime != null) {
          logBuilder.append("  Conversion   : ${conversionTime}ms\n")
        } else {
          logBuilder.append("  Conversion   : N/A\n")
        }
        
        if (analysisTime != null) {
          logBuilder.append("  Analysis     : ${analysisTime}ms\n")
        } else {
          logBuilder.append("  Analysis     : N/A\n")
        }
        
        if (resultTime != null) {
          logBuilder.append("  Result       : ${resultTime}ms\n")
        } else {
          logBuilder.append("  Result       : N/A\n")
        }
        
        if (totalTime != null) {
          logBuilder.append("  Total        : ${totalTime}ms")
        } else {
          logBuilder.append("  Total        : N/A")
        }
        
        Log.d(TAG, logBuilder.toString())
      }
    }
    
    /**
     * タイムアウトした古いセッションをクリーンアップ
     * 
     * @param currentTime 現在のタイムスタンプ
     */
    private fun cleanupOldSessions(currentTime: Long) {
      val iterator = sessions.entries.iterator()
      while (iterator.hasNext()) {
        val entry = iterator.next()
        val session = entry.value
        
        // 10秒以上前のセッションを削除
        if (currentTime - session.frameCapturedAt > SESSION_TIMEOUT_NS) {
          iterator.remove()
          Log.w(TAG, "Cleaned up old session for frame #${session.frameId}")
        }
      }
    }
    
    /**
     * すべてのセッションをクリア（テスト用）
     */
    fun clearAllSessions() {
      sessions.clear()
    }
    
    /**
     * アクティブなセッション数を取得（デバッグ用）
     */
    fun getActiveSessionCount(): Int = sessions.size
  }
}
