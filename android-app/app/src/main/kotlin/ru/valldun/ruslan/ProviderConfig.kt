package ru.valldun.ruslan

/**
 * Provider configuration data class
 */
data class ProviderConfig(
    val id: String,           // unique id (deepseek, openai, etc.)
    val name: String,         // display name (DeepSeek, OpenAI, etc.)
    val apiKey: String = "",
    val baseUrl: String = "",
    val defaultModel: String = "",
    val isActive: Boolean = false
) {
    companion object {
        val BUILT_IN = listOf(
            ProviderConfig("deepseek", "DeepSeek", defaultModel = "deepseek-chat"),
            ProviderConfig("openai", "OpenAI", defaultModel = "gpt-4o"),
            ProviderConfig("anthropic", "Anthropic", defaultModel = "claude-sonnet-4"),
            ProviderConfig("openrouter", "OpenRouter", baseUrl = "https://openrouter.ai/api/v1"),
            ProviderConfig("glm", "GLM (Zhipu)", baseUrl = "https://open.bigmodel.cn/api/paas/v4", defaultModel = "glm-5.2"),
            ProviderConfig("qwen", "Qwen (Alibaba)", baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1", defaultModel = "qwen-plus"),
            ProviderConfig("yandex", "YandexGPT", defaultModel = "yandexgpt-lite"),
            ProviderConfig("gigachat", "GigaChat (Sber)", defaultModel = "GigaChat-Max"),
            ProviderConfig("custom", "Пользовательский", baseUrl = "https://"),
        )
    }
}
