package com.kangsam.daangnrecruit.sync

/**
 * 당근 '나의 당근' 화면에서 계정 닉네임 읽기 (계정 맞추기 — AccountSync). 순수 코드.
 * 닉네임 칸 (10/20 운영폰 화면으로 정함 — 구현 계획 5단계): 맨 위 프로필 줄. 설명 글자가 '프로필 보기'인 칸과 같은 줄(그 칸 또는
 * 그 윗칸) 안 글자 중 매너온도(예: 38.8℃) 바로 앞 글자가 닉네임.
 * 프로필 줄·매너온도를 못 찾으면 null → 계정은 안 맞추고 가져오기는 그대로 돎 (SyncRunner가 위쪽 글자와 화면을 남김)
 */
object MyPageParser {
    /** 프로필 줄을 알아보는 설명 글자 (content-desc) */
    const val PROFILE_DESC = "프로필 보기"

    /** 닉네임으로 볼 가장 긴 글자 수 (다른 긴 글자를 닉네임으로 읽지 않게) */
    private const val MAX_NICKNAME = 20

    /** 매너온도 (예: 38.8℃ · 36.5°C) */
    private val TEMPERATURE = Regex("""^\d{1,3}(\.\d+)?\s*(℃|°C)$""")

    /** 나의 당근의 계정 닉네임 (못 읽으면 null) */
    fun nickname(nodes: List<UiNode>): String? {
        val anchor = nodes.indexOfFirst { it.desc.trim() == PROFILE_DESC }
        if (anchor < 0) return null
        for (row in listOf(anchor, nodes[anchor].parent).filter { it >= 0 }) {
            val texts = UiTree.texts(nodes, row)
            val t = texts.indexOfFirst { TEMPERATURE.matches(it) }
            if (t < 0) continue
            val nick = texts.getOrNull(t - 1)?.trim().orEmpty()
            return nick.takeIf { it.isNotEmpty() && it.length <= MAX_NICKNAME && !TEMPERATURE.matches(it) }
        }
        return null
    }

    /** 닉네임 칸을 못 찾았을 때 기록에 남기는 화면 위쪽 글자 몇 개 (가게 계정 화면이라 지원자 정보는 없음) */
    fun topTexts(nodes: List<UiNode>, n: Int = 8): List<String> = nodes.map { it.text.trim() }.filter { it.isNotEmpty() }.take(n)
}
