package com.kangsam.daangnrecruit

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * 폰 안 DB (파일 이름 recruit.db, 이 앱 전용 저장소에만 있음).
 * 표는 DB 설계 그대로: 1단계 게시물 DB(계정·계정 상태 기록·상시 공고·게시물) + 2단계 지원 DB(지원자·대화), 그리고 오류 기록.
 * 표 정의는 Entities.kt, 읽고 쓰는 곳은 Daos.kt, 표 구조가 바뀔 때 옮기는 코드는 Migrations.kt.
 * 처음 여는 곳은 DbOpen (사본 → 열기·옮기기 → 못 열면 전체 멈춤) — 서비스는 DbOpen.db()로 받음
 */
@Database(
    entities = [
        Account::class, AccountEvent::class, StandingPost::class, Posting::class,
        Applicant::class, Message::class, ErrorLog::class,
    ],
    version = 4, // 10/19 4: 게시물 '마감 이유' 칸 (3 → 4 옮기기). 버전을 올리면 Migrations.kt에 옮기기를 꼭 더함 — 없으면 DB를 못 열고 전체 멈춤
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun postDao(): PostDao
    abstract fun applicantDao(): ApplicantDao
    abstract fun messageDao(): MessageDao
    abstract fun errorLogDao(): ErrorLogDao
    abstract fun checkDao(): CheckDao
    abstract fun sampleDao(): SampleDao

    companion object {
        const val FILE_NAME = "recruit.db"

        /** 표 구조 버전 — 위 @Database의 version과 같게 (DbOpen이 '옮기기 전 사본'을 정할 때 씀, 시험 틀이 같은지 봄) */
        const val VERSION = 4

        @Volatile
        private var instance: AppDatabase? = null

        /**
         * 앱 안 DB 하나 (화면이 씀 — 화면은 DbOpen이 연 뒤에만 보임). 서비스·뒤에서 도는 일은 DbOpen.db()로.
         * 표 구조가 바뀌었으면 처음 열 때 Migrations대로 옮김. 옮길 길이 없거나 실패하면 지우지 않고 오류 (10/19 — 전에는 지우고 새로 만듦)
         */
        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, FILE_NAME)
                    .addMigrations(*Migrations.ALL)
                    .build()
                    .also { instance = it }
            }

        /** 못 연 DB를 닫고 버림 — 다시 열어 볼 때 새로 만듦 (DbOpen) */
        fun reset() {
            synchronized(this) {
                runCatching { instance?.close() }
                instance = null
            }
        }
    }
}
