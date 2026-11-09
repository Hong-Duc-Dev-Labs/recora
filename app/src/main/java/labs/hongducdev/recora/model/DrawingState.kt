package labs.hongducdev.recora.model

import android.graphics.Path

data class DrawingStroke(
    val path: Path,
    val color: Int,
    val strokeWidth: Float,
)

data class DrawingToolState(
    val isDrawingActive: Boolean = false,
    val selectedColor: Int = android.graphics.Color.RED,
    val strokeWidth: Float = 12f,
)
