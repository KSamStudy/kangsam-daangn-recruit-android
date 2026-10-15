package com.kangsam.daangnrecruit.sync

import com.kangsam.daangnrecruit.AppDatabase
import java.time.ZoneId

/**
 * 당근 화면 한 장 → 앱 기록. 읽는 화면은 셋: 내 공고 상세(공고), 지원자 목록(지원자), 채팅방(대화).
 * 사람이 연 화면을 읽을 때(접근성 서비스)와 '당근에서 가져오기'가 돌 때(SyncRunner) 모두 여기를 씀
 */
object ScreenImport {
    /** 읽는 화면이면 읽은 것, 아니면 null */
    fun read(nodes: List<UiNode>): DaangnScreen? {
        val screen = UiTree.toScreenNodes(nodes)
        return MyPostParser.parse(screen) ?: ApplicantListParser.parse(screen) ?: ChatRoomParser.parse(nodes)
    }

    /** 앱 기록에 맞추고, 무엇을 했는지 한 줄을 돌려줌 */
    suspend fun save(db: AppDatabase, screen: DaangnScreen, zone: ZoneId, now: Long = System.currentTimeMillis()): String =
        when (screen) {
            is ScreenPost -> PostImport.importFromScreen(db, screen, now)
            is ScreenApplicantList -> ApplicantImport.importFromScreen(db, screen, now)
            is ScreenChatRoom -> {
                val (lines, skipped) = ChatRoomParser.resolve(screen.items, zone)
                ChatImport.save(db, screen.header, lines, skipped)
            }
        }
}
