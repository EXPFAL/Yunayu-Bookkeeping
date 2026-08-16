package com.expfal.yunayu.domain.usecase

import com.expfal.yunayu.domain.model.AccountPresets
import com.expfal.yunayu.domain.model.DuplicateAccountNameException
import com.expfal.yunayu.domain.repository.AccountRepository

/**
 * 应用启动时的预置账户补齐编排用例。
 *
 * 按名幂等补齐 [AccountPresets.PRESET_NAMES]：已存在则跳过，新建计入
 * [SeedResult.created]，竞态命中 [DuplicateAccountNameException] 计入 [SeedResult.skipped]；
 * 其余异常（含 [kotlinx.coroutines.CancellationException]）上抛由调用方兜底。
 */
class EnsureAccountsUseCase(
    private val accountRepository: AccountRepository,
) {

    /** 一次补齐的结果快照。 */
    data class SeedResult(
        val created: List<String>,
        val skipped: List<String>,
    )

    /** 确保预置账户存在，返回补齐结果。 */
    suspend operator fun invoke(): SeedResult {
        val existingNames = accountRepository.getAccounts().map { it.name }.toMutableSet()
        val created = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        for (name in AccountPresets.PRESET_NAMES) {
            if (name in existingNames) continue
            try {
                accountRepository.addAccount(name)
                created += name
                existingNames += name
            } catch (e: DuplicateAccountNameException) {
                // 并发双插竞态：账户已由其它协程/进程创建，标记 skipped。
                skipped += name
            }
        }
        return SeedResult(created, skipped)
    }
}
