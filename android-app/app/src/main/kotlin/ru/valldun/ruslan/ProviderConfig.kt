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

        val PROVIDER_MODELS: Map<String, List<String>> = mapOf(
            "deepseek" to listOf("deepseek-chat", "deepseek-reasoner", "deepseek-v3", "deepseek-r1", "deepseek-v4-flash"),
            "opencode-go" to listOf("deepseek-v4-flash", "kimi-k2.5", "deepseek-v4", "glm-5.2"),
            "openai" to listOf("gpt-4o", "gpt-4o-mini", "gpt-4.1", "gpt-4.1-mini", "gpt-4.1-nano", "o3-mini", "o4-mini"),
            "anthropic" to listOf("claude-sonnet-4-20250514", "claude-3.5-sonnet", "claude-3.5-haiku", "claude-3-opus"),
            "openrouter" to listOf("deepseek/deepseek-chat", "deepseek/deepseek-r1", "openai/gpt-4o", "openai/o3-mini", "anthropic/claude-sonnet-4", "google/gemini-2.0-flash"),
            "google" to listOf("gemini-2.0-flash", "gemini-2.0-flash-lite", "gemini-1.5-flash", "gemini-1.5-pro"),
            "grok" to listOf("grok-3", "grok-3-mini", "grok-3-mini-beta"),
            "yandex" to listOf("yandexgpt-lite", "yandexgpt-pro", "yandexgpt"),
            "gigachat" to listOf("GigaChat-Max", "GigaChat-Plus", "GigaChat-Standard", "GigaChat-Pro"),
            "glm" to listOf("glm-5.2", "glm-5.1", "glm-4-plus", "glm-4-flash"),
            "qwen" to listOf("qwen-plus", "qwen-max", "qwen-turbo", "qwen2.5-72b-instruct"),
            "mistral" to listOf("mistral-large-latest", "mistral-small-latest", "mistral-medium"),
            "perplexity" to listOf("sonar", "sonar-pro", "sonar-deep-research"),
            "ollama" to listOf("llama3.2", "llama3.2-vision", "llama3.1", "mistral", "qwen2.5"),
        )

        fun getModelsForProvider(providerId: String): List<String> =
            PROVIDER_MODELS[providerId] ?: listOf("default")
    }
}
