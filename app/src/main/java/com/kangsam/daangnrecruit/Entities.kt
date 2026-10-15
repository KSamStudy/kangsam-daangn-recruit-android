package com.kangsam.daangnrecruit

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// DB 설계 v0.6의 표를 그대로 옮김. 열 이름은 영어, 뜻은 주석(문서의 열 이름)
// isSample = 개발용 예시 데이터 표시. 설정 → 알림 기록 (개발용)에서 한 번에 지움

/** 1. 계정 */
@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0, // 계정ID
    val alias: String,                  // 별칭 (예: 구인1)
    val nickname: String = "",          // 당근 닉네임
    val device: String = "",            // 기기
    val verifiedArea: String = "",      // 인증 동네
    val verificationType: String = "",  // 인증 종류: 개인 / 사업자 / 담당자(재직)
    val status: String = "사용 중",      // 상태: 사용 중 / 쉬는 중 / 경고 / 정지
    val dailyPostLimit: Int = 1,        // 한도: 하루 게시 수
    val concurrentPostLimit: Int = 1,   // 한도: 동시 게시 수
    val dailyMessageLimit: Int = 30,    // 한도: 하루 당근 메시지 수
    val isSample: Boolean = false,
)

/** 2. 계정 상태 기록 */
@Entity(tableName = "account_events", indices = [Index("accountId")])
data class AccountEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0, // 기록ID
    val accountId: Long,
    val time: Long,
    val kind: String,                   // 종류: 경고 / 정지 / 해제 / 이의 신청
    val daangnMessage: String = "",     // 당근 안내 문구
    val recentActivity: String = "",    // 직전 앱 동작 (그 전 하루 게시·발송 횟수)
    val isSample: Boolean = false,
)

/** 3. 상시 공고 (반복해서 올릴 공고 1개) */
@Entity(tableName = "standing_posts")
data class StandingPost(
    @PrimaryKey(autoGenerate = true) val id: Long = 0, // 상시공고ID
    val name: String,                   // 이름 (예: 송도 · 중등 수학)
    val category: String = "",          // 성격 (일 종류 + 과목)
    val area: String = "",              // 값: 동네(근무지)
    val targetGrade: String = "",       // 값: 대상 학년
    val schedule: String = "",          // 값: 요일·시간대
    val pay: String = "",               // 값: 급여
    val teachingMode: String = "",      // 값: 수업 방식
    val title: String = "",             // 제목 (당근 공고명)
    val body: String = "",              // 본문
    val textVersion: Int = 1,           // 문구 버전
    val approvedBy: String = "",        // 승인: 누가
    val approvedAt: Long? = null,       // 승인: 언제
    val accountId: Long? = null,        // 올리는 계정
    val postWhen: String = "마감되면",   // 게시 규칙: 올리는 때 (마감되면 / 주기마다 / 직접 올릴 때만)
    val waitAfterCloseMinutes: Int = 0, // 게시 규칙: 마감 후 기다리는 시간(분)
    val periodText: String = "",        // 게시 규칙: 주기·시각 (주기마다일 때)
    val whenPreviousRemains: String = "", // 게시 규칙: 주기 때 이전 글이 남아 있으면 (그대로 둠 / 앱이 마감하고 새로)
    val bump: String = "안 함",          // 게시 규칙: 끌어올리기 (안 함 / 간격)
    val appCloseCondition: String = "없음", // 게시 규칙: 앱이 마감하는 조건
    val skipCondition: String = "",     // 게시 규칙: 건너뛰기 조건
    val linearTicket: String = "",      // 넘길 구인 티켓 (기본값)
    val status: String = "꺼짐",         // 상태: 켜짐 / 꺼짐
    val nextPostAt: Long? = null,       // 다음 게시 시각 (앱이 채움)
    val currentPostingId: Long? = null, // 지금 올라간 게시물ID (앱이 채움)
    val isSample: Boolean = false,
)

/** 4. 게시물 (회차마다 한 줄) */
@Entity(tableName = "postings", indices = [Index("standingPostId")])
data class Posting(
    @PrimaryKey(autoGenerate = true) val id: Long = 0, // 게시물ID
    val standingPostId: Long,
    val round: Int,                     // 회차
    val scheduledAt: Long? = null,      // 예정 시각
    val accountId: Long? = null,        // 실제로 올린(올리려던) 계정
    val textVersion: Int = 1,           // 그때 쓴 문구 버전
    val title: String = "",             // 공고명 (지원을 이 글에 붙이는 기준)
    val body: String = "",              // 본문
    val postedBy: String = "",          // 올린 방법: 앱 자동 / 지금 올리기 / 사람이 올림
    val postedAt: Long? = null,         // 게시 시각
    val status: String = "대기",         // 상태: 대기 / 건너뜀 / 검사 걸림 / 결과 불명 / 게시됨 / 숨김 / 자동 마감 / 반려 / 마감
    val watchResult: String = "",       // 30분 지켜보기: 통과 / 숨김 / 자동 마감 / 반려
    val bumpCount: Int = 0,             // 끌어올리기 횟수
    val lastBumpAt: Long? = null,       // 끌어올리기 마지막 시각
    val closedAt: Long? = null,         // 마감 시각
    val closedBy: String = "",          // 마감한 쪽: 담당자 / 앱
    val closeCheckedAt: Long? = null,   // 마감 확인: 때
    val closeCheckMethod: String = "",  // 마감 확인: 방법
    val reason: String = "",            // 사유
    val isSample: Boolean = false,
)

