package com.kangsam.daangnrecruit.reply

import kotlin.random.Random

/**
 * 지원자에게 보낼 문구 틀 (당근 첫 메시지 · 번호 다시 요청 · 번호 받은 뒤 마무리 · 첫 문자).
 *
 * 같은 문구만 계속 보내면 매크로처럼 보일 수 있어서(재걸 10/18), 문구를 조각(인사·감사·요청 …)으로 나누고 조각마다
 * 뜻이 같은 후보를 여러 개 둬서 무작위로 조합함. 최근에 보낸 문구와 똑같은 조합은 피함 (pick).
 *
 * - 후보는 '사람이 정할 항목 1차값'의 문구 초안을 바탕으로 만든 것 (담당자 검토 전 — 확인사항 26). 문구를 고칠 땐 여기만 고치면 됨
 * - 지킬 것은 ReplyRules (당근: 1~2줄·70자·업체 느낌 말·일정 확정 말 금지 / 문자: 첫 문장 고정). 시험(TemplatesTest)이
 *   모든 조합이 검사를 통과하는지 봄 → 후보를 늘리거나 고친 뒤 시험이 깨지면 그 후보가 규칙에 안 맞는 것
 * - 문자 첫 문장 "선생님 안녕하세요! 당근 보고 연락드렸습니다."는 담당자 검토(10/13)로 고정 → 그 뒤만 바꿈
 */
object Templates {

    /** 앱이 최근에 보낸 문구 몇 개와 똑같은 문구를 피할지 (pick의 avoid) */
    const val RECENT_TO_AVOID = 30

    /** 문구 틀 하나: 조각(slot)을 차례로 이어 붙임. 조각마다 후보 중 하나를 고름 (띄어쓰기·줄바꿈도 조각) */
    class Template(val name: String, private val slots: List<List<String>>) {
        init {
            require(slots.isNotEmpty() && slots.all { it.isNotEmpty() }) { "$name: 빈 조각이 있음" }
        }

        /** 만들 수 있는 조합 수 */
        val size: Long = slots.fold(1L) { acc, s -> acc * s.size }

        /** index번째 조합 (0 ≤ index < size) */
        fun compose(index: Long): String {
            var i = Math.floorMod(index, size)
            val sb = StringBuilder()
            for (s in slots) {
                sb.append(s[(i % s.size).toInt()])
                i /= s.size
            }
            return sb.toString()
        }

        /** 모든 조합 (시험용) */
        fun all(): Sequence<String> = (0 until size).asSequence().map { compose(it) }
    }

    /**
     * 무작위로 하나 고름. avoid = 최근에 보낸 문구 (같은 문구는 피함 — 모두 겹치면 그중 하나).
     * random을 지원자·단계로 정한 씨앗(seed)으로 만들면 같은 지원자에겐 보낼 때까지 같은 문구가 보임 (ReplyPlanner)
     */
    fun pick(t: Template, random: Random, avoid: Collection<String> = emptyList()): String {
        repeat(30) {
            val s = t.compose(random.nextLong(t.size))
            if (s !in avoid) return s
        }
        return t.all().firstOrNull { it !in avoid } ?: t.compose(random.nextLong(t.size))
    }

    // ---------- 당근 (1~2줄, 번호만 받음 — 문구 규칙 '당근 채팅')

    /** 당근 첫 메시지 (B2): 인사 + 지원 감사 + 문자로 안내할 테니 번호 요청 */
    val DAANGN_FIRST = Template(
        "당근 첫 메시지",
        listOf(
            listOf("안녕하세요 선생님!", "선생님 안녕하세요!", "안녕하세요, 선생님!", "선생님, 안녕하세요!"),
            listOf(" "),
            listOf("지원해주셔서 감사합니다.", "지원해 주셔서 감사해요.", "지원 감사드립니다.", "관심 가져주셔서 감사합니다."),
            listOf(" ", "\n"),
            listOf(
                "자세한 내용은 문자로 안내드리려고 하는데, 연락처 남겨주실 수 있을까요?",
                "자세한 건 문자로 안내드릴게요. 연락처 남겨주시겠어요?",
                "문자로 자세히 안내드리고 싶은데, 연락처 알려주실 수 있을까요?",
                "자세한 내용은 문자로 드릴게요. 번호 남겨주실 수 있을까요?",
            ),
        ),
    )

