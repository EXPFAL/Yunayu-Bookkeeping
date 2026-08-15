package com.expfal.yunayu.data.nlparse

import com.expfal.yunayu.domain.model.NlApiConfig
import com.expfal.yunayu.domain.repository.NlApiConfigRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** [ApiNlParser] 配置解析（trim + 逐字段回退）与 [ApiNlParser.isAvailable] 三字段判定的 JVM 直测。 */
class ApiNlParserTest {

    private val defaultBaseUrl = "https://default.example.com"
    private val defaultModel = "default-model"
    private val defaultApiKey = "default-key"

    @Test
    fun `saved non-blank values win over defaults`() {
        val saved = NlApiConfig("https://saved.example.com", "saved-model", "saved-key")

        assertEquals(saved, resolve(saved))
    }

    @Test
    fun `blank saved fields fall back to defaults per field`() {
        val saved = NlApiConfig(" ", "\t", "")

        assertEquals(
            NlApiConfig(defaultBaseUrl, defaultModel, defaultApiKey),
            resolve(saved),
        )
    }

    @Test
    fun `partially saved config only falls back for blank fields`() {
        val saved = NlApiConfig("https://saved.example.com", "", "saved-key")

        assertEquals(
            NlApiConfig("https://saved.example.com", defaultModel, "saved-key"),
            resolve(saved),
        )
    }

    @Test
    fun `all empty config falls back entirely to defaults`() {
        assertEquals(
            NlApiConfig(defaultBaseUrl, defaultModel, defaultApiKey),
            resolve(NlApiConfig("", "", "")),
        )
    }

    @Test
    fun `saved values are trimmed before fallback`() {
        val saved = NlApiConfig("  https://saved.example.com  ", "  saved-model  ", "  saved-key  ")

        assertEquals(
            NlApiConfig("https://saved.example.com", "saved-model", "saved-key"),
            resolve(saved),
        )
    }

    @Test
    fun `isAvailable true when blank saved fields fall back to populated defaults`() = runTest {
        val parser = ApiNlParser(FakeNlApiConfigRepository(NlApiConfig("", "", "sk-fake")))

        assertTrue(parser.isAvailable())
    }

    @Test
    fun `isAvailable false when fallback api key remains blank`() = runTest {
        val parser = ApiNlParser(FakeNlApiConfigRepository(NlApiConfig("", "", "")))

        assertFalse(parser.isAvailable())
    }

    private fun resolve(saved: NlApiConfig): NlApiConfig =
        CompletionRequester.resolveConfig(
            saved = saved,
            defaultBaseUrl = defaultBaseUrl,
            defaultModel = defaultModel,
            defaultApiKey = defaultApiKey,
        )

    /** 手写 fake 仓储，返回固定配置。 */
    private class FakeNlApiConfigRepository(
        private val config: NlApiConfig,
    ) : NlApiConfigRepository {
        override suspend fun load(): NlApiConfig = config

        override suspend fun save(config: NlApiConfig) = Unit
    }
}
