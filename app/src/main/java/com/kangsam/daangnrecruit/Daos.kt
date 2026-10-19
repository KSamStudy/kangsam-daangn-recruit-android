package com.kangsam.daangnrecruit

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

// 폰 안 DB를 읽고 쓰는 곳. Flow로 읽는 것은 DB가 바뀌면 화면이 자동으로 다시 그려짐

@Dao
interface AccountDao {
    @Insert
    suspend fun insert(account: Account): Long

    @Query("SELECT * FROM accounts ORDER BY id")
    fun observeAll(): Flow<List<Account>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun get(id: Long): Account?

    /** 계정 모두 (한 번 읽음 — 계정 맞추기·계정 상태 기록) */
    @Query("SELECT * FROM accounts ORDER BY id")
    suspend fun all(): List<Account>

    @Update
    suspend fun update(account: Account)

    /** 계정 상태 기록 한 줄 (경고·정지 — Safety) */
    @Insert
    suspend fun insertEvent(event: AccountEvent): Long

    /** 한 계정의 상태 기록 (최근 것부터) */
    @Query("SELECT * FROM account_events WHERE accountId = :accountId ORDER BY time DESC")
    suspend fun eventsOf(accountId: Long): List<AccountEvent>
}

@Dao
interface PostDao {
    @Insert
    suspend fun insertStandingPost(post: StandingPost): Long

    @Insert
    suspend fun insertPosting(posting: Posting): Long

    @Query("UPDATE standing_posts SET currentPostingId = :postingId WHERE id = :standingPostId")
    suspend fun setCurrentPosting(standingPostId: Long, postingId: Long)

    @Update
    suspend fun updateStandingPost(post: StandingPost)

    @Update
    suspend fun updatePosting(posting: Posting)

    @Query("SELECT * FROM standing_posts WHERE id = :id")
    suspend fun getStandingPost(id: Long): StandingPost?

    @Query("SELECT * FROM standing_posts WHERE title = :title")
    suspend fun standingPostsWithTitle(title: String): List<StandingPost>

    /** 한 상시 공고의 게시물 (최근 회차가 위로) */
    @Query("SELECT * FROM postings WHERE standingPostId = :standingPostId ORDER BY round DESC")
    suspend fun postingsOf(standingPostId: Long): List<Posting>

    @Query("SELECT * FROM standing_posts ORDER BY id")
    fun observeStandingPosts(): Flow<List<StandingPost>>

    @Query("SELECT * FROM postings ORDER BY standingPostId, round DESC")
    fun observePostings(): Flow<List<Posting>>

    /** 지금 올라가 있는 게시물 (확인 필요의 '공고 고르기') */
    @Query("SELECT * FROM postings WHERE status = '게시됨' ORDER BY postedAt DESC")
    fun observeLivePostings(): Flow<List<Posting>>

    /** 공고 쪽 확인 필요: 검사 걸림·결과 불명 */
    @Query("SELECT * FROM postings WHERE status IN ('검사 걸림', '결과 불명') ORDER BY id DESC")
    fun observeProblemPostings(): Flow<List<Posting>>

    /** 공고명이 같은 게시물 (공고명 규칙으로 지원을 붙일 때) */
    @Query("SELECT * FROM postings WHERE title = :title")
    suspend fun findByTitle(title: String): List<Posting>

    /** 사유가 이 글자로 시작하는 게시물 (개발용: '내 공고' 화면에서 가져온 것 찾기) */
    @Query("SELECT * FROM postings WHERE reason LIKE :prefix || '%'")
    suspend fun postingsWithReasonPrefix(prefix: String): List<Posting>

    /** 계정 칸이 빈 상시 공고에 이 계정 (예시는 빼고 — 계정 맞추기). 바꾼 수 */
    @Query("UPDATE standing_posts SET accountId = :accountId WHERE accountId IS NULL AND isSample = 0")
    suspend fun fillStandingAccount(accountId: Long): Int

