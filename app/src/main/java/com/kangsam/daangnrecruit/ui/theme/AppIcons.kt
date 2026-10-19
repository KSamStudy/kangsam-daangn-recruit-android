package com.kangsam.daangnrecruit.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * 아이콘: 화면 시안이 쓴 선 아이콘(Tabler Icons, MIT)을 그대로 옮김 — 24칸 안에 선으로 그린 그림.
 * Icon(…, tint = 색)으로 쓰면 선 색이 바뀜. 새 아이콘은 시안 SVG의 path 글자를 그대로 넣으면 됨
 */
object AppIcons {
    // 아래 탭
    val Home: ImageVector by lazy {
        line("home", "M5 12l-2 0l9 -9l9 9l-2 0", "M5 12v7a2 2 0 0 0 2 2h10a2 2 0 0 0 2 -2v-7", "M9 21v-6a2 2 0 0 1 2 -2h2a2 2 0 0 1 2 2v6")
    }
    val Users: ImageVector by lazy {
        line(
            "users", "M5 7a4 4 0 1 0 8 0a4 4 0 1 0 -8 0", "M3 21v-2a4 4 0 0 1 4 -4h4a4 4 0 0 1 4 4v2",
            "M16 3.13a4 4 0 0 1 0 7.75", "M21 21v-2a4 4 0 0 0 -3 -3.85",
        )
    }
    val Megaphone: ImageVector by lazy {
        line(
            "speakerphone", "M18 8a3 3 0 0 1 0 6", "M10 8v11a1 1 0 0 1 -1 1h-1a1 1 0 0 1 -1 -1v-5",
            "M12 8l4.524 -3.77a.9 .9 0 0 1 1.476 .692v12.156a.9 .9 0 0 1 -1.476 .692l-4.524 -3.77h-8a1 1 0 0 1 -1 -1v-4a1 1 0 0 1 1 -1h8",
        )
    }
    val Alert: ImageVector by lazy {
        line(
            "alert-triangle", "M12 9v4",
            "M10.363 3.591l-8.106 13.534a1.914 1.914 0 0 0 1.636 2.871h16.214a1.914 1.914 0 0 0 1.636 -2.87l-8.106 -13.536a1.914 1.914 0 0 0 -3.274 0z",
            "M12 16h.01",
        )
    }
    val Settings: ImageVector by lazy {
        line(
            "settings",
            "M10.325 4.317c.426 -1.756 2.924 -1.756 3.35 0a1.724 1.724 0 0 0 2.573 1.066c1.543 -.94 3.31 .826 2.37 2.37a1.724 1.724 0 0 0 1.065 2.572c1.756 .426 1.756 2.924 0 3.35a1.724 1.724 0 0 0 -1.066 2.573c.94 1.543 -.826 3.31 -2.37 2.37a1.724 1.724 0 0 0 -2.572 1.065c-.426 1.756 -2.924 1.756 -3.35 0a1.724 1.724 0 0 0 -2.573 -1.066c-1.543 .94 -3.31 -.826 -2.37 -2.37a1.724 1.724 0 0 0 -1.065 -2.572c-1.756 -.426 -1.756 -2.924 0 -3.35a1.724 1.724 0 0 0 1.066 -2.573c-.94 -1.543 .826 -3.31 2.37 -2.37c1 .608 2.296 .07 2.572 -1.065z",
            "M9 12a3 3 0 1 0 6 0a3 3 0 0 0 -6 0",
        )
    }

    // 화면 안
    val Back: ImageVector by lazy { line("chevron-left", "M15 6l-6 6l6 6", stroke = 2f) }
    val ChevronRight: ImageVector by lazy { line("chevron-right", "M9 6l6 6l-6 6", stroke = 2f) }
    val ChevronDown: ImageVector by lazy { line("chevron-down", "M6 9l6 6l6 -6", stroke = 2f) }
    val ChevronUp: ImageVector by lazy { line("chevron-up", "M6 15l6 -6l6 6", stroke = 2f) }
    val Lock: ImageVector by lazy {
        line(
            "lock", "M5 13a2 2 0 0 1 2 -2h10a2 2 0 0 1 2 2v6a2 2 0 0 1 -2 2h-10a2 2 0 0 1 -2 -2v-6z",
            "M11 16a1 1 0 1 0 2 0a1 1 0 0 0 -2 0", "M8 11v-4a4 4 0 1 1 8 0v4", stroke = 2f,
        )
    }
    val Ticket: ImageVector by lazy {
        line(
            "ticket", "M15 5l0 2", "M15 11l0 2", "M15 17l0 2",
            "M5 5h14a2 2 0 0 1 2 2v3a2 2 0 0 0 0 4v3a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-3a2 2 0 0 0 0 -4v-3a2 2 0 0 1 2 -2",
        )
    }
    val Plus: ImageVector by lazy { line("plus", "M12 5l0 14", "M5 12l14 0", stroke = 2.2f) }
    val Check: ImageVector by lazy { line("check", "M5 12l5 5l10 -10", stroke = 2.4f) }
    val Close: ImageVector by lazy { line("x", "M18 6l-12 12", "M6 6l12 12", stroke = 2.4f) }
    val Stop: ImageVector by lazy {
        line("square", "M5 7a2 2 0 0 1 2 -2h10a2 2 0 0 1 2 2v10a2 2 0 0 1 -2 2h-10a2 2 0 0 1 -2 -2z", stroke = 2f)
    }
    val Play: ImageVector by lazy { line("player-play", "M7 4v16l13 -8z", stroke = 2f) }
    val Download: ImageVector by lazy {
        line("download", "M4 17v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2 -2v-2", "M7 11l5 5l5 -5", "M12 4l0 12", stroke = 2f)
    }

    /** 선 아이콘 하나 (24×24 칸, 둥근 선 끝) */
    private fun line(name: String, vararg paths: String, stroke: Float = 1.8f): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            paths.forEach { d ->
                addPath(
                    pathData = addPathNodes(d),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = stroke,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()
}
