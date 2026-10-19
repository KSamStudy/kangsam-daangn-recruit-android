package com.kangsam.daangnrecruit.sync

import com.kangsam.daangnrecruit.Account
import com.kangsam.daangnrecruit.AppDatabase

/**
 * 계정 맞추기 (DB 설계 '계정', 사용자 프로세스 C2): 당근 가져오기가 나의 당근에서 읽은 닉네임으로 앱 계정을 정함. 순수 코드.
 * - 앱에 계정이 없으면 등록: 별칭 = 닉네임, 기기 '이 폰 · 당근 앱', 상태 사용 중, 한도 1차값(하루 게시 1 · 동시 1 · 하루 당근 30).
 *   인증 동네·인증 종류는 비워 둠 (사람이 설정 '계정'에서 고침)
 * - 같은 닉네임의 계정이 있으면 그 계정
 * - 계정은 있는데 닉네임이 다르면 새로 만들지 않고 멈춤 → 사람이 설정 '계정'에서 같은 계정(닉네임 고침)인지 다른 계정(새로 추가)인지 정함
 * 맞춘 계정은 계정 칸이 빈 상시 공고·게시물에 채움. 예시 계정은 안 봄
 */
object AccountSync {
    /** 등록할 때 기기 칸 */
    const val DEVICE = "이 폰 · 당근 앱"

    sealed interface Result {
        data class Registered(val account: Account) : Result
        data class Matched(val account: Account) : Result
        data class Mismatch(val seen: String, val known: List<String>) : Result
        data object NoNickname : Result
    }

    /** 나의 당근 닉네임으로 계정 맞추기 */
    suspend fun sync(db: AppDatabase, nickname: String?): Result {
        val nick = nickname?.trim().orEmpty()
        if (nick.isEmpty()) return Result.NoNickname
        val accounts = real(db)
        accounts.firstOrNull { it.nickname == nick }?.let {
            fillAccountIds(db, it.id)
            return Result.Matched(it)
        }
        if (accounts.isNotEmpty()) return Result.Mismatch(nick, accounts.map { it.nickname.ifBlank { it.alias } })
        val a = register(db, nick)
        fillAccountIds(db, a.id)
        return Result.Registered(a)
    }

    /** 닉네임이 달라 멈췄을 때 '같은 계정': 그 계정의 닉네임을 나의 당근 닉네임으로 고침 */
    suspend fun sameAccount(db: AppDatabase, account: Account, nickname: String): Account {
        val u = account.copy(nickname = nickname.trim())
        db.accountDao().update(u)
        fillAccountIds(db, u.id)
        return u
    }

    /** 닉네임이 달라 멈췄을 때 '다른 계정': 새로 추가 (계정 칸이 빈 공고는 다음 가져오기에서 맞춘 계정으로) */
    suspend fun otherAccount(db: AppDatabase, nickname: String): Account = register(db, nickname.trim())

    /** 지금 계정: 예시가 아닌 계정이 하나면 그 계정 (여럿이면 모름 — null) */
    suspend fun current(db: AppDatabase): Account? = real(db).singleOrNull()

    /** 계정 칸이 빈 상시 공고·게시물에 이 계정 (예시는 빼고). 채운 줄 수 */
    suspend fun fillAccountIds(db: AppDatabase, accountId: Long): Int =
        db.postDao().fillStandingAccount(accountId) + db.postDao().fillPostingAccount(accountId)

    private suspend fun register(db: AppDatabase, nickname: String): Account {
        val a = Account(alias = nickname, nickname = nickname, device = DEVICE)
        return a.copy(id = db.accountDao().insert(a))
    }

    private suspend fun real(db: AppDatabase): List<Account> = db.accountDao().all().filter { !it.isSample }
}
