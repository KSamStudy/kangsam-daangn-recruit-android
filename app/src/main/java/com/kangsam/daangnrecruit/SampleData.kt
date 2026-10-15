package com.kangsam.daangnrecruit

/**
 * 개발용 예시 데이터. 화면 설계의 예시 상황(계정 구인1, 켜진 상시 공고 송도 · 중등 수학 하나)을 지금 시각 기준으로 넣음.
 * 게시물은 DB 설계 게시물 예시(1·2회차 마감, 3회차 게시됨), 김○○ 대화는 DB 설계 대화 예시.
 * 지원자 이름·번호는 문서처럼 가린 값만 씀. 모든 줄이 isSample = true라 한 번에 지울 수 있음.
 */
object SampleData {
    private const val MIN = 60_000L
    private const val HOUR = 60 * MIN
    private const val DAY = 24 * HOUR

    private const val TITLE = "송도 중등 수학 선생님 구해요"
    private const val TICKET = "송도 중등 수학 구인 티켓 (예시)"
    private const val FIRST_MSG = "안녕하세요 선생님! 지원해주셔서 감사합니다. 자세한 내용은 문자로 안내드리려고 하는데, 연락처 남겨주실 수 있을까요?"
    private const val THANKS = "감사합니다! 문자로 연락드릴게요."
    private const val FIRST_SMS = "선생님 안녕하세요! 당근 보고 연락드렸습니다.\n아래 내용 보내주시면 확인 후 다시 연락드리겠습니다.\n1. 성함\n2. 대학·학과\n3. 과외·교육 경험\n4. 수업 가능한 요일·시간대\n5. 수업 가능 기간"
    private const val ASK_PERIOD = "감사합니다. 수업 가능한 기간도 알려주실 수 있을까요?"

    /** 대화 한 줄 (ago = 지금부터 몇 ms 전) */
    private data class Line(
        val ago: Long,
        val channel: String,
        val sender: String,
        val content: String,
        val method: String = "",
        val handling: String = "",
    )

    private fun we(ago: Long, channel: String, text: String) = Line(ago, channel, "우리", text, method = "앱 자동")
    private fun they(ago: Long, channel: String, text: String, handling: String = "답함") =
        Line(ago, channel, "지원자", text, handling = handling)

