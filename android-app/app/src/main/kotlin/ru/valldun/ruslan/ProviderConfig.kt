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
            ProviderConfig("deepseek", "DeepSeek", baseUrl = "https://api.deepseek.com/v1", defaultModel = "deepseek-chat"),
            ProviderConfig("opencode-go", "OpenCode Go", baseUrl = "https://opencode.ai/zen/go/v1", defaultModel = "deepseek-v4-flash"),
            ProviderConfig("openai", "OpenAI", baseUrl = "https://api.openai.com/v1", defaultModel = "gpt-4o"),
            ProviderConfig("anthropic", "Anthropic", baseUrl = "https://api.anthropic.com/v1", defaultModel = "claude-sonnet-4-20250514"),
            ProviderConfig("openrouter", "OpenRouter", baseUrl = "https://openrouter.ai/api/v1", defaultModel = "deepseek/deepseek-chat"),
            ProviderConfig("google", "Google Gemini", baseUrl = "https://generativelanguage.googleapis.com/v1beta", defaultModel = "gemini-2.0-flash"),
            ProviderConfig("glm", "GLM (Zhipu)", baseUrl = "https://open.bigmodel.cn/api/paas/v4", defaultModel = "glm-5.2"),
            ProviderConfig("qwen", "Qwen (Alibaba)", baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1", defaultModel = "qwen-plus"),
            ProviderConfig("yandex", "YandexGPT", baseUrl = "https://llm.api.cloud.yandex.net/foundationModels/v1", defaultModel = "yandexgpt-lite"),
            ProviderConfig("gigachat", "GigaChat (Sber)", baseUrl = "https://gigachat.devices.sberbank.ru/api/v1", defaultModel = "GigaChat-Max"),
            ProviderConfig("grok", "Grok (xAI)", baseUrl = "https://api.x.ai/v1", defaultModel = "grok-3-mini"),
            ProviderConfig("mistral", "Mistral", baseUrl = "https://api.mistral.ai/v1", defaultModel = "mistral-large-latest"),
            ProviderConfig("perplexity", "Perplexity", baseUrl = "https://api.perplexity.ai", defaultModel = "sonar"),
            ProviderConfig("ollama", "Ollama (локальный)", baseUrl = "http://localhost:11434/v1", defaultModel = "llama3.2"),
            ProviderConfig("custom", "Пользовательский", baseUrl = "https://", defaultModel = ""),
        )
    }
}
