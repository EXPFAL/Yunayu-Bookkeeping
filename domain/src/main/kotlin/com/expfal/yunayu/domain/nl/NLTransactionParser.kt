package com.expfal.yunayu.domain.nl

/**
 * 自然语言交易解析引擎接缝。
 *
 * 职责：发起推理并返回模型原始文本。本模块只消费该接口、不关心引擎实现；
 * 当前由 :data 的在线实现 ApiNlParser 提供，接缝对后端（在线 API / 未来端侧）无感知。
 */
interface NLTransactionParser {

    /** 引擎是否已就绪可用。 */
    suspend fun isAvailable(): Boolean

    /**
     * 依据系统指令与用户输入推理，返回模型原始输出文本；
     * 引擎不可用或推理出错时返回 `null`。
     */
    suspend fun generate(systemInstruction: String, userText: String): String?
}