    /** 넣은 지원자 수를 돌려줌. 예시 데이터가 이미 있으면 0 */
    suspend fun insert(db: AppDatabase): Int {
        if (db.sampleDao().countSampleApplicants() > 0) return 0
        val now = System.currentTimeMillis()

        // 1단계: 계정 · 상시 공고 · 게시물
        val accountId = db.accountDao().insert(
            Account(
                alias = "구인1", nickname = "예시 계정", device = "운영폰 S22",
                verifiedArea = "송도동", verificationType = "개인", isSample = true,
            )
        )
        val posts = db.postDao()
        val standingId = posts.insertStandingPost(
            StandingPost(
                name = "송도 · 중등 수학", category = "과목 수업 — 수학", area = "송도동 일대", targetGrade = "중등",
                schedule = "협의 가능", pay = "[정할 것]", teachingMode = "방문",
                title = TITLE,
                body = "송도에서 중등 수학 과외해주실 선생님을 찾고 있어요.\n학생 성향과 수준에 맞춰 개념부터 내신까지 함께 봐주실 분이면 좋겠어요.",
                textVersion = 2, approvedBy = "담당자", approvedAt = now - 20 * DAY,
                accountId = accountId, postWhen = "마감되면", bump = "안 함", appCloseCondition = "없음",
                skipCondition = "진행 중 지원자 4명 이상이면 기다림", linearTicket = TICKET, status = "켜짐", isSample = true,
            )
        )
        posts.insertPosting(
            Posting(
                standingPostId = standingId, round = 1, scheduledAt = now - 16 * DAY, accountId = accountId, textVersion = 1,
                title = TITLE, postedBy = "앱 자동", postedAt = now - 16 * DAY, status = "마감", watchResult = "통과",
                closedAt = now - 13 * DAY, closedBy = "담당자", closeCheckedAt = now - 13 * DAY,
                closeCheckMethod = "담당자의 '마감했어요' 뒤 '내 공고' 확인", reason = "채용 끝남", isSample = true,
            )
        )
        val round2 = posts.insertPosting(
            Posting(
                standingPostId = standingId, round = 2, scheduledAt = now - 13 * DAY + 2 * HOUR, accountId = accountId, textVersion = 1,
                title = TITLE, postedBy = "앱 자동", postedAt = now - 13 * DAY + 2 * HOUR, status = "마감", watchResult = "통과",
                closedAt = now - 5 * DAY, closedBy = "담당자", closeCheckedAt = now - 5 * DAY + 3 * HOUR,
                closeCheckMethod = "'내 공고' 확인", reason = "채용 끝남", isSample = true,
            )
        )
        val round3 = posts.insertPosting(
            Posting(
                standingPostId = standingId, round = 3, scheduledAt = now - 2 * DAY, accountId = accountId, textVersion = 2,
                title = TITLE, postedBy = "앱 자동", postedAt = now - 2 * DAY, status = "게시됨", watchResult = "통과",
                isSample = true,
            )
        )
        posts.setCurrentPosting(standingId, round3)

        // 2단계: 지원자 + 대화 (상태마다 한 명씩)
        fun base(name: String, area: String, status: String, appliedAgo: Long, postingId: Long) = Applicant(
            standingPostId = standingId, postingId = postingId, postTitle = TITLE, chatRoom = "구인1 · $name",
            name = name, area = area, status = status, appliedAt = now - appliedAgo,
            sourceKey = "sample|$name|$appliedAgo", isSample = true,
        )

        // 문자 진행 중 — DB 설계 대화 예시와 같음 (가능 기간 답을 기다림)
        add(db, now, base("김○○", "송도2동", ApplicantStatus.SMS, 90 * MIN, round3).copy(
            phone = "010-••••-5821", phoneKey = "sample-5821", realName = "김○○", school = "○○대 수학교육과",
            experience = "중등 수학 과외 2년", availableTime = "화·목 저녁 7시 이후",
        ), listOf(
            we(80 * MIN, "당근", "안녕하세요 선생님! 지원해주셔서 감사합니다. 연락처 남겨주실 수 있을까요?"),
            they(65 * MIN, "당근", "010-••••-5821이요"),
            we(59 * MIN, "당근", THANKS),
            we(58 * MIN, "문자", FIRST_SMS),
            they(2 * MIN, "문자", "김○○ / ○○대 수학교육과 / 중등 수학 과외 2년, 화·목 저녁 7시 이후 가능해요"),
            we(0L, "문자", ASK_PERIOD),
        ))
        // 넘김 — 앱 처리 '기다림'이 있는 예
        add(db, now, base("정○○", "송도1동", ApplicantStatus.HANDED_OVER, DAY + 2 * HOUR, round3).copy(
            phone = "010-••••-3170", phoneKey = "sample-3170", realName = "정○○", school = "○○대 수학과",
            experience = "중등 수학 과외 1년, 학원 보조 6개월", availableTime = "월·수 저녁", availablePeriod = "12월까지",
            linearTicket = TICKET, handedOverAt = now - 3 * HOUR,
        ), listOf(
            we(DAY + 100 * MIN, "당근", FIRST_MSG),
            they(DAY + 60 * MIN, "당근", "010-••••-3170 입니다"),
            we(DAY + 59 * MIN, "당근", THANKS),
            we(DAY + 58 * MIN, "문자", FIRST_SMS),
            they(20 * HOUR, "문자", "정○○ / ○○대 수학과 / 중등 수학 과외 1년, 학원 보조 6개월 / 월·수 저녁 가능합니다"),
            we(20 * HOUR - 2 * MIN, "문자", ASK_PERIOD),
            they(6 * HOUR, "문자", "일정 확인하고 다시 말씀드릴게요", handling = "기다림"),
            they(3 * HOUR + 5 * MIN, "문자", "12월까지 가능합니다", handling = "넘김"),
            we(3 * HOUR, "문자", "보내주셔서 감사합니다. 확인 후 다시 연락드리겠습니다."),
        ))
        // 연락처 확보 — 다음은 첫 문자
        add(db, now, base("박○○", "송도3동", ApplicantStatus.CONTACT, 5 * HOUR, round3).copy(
            phone = "010-••••-2284", phoneKey = "sample-2284",
        ), listOf(
            we(5 * HOUR - 10 * MIN, "당근", FIRST_MSG),
            they(40 * MIN, "당근", "네 010-••••-2284 입니다"),
            we(39 * MIN, "당근", THANKS),
        ))
        // 담당자 확인 — 예외 주제(페이 문의)
        add(db, now, base("오○○", "연수2동", ApplicantStatus.STAFF_CHECK, 9 * HOUR, round3).copy(
            phone = "010-••••-7731", phoneKey = "sample-7731", statusBeforeHold = ApplicantStatus.SMS,
            reason = "예외 주제: 페이 문의",
        ), listOf(
            we(9 * HOUR - 5 * MIN, "당근", FIRST_MSG),
            they(8 * HOUR, "당근", "010-••••-7731"),
            we(8 * HOUR - MIN, "당근", THANKS),
            we(8 * HOUR - 2 * MIN, "문자", FIRST_SMS),
            they(70 * MIN, "문자", "시급은 어떻게 되나요? 그거 먼저 알고 싶어요", handling = "넘김"),
        ))
        // 새 지원 — 아직 메시지 없음
        add(db, now, base("이○○", "송도동", ApplicantStatus.NEW, 10 * MIN, round3), emptyList())
        // 첫 메시지 보냄 — 번호 기다림
        add(db, now, base("최○○", "옥련동", ApplicantStatus.FIRST_SENT, 35 * MIN, round3),
            listOf(we(30 * MIN, "당근", FIRST_MSG)))
        // 보류 — 같은 번호 다른 이름 (합치기 보류). 김○○보다 나중 지원이라 '같은 사람'으로 정하면 이 줄이 '합침'
        add(db, now, base("한○○", "송도2동", ApplicantStatus.HOLD, 40 * MIN, round3).copy(
            phone = "010-••••-5821", phoneKey = "sample-5821", statusBeforeHold = ApplicantStatus.CONTACT,
            reason = "같은 번호 다른 이름 (합치기 보류)", sameCandidate = "김○○",
        ), listOf(
            we(38 * MIN, "당근", FIRST_MSG),
            they(20 * MIN, "당근", "010-••••-5821 입니다"),
        ))
        // 미응답 — 2회차 지원, 첫 메시지 뒤 72시간 답 없음
        add(db, now, base("윤○○", "동춘동", ApplicantStatus.NO_REPLY, 7 * DAY, round2).copy(
            reason = "첫 메시지 뒤 72시간 답 없음",
        ), listOf(we(7 * DAY - 10 * MIN, "당근", FIRST_MSG)))

        // 보류 — 공고 못 찾음 (알림 공고명과 같은 공고가 앱에 없음 → 확인 필요에서 공고 고르기 / 직접 올린 공고 등록)
        add(db, now, Applicant(
            postTitle = "중2 영어 같이 공부해요!!", chatRoom = "구인1 · 강○○", name = "강○○", area = "송도1동",
            status = ApplicantStatus.HOLD, statusBeforeHold = ApplicantStatus.NEW, reason = HoldReason.POST_NOT_FOUND,
            appliedAt = now - 25 * MIN, sourceKey = "sample|강○○|${25 * MIN}", isSample = true,
        ), emptyList())

        return 9
    }

    /** 예시 데이터를 모두 지움. 지운 지원자 수를 돌려줌 */
    suspend fun delete(db: AppDatabase): Int {
        val dao = db.sampleDao()
        dao.deleteMessages()
        val n = dao.deleteApplicants()
        dao.deletePostings()
        dao.deleteStandingPosts()
        dao.deleteAccountEvents()
        dao.deleteAccounts()
        return n
    }

    private suspend fun add(db: AppDatabase, now: Long, applicant: Applicant, lines: List<Line>) {
        val lastAgo = lines.minOfOrNull { it.ago }
        val id = db.applicantDao().insert(applicant.copy(lastMessageAt = lastAgo?.let { now - it }))
        if (id <= 0) return
        for (l in lines) {
            db.messageDao().insert(
                Message(
                    time = now - l.ago, applicantId = id, channel = l.channel, sender = l.sender,
                    content = l.content, sendMethod = l.method, appHandling = l.handling, isSample = true,
                )
            )
        }
    }
}
