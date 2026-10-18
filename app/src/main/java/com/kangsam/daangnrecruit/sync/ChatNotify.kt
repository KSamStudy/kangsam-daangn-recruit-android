package com.kangsam.daangnrecruit.sync

import com.kangsam.daangnrecruit.AppDatabase
import com.kangsam.daangnrecruit.ApplicantStatus
import com.kangsam.daangnrecruit.Message

/** 알림 한 건에서 꺼낸 값 (안드로이드 알림을 순수 값으로 — 폰 없이 시험할 수 있게) */
data class Notice(
    val pkg: String,
    val channel: String = "",
    val group: String = "",
    val tag: String = "",
    val title: String = "",
    val conversation: String = "",
    val messages: List<NoticeMessage> = emptyList(),
    val text: String = "", // 채팅 메시지 목록이 없는 알림이면 이 내용을 제목(보낸 사람)의 메시지로 봄
    val time: Long = 0,
)

/** 채팅 알림에 묶여 온 메시지 하나: 보낸 사람 이름 · 내용 · 시각(ms) */
data class NoticeMessage(val sender: String, val text: String, val time: Long)

/**
 * 당근 가져오기 C3의 알림 쪽: 지원자가 보낸 채팅 메시지를 알림으로 받아 대화 표에 남김
 * (사용자 프로세스 C3 — 안 읽은 방은 화면으로 열지 않으므로, 그 방의 새 메시지는 알림으로 받아 기록).
 * 당근에는 아무것도 하지 않음 (알림을 지우거나 누르지 않음).
 *
 * - 당근 채팅 알림(그룹 chat · 채널 chat…)에 묶여 온 메시지 중 보낸 사람 이름이 있는 것만.
 *   당근알바·당근 공식 방, 이름 없는 메시지(우리가 알림에서 바로 답한 것)는 뺌.
 *   채팅 메시지 목록이 없는 채팅 알림이면 제목을 보낸 사람, 내용을 메시지로 봄
 * - 보낸 사람 이름이 앱에 있는 지원자 한 명과 같을 때만 그 지원자의 대화로.
 *   앱에 없는 이름 → 지원자 채팅이 아님(남기지 않음), 이름이 같은 지원자가 여럿 → 어느 방인지 몰라 남기지 않고 '못 붙임'으로 알림
 * - 지원자 메시지 · 앱 처리는 비워 둠(처리 전). 메시지 키 = noti|지원자ID|알림의 메시지 시각|길이|내용 해시
 *   → 같은 메시지가 다음 알림에 또 묶여 와도 한 번만
 * - 화면에서 가져온 같은 메시지(같은 지원자·내용, 앞뒤 12시간 안)가 이미 있으면 남기지 않음.
 *   알림은 묶여 늦게 올 수 있어(10/18 00:00에 몰려 옴) 시각이 조금 달라도 같은 메시지로 봄.
 *   반대로 나중에 화면에서 그 방을 읽으면 화면 쪽 시각·키로 고침 (ChatImport)
 * - 개발 시험: adb 테스트 알림(com.android.shell)은 예시 지원자에게만 붙이고 예시 표시 → '예시 지우기'로 지워짐
 *
 * 실제 지원자 채팅 알림은 10/18까지 아직 못 봄 → 당근알바 지원 알림과 같은 모양(채널 chat44 · 그룹 chat · 채팅 메시지 목록)으로 보고 만듦.
 * 실제 알림이 오면 알림 기록으로 맞춰 봄
 */
object ChatNotify {
    const val KEY = "noti"
    const val TEST_PKG = "com.android.shell"
    const val TWIN_MS = 12 * 60 * 60 * 1000L // 알림과 화면에서 각각 온 같은 메시지로 볼 시각 차이
    private const val ALBA = "당근알바"
    private const val DAANGN = "당근"
    private const val SENDER = "지원자" // 대화 표의 보낸 쪽

    /**
     * 한 알림을 남긴 결과: 남긴 것 한 줄씩, 못 붙인 것 한 줄씩, 새로 남긴 메시지
     * (newMessages — 알림 리스너가 이것으로 번호 받기(ContactFlow, 응대 B3)를 부름)
     */
    data class Result(val saved: List<String>, val problems: List<String>, val newMessages: List<NewMessage> = emptyList()) {
        val isEmpty: Boolean get() = saved.isEmpty() && problems.isEmpty()
    }

    /** 새로 남긴 지원자 메시지 하나 */
    data class NewMessage(val applicantId: Long, val text: String, val time: Long)

    /** 이 알림에서 지원자가 보낸 것으로 보이는 메시지들 (아니면 빈 목록) */
    fun messages(n: Notice): List<NoticeMessage> {
        val test = n.pkg == TEST_PKG
        val chat = n.group == "chat" || n.channel.startsWith("chat")
        if (!test && !chat) return emptyList()
        if (official(n.title) || official(n.conversation)) return emptyList()
        val list = n.messages.ifEmpty {
            if (n.title.isNotBlank() && n.text.isNotBlank() && n.time > 0) listOf(NoticeMessage(n.title, n.text, n.time)) else emptyList()
        }
        return list.filter { m -> m.sender.isNotBlank() && !official(m.sender) && m.text.isNotBlank() }
    }

    private fun official(name: String): Boolean {
        val t = name.trim()
        return t == DAANGN || t.contains(ALBA)
    }

    /** 앱에 있는 지원자의 메시지만 대화에 남김 */
    suspend fun save(db: AppDatabase, n: Notice): Result {
        val list = messages(n)
        if (list.isEmpty()) return Result(emptyList(), emptyList())
        val test = n.pkg == TEST_PKG
        val dao = db.messageDao()
        val people = (if (test) db.applicantDao().samples() else db.applicantDao().nonSample())
            .filter { it.status != ApplicantStatus.MERGED }
        val saved = mutableListOf<String>()
        val problems = mutableListOf<String>()
        val fresh = mutableListOf<NewMessage>()
        for ((sender, msgs) in list.groupBy { it.sender.trim() }) {
            val same = people.filter { it.name == sender }
            if (same.isEmpty()) continue // 앱에 없는 사람 (지원자 채팅이 아님)
            if (same.size > 1) {
                problems += "'$sender' 메시지 ${msgs.size}개 — 이름이 같은 지원자가 ${same.size}명이라 못 붙임"
                continue
            }
            val a = same.single()
            var added = 0
            for (m in msgs) {
                // 화면에서 이미 가져온 같은 메시지면 남기지 않음
                val fromScreen = dao.findSame(a.id, SENDER, m.text, m.time - TWIN_MS, m.time + TWIN_MS, ChatImport.KEY + "|")
                if (fromScreen.isNotEmpty()) continue
                val id = dao.insertIgnore(
                    Message(
                        time = m.time, applicantId = a.id, channel = "당근", sender = SENDER, content = m.text,
                        messageKey = "$KEY|${a.id}|${m.time}|${m.text.length}|${m.text.hashCode()}",
                        isSample = test,
                    )
                )
                if (id > 0) {
                    added++
                    fresh += NewMessage(a.id, m.text, m.time)
                }
            }
            if (added > 0) {
                msgs.maxOfOrNull { it.time }?.let { db.applicantDao().touchLastMessage(a.id, it) }
                saved += "'$sender' 메시지 ${added}개 남김"
            }
        }
        return Result(saved, problems, fresh)
    }
}