/** 5. 지원자 (지원 건 1개 = 한 줄) */
@Entity(
    tableName = "applicants",
    indices = [Index(value = ["sourceKey"], unique = true), Index("phoneKey")],
)
data class Applicant(
    @PrimaryKey(autoGenerate = true) val id: Long = 0, // 지원자ID
    val standingPostId: Long? = null,   // 상시공고ID (알림 공고명으로 찾은 공고, 못 찾으면 비움)
    val postingId: Long? = null,        // 게시물ID
    val postTitle: String = "",         // 알림 공고명 (지원 알림에 온 그대로)
    val chatRoom: String = "",          // 채팅방 (다시 찾는 값: 계정 + 상대 닉네임)
    val name: String,                   // 당근 이름
    val area: String = "",              // 동네
    val phone: String = "",             // 연락처 (받은 그대로)
    val phoneKey: String = "",          // 연락처 키 (숫자만 남긴 010 번호, 같으면 같은 사람)
    val realName: String = "",          // 성함 (문자로 받은 인적사항)
    val school: String = "",            // 대학·학과
    val experience: String = "",        // 교육 경험
    val availableTime: String = "",     // 가능 요일·시간대
    val availablePeriod: String = "",   // 가능 기간
    val status: String,                 // 상태 (ApplicantStatus)
    val statusBeforeHold: String = "",  // 멈추기 전 상태
    val reason: String = "",            // 사유
    val mergedInto: Long? = null,       // 합친 곳
    val sameCandidate: String = "",     // 같은 사람 후보 (표시만)
    val contactingMark: String = "",    // 연락 중 표시 (누가·언제)
    val linearTicket: String = "",      // 리니어 티켓
    val handedOverAt: Long? = null,     // 넘긴 시각
    val appliedAt: Long,                // 지원 시각
    val lastMessageAt: Long? = null,    // 마지막 대화 시각 (대화 표에서 채움)
    val sourceKey: String,              // 같은 알림을 두 번 저장하지 않으려고 쓰는 값
    val rawText: String = "",           // 알림 원문 (감지 규칙 확인용)
    val isSample: Boolean = false,
)

/** 6. 대화 (오간 메시지 1개 = 한 줄) */
@Entity(tableName = "messages", indices = [Index("applicantId")])
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,                     // 시각
    val applicantId: Long,              // 지원자ID
    val channel: String,                // 채널: 당근 / 문자
    val sender: String,                 // 보낸 쪽: 우리 / 지원자
    val content: String,                // 내용 (원문 그대로)
    val sendMethod: String = "",        // 보낸 방법 (우리일 때): 앱 자동 / 직접 보냄 / 결과 불명
    val appHandling: String = "",       // 앱 처리 (지원자일 때): 답함 / 기다림 / 넘김. 비어 있으면 처리 전
    val isSample: Boolean = false,
)

/** 오류 기록 (티켓 1차 완료 기준 4번) */
@Entity(tableName = "error_logs")
data class ErrorLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,         // 시각
    val place: String,      // 어디서 (예: 지원 감지)
    val message: String,    // 무슨 오류
)

/** 지원자 상태 값 (DB 설계 '상태 흐름 (지원자)') */
object ApplicantStatus {
    const val NEW = "새 지원"
    const val FIRST_SENT = "첫 메시지 보냄"
    const val CONTACT = "연락처 확보"
    const val SMS = "문자 진행 중"
    const val TIME = "가능 시간 확보"
    const val HANDED_OVER = "넘김"
    const val NO_REPLY = "미응답"
    const val ENDED = "종료"
    const val MERGED = "합침"
    const val HOLD = "보류"
    const val STAFF_HANDLING = "담당자 응대 중"
    const val STAFF_CHECK = "담당자 확인"
}

/** 보류 사유 (확인 필요 카드 구분용) */
object HoldReason {
    const val POST_NOT_FOUND = "공고 못 찾음"
    const val MERGE = "합치기 보류"
}