    /** 계정 칸이 빈 게시물에 이 계정 (예시는 빼고 — 계정 맞추기). 바꾼 수 */
    @Query("UPDATE postings SET accountId = :accountId WHERE accountId IS NULL AND isSample = 0")
    suspend fun fillPostingAccount(accountId: Long): Int

    /** 이 때 사이에 앱이 올린 게시물 수 (계정 상태 기록의 '직전 앱 동작') */
    @Query("SELECT COUNT(*) FROM postings WHERE postedAt BETWEEN :since AND :until AND postedBy IN ('앱 자동', '지금 올리기')")
    suspend fun appPostedBetween(since: Long, until: Long): Int

    @Query("UPDATE standing_posts SET currentPostingId = NULL WHERE currentPostingId IN (:postingIds)")
    suspend fun clearCurrentPosting(postingIds: List<Long>)

    @Query("DELETE FROM postings WHERE id IN (:ids)")
    suspend fun deletePostingsById(ids: List<Long>): Int

    @Query("DELETE FROM standing_posts WHERE id IN (:ids)")
    suspend fun deleteStandingPostsById(ids: List<Long>): Int
}

@Dao
interface ApplicantDao {
    /** 같은 sourceKey가 이미 있으면 저장하지 않고 -1을 돌려줌 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(applicant: Applicant): Long

    /** 지원자 목록 (최근 대화·지원이 위로) */
    @Query("SELECT * FROM applicants ORDER BY COALESCE(lastMessageAt, appliedAt) DESC")
    fun observeAll(): Flow<List<Applicant>>

    @Query("SELECT * FROM applicants WHERE id = :id")
    fun observe(id: Long): Flow<Applicant?>

    @Update
    suspend fun update(applicant: Applicant)

    /** 상태로 고르기 (확인 필요: 보류·담당자 확인) */
    @Query("SELECT * FROM applicants WHERE status IN (:statuses) ORDER BY appliedAt DESC")
    fun observeByStatus(statuses: List<String>): Flow<List<Applicant>>

    @Query("SELECT * FROM applicants WHERE postTitle = :title AND status = :status")
    suspend fun findByTitleAndStatus(title: String, status: String): List<Applicant>

    /** 같은 사람: 연락처 키가 같은 다른 지원 건 */
    @Query("SELECT * FROM applicants WHERE phoneKey = :phoneKey AND phoneKey != '' AND id != :exceptId ORDER BY appliedAt")
    fun observeSamePerson(phoneKey: String, exceptId: Long): Flow<List<Applicant>>

    /** 연락 중 표시 (지원자 상세의 '직접 연락하기'). 빈 값이면 표시를 품 */
    @Query("UPDATE applicants SET contactingMark = :mark WHERE id = :id")
    suspend fun setContactingMark(id: Long, mark: String)

    /** 개발용: 테스트 알림(adb)으로 들어간 줄을 지움. 지운 줄 수를 돌려줌 */
    @Query("DELETE FROM applicants WHERE sourceKey LIKE 'com.android.shell|%' OR rawText LIKE '테스트%'")
    suspend fun deleteTestRows(): Int

    /** 개발용: 지운 게시물에 붙어 있던 지원자를 다시 보류로 (다시 가져오면 다시 붙음). 이미 보류·합침인 줄은 그대로 */
    @Query(
        "UPDATE applicants SET statusBeforeHold = status, status = :hold, reason = :reason, " +
            "standingPostId = NULL, postingId = NULL WHERE postingId IN (:postingIds) AND status NOT IN (:hold, :merged)"
    )
    suspend fun holdForDeletedPostings(postingIds: List<Long>, hold: String, reason: String, merged: String): Int

    /** 공고명·이름이 같은 지원 건 (화면에서 가져올 때 이미 있는 사람인지 봄) */
    @Query("SELECT * FROM applicants WHERE postTitle = :title AND name = :name")
    suspend fun findByTitleAndName(title: String, name: String): List<Applicant>

    /** 아직 공고(게시물)를 못 붙인 지원 건 중 공고명이 같은 것 — '공고 못 찾음'으로 기다리는 줄 (같은 지원인지 볼 때) */
    @Query("SELECT * FROM applicants WHERE postingId IS NULL AND postTitle = :title")
    suspend fun unlinked(title: String): List<Applicant>

