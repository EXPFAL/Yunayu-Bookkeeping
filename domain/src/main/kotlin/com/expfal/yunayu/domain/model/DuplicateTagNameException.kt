package com.expfal.yunayu.domain.model

/** 同一父标签下存在同名标签时抛出（唯一索引 `(parent_id, name)` 约束的领域化表达）。 */
class DuplicateTagNameException(message: String) : IllegalArgumentException(message)
