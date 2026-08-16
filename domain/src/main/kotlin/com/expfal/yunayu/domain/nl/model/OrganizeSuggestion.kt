package com.expfal.yunayu.domain.nl.model

/** 整理动作类型。 */
enum class Action {
    /** 挂载到已有标签。 */
    ATTACH,

    /** 在根类下新建标签。 */
    CREATE,
}

/**
 * 一条整理建议。
 *
 * [action] 为 [Action.ATTACH] 时，[tagName] 为「根·子」或裸标签名，[rootName] 恒为 `null`；
 * 为 [Action.CREATE] 时，[tagName] 为新标签名，[rootName] 为所属根类名。
 */
data class OrganizeSuggestion(
    val recordId: Long,
    val action: Action,
    val tagName: String,
    val rootName: String?,
)
