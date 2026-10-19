package com.kangsam.daangnrecruit

import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 폰 안 DB 사본 (DB 설계 '폰 안 사본·표 구조가 바뀔 때' — 1차값 최근 7개). 앱 전용 폴더 files/db_backup에만 둠
 * (지원자 이름·번호·대화가 들어 있어 폰 밖으로는 안 내보냄 — 폰 밖 사본은 담당자 결정, 확인사항 30).
 *  - 업데이트 뒤 처음 열기 전: 닫혀 있는 DB 파일(recruit.db + 아직 안 합친 recruit.db-wal)을 그대로 복사 — 표 구조를 옮기기 전 기록
 *  - 하루 한 번: 열린 DB에서 VACUUM INTO로 파일 하나 (안 되는 옛 폰이면 쓰기를 잠깐 막고 파일 복사)
 * 언제 할지(설치 시각·하루)는 DbOpen(폰)이 정하고, 여기는 파일만 다룸 — 폰 없이 시험
 * 사본 이름: recruit-20261019-213000-update.db (짝 -wal이 있으면 같은 이름 + -wal — SQLite가 열 때 합침)
 */
object DbBackup {
    const val DIR = "db_backup"
    const val KEEP = 7 // 1차값 (사람이 정할 항목 B의 DB)

    enum class Kind(val tag: String, val label: String) {
        UPDATE("update", "업데이트 뒤 열기 전"),
        DAILY("daily", "하루 한 번"),
    }

    /** 사본 하나. stamp = yyyyMMdd-HHmmss (폰 시각) */
    data class Copy(val file: File, val kind: Kind, val stamp: String) {
        val day: String get() = stamp.take(8)
        fun time(zone: ZoneId): Long = LocalDateTime.parse(stamp, STAMP).atZone(zone).toInstant().toEpochMilli()
    }

    private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    private val NAME = Regex("""recruit-(\d{8}-\d{6})-(update|daily)\.db""")

    fun stamp(now: Long, zone: ZoneId): String = STAMP.format(Instant.ofEpochMilli(now).atZone(zone))

    fun name(kind: Kind, now: Long, zone: ZoneId): String = "recruit-${stamp(now, zone)}-${kind.tag}.db"

    /** 업데이트 뒤 처음 열기 전: 닫힌 DB를 파일 그대로. DB가 아직 없으면(처음 깐 앱) null */
    fun copyClosed(dbFile: File, dir: File, now: Long, zone: ZoneId): File? {
        if (!dbFile.exists()) return null
        dir.mkdirs()
        val target = File(dir, name(Kind.UPDATE, now, zone))
        copyPair(dbFile, target)
        prune(dir)
        return target
    }

    /**
     * 하루 한 번: 열린 DB에서. VACUUM INTO(SQLite 3.27~, 안드로이드 11~)는 그 순간의 기록을 파일 하나로 —
     * 안 되면 WAL을 DB 파일로 옮겨 두고 쓰기를 잠깐 막은 채(읽기는 됨) 파일 그대로
     */
    fun copyOpen(db: SupportSQLiteDatabase, dbFile: File, dir: File, now: Long, zone: ZoneId): File {
        dir.mkdirs()
        val target = File(dir, name(Kind.DAILY, now, zone))
        val tmp = File(target.path + ".tmp")
        tmp.delete()
        val vacuumed = runCatching { db.execSQL("VACUUM INTO ?", arrayOf<Any?>(tmp.path)) }.isSuccess && tmp.length() > 0
        if (vacuumed) {
            File(target.path + "-wal").delete()
            target.delete()
            check(tmp.renameTo(target)) { "사본 이름 바꾸기 실패: ${target.name}" }
        } else {
            tmp.delete()
            runCatching { db.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() } }
            db.beginTransaction()
            try {
                copyPair(dbFile, target)
            } finally {
                db.endTransaction()
            }
        }
        prune(dir)
        return target
    }

    /** DB 파일과 짝 -wal을 같이 (임시 이름으로 쓴 뒤 바꿔서, 반쯤 쓴 사본이 사본 목록에 안 보이게) */
    private fun copyPair(dbFile: File, target: File) {
        val wal = File(dbFile.path + "-wal")
        val targetWal = File(target.path + "-wal")
        if (wal.exists() && wal.length() > 0) wal.copyTo(targetWal, overwrite = true) else targetWal.delete()
        val tmp = File(target.path + ".tmp")
        dbFile.copyTo(tmp, overwrite = true)
        target.delete()
        check(tmp.renameTo(target)) { "사본 이름 바꾸기 실패: ${target.name}" }
    }

    /** 사본 목록 (최근 것부터 — 같은 초면 하루 사본이 뒤에 만든 것) */
    fun list(dir: File): List<Copy> =
        dir.listFiles().orEmpty().mapNotNull { f ->
            NAME.matchEntire(f.name)?.let { m -> Copy(f, if (m.groupValues[2] == Kind.DAILY.tag) Kind.DAILY else Kind.UPDATE, m.groupValues[1]) }
        }.sortedWith(compareByDescending<Copy> { it.stamp }.thenByDescending { it.kind == Kind.DAILY })

    /** 최근 keep개만 남김 (짝 -wal도 같이). 끝나지 못한 임시 파일·짝 잃은 -wal도 지움 */
    fun prune(dir: File, keep: Int = KEEP) {
        list(dir).drop(keep).forEach { c ->
            File(c.file.path + "-wal").delete()
            c.file.delete()
        }
        dir.listFiles().orEmpty().forEach { f ->
            val orphanWal = f.name.endsWith(".db-wal") && !File(f.path.removeSuffix("-wal")).exists()
            if (f.name.endsWith(".tmp") || orphanWal) f.delete()
        }
    }

    /** 오늘 하루 사본이 아직 없으면 true (사본 이름의 날짜로 봄) */
    fun dailyDue(dir: File, now: Long, zone: ZoneId): Boolean {
        val today = stamp(now, zone).take(8)
        return list(dir).none { it.kind == Kind.DAILY && it.day == today }
    }
}
