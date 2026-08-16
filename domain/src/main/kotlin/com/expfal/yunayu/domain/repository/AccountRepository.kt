package com.expfal.yunayu.domain.repository

import com.expfal.yunayu.domain.model.Account
import com.expfal.yunayu.domain.model.AccountBalance
import com.expfal.yunayu.domain.model.AccountDeleteImpact
import com.expfal.yunayu.domain.model.DuplicateAccountNameException
import kotlinx.coroutines.flow.Flow

/** 账户仓储接口，由 :data 模块实现。 */
interface AccountRepository {

    /** 观察全部账户，按创建时间升序。 */
    fun observeAccounts(): Flow<List<Account>>

    /** 一次性获取全部账户（启动补齐幂等校验等场景使用）。 */
    suspend fun getAccounts(): List<Account>

    /** 观察按账户分组的余额；「未指定账户」分组 `accountId` / `accountName` 均为 null。 */
    fun observeBalances(): Flow<List<AccountBalance>>

    /**
     * 新增账户，返回新账户 id。
     *
     * 空名（trim 后）抛 [IllegalArgumentException]；同名账户已存在时抛
     * [DuplicateAccountNameException]（唯一索引 `name` 约束的领域化表达）。
     */
    suspend fun addAccount(name: String): Long

    /**
     * 重命名账户；空名抛 [IllegalArgumentException]，目标不存在抛 [IllegalArgumentException]，
     * 与其它账户重名抛 [DuplicateAccountNameException]。
     */
    suspend fun renameAccount(id: Long, newName: String)

    /**
     * 计算删除 [id] 的影响面（将被置空账户 `account_id → NULL` 影响的交易数）。
     */
    suspend fun getDeleteImpact(id: Long): AccountDeleteImpact

    /**
     * 删除账户。交易的账户归属由 DB 外键 `ON DELETE SET NULL` 置为「未指定账户」，
     * 不级联删除交易。
     */
    suspend fun deleteAccount(id: Long)

    /** 观察上次使用的账户 id；用户尚未选择过账户时发射 `null`。 */
    fun observeLastUsedAccountId(): Flow<Long?>

    /** 保存上次使用的账户 id；传 `null` 清除记忆（等价于「未选择过账户」）。 */
    suspend fun saveLastUsedAccountId(id: Long?)
}
