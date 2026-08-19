package com.expfal.yunayu.domain.usecase

import com.expfal.yunayu.domain.model.AccountPresets
import com.expfal.yunayu.domain.model.DuplicateAccountNameException
import com.expfal.yunayu.domain.repository.AccountRepository

/**
 * 应用启动时的预置账户补齐编排用例。
 *
 * 仅在首次安装（无任何账户）时创建预置账户，避免用户修改/删除后重启又被还原。
 * 竞态命中 [DuplicateAccountNameException] 计入 [SeedResult.skipped]；
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

    /** 确保预置账户存在，返回补齐结果。仅在无任何账户时创建。 */
    suspend operator fun invoke(): SeedResult {
        val existingAccounts = accountRepository.getAccounts()
        // 已有账户则跳过，尊重用户的修改和删除
        if (existingAccounts.isNotEmpty()) return SeedResult(emptyList(), emptyList())

        val created = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        for (name in AccountPresets.PRESET_NAMES) {
            try {
                accountRepository.addAccount(name)
                created += name
            } catch (e: DuplicateAccountNameException) {
                // 并发双插竞态：账户已由其它协程/进程创建，标记 skipped。
                skipped += name
            }
        }
        return SeedResult(created, skipped)
    }
}
