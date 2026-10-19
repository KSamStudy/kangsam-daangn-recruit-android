package com.kangsam.daangnrecruit.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 앱 색 (화면 시안 v0.7의 색을 그대로). 화면 설계 '공통'의 색 뜻:
 * 초록 = 켜짐·정상 · 노랑 = 사람이 볼 것·사람이 맡은 것 · 보라 = 문자 단계 · 회색 = 끝났거나 꺼짐 · 빨강 = 전체 멈춤과 확인 탭 숫자.
 * 색을 바꿀 때는 여기만 고침 (화면 파일에는 색 값을 직접 쓰지 않음)
 */
object Palette {
    // 바탕·글자
    val Bg = Color(0xFFF3F5F4)          // 화면 바탕
    val Surface = Color(0xFFFFFFFF)     // 카드·아래 탭·윗줄
    val Ink = Color(0xFF1B2421)         // 기본 글자, 검은 버튼
    val Ink2 = Color(0xFF2E3936)        // 조금 옅은 글자 (최근 활동·필터)
    val Muted = Color(0xFF5A6662)       // 회색 글자 (설명·시각)
    val Muted2 = Color(0xFF46514D)      // 회색 표시 글자 (DB에만 저장 등)
    val Placeholder = Color(0xFFA9B3AF) // 빈 칸 안내 글자, 점선

    // 선
    val Line = Color(0xFFE2E7E5)        // 카드 테두리, 윗줄·아래 탭 경계
    val LineSoft = Color(0xFFEEF1F0)    // 카드 안 줄 사이
    val LineInput = Color(0xFFD5DCD9)   // 입력칸·필터·나눔 버튼 테두리
    val LineButton = Color(0xFFC9D2CE)  // 흰 버튼 테두리, 꺼진 스위치
    val LineStrong = Color(0xFF8A9592)  // 점선 버튼, 회색 테두리 표시
    val TabLine = Color(0xFFDCE2DF)     // 공고 칸 아래 줄

    // 초록 (켜짐·정상)
    val Green = Color(0xFF0F6E56)
    val GreenDark = Color(0xFF085041)
    val GreenSoft = Color(0xFFE1F5EE)
    val GreenLine = Color(0xFF9FD5C2)

    // 노랑 (사람이 볼 것·사람이 맡은 것)
    val Amber = Color(0xFF7A4A00)
    val AmberSoft = Color(0xFFFBEFD7)
    val AmberLine = Color(0xFFF1D9A6)
    val AmberLineStrong = Color(0xFFE0B865)
    val AmberCard = Color(0xFFFFF9EE)

    // 보라 (문자 단계)
    val Purple = Color(0xFF3C3489)
    val PurpleMeta = Color(0xFF534AB7)
    val PurpleInk = Color(0xFF26215C)
    val PurpleSoft = Color(0xFFEEEDFE)
    val PurpleCard = Color(0xFFF5F4FE)
    val PurpleLine = Color(0xFFD6D3F7)

    // 회색 (끝남·꺼짐)
    val GraySoft = Color(0xFFECEFEE)

    // 빨강 (전체 멈춤·확인 탭 숫자)
    val Red = Color(0xFFB42318)
    val RedSoft = Color(0xFFFEE4E2)
    val RedLine = Color(0xFFF4C7C2)

    // 개발용 칸 (시안에 없는 것 — 점선 카드)
    val DevBg = Color(0xFFFAFBFA)
}
