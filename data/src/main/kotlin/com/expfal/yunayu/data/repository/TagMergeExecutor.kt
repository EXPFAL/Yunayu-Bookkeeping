package com.expfal.yunayu.data.repository

/**
 * 标签合并的执行接缝：把 [dropTagId] 下的交易迁移到 [keepTagId]，随后删除 [dropTagId]。
 *
 * 该接缝仅描述数据库侧的原子写路径，业务校验（两标签存在、均非根、drop 为叶子等）由
 * [com.expfal.yunayu.domain.repository.TagRepository.mergeTags] 完成后再委托本接缝执行。
 */
interface TagMergeExecutor {

    /**
     * 在同一数据库事务内：先将 [dropTagId] 下的交易 `tag_id` 迁移为 [keepTagId]，
     * 再删除 [dropTagId] 标签。
     *
     * 顺序敏感：先迁移后删除，否则外键 `ON DELETE SET_NULL` 会先把迁移目标的交易置空，
     * 导致合并后交易丢失标签。
     */
    suspend fun merge(keepTagId: Long, dropTagId: Long)
}