    /**
     * 답에 휴대폰 번호가 없거나 형식이 다를 때 (B3, 10/18 재걸: 정해진 형식으로 다시 보내 달라고).
     * 형식은 '010-XXXX-XXXX'로 보여 줌 — 숫자로 된 예시 번호는 발송 전 검사가 전화번호로 보고 막음
     */
    val DAANGN_RETRY = Template(
        "번호 다시 요청",
        listOf(
            listOf("죄송하지만", "번거로우시겠지만", "혹시"),
            listOf(" "),
            listOf("휴대폰 번호를", "연락받으실 휴대폰 번호를"),
            listOf(" "),
            listOf("010-XXXX-XXXX 형식으로", "010-XXXX-XXXX처럼"),
            listOf(" "),
            listOf("다시 보내주실 수 있을까요?", "한 번만 다시 보내주시겠어요?", "다시 남겨주실 수 있을까요?"),
        ),
    )

    /** 번호를 받은 뒤 당근 마무리 1회 (B3): 감사 + 문자로 연락하겠다 (10/18 재걸: 꼭 감사 인사로 시작) */
    val DAANGN_AFTER_NUMBER = Template(
        "번호 받은 뒤 마무리",
        listOf(
            listOf("감사합니다!", "감사해요!", "번호 감사합니다!", "알려주셔서 감사합니다."),
            listOf(" "),
            listOf("문자로 연락드릴게요.", "곧 문자로 연락드리겠습니다.", "문자로 다시 연락드릴게요.", "문자로 안내드리겠습니다."),
        ),
    )

    // ---------- 문자 (첫 문장 고정, 업체·이름 소개 없음 — 문구 규칙 '문자')

    /** 문자 첫 문장 (고정) */
    const val SMS_FIRST_LINE = "선생님 안녕하세요! 당근 보고 연락드렸습니다."

    /** 첫 문자 (B4-2): 고정 첫 문장 + 안내 한 줄 + 받을 항목 5개(1차값 '문자 문구 초안') + 맺음말(없을 수도) */
    val SMS_FIRST = Template(
        "첫 문자",
        listOf(
            listOf(SMS_FIRST_LINE + "\n"),
            listOf(
                "아래 내용 보내주시면 확인 후 다시 연락드리겠습니다.",
                "아래 내용 알려주시면 확인하고 다시 연락드릴게요.",
                "아래 몇 가지 보내주시면 확인 후 다시 연락드리겠습니다.",
                "아래 내용 답장 주시면 확인 후 연락드리겠습니다.",
            ),
            listOf("\n1. "),
            listOf("성함", "이름"),
            listOf("\n2. "),
            listOf("대학·학과", "학교·전공"),
            listOf("\n3. "),
            listOf("과외·교육 경험", "과외나 가르쳐 본 경험"),
            listOf("\n4. "),
            listOf("수업 가능한 요일·시간대", "가능한 요일과 시간대"),
            listOf("\n5. "),
            listOf("수업 가능 기간", "수업 가능한 기간"),
            listOf("", "\n편하실 때 답장 주세요.", "\n감사합니다."),
        ),
    )

    /** 당근 문구 틀 전부 (시험·화면 표시용) */
    val DAANGN = listOf(DAANGN_FIRST, DAANGN_RETRY, DAANGN_AFTER_NUMBER)

    /** 문자 문구 틀 전부 */
    val SMS = listOf(SMS_FIRST)
}
