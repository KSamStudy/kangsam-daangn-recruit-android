package com.kangsam.daangnrecruit.reply

/**
 * 메시지에서 휴대폰 번호 찾기·정규화 (사용자 프로세스 B3 '번호 추출·정규화'). 순수 코드라 폰 없이 시험함.
 *
 * 9/15 지원자 요약: 번호 쓰는 모양이 제각각 (붙여 쓰기 01012345678 · 하이픈 010-1234-5678 · 띄어쓰기 010 1234 5678 ·
 * 점 010.1234.5678 · +82 10-1234-5678) → 모두 숫자만 남긴 키(01012345678)로 맞춤.
 * - 키 = 숫자만 남긴 0으로 시작하는 번호 (DB 'phoneKey' — 같으면 같은 사람)
 * - 쓸 수 있는 번호 = 010으로 시작하는 11자리 (B3 '010으로 시작하는지 확인'). 011·016 같은 옛 번호나 자릿수가 이상하면 '이상한 번호'
 * - 가린 번호(010-••••-1234), 더 긴 숫자 안의 숫자(계좌번호 등)는 번호로 안 봄
 */
object PhoneNumber {
    /** 찾은 번호: 받은 그대로(raw) · 숫자만 남긴 키(key) */
    data class Found(val raw: String, val key: String) {
        val isValid: Boolean get() = isValidKey(key)
    }

    // 앞: +82 / 82 (뒤에 0이 있어도 됨) 또는 0 → 1X → 3~4자리 → 4자리. 사이에 띄어쓰기·하이픈·점 (최대 3글자)
    private val CANDIDATE = Regex("""(?<![\d+])(?:\+?82[\s.\-]{0,3}0?|0)(1[016789])[\s.\-]{0,3}(\d{3,4})[\s.\-]{0,3}(\d{4})(?!\d)""")
    private val VALID = Regex("""010\d{8}""")

    /** 메시지에서 번호처럼 보이는 것을 모두 찾음 (같은 번호를 두 번 써도 두 개) */
    fun find(text: String): List<Found> = CANDIDATE.findAll(text).map { m ->
        val (head, mid, tail) = m.destructured
        Found(raw = m.value.trim(), key = "0$head$mid$tail")
    }.toList()

    /** 쓸 수 있는 키인지 (010 + 8자리) */
    fun isValidKey(key: String): Boolean = VALID.matches(key)

    /** 키 → 010-1234-5678 (쓸 수 없는 키면 그대로) */
    fun format(key: String): String =
        if (isValidKey(key)) "${key.substring(0, 3)}-${key.substring(3, 7)}-${key.substring(7)}" else key
}
