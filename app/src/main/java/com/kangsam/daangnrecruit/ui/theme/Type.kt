package com.kangsam.daangnrecruit.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.kangsam.daangnrecruit.R

/**
 * 글꼴: 화면 시안의 IBM Plex Sans KR (res/font, 굵기 4개 — 고치지 않은 원본 파일이라 글꼴 안에 OFL 라이선스가 들어 있음).
 * 화면에서는 아래 AppText의 이름 붙은 글자 모양만 씀 (시안의 글자 크기·굵기를 그대로 옮김)
 */
val PlexSansKr = FontFamily(
    Font(R.font.ibm_plex_sans_kr_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_kr_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_kr_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_sans_kr_bold, FontWeight.Bold),
)

/** 시안처럼 줄 높이를 글자 크기 × 배수로 두고, 글자를 줄 가운데에 놓음 (CSS line-height와 같게) */
private fun plex(size: Int, weight: FontWeight = FontWeight.Normal, lineHeight: Float = 1.4f, letterSpacing: TextUnit = 0.sp) =
    TextStyle(
        fontFamily = PlexSansKr,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = (size * lineHeight).sp,
        letterSpacing = letterSpacing,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )

/** 화면에서 쓰는 글자 모양 (이름 = 쓰는 곳). 색은 쓰는 곳에서 정함 */
object AppText {
    val ScreenTitle = plex(22, FontWeight.Bold, letterSpacing = (-0.22).sp) // 탭 화면 제목
    val DetailTitle = plex(17, FontWeight.Bold)                             // 상세 화면 윗줄 제목
    val CardTitle = plex(16, FontWeight.Bold)                               // 공고 카드 이름
    val Section = plex(15, FontWeight.SemiBold)                             // 카드 제목 (기능·오늘·최근 활동)
    val Name = plex(15, FontWeight.SemiBold)                                // 목록·카드의 지원자 이름
    val Body = plex(14)
    val BodyMedium = plex(14, FontWeight.Medium)
    val BodyStrong = plex(14, FontWeight.SemiBold)
    val Small = plex(13)
    val SmallMedium = plex(13, FontWeight.Medium)
    val SmallStrong = plex(13, FontWeight.SemiBold)
    val SmallBold = plex(13, FontWeight.Bold)
    val Caption = plex(12)
    val CaptionMedium = plex(12, FontWeight.Medium)
    val CaptionStrong = plex(12, FontWeight.SemiBold)
    val Tiny = plex(11, FontWeight.SemiBold)                                // 말풍선 위 '문자 · 앱 자동 · 13:42', 작은 표시
    val Number = plex(22, FontWeight.Bold, lineHeight = 1.25f)              // 홈 '오늘' 숫자
    val Message = plex(13, lineHeight = 1.5f)                               // 말풍선 글
    val Mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp) // 개발용 기록
}

/** Material 부품(입력칸·대화상자·알림 띠)도 같은 글꼴로 */
private val Base = Typography()
val AppTypography = Base.copy(
    displayLarge = Base.displayLarge.copy(fontFamily = PlexSansKr),
    displayMedium = Base.displayMedium.copy(fontFamily = PlexSansKr),
    displaySmall = Base.displaySmall.copy(fontFamily = PlexSansKr),
    headlineLarge = Base.headlineLarge.copy(fontFamily = PlexSansKr),
    headlineMedium = Base.headlineMedium.copy(fontFamily = PlexSansKr),
    headlineSmall = Base.headlineSmall.copy(fontFamily = PlexSansKr),
    titleLarge = Base.titleLarge.copy(fontFamily = PlexSansKr, fontWeight = FontWeight.Bold, fontSize = 18.sp),
    titleMedium = Base.titleMedium.copy(fontFamily = PlexSansKr),
    titleSmall = Base.titleSmall.copy(fontFamily = PlexSansKr),
    bodyLarge = Base.bodyLarge.copy(fontFamily = PlexSansKr, letterSpacing = 0.sp),
    bodyMedium = Base.bodyMedium.copy(fontFamily = PlexSansKr, letterSpacing = 0.sp),
    bodySmall = Base.bodySmall.copy(fontFamily = PlexSansKr, letterSpacing = 0.sp),
    labelLarge = Base.labelLarge.copy(fontFamily = PlexSansKr),
    labelMedium = Base.labelMedium.copy(fontFamily = PlexSansKr),
    labelSmall = Base.labelSmall.copy(fontFamily = PlexSansKr),
)
