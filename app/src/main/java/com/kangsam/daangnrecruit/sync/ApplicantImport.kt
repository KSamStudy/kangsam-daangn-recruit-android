package com.kangsam.daangnrecruit.sync

import com.kangsam.daangnrecruit.AppDatabase
import com.kangsam.daangnrecruit.Applicant
import com.kangsam.daangnrecruit.ApplicantStatus
import com.kangsam.daangnrecruit.HoldReason

/**
 * 당근 '지원자 목록' 화면에서 읽은 지원자를 앱 지원자 표에 맞춤 (개발 5단계). 당근에는 아무것도 하지 않음.
 * 새 지원자는 'N' 표시(안 읽음)로 봄 (10/9 설계 메모): 'N' → 새 지원,
 * 그 밖(연락중·연락 대기중 — 담당자가 이미 지원서를 본 사람) → 담당자 응대 중 (앱이 먼저 연락하지 않게)
 *
 * 공고별로 맞춤: 지원자 목록 화면 하나 = 공고 하나(지금 올라가 있는 글)의 지원자 → 그 글(게시물·회차)에 붙임.
 * 이미 있는 사람은 이 글에 붙은 줄이나 아직 글을 못 찾은 줄('공고 못 찾음')만 같은 사람으로 봄
 * (같은 공고명이라도 지난 회차의 지원은 다른 지원 건). 글을 못 찾은 줄은 이 글에 붙이고 '공고 못 찾음' 보류를 풂.
 *
 * 10/18 더함 (폰 DB와 당근 목록을 비교해 찾은 빈 곳):
 * - 목록을 다 읽었을 때(탭의 수 = 카드 수) 이 글에 붙은 줄인데 목록에 없는 사람 → '종료' (사유가 GONE으로 시작). 다시 보이면 되돌림
 *   - 새 지원: 당근이 안 읽은 지원서를 며칠 뒤 정리함 → 앱이 만료된 사람에게 첫 메시지를 보내려 하지 않게 (EXPIRED)
 *   - 담당자 응대 중: 담당자가 '다음 기회에'로 옮겼거나 지원자가 취소함 (GONE_READ) — 앱에 보이는 지원자 수가 당근과 같게 (재걸 결정 10/18)
 *   - 앱이 응대 중인 사람(첫 메시지 보냄 ~ 넘김 등)은 상태를 바꾸지 않고 결과 줄에 '당근 목록에 없음'으로 알림
 *   - 지원한 지 30분이 안 된 사람은 목록에 아직 안 떴을 수 있어 그대로 둠
 * - 결과 줄에 이 글의 앱 기록 수와 당근 탭의 수를 견줌 (같으면 '당근과 같음')
 * - 알림이 묶여 늦게 와 지원 시각이 늦게 적힌 사람(10/18 00:00에 몰려 옴) → 목록의 'N분·N시간 전'으로 고침 ('N일 전'은 거칠어서 안 씀)
 */
object ApplicantImport {
    /** 화면에서 가져온 지원자의 sourceKey 앞부분 (알림으로 들어온 줄과 구분) */
    const val SOURCE = "screen"

    /** 당근 지원자 목록에서 빠져 '종료'로 둔 사람의 사유 앞부분 (종료는 앱 화면에 안 보임 — ApplicantView) */
    const val GONE = "당근 지원자 목록에 없음"

    /** 목록에서 빠진 '새 지원'의 종료 사유 */
    const val EXPIRED = "$GONE — 안 읽은 지원서 만료(3일) 또는 '다음 기회에'로 보임"

    /** 목록에서 빠진 '담당자 응대 중'의 종료 사유 */
    const val GONE_READ = "$GONE — 담당자가 '다음 기회에'로 옮겼거나 지원자가 취소한 것으로 보임"

    /** 목록에서 빠지면 '종료'로 두는 상태 (앱이 아직 아무것도 안 한 상태만) */
    private val ENDS_WHEN_GONE = setOf(ApplicantStatus.NEW, ApplicantStatus.STAFF_HANDLING)

    /** 알림(ApplyDetector)으로 들어온 지원자의 지원 키 앞부분 */
    private const val FROM_NOTIFICATION = "com.towneers.www|"
    private const val FINE_AGO_MS = 24 * 3_600_000L // 지원 시각 고칠 때 쓰는 'N분·N시간 전'(하루 안쪽)만
    private const val FIX_GAP_MS = 3_600_000L        // 이만큼 넘게 늦게 적혔을 때만 고침
    private const val JUST_APPLIED_MS = 30 * 60_000L // 막 들어온 지원은 목록에 아직 안 보일 수 있어 종료하지 않음

    /** 당근 지원자 목록에서 빠져 '종료'로 둔 사람인지 */
    fun isGone(a: Applicant): Boolean = a.status == ApplicantStatus.ENDED && a.reason.startsWith(GONE)

