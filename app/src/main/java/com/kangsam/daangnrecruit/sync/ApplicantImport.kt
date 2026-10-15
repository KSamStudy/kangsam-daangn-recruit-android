package com.kangsam.daangnrecruit.sync

import com.kangsam.daangnrecruit.AppDatabase
import com.kangsam.daangnrecruit.Applicant
import com.kangsam.daangnrecruit.ApplicantStatus
import com.kangsam.daangnrecruit.HoldReason

/**
 * 당근 '지원자 목록' 화면에서 읽은 지원자를 앱 지원자 표에 맞춤 (개발 5단계). 당근에는 아무것도 하지 않음.
 * 새 지원자는 'N' 표시(안 읽음)로 봄 (10/9 설계 메모): 'N' → 새 지원,
 * 그 밖(연락중·연락 대기중 — 담당자가 이미 지원서를 본 사람) → 담당자 응대 중 (앱이 먼저 연락하지 않게)
 */
object ApplicantImport {
    /** 화면에서 가져온 지원자의 sourceKey 앞부분 (알림으로 들어온 줄과 구분) */
    const val SOURCE = "screen"

    suspend fun importFromScreen(db: AppDatabase, list: ScreenApplicantList, now: Long = System.currentTimeMillis()): String {
        val dao = db.applicantDao()
        // 이 공고가 앱에 올라가 있는 글로 있으면 거기에 붙이고, 없으면 '공고 못 찾음' 보류 (공고를 가져오면 그때 붙음)
        val posting = db.postDao().findByTitle(list.postTitle).singleOrNull { it.status == "게시됨" }
        var added = 0
        var changed = 0
        for (s in list.applicants) {
            val status = if (s.unread) ApplicantStatus.NEW else ApplicantStatus.STAFF_HANDLING
            val reason = if (s.unread) "" else "당근 상태: ${s.daangnStatus.ifBlank { "읽음" }}"

            // 이미 있는 사람 (알림으로 들어왔거나 전에 화면에서 가져옴): 빈 동네만 채우고,
            // 앱이 아직 아무것도 안 한 '새 지원'인데 당근에선 담당자가 이미 봤으면 → 담당자 응대 중
            val existing = dao.findByTitleAndName(list.postTitle, s.name)
            if (existing.isNotEmpty()) {
                for (e in existing) {
                    var u = e.copy(area = e.area.ifBlank { s.area })
                    if (!s.unread && e.status == ApplicantStatus.NEW) {
                        u = u.copy(status = ApplicantStatus.STAFF_HANDLING, reason = reason)
                    }
                    if (u != e) {
                        dao.update(u)
                        changed++
                    }
                }
                continue
            }

            val base = Applicant(
                name = s.name, area = s.area, postTitle = list.postTitle, status = status, reason = reason,
                appliedAt = MyPostParser.estimatePostedAt(s.appliedAgo, now) ?: now,
                sourceKey = "$SOURCE|${list.postTitle}|${s.name}|${s.ageGender}|${s.area}",
                rawText = "[지원자 목록] " + listOf(
                    if (s.unread) "N" else s.daangnStatus, s.ageGender, s.area, "${s.appliedAgo} 지원",
                ).filter { it.isNotBlank() }.joinToString(" · "),
            )
            val row = if (posting != null) {
                base.copy(standingPostId = posting.standingPostId, postingId = posting.id)
            } else {
                base.copy(status = ApplicantStatus.HOLD, statusBeforeHold = status, reason = HoldReason.POST_NOT_FOUND)
            }
            if (dao.insert(row) > 0) added++
        }
        val unread = list.applicants.count { it.unread }
        return "'${list.postTitle}' ${list.tabText} — 카드 ${list.applicants.size}개 읽음 (안 읽음 ${unread}) · 새로 넣음 ${added}명" +
            (if (changed > 0) " · 고침 ${changed}명" else "") +
            (if (posting == null) " · 앱에 이 공고가 없어 '공고 못 찾음'으로 둠" else "")
    }

    /** 개발용: 화면에서 가져온 지원자를 모두 지움. 지운 수를 돌려줌 */
    suspend fun deleteImported(db: AppDatabase): Int = db.applicantDao().deleteBySourcePrefix("$SOURCE|")
}
