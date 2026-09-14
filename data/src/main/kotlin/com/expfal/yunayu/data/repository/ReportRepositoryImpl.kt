package com.expfal.yunayu.data.repository

import com.expfal.yunayu.data.local.dao.ReportDao
import com.expfal.yunayu.data.local.entity.ReportEntity
import com.expfal.yunayu.domain.report.model.CategoryShare
import com.expfal.yunayu.domain.report.model.Report
import com.expfal.yunayu.domain.report.model.ReportPeriodType
import com.expfal.yunayu.domain.report.model.ReportStatus
import com.expfal.yunayu.domain.repository.ReportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** [ReportRepository] 的 Room 实现。 */
@Singleton
class ReportRepositoryImpl @Inject constructor(
    private val reportDao: ReportDao,
) : ReportRepository {

    override fun observeByType(type: ReportPeriodType): Flow<List<Report>> =
        reportDao.observeByType(type.name).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getByKey(periodType: ReportPeriodType, periodKey: String): Report? =
        reportDao.getByKey(periodType.name, periodKey)?.toDomain()

    override suspend fun upsert(report: Report) {
        reportDao.upsert(report.toEntity())
    }

    override suspend fun invalidateWhereWindowContains(epochMillis: Long) {
        reportDao.invalidateWhereWindowContains(epochMillis)
    }

    private fun ReportEntity.toDomain(): Report = Report(
        id = id,
        periodType = runCatching { ReportPeriodType.valueOf(reportType) }
            .getOrDefault(ReportPeriodType.MONTHLY),
        periodKey = periodKey,
        windowStartMs = windowStartMs,
        windowEndMs = windowEndMs,
        incomeCents = incomeCents,
        expenseCents = expenseCents,
        topCategories = parseTopCategories(topCategories),
        prevIncomeCents = prevIncomeCents,
        prevExpenseCents = prevExpenseCents,
        analysisText = analysisText,
        status = runCatching { ReportStatus.valueOf(status) }
            .getOrDefault(ReportStatus.FAILED),
        generatedAtMs = generatedAt,
    )

    private fun Report.toEntity(): ReportEntity = ReportEntity(
        id = id,
        reportType = periodType.name,
        periodKey = periodKey,
        windowStartMs = windowStartMs,
        windowEndMs = windowEndMs,
        incomeCents = incomeCents,
        expenseCents = expenseCents,
        topCategories = serializeTopCategories(topCategories),
        prevIncomeCents = prevIncomeCents,
        prevExpenseCents = prevExpenseCents,
        analysisText = analysisText,
        status = status.name,
        engine = ENGINE,
        contentVersion = CONTENT_VERSION,
        generatedAt = generatedAtMs,
    )

    companion object {
        /** 生成引擎标识（当前为 OpenAI 兼容在线 API）。 */
        const val ENGINE = "api"

        /** 报告提示词 / 内容版本。 */
        const val CONTENT_VERSION = "1"

        /** JSON 序列化前缀，与 legacy `name:cents:percent;` 区分。 */
        private const val JSON_PREFIX = "j1:"

        /**
         * 序列化分类占比：`j1:` + JSONArray of `{n,c,p}`（未分类 `n` 为空串）。
         * 名称可含任意字符，不再做冒号/分号转义。
         */
        internal fun serializeTopCategories(categories: List<CategoryShare>): String {
            val array = JSONArray()
            for (share in categories) {
                array.put(
                    JSONObject()
                        .put("n", share.tagName.orEmpty())
                        .put("c", share.cents)
                        .put("p", share.percent),
                )
            }
            return JSON_PREFIX + array.toString()
        }

        /**
         * 反序列化分类占比：`j1:` 前缀走 JSON；否则按 legacy 分号格式。
         * 单个条目非法即跳过，整体失败回退空列表。
         */
        internal fun parseTopCategories(raw: String): List<CategoryShare> {
            if (raw.startsWith(JSON_PREFIX)) {
                return parseJsonTopCategories(raw.removePrefix(JSON_PREFIX))
            }
            return raw.split(';').mapNotNull { entry -> parseLegacyEntry(entry) }
        }

        private fun parseJsonTopCategories(json: String): List<CategoryShare> =
            runCatching {
                val array = JSONArray(json)
                buildList {
                    for (i in 0 until array.length()) {
                        val obj = array.optJSONObject(i) ?: continue
                        val cents = obj.optLong("c", Long.MIN_VALUE)
                        if (cents == Long.MIN_VALUE) continue
                        if (!obj.has("p")) continue
                        val percent = obj.optInt("p")
                        val name = obj.optString("n", "")
                        add(
                            CategoryShare(
                                tagName = name.takeIf { it.isNotEmpty() },
                                cents = cents,
                                percent = percent,
                            ),
                        )
                    }
                }
            }.getOrDefault(emptyList())

        private fun parseLegacyEntry(entry: String): CategoryShare? {
            val parts = entry.split(':')
            if (parts.size != 3) return null
            val cents = parts[1].toLongOrNull() ?: return null
            val percent = parts[2].toIntOrNull() ?: return null
            return CategoryShare(
                tagName = parts[0].takeIf { it.isNotEmpty() },
                cents = cents,
                percent = percent,
            )
        }
    }
}