    suspend fun importFromScreen(db: AppDatabase, list: ScreenApplicantList, now: Long = System.currentTimeMillis()): String {
        val dao = db.applicantDao()
        // 이 공고가 앱에 올라가 있는 글로 있으면 거기에 붙이고, 없으면 '공고 못 찾음' 보류 (공고를 가져오면 그때 붙음)
        val posting = db.postDao().findByTitle(list.postTitle).singleOrNull { it.status == "게시됨" }
        var added = 0
        var changed = 0
        for (s in list.applicants) {
            val status = if (s.unread) ApplicantStatus.NEW else ApplicantStatus.STAFF_HANDLING
            val reason = if (s.unread) "" else "당근 상태: ${s.daangnStatus.ifBlank { "읽음" }}"

            // 이미 있는 사람 (알림으로 들어왔거나 전에 화면에서 가져옴) — 이 글에 붙은 줄이나 아직 글을 못 찾은 줄만
            val existing = dao.findByTitleAndName(list.postTitle, s.name)
                .filter { posting == null || it.postingId == null || it.postingId == posting.id }
            if (existing.isNotEmpty()) {
                for (e in existing) {
                    var u = e.copy(area = e.area.ifBlank { s.area })
                    // 앱이 아직 아무것도 안 한 '새 지원'인데 당근에선 담당자가 이미 봤으면 → 담당자 응대 중
                    if (!s.unread && e.status == ApplicantStatus.NEW) {
                        u = u.copy(status = ApplicantStatus.STAFF_HANDLING, reason = reason)
                    }
                    // 목록에서 빠져 '종료'로 뒀던 사람이 다시 보이면 되돌림
                    if (isGone(e)) u = u.copy(status = status, reason = reason)
                    // 아직 글을 못 찾은 줄이면 이 글에 붙이고, '공고 못 찾음' 보류였으면 풂
                    if (posting != null && e.postingId == null) {
                        u = u.copy(standingPostId = posting.standingPostId, postingId = posting.id)
                        if (e.status == ApplicantStatus.HOLD && e.reason.startsWith(HoldReason.POST_NOT_FOUND)) {
                            u = u.copy(status = status, statusBeforeHold = "", reason = reason)
                        }
                    }
                    // 알림이 묶여 늦게 와서 지원 시각이 늦게 적혔으면 목록의 'N분·N시간 전'으로 고침
                    val estimate = MyPostParser.estimatePostedAt(s.appliedAgo, now)
                    if (e.sourceKey.startsWith(FROM_NOTIFICATION) && estimate != null &&
                        now - estimate < FINE_AGO_MS && e.appliedAt - estimate > FIX_GAP_MS
                    ) {
                        u = u.copy(appliedAt = estimate)
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

        // 목록을 다 읽었으면(탭의 수만큼) 이 글의 앱 기록과 맞춤: 목록에 없는 사람 → 종료, 그리고 수 견주기
        val want = list.tabText.filter { it.isDigit() }.toIntOrNull()
        var gone = 0
        var compared = ""
        if (posting != null && want != null && want == list.applicants.size) {
            val names = list.applicants.map { it.name }.toSet()
            for (e in dao.byPosting(posting.id)) {
                if (e.isSample || e.name in names || e.status !in ENDS_WHEN_GONE || now - e.appliedAt < JUST_APPLIED_MS) continue
                dao.update(e.copy(status = ApplicantStatus.ENDED, reason = if (e.status == ApplicantStatus.NEW) EXPIRED else GONE_READ))
                gone++
            }
            val rows = dao.byPosting(posting.id).filter { !it.isSample && !isGone(it) }
            // 방금 지원해 목록에 아직 안 뜬 사람은 빼고 견줌 (다음에 목록을 읽으면 맞춰짐)
            val waiting = rows.filter { it.name !in names && now - it.appliedAt < JUST_APPLIED_MS }
            compared = compare(rows - waiting.toSet(), names, want) +
                (if (waiting.isNotEmpty()) " · 방금 지원해 목록에 아직 없는 ${waiting.size}명은 빼고 셈" else "")
        }
        val unread = list.applicants.count { it.unread }
        return "'${list.postTitle}' ${list.tabText} — 카드 ${list.applicants.size}개 읽음 (안 읽음 ${unread}) · 새로 넣음 ${added}명" +
            (if (changed > 0) " · 고침 ${changed}명" else "") +
            (if (gone > 0) " · 당근 목록에 없어 종료 ${gone}명" else "") +
            compared +
            (if (posting == null) " · 앱에 이 공고가 없어 '공고 못 찾음'으로 둠" else "")
    }

    /** 이 글의 앱 기록(목록에서 빠진 사람 빼고)과 당근 탭의 수를 견준 한 줄. 다르면 당근 목록에 없는 사람을 적음 */
    private fun compare(rows: List<Applicant>, names: Set<String>, want: Int): String {
        if (rows.size == want) return " · 앱 기록도 ${want}명 (당근과 같음)"
        val missing = rows.filter { it.name !in names }.map { "${it.name}(${it.status})" }
        return " · 앱 기록 ${rows.size}명 / 당근 ${want}명 — 다름" +
            (if (missing.isNotEmpty()) " (당근 목록에 없음: ${missing.joinToString(", ")})" else "")
    }

    /** 개발용: 화면에서 가져온 지원자를 모두 지움. 지운 수를 돌려줌 */
    suspend fun deleteImported(db: AppDatabase): Int = db.applicantDao().deleteBySourcePrefix("$SOURCE|")
}
