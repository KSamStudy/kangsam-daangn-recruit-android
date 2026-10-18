package com.kangsam.daangnrecruit.reply

import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random

/**
 * 언제 보낼지 (사용자 프로세스 '안전장치' — 시간대·동작 사이 간격). 순수 코드라 폰 없이 시험함.
 *
 * 받자마자 보내거나 늘 같은 간격으로 보내면 매크로처럼 보일 수 있어서(재걸 10/18) 기다리는 시간을 무작위로 둠:
 * - 지원자 답장(또는 지원)을 받은 뒤 2~10분 사이 아무 때나
 * - 한 번에 두 개를 보낼 때(당근 마무리 → 첫 문자) 둘 사이는 1~3분
 * - 발송 허용 시간(09~21시, ReplyRules) 밖이면 다음 허용 시작(09:00) + 0~30분
 * 값은 '사람이 정할 항목'의 '동작 사이 최소 간격' 초안 (담당자 결정 전). 바꿀 땐 아래 상수만 고치면 됨
 */
object SendTiming {
    const val MIN_WAIT_MS = 2 * 60_000L   // 받은 뒤 가장 빨리
    const val MAX_WAIT_MS = 10 * 60_000L  // 받은 뒤 가장 늦게
    const val MIN_GAP_MS = 60_000L        // 이어 보낼 때 사이 (가장 짧게)
    const val MAX_GAP_MS = 3 * 60_000L    // 이어 보낼 때 사이 (가장 길게)
    const val MORNING_SPREAD_MS = 30 * 60_000L // 시간 밖이라 아침으로 미룰 때 09:00부터 퍼뜨리는 폭

    /** 받은 시각 → 보낼 시각 (2~10분 뒤, 허용 시간 안으로) */
    fun afterReceived(receivedAt: Long, zone: ZoneId, random: Random): Long =
        inHours(receivedAt + random.nextLong(MIN_WAIT_MS, MAX_WAIT_MS + 1), zone, random)

    /** 앞에 보낸 시각 → 이어 보낼 시각 (1~3분 뒤, 허용 시간 안으로) */
    fun afterPrevious(previousAt: Long, zone: ZoneId, random: Random): Long =
        inHours(previousAt + random.nextLong(MIN_GAP_MS, MAX_GAP_MS + 1), zone, random)

    /** t가 발송 허용 시간이면 그대로, 아니면 다음 허용 시작 + 0~30분 */
    fun inHours(t: Long, zone: ZoneId, random: Random): Long {
        val at = Instant.ofEpochMilli(t).atZone(zone)
        if (ReplyRules.inSendHours(at.hour)) return t
        var start = at.toLocalDate().atTime(ReplyRules.SEND_FROM, 0).atZone(zone)
        if (at.hour >= ReplyRules.SEND_UNTIL) start = start.plusDays(1)
        return start.toInstant().toEpochMilli() + random.nextLong(0, MORNING_SPREAD_MS + 1)
    }
}
