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
}

@Dao
interface MessageDao {
    @Insert
    suspend fun insert(message: Message): Long

    /** 한 지원자의 대화 (오래된 것부터) */
    @Query("SELECT * FROM messages WHERE applicantId = :applicantId ORDER BY time")
    fun observeFor(applicantId: Long): Flow<List<Message>>
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
