package com.kangsam.daangnrecruit.reply

import com.kangsam.daangnrecruit.ApplicantStatus

/**
 * 실제 발송 시험 ③ 당근 대화에서 다음에 할 일 (사용자 프로세스 B2·B3 — 순수 코드, 폰 없이 시험).
 * 채팅방에 보이는 말풍선과 앱이 보낸 기록(대화 표)을 보고 정함:
 *
 *   방에 앱이 안 보낸 우리 쪽 말          → 멈춤 (사람이 응대 중 → '담당자 응대 중')
 *   마무리를 이미 보냄                    → 끝 (더 안 보냄)
 *   앱이 아직 안 보냄                     → 첫 메시지 (지원자가 먼저 번호를 보냈으면 바로 마무리)
 *   앱이 보낸 뒤 지원자 답이 없음          → 기다림
 *   답에 010 번호가 하나로 분명함          → 마무리 (감사 + 문자로 연락드리겠다) — 연락처 저장 + '연락처 확보'
 *     (앱에 이미 받은 번호가 있는데 다른 번호면 → 멈춤 '담당자 확인' — ContactFlow와 같은 규칙)
 *   답에 번호가 없거나 여러 개·형식이 다름 → 번호 다시 요청 (MAX_RETRY번까지) → 넘으면 멈춤 ('담당자 확인')
 *
 * 문자(SMS)는 아직 안 보냄 — 마무리에서 '문자로 연락드리겠다'고만 함 (10/18 재걸)
 */
object Conversation {
    const val MAX_RETRY = 2

    /** 앱이 보내는 당근 메시지 종류 — step은 대화 표의 메시지 키(app|step|…)와 문구 씨앗에 씀 */
    enum class Kind(val step: String, val label: String) {
        FIRST(ReplyPlanner.STEP_FIRST, "첫 메시지"),
        RETRY(ReplyPlanner.STEP_RETRY, "번호 다시 요청"),
        CLOSE(ReplyPlanner.STEP_CLOSE, "번호 받은 뒤 마무리"),
    }

    /** 앱이 보낸 당근 메시지 하나 (대화 표에서) */
    data class Sent(val kind: Kind, val text: String, val time: Long)

    /** 채팅방 말풍선 하나 (위에서 아래 차례) */
    data class Bubble(val ours: Boolean, val text: String)

    sealed class Next {
        /** 보냄. CLOSE면 phone = 받은 번호 */
        data class Send(val kind: Kind, val phone: PhoneNumber.Found? = null) : Next()

        /** 아직 할 일 없음 (답 기다림) */
        data class Wait(val reason: String) : Next()

        /** 사람에게 넘김 — 지원자 상태를 status로 (앱은 더 안 보냄) */
        data class Stop(val reason: String, val status: String) : Next()

        /** 끝 — 앱이 보낼 것 없음 */
        data class Done(val reason: String) : Next()
    }

    /** knownKey = 앱에 이미 저장된 연락처 키 (없으면 빈 칸) */
    fun next(sent: List<Sent>, room: List<Bubble>, knownKey: String = ""): Next {
        val appTexts = sent.map { norm(it.text) }.filter { it.isNotEmpty() }
        val human = room.filter { it.ours && !byApp(it, appTexts) }
        if (human.isNotEmpty()) {
            return Next.Stop("채팅방에 앱이 보내지 않은 우리 쪽 메시지 ${human.size}개 — 사람이 응대 중", ApplicantStatus.STAFF_HANDLING)
        }
        if (sent.any { it.kind == Kind.CLOSE }) return Next.Done("마무리까지 보냄 — 앱이 더 보낼 것 없음")
        // 앱이 보낸 마지막 말 뒤의 지원자 말 (앱이 보낸 말이 방에 안 보이면 보이는 지원자 말 전부)
        val lastOurs = room.indexOfLast { it.ours }
        val replies = room.drop(lastOurs + 1).filter { !it.ours }
        if (sent.none { it.kind == Kind.FIRST }) {
            number(replies)?.let { return close(it, knownKey) } // 지원자가 먼저 번호를 보냄
            return Next.Send(Kind.FIRST)
        }
        if (replies.isEmpty()) return Next.Wait("답장 기다림")
        number(replies)?.let { return close(it, knownKey) }
        val retries = sent.count { it.kind == Kind.RETRY }
        if (retries < MAX_RETRY) return Next.Send(Kind.RETRY)
        return Next.Stop("번호를 못 받음 — 다시 요청 ${retries}번 뒤에도 (담당자가 대화를 보고 답해 주세요)", ApplicantStatus.STAFF_CHECK)
    }

    /** 번호를 받음 → 마무리. 앱에 이미 다른 번호가 있으면 멈춤 (어느 번호가 맞는지 사람이 봄) */
    private fun close(f: PhoneNumber.Found, knownKey: String): Next =
        if (knownKey.isNotBlank() && knownKey != f.key) {
            Next.Stop("앱에 이미 받은 번호와 다른 번호가 옴 (담당자가 대화를 보고 확인해 주세요)", ApplicantStatus.STAFF_CHECK)
        } else {
            Next.Send(Kind.CLOSE, f)
        }

    /**
     * 우리 쪽 말풍선이 앱이 보낸 말인지: 보낸 문구가 말풍선 글자 안에 그대로 있으면 (띄어쓰기·줄바꿈 빼고).
     * 말풍선 칸에 '읽음' 같은 글자가 같이 들어 있어도 앱이 보낸 말로 봄
     */
    fun byApp(b: Bubble, appTexts: List<String>): Boolean {
        val t = norm(b.text)
        return appTexts.any { t.contains(it) }
    }

    /** 말풍선 글자에 보낸 문구가 들어 있는지 (전송 뒤 방에 보이는지 볼 때) */
    fun shows(bubbleText: String, sentText: String): Boolean {
        val want = norm(sentText)
        return want.isNotEmpty() && norm(bubbleText).contains(want)
    }

    /** 답에서 쓸 번호: 010으로 시작하는 11자리 번호가 하나로 분명할 때만 (여러 개·형식이 다르면 null) */
    fun number(replies: List<Bubble>): PhoneNumber.Found? {
        val found = replies.flatMap { PhoneNumber.find(it.text) }
        val keys = found.map { it.key }.distinct()
        if (keys.size != 1 || !PhoneNumber.isValidKey(keys.single())) return null
        return found.first()
    }

    /** 말풍선과 보낸 문구를 견줄 때: 띄어쓰기·줄바꿈은 빼고 (당근이 줄바꿈·공백을 바꿔 보여 줄 수 있어서) */
    fun norm(s: String): String = s.filter { !it.isWhitespace() }

    /** 대화 표의 메시지 키 앞부분 → 종류 (app|B2|… → 첫 메시지) */
    fun kindOf(messageKey: String?): Kind? {
        val key = messageKey ?: return null
        if (!key.startsWith("$KEY|")) return null
        val step = key.split("|").getOrNull(1) ?: return null
        return Kind.entries.firstOrNull { it.step == step }
    }

    /** 앱이 실제로 보낸 당근 메시지의 메시지 키 앞부분 */
    const val KEY = "app"
}
