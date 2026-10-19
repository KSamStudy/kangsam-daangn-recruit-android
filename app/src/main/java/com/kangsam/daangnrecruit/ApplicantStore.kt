package com.kangsam.daangnrecruit

import com.kangsam.daangnrecruit.reply.ReplyScope
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 지원자 줄을 바꾸는 한 곳 (구현 계획 '지키는 것'). 지원자 줄을 고치는 코드는 dao.update 대신 여기를 씀:
 * - update: 줄을 바꾼 뒤 앱이 응대하지 않게 됐으면(넘김·끝·연락 중 표시) 처리 전 메시지를 넘김으로 (DB 설계 '앱 처리 기록').
 *   연락 중 표시는 안 바꿈 (DB 값 그대로 — 번호를 나중에 받은 줄은 같은 번호 줄의 표시를 이어받음)
 * - setContacting: 연락 중 표시를 같은 사람(같은 연락처 키) 줄에 같이 (DB 설계 '지원자' — 같은 사람의 줄끼리 같이 바꿈)
 * - settleAll: 실제 발송 시험을 끄거나 대상이 바뀐 뒤 모든 지원자를 다시 맞춤
 */
object ApplicantStore {
    /** 연락 중 표시의 '어디서': 앱의 '직접 연락하기' (B4-1에서 찾은 곳 — 리니어 티켓·문자함·대표폰 문자 기록 — 은 문자 단계 때) */
    const val FROM_APP_BUTTON = "직접 연락하기"

    private val MARK_TIME = DateTimeFormatter.ofPattern("M/d HH:mm")

    /** 연락 중 표시 글자: 어디서 · 언제 (누가는 안 적음 — DB 설계 v0.10) */
    fun contactMark(where: String, now: Long, zone: ZoneId): String = "$where · ${MARK_TIME.format(Instant.ofEpochMilli(now).atZone(zone))}"

    /** 지원자 줄을 바꿈 + 앱이 응대하지 않게 됐으면 처리 전 메시지를 넘김으로. 연락 중 표시는 바꾸지 않음 (markFor) */
    suspend fun update(db: AppDatabase, a: Applicant) {
        val row = a.copy(contactingMark = markFor(db, a))
        db.applicantDao().update(row)
        settle(db, row)
    }

    /**
     * 이 줄에 둘 연락 중 표시. 표시는 setContacting만 바꿈 — update는 DB에 있는 표시를 그대로 둠
     * (줄을 읽어 둔 사이 사람이 '직접 연락하기'를 눌러도 앱이 옛 줄로 덮어 지우지 않게).
     * 없으면 같은 사람(같은 연락처 키) 줄의 표시를 이어받음 — 번호를 나중에 받은 줄 (DB 설계 '규칙' — 같은 사람 줄끼리 같이)
     */
    private suspend fun markFor(db: AppDatabase, a: Applicant): String {
        val dao = db.applicantDao()
        val current = dao.get(a.id)?.contactingMark ?: a.contactingMark
        if (current.isNotBlank() || a.phoneKey.isBlank()) return current
        return dao.samePhone(a.phoneKey, a.id).firstOrNull { it.contactingMark.isNotBlank() }?.contactingMark ?: ""
    }

    /** 앱이 응대 중인 지원자가 아니면 그 지원자의 처리 전 메시지를 모두 넘김으로. 바꾼 메시지 수 */
    suspend fun settle(db: AppDatabase, a: Applicant): Int =
        if (ReplyScope.handles(a)) 0 else db.messageDao().handOverPending(a.id)

    /** 처리 전 메시지가 있는 지원자를 모두 다시 봄 (실제 발송 시험을 끄거나 대상이 바뀐 뒤). 바꾼 메시지 수 */
    suspend fun settleAll(db: AppDatabase): Int {
        var n = 0
        for (id in db.messageDao().pendingApplicantIds()) {
            val a = db.applicantDao().get(id) ?: continue
            n += settle(db, a)
        }
        return n
    }

    /**
     * 연락 중 표시를 이 지원자와 같은 사람(같은 연락처 키)의 줄에 같이 — where가 null이면 표시를 풂.
     * 표시가 생기면 앱이 응대하지 않으므로 처리 전 메시지는 넘김으로. 바꾼 줄 수를 돌려줌 (연락처가 없으면 이 줄만)
     */
    suspend fun setContacting(db: AppDatabase, a: Applicant, where: String?, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Int {
        val mark = where?.let { contactMark(it, now, zone) } ?: ""
        val dao = db.applicantDao()
        val rows = samePerson(db, a)
        for (r in rows) {
            dao.setContactingMark(r.id, mark)
            settle(db, r.copy(contactingMark = mark))
        }
        return rows.size
    }

    /** 이 지원자와 같은 사람의 줄 (자기 줄 포함 — 연락처가 없으면 자기 줄만) */
    suspend fun samePerson(db: AppDatabase, a: Applicant): List<Applicant> {
        val dao = db.applicantDao()
        val self = dao.get(a.id) ?: a
        if (self.phoneKey.isBlank()) return listOf(self)
        return listOf(self) + dao.samePhone(self.phoneKey, self.id)
    }
}
