package com.kangsam.daangnrecruit.sync

import com.kangsam.daangnrecruit.AppDatabase
import com.kangsam.daangnrecruit.Message

/**
 * 채팅방에서 읽은 메시지를 대화 표(DB 설계 '대화')에 남김 (개발 5단계). 당근에는 아무것도 하지 않음.
 * - 방 위쪽의 공고명 + 상대 이름으로 지원자 줄을 찾음 (없으면 건너뜀)
 * - 같은 메시지는 두 번 저장하지 않음: 메시지 키 = 지원자·시각·보낸 쪽·내용 (같은 분에 같은 말이면 차례 번호)
 * - 알림으로 먼저 받은 지원자 메시지(ChatNotify)와 같은 것이면 새로 넣지 않고 화면의 시각·키로 고침 (10/18)
 * - 우리 메시지는 보낸 방법 '직접 보냄'(사람이 보냄). 지원자 메시지는 앱 처리 '넘김' — 담당자가 처리한 지난 대화라 앱이 답하지 않게
 */
object ChatImport {
    /** 화면에서 가져온 메시지 키 앞부분 */
    const val KEY = "daangn"

    suspend fun save(db: AppDatabase, header: ChatRoomHeader, lines: List<ChatLine>, skipped: Int): String {
        val applicant = db.applicantDao().findByTitleAndName(header.postTitle, header.name).maxByOrNull { it.appliedAt }
            ?: return "'${header.name}' 방 — '${header.postTitle}' 지원자가 앱에 없어 건너뜀"
        val dao = db.messageDao()
        var added = 0
        var fixed = 0
        val seen = HashMap<String, Int>()
        for (l in lines) {
            val sender = if (l.ours) "우리" else "지원자"
            val base = "$KEY|${applicant.id}|${l.time}|$sender|${l.text.length}|${l.text.hashCode()}"
            val n = (seen[base] ?: 0) + 1
            seen[base] = n
            val key = "$base|$n"
            if (dao.countByKey(key) > 0) continue // 전에 화면에서 가져옴
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
            (if (skipped > 0) " · 날짜를 몰라 뺀 것 ${skipped}개" else "")
    }

    /** 개발용: 당근 화면·알림에서 가져온 메시지를 모두 지움 (가져온 지원자를 지울 때 남는 메시지가 없게). 지운 수를 돌려줌 */
    suspend fun deleteImported(db: AppDatabase): Int =
        db.messageDao().deleteByKeyPrefix("$KEY|") + db.messageDao().deleteByKeyPrefix("${ChatNotify.KEY}|")
}