    /** 지원 키로 한 줄 (같은 알림이 두 번 왔는지) */
    @Query("SELECT * FROM applicants WHERE sourceKey = :key")
    suspend fun bySourceKey(key: String): Applicant?

    /** 한 게시물(회차)에 붙은 지원 건 (당근 지원자 목록과 맞출 때 — 목록에서 빠진 사람·수 비교) */
    @Query("SELECT * FROM applicants WHERE postingId = :postingId")
    suspend fun byPosting(postingId: Long): List<Applicant>

    /** 개발용: sourceKey가 이 글자로 시작하는 줄을 지움 (화면에서 가져온 지원자 지우기). 지운 줄 수를 돌려줌 */
    @Query("DELETE FROM applicants WHERE sourceKey LIKE :prefix || '%'")
    suspend fun deleteBySourcePrefix(prefix: String): Int

    /** 마지막 대화 시각을 더 늦은 값으로만 고침 */
    @Query("UPDATE applicants SET lastMessageAt = :time WHERE id = :id AND (lastMessageAt IS NULL OR lastMessageAt < :time)")
    suspend fun touchLastMessage(id: Long, time: Long)

    /** 예시가 아닌 지원자 (당근 가져오기가 열 채팅방을 고를 때, 알림으로 온 메시지를 붙일 때) */
    @Query("SELECT * FROM applicants WHERE isSample = 0")
    suspend fun nonSample(): List<Applicant>

    /** 지원자 한 명 (첫 메시지 드라이런) */
    @Query("SELECT * FROM applicants WHERE id = :id")
    suspend fun get(id: Long): Applicant?

    /** 개발용: 예시 지원자 (adb 테스트 알림으로 온 메시지는 예시에만 붙임) */
    @Query("SELECT * FROM applicants WHERE isSample = 1")
    suspend fun samples(): List<Applicant>

    /** 연락처 키가 같은 다른 지원 건 (번호를 받을 때 합치기 확인 — ContactFlow) */
    @Query("SELECT * FROM applicants WHERE phoneKey = :phoneKey AND phoneKey != '' AND id != :exceptId ORDER BY appliedAt")
    suspend fun samePhone(phoneKey: String, exceptId: Long): List<Applicant>

    /** 연락처가 빈 지원자 (합친 줄은 뺌 — 지난 대화에서 번호 채우기) */
    @Query("SELECT * FROM applicants WHERE phoneKey = '' AND status != :merged")
    suspend fun withoutPhone(merged: String): List<Applicant>
}

@Dao
interface MessageDao {
    @Insert
    suspend fun insert(message: Message): Long

    /** 같은 메시지 키가 이미 있으면 저장하지 않고 -1 (당근 화면·알림에서 가져온 대화) */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(message: Message): Long

    @Update
    suspend fun update(message: Message)

    /** 이 메시지 키가 있는 줄 수 (0이면 없음) */
    @Query("SELECT COUNT(*) FROM messages WHERE messageKey = :key")
    suspend fun countByKey(key: String): Int

    /**
     * 같은 지원자·보낸 쪽·내용이고 시각이 fromTime~toTime 사이이며 메시지 키가 prefix로 시작하는 줄 —
     * 알림과 화면에서 각각 들어온 같은 메시지를 찾을 때 (ChatNotify·ChatImport)
     */
    @Query(
        "SELECT * FROM messages WHERE applicantId = :applicantId AND sender = :sender AND content = :content " +
            "AND time BETWEEN :fromTime AND :toTime AND messageKey LIKE :prefix || '%' ORDER BY time"
    )
    suspend fun findSame(applicantId: Long, sender: String, content: String, fromTime: Long, toTime: Long, prefix: String): List<Message>

    /** 개발용: 메시지 키가 이 글자로 시작하는 줄을 지움. 지운 줄 수를 돌려줌 */
    @Query("DELETE FROM messages WHERE messageKey LIKE :prefix || '%'")
    suspend fun deleteByKeyPrefix(prefix: String): Int

