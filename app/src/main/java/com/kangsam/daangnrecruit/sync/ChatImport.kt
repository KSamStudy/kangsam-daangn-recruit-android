package com.kangsam.daangnrecruit.sync

import com.kangsam.daangnrecruit.AppDatabase
import com.kangsam.daangnrecruit.Message

/**
 * 채팅방에서 읽은 메시지를 대화 표(DB 설계 '대화')에 남김 (개발 5단계). 당근에는 아무것도 하지 않음.
 * - 방 위쪽의 공고명 + 상대 이름으로 지원자 줄을 찾음 (없으면 건너뜀)
 * - 같은 메시지는 두 번 저장하지 않음: 메시지 키 = 지원자·시각·보낸 쪽·내용 (같은 분에 같은 말이면 차례 번호)
 * - 알림으로 먼저 받은 지원자 메시지(ChatNotify)와 같은 것이면 새로 넣지 않고 화면의 시각·키로 고침 (10/18)
 * - 앱이 보낸 우리 메시지(실제 발송 시험 — 메시지 키 app|…)와 같은 것이면 새로 넣지 않음. 안 그러면 '직접 보냄'(사람)으로 한 번 더
 *   남아 앱이 사람이 응대 중으로 봄 (10/18). 앱이 보낸 줄의 키는 그대로 둠 (무엇을 보냈는지 — 첫 메시지·다시 요청·마무리 — 를 키로 봄)
 * - 우리 메시지는 보낸 방법 '직접 보냄'(사람이 보냄). 지원자 메시지는 앱 처리 '넘김' — 담당자가 처리한 지난 대화라 앱이 답하지 않게
 */
object ChatImport {
    /** 화면에서 가져온 메시지 키 앞부분 */
    const val KEY = "daangn"

    suspend fun save(db: AppDatabase, header: ChatRoomHeader, lines: List<ChatLine>, skipped: Int): String {
        val applicant = db.applicantDao().findByTitleAndName(header.postTitle, header.name).maxByOrNull { it.appliedAt }
            ?: return "'${header.name}' 방 — '${header.postTitle}' 지원자가 앱에 없어 건너뜀"
        val dao = db.messageDao()
        val appSent = dao.listFor(applicant.id).filter { it.sender == "우리" && it.messageKey?.startsWith(APP_KEY + "|") == true }
        var added = 0
        var fixed = 0
        var byApp = 0
        val seen = HashMap<String, Int>()
        for (l in lines) {
            val sender = if (l.ours) "우리" else "지원자"
            val base = "$KEY|${applicant.id}|${l.time}|$sender|${l.text.length}|${l.text.hashCode()}"
            val n = (seen[base] ?: 0) + 1
            seen[base] = n
            val key = "$base|$n"
            if (dao.countByKey(key) > 0) continue // 전에 화면에서 가져옴
            if (l.ours && appSent.any { same(it.content, l.text) && kotlin.math.abs(it.time - l.time) <= ChatNotify.TWIN_MS }) {
                byApp++ // 앱이 보낸 메시지 (이미 기록됨)
                continue
            }
            if (!l.ours) {
                // 알림으로 먼저 받은 같은 메시지(같은 내용, 앞뒤 12시간 안) → 새로 넣지 않고 화면의 시각·키로 고침 (앱 처리는 그대로)
                val twin = dao.findSame(
                    applicant.id, sender, l.text, l.time - ChatNotify.TWIN_MS, l.time + ChatNotify.TWIN_MS, ChatNotify.KEY + "|",
                ).firstOrNull()
                if (twin != null) {
                    dao.update(twin.copy(time = l.time, messageKey = key))
                    fixed++
                    continue
                }
            }
            val id = dao.insertIgnore(
                Message(
                    time = l.time, applicantId = applicant.id, channel = "당근", sender = sender, content = l.text,
                    sendMethod = if (l.ours) "직접 보냄" else "", appHandling = if (l.ours) "" else "넘김",
                    messageKey = key,
                )
            )
            if (id > 0) added++
        }
        lines.maxOfOrNull { it.time }?.let { db.applicantDao().touchLastMessage(applicant.id, it) }
        return "'${header.name}' 대화 ${lines.size}개 읽음 · 새로 ${added}개" +
            (if (fixed > 0) " · 알림으로 받은 것 ${fixed}개 맞춤" else "") +
            (if (byApp > 0) " · 앱이 보낸 것 ${byApp}개는 이미 있음" else "") +
            (if (skipped > 0) " · 날짜를 몰라 뺀 것 ${skipped}개" else "")
    }

    /** 앱이 실제로 보낸 당근 메시지의 키 앞부분 (reply/Conversation.KEY와 같음) */
    private const val APP_KEY = "app"

    /**
     * 앱이 보낸 말(app)이 화면 말풍선(screen)과 같은지: 띄어쓰기·줄바꿈 빼고 말풍선 글자 안에 그대로 있으면
     * (화면은 줄바꿈을 바꿔 보여 줄 수 있고, 말풍선 칸에 '읽음' 같은 글자가 같이 들어 있을 수 있음 — reply/Conversation.shows와 같음)
     */
    private fun same(app: String, screen: String): Boolean {
        val a = app.filter { !it.isWhitespace() }
        return a.isNotEmpty() && screen.filter { !it.isWhitespace() }.contains(a)
    }

    /** 개발용: 당근 화면·알림에서 가져온 메시지를 모두 지움 (가져온 지원자를 지울 때 남는 메시지가 없게). 지운 수를 돌려줌 */
    suspend fun deleteImported(db: AppDatabase): Int =
        db.messageDao().deleteByKeyPrefix("$KEY|") + db.messageDao().deleteByKeyPrefix("${ChatNotify.KEY}|")
}
