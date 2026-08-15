package com.expfal.yunayu.domain.nl.model

/** 新建标签的 AI 建议结果：模型给出的新标签名与所属根类名。 */
data class TagSuggestion(
    val tagName: String,
    val rootName: String,
)
