package dev.snapledger.desktop

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter

/** Window/taskbar icon drawn in code (same design as icons/snapledger.ico): blue rounded square, three white bars. */
object AppIcon : Painter() {
    private val blue = Color(0xFF1F6FEB)

    override val intrinsicSize = Size(256f, 256f)

    override fun DrawScope.onDraw() {
        val w = size.width
        val h = size.height
        drawRoundRect(blue, cornerRadius = CornerRadius(w * 0.18f, h * 0.18f))
        listOf(Triple(0.28f, 0.38f, 0.75f), Triple(0.45f, 0.55f, 0.75f), Triple(0.62f, 0.72f, 0.58f)).forEach { (top, bottom, right) ->
            drawRect(Color.White, topLeft = Offset(w * 0.25f, h * top), size = Size(w * (right - 0.25f), h * (bottom - top)))
        }
    }
}