    /** 한 지원자의 대화 (오래된 것부터) */
    @Query("SELECT * FROM messages WHERE applicantId = :applicantId ORDER BY time")
    fun observeFor(applicantId: Long): Flow<List<Message>>

    /** 이 시각 뒤의 대화 (최근 것부터 — 홈 '오늘'·'최근 활동') */
    @Query("SELECT * FROM messages WHERE time >= :since ORDER BY time DESC")
    fun observeSince(since: Long): Flow<List<Message>>

    /** 한 지원자의 대화를 한 번 읽음 (오래된 것부터 — 첫 메시지 드라이런) */
    @Query("SELECT * FROM messages WHERE applicantId = :applicantId ORDER BY time")
    suspend fun listFor(applicantId: Long): List<Message>

    /** 이 지원자의 처리 전(앱 처리 빈 칸) 지원자 메시지를 모두 '넘김'으로 — 앱이 응대하지 않게 됐을 때 (ApplicantStore.settle). 바꾼 수 */
    @Query("UPDATE messages SET appHandling = '넘김' WHERE applicantId = :applicantId AND sender = '지원자' AND appHandling = ''")
    suspend fun handOverPending(applicantId: Long): Int

    /** 처리 전 지원자 메시지가 있는 지원자 (실제 발송 시험을 끌 때 한 번에 맞춤) */
    @Query("SELECT DISTINCT applicantId FROM messages WHERE sender = '지원자' AND appHandling = ''")
    suspend fun pendingApplicantIds(): List<Long>

    /** 이 때 사이에 앱이 이 채널로 보낸 메시지 수 (결과 불명 포함 — 계정 상태 기록의 '직전 앱 동작') */
    @Query("SELECT COUNT(*) FROM messages WHERE sender = '우리' AND sendMethod IN ('앱 자동', '결과 불명') AND channel = :channel AND time BETWEEN :since AND :until")
    suspend fun appSentBetween(channel: String, since: Long, until: Long): Int

    /** 이 방법으로 보낸 최근 메시지 (최근 것부터 — 앱이 보낸 문구와 같은 문구를 피할 때) */
    @Query("SELECT * FROM messages WHERE sendMethod = :method ORDER BY time DESC LIMIT :limit")
    suspend fun recentByMethod(method: String, limit: Int): List<Message>
}

@Dao
interface ErrorLogDao {
    @Insert
    suspend fun insert(error: ErrorLog)

    @Query("SELECT * FROM error_logs ORDER BY time DESC LIMIT 50")
    fun observeRecent(): Flow<List<ErrorLog>>
}

@Dao
interface CheckDao {
    /** 확인 필요 건수 = 보류·담당자 확인 지원자 + 검사 걸림·결과 불명 게시물 (확인 탭 숫자) */
    @Query(
        "SELECT COUNT(*) FROM (" +
            "SELECT id FROM applicants WHERE status IN ('보류', '담당자 확인') " +
            "UNION ALL SELECT id FROM postings WHERE status IN ('검사 걸림', '결과 불명'))"
    )
    fun observeCount(): Flow<Int>
}

/** 개발용: 예시 데이터 세기·지우기 */
@Dao
interface SampleDao {
    @Query("SELECT COUNT(*) FROM applicants WHERE isSample = 1")
    suspend fun countSampleApplicants(): Int

    @Query("DELETE FROM messages WHERE isSample = 1")
    suspend fun deleteMessages(): Int

    @Query("DELETE FROM applicants WHERE isSample = 1")
    suspend fun deleteApplicants(): Int

    @Query("DELETE FROM postings WHERE isSample = 1")
    suspend fun deletePostings(): Int

    @Query("DELETE FROM standing_posts WHERE isSample = 1")
    suspend fun deleteStandingPosts(): Int

    @Query("DELETE FROM account_events WHERE isSample = 1")
    suspend fun deleteAccountEvents(): Int

    @Query("DELETE FROM accounts WHERE isSample = 1")
    suspend fun deleteAccounts(): Int
}
