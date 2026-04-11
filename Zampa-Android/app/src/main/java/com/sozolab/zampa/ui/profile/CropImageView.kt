package com.sozolab.zampa.ui.profile

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val CROP_DIAMETER_DP = 280f

@Composable
fun CropImageView(
    sourceBitmap: Bitmap,
    onConfirm: (Bitmap) -> Unit,
    onCancel: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    val density = LocalDensity.current
    val cropPx = with(density) { CROP_DIAMETER_DP.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { containerSize = it }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceAtLeast(1f)
                        offset += pan
                    }
                }
        ) {
            if (containerSize != IntSize.Zero) {
                val cx = containerSize.width / 2f
                val cy = containerSize.height / 2f

                val imgSize = cropPx * scale
                val imgX = cx + offset.x - imgSize / 2f
                val imgY = cy + offset.y - imgSize / 2f

                // Image
                with(density) {
                    Image(
                        bitmap = sourceBitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(imgSize.toDp())
                            .offset(x = imgX.toDp(), y = imgY.toDp())
                    )
                }

                // Dark overlay with circular hole
                androidx.compose.foundation.Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                ) {
                    drawRect(color = Color.Black.copy(alpha = 0.65f))
                    drawIntoCanvas { canvas ->
                        val holePaint = Paint().apply {
                            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
                        }
                        canvas.nativeCanvas.drawCircle(cx, cy, cropPx / 2f, holePaint)
                    }
                }

                // Circle border
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.8f),
                        radius = cropPx / 2f,
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }

                // Hint text
                with(density) {
                    val hintY = cy + cropPx / 2f + 24.dp.toPx()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = hintY.toDp()),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Arrastra y pellizca para encuadrar",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Buttons
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 28.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onCancel) {
                Text("Cancelar", color = Color.White)
            }
            TextButton(
                onClick = {
                    if (containerSize != IntSize.Zero) {
                        onConfirm(renderCrop(sourceBitmap, scale, offset, containerSize, cropPx))
                    }
                }
            ) {
                Text("Confirmar", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun renderCrop(
    source: Bitmap,
    scale: Float,
    offset: Offset,
    containerSize: IntSize,
    cropPx: Float
): Bitmap {
    val outputPx = 280

    val cx = containerSize.width / 2f
    val cy = containerSize.height / 2f

    val imgSize = cropPx * scale
    val imgX = cx + offset.x - imgSize / 2f
    val imgY = cy + offset.y - imgSize / 2f
    val cropLeft = cx - cropPx / 2f
    val cropTop = cy - cropPx / 2f

    val srcW = source.width.toFloat()
    val srcH = source.height.toFloat()

    // ContentScale.Crop fills the imgSize square: scale so shorter side = imgSize
    val srcScale = imgSize / minOf(srcW, srcH)
    val displayedW = srcW * srcScale
    val displayedH = srcH * srcScale
    // How much of the source overflows each side (centered):
    val displayOffX = (displayedW - imgSize) / 2f
    val displayOffY = (displayedH - imgSize) / 2f

    // Position of crop circle top-left relative to image box top-left:
    val cropInImgX = cropLeft - imgX
    val cropInImgY = cropTop - imgY

    // Map into source pixel coordinates:
    val srcX = (cropInImgX + displayOffX) / srcScale
    val srcY = (cropInImgY + displayOffY) / srcScale
    val srcCropSize = cropPx / srcScale

    val clampedX = srcX.coerceIn(0f, (srcW - 1f))
    val clampedY = srcY.coerceIn(0f, (srcH - 1f))
    val clampedW = srcCropSize.coerceIn(1f, srcW - clampedX)
    val clampedH = srcCropSize.coerceIn(1f, srcH - clampedY)

    val cropped = Bitmap.createBitmap(
        source,
        clampedX.toInt(), clampedY.toInt(),
        clampedW.toInt(), clampedH.toInt()
    )

    val scaled = Bitmap.createScaledBitmap(cropped, outputPx, outputPx, true)

    // Apply circular mask
    val output = Bitmap.createBitmap(outputPx, outputPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    canvas.drawCircle(outputPx / 2f, outputPx / 2f, outputPx / 2f, paint)
    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    canvas.drawBitmap(scaled, 0f, 0f, paint)

    return output
}
