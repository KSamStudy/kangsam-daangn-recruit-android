package com.kangsam.daangnrecruit.sync

/**
 * 당근 가져오기가 폰 화면을 다루는 손. 폰에서는 접근성 서비스(DaangnAccessibilityService)가 맡고,
 * 시험에서는 덤프 화면을 돌려주는 가짜가 맡음. 그래서 이 기능은 다른 기능·화면 코드와 상관없이 혼자 돎
 */
interface ScreenDriver {
    /** 지금 맨 앞 화면이 당근이면 그 화면, 아니면 null */
    fun current(): Snapshot?

    /** 당근 열기. 못 열면 false */
    fun openDaangn(): Boolean

    /** 우리 앱을 앞으로 (다 돌고 나서) */
    fun openApp()

    /** 칸 누르기 (화면 이동 버튼만 — 누를 칸은 DaangnNav가 고르고 isSafeToClick으로 한 번 더 봄) */
    fun click(s: Snapshot, index: Int): Boolean

    /** 칸 스크롤 (위·아래) */
    fun scroll(s: Snapshot, index: Int, up: Boolean): Boolean

    /** 뒤로 */
    fun back()

    /** 기다리기 */
    suspend fun pause(ms: Long)

    /** 지금 시각 (ms) — 기다린 시간 계산용 */
    fun clock(): Long
}
