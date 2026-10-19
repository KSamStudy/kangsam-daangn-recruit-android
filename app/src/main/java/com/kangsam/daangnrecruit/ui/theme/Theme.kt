package com.kangsam.daangnrecruit.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * 앱 테마: 화면 시안의 색으로 고정 (폰 배경화면 색을 따라가는 동적 색·어두운 테마는 안 씀 — 시안이 밝은 화면 하나라서).
 * Material 부품(입력칸 커서, 대화상자, 메뉴, 알림 띠)이 시안 색으로 보이게 색 이름을 맞춤
 */
private val AppColors = lightColorScheme(
    primary = Palette.Green,
    onPrimary = Palette.Surface,
    primaryContainer = Palette.GreenSoft,
    onPrimaryContainer = Palette.GreenDark,
    secondary = Palette.Ink2,
    onSecondary = Palette.Surface,
    secondaryContainer = Palette.GraySoft,
    onSecondaryContainer = Palette.Ink,
    tertiary = Palette.Purple,
    onTertiary = Palette.Surface,
    background = Palette.Bg,
    onBackground = Palette.Ink,
    surface = Palette.Surface,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.Bg,
    onSurfaceVariant = Palette.Muted,
    surfaceTint = Palette.Surface,
    inverseSurface = Palette.Ink,
    inverseOnSurface = Palette.Surface,
    error = Palette.Red,
    onError = Palette.Surface,
    errorContainer = Palette.RedSoft,
    onErrorContainer = Palette.Red,
    outline = Palette.LineButton,
    outlineVariant = Palette.Line,
    surfaceBright = Palette.Surface,
    surfaceDim = Palette.Bg,
    surfaceContainerLowest = Palette.Surface,
    surfaceContainerLow = Palette.Surface,
    surfaceContainer = Palette.Surface,
    surfaceContainerHigh = Palette.Surface,
    surfaceContainerHighest = Palette.Bg,
)

@Composable
fun KangsamDaangnRecruitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColors,
        typography = AppTypography,
        content = content,
    )
}
