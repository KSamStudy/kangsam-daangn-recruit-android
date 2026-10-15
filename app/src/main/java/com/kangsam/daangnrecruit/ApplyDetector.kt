package com.kangsam.daangnrecruit

import android.app.Notification
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat

/**
 * 당근 알림 가운데 지원 알림을 골라 지원자 줄(상태 '새 지원')로 바꾸는 곳 (사용자 프로세스 B1).
 * 지원은 '당근알바' 공식 채팅방에 이런 메시지로 옴 (10/15 채팅방 화면에서 확인):
 *   ○○님이 지원했어요
 *   ■ 공고명: 중2 공부 도와주실분!!
 *   [지원서 확인하기] [지원자 목록으로 이동]
 * 실제 알림(푸시)에 둘째 줄(공고명)까지 오는지는 다음 지원 때 기록 파일로 확인.
 */
object ApplyDetector {
    /** 지원 알림을 보내는 쪽 이름 */
    private const val SENDER = "당근알바"

    /** '이름님이 지원했어요'에서 이름을 꺼냄 (지원 취소 같은 다른 문구는 안 걸리게 '지원했'까지 봄) */
    private val NAME = Regex("""(\S+?)님이\s*지원했""")

    /** '공고명: …'이 있으면 공고명을 꺼냄 (없으면 빈 칸) */
    private val POST_TITLE = Regex("""공고명\s*[:：]\s*(.+)""")

    /** 지원 알림이면 지원자 줄 목록을, 아니면 빈 목록을 돌려줌 */
    fun detect(sbn: StatusBarNotification): List<Applicant> {
        val n = sbn.notification
        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val conversation = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString() ?: ""

        // 1) 채팅 알림이면 묶여 있는 메시지를 하나씩 봄 (지원 여러 건이 한 알림에 올 수 있음)
        val style = runCatching {
            NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
        }.getOrNull()
        if (style != null && style.messages.isNotEmpty()) {
            return style.messages.mapNotNull { m ->
                val sender = m.person?.name?.toString() ?: ""
                val text = m.text?.toString() ?: return@mapNotNull null
                if (!fromDaangnAlba(sender, title, conversation)) return@mapNotNull null
                toApplicant(text, m.timestamp, "${sbn.packageName}|msg|${m.timestamp}|$text")
            }
        }

        // 2) 채팅 모양이 아니면 제목·내용으로 봄
        if (!fromDaangnAlba("", title, conversation)) return emptyList()
        val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: return emptyList()
        val time = if (n.`when` > 0) n.`when` else sbn.postTime
        return listOfNotNull(toApplicant(text, time, "${sbn.packageName}|n|$time|$text"))
    }

    private fun fromDaangnAlba(sender: String, title: String, conversation: String): Boolean =
        sender.contains(SENDER) || title.contains(SENDER) || conversation.contains(SENDER)

    private fun toApplicant(text: String, time: Long, sourceKey: String): Applicant? {
        val name = NAME.find(text)?.groupValues?.get(1)?.trim() ?: return null
        val postTitle = POST_TITLE.find(text)?.groupValues?.get(1)?.trim() ?: ""
        return Applicant(
            name = name,
            postTitle = postTitle,
            status = "새 지원",
            appliedAt = time,
            sourceKey = sourceKey,
            rawText = text,
        )
    }
}
