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

    /**
     * 입력창에 글자 넣기 — 실제 발송 시험(reply/LiveTurn)이 채팅방 입력창에만 씀 (당근 가져오기는 안 씀).
     * 못 넣으면 false. 기본은 아무것도 안 함
     */
    fun setText(s: Snapshot, index: Int, text: String): Boolean = false

    /** 뒤로 */
    fun back()

    /** 기다리기 */
    suspend fun pause(ms: Long)

    /** 지금 시각 (ms) — 기다린 시간 계산용 */
    fun clock(): Long

    /**
     * 개발용: 앱이 본 화면을 폰 안(앱 전용 폴더)에 남김 — 화면이 덤프와 다르게 보일 때 원인을 보려고.
     * 이름 하나에 파일 하나(덮어씀). 기본은 아무것도 안 함
     */
    fun saveScreen(s: Snapshot, name: String) {}

    /** 개발용: 이름이 이 글자로 시작하는 저장 화면을 지움 (화면마다 남기는 채팅 목록 — 지난번 것이 섞이지 않게). 기본은 아무것도 안 함 */
    fun clearScreens(prefix: String) {}
}
