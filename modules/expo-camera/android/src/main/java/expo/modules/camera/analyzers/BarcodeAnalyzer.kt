package expo.modules.camera.analyzers

import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import expo.modules.camera.records.BarcodeType
import expo.modules.camera.utils.BarCodeScannerResult
import expo.modules.camera.utils.PerformanceLogger

@OptIn(ExperimentalGetImage::class)
class BarcodeAnalyzer(formats: List<BarcodeType>, val onComplete: (BarCodeScannerResult) -> Unit) : ImageAnalysis.Analyzer {
  private val barcodeFormats = if (formats.isEmpty()) {
    0
  } else {
    formats.map { it.mapToBarcode() }.reduce { acc, it ->
      acc or it
    }
  }
  private var barcodeScannerOptions =
    BarcodeScannerOptions.Builder()
      .setBarcodeFormats(barcodeFormats)
      .build()
  private var barcodeScanner = BarcodeScanning.getClient(barcodeScannerOptions)

  override fun analyze(imageProxy: ImageProxy) {
    // フレームキャプチャの開始を記録
    val frameId = imageProxy.imageInfo.timestamp
    PerformanceLogger.startFrameCapture(frameId)
    
    val mediaImage = imageProxy.image

    if (mediaImage != null) {
      val rotationDegrees = imageProxy.imageInfo.rotationDegrees
      
      // 変換処理の開始を記録
      PerformanceLogger.recordConversionStart(frameId)
      val image = InputImage.fromMediaImage(mediaImage, rotationDegrees)
      // 変換処理の終了を記録
      PerformanceLogger.recordConversionEnd(frameId)

      // MLKit returns coordinates in the upright (rotated) coordinate space,
      // so we need the post-rotation dimensions for correct scaling.
      val isRotated = rotationDegrees == 90 || rotationDegrees == 270
      val effectiveWidth = if (isRotated) imageProxy.height else imageProxy.width
      val effectiveHeight = if (isRotated) imageProxy.width else imageProxy.height

      // 解析処理の開始を記録
      PerformanceLogger.recordAnalysisStart(frameId)
      barcodeScanner.process(image)
        .addOnSuccessListener { barcodes ->
          // 解析処理の終了を記録
          PerformanceLogger.recordAnalysisEnd(frameId)
          if (barcodes.isEmpty()) {
            return@addOnSuccessListener
          }
          val barcode = barcodes.first()
          val raw = barcode.rawValue ?: barcode.rawBytes?.let { String(it) }

          val cornerPoints = barcode.cornerPoints?.let { points ->
            // Pre-allocate array
            IntArray(points.size * 2).apply {
              points.forEachIndexed { index, point ->
                this[index * 2] = point.x
                this[index * 2 + 1] = point.y
              }
            }.toMutableList()
          } ?: mutableListOf()

          val extra = BarCodeScannerResultSerializer.parseExtraDate(barcode)
          onComplete(
            BarCodeScannerResult(
              barcode.format,
              barcode.displayValue,
              raw,
              extra,
              cornerPoints,
              effectiveHeight,
              effectiveWidth,
              frameId  // タイムスタンプを追加
            )
          )
        }
        .addOnFailureListener {
          Log.d("SCANNER", it.cause?.message ?: "Barcode scanning failed")
        }
        .addOnCompleteListener {
          imageProxy.close()
        }
    }
  }
}

fun Array<ImageProxy.PlaneProxy>.toByteArray(): ByteArray {
  val totalSize = this.sumOf { it.buffer.remaining() }
  val result = ByteArray(totalSize)
  var offset = 0

  for (plane in this) {
    val buffer = plane.buffer
    val size = buffer.remaining()
    buffer.get(result, offset, size)
    offset += size
  }

  return result
}
