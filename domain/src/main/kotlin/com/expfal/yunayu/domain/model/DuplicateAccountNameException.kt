package com.expfal.yunayu.domain.model

/** 账户重名时抛出（唯一索引 `name` 约束的领域化表达）。 */
class DuplicateAccountNameException(message: String) : IllegalArgumentException(message)
