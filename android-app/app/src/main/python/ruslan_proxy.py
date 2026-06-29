"""
Ruslan Agent API Proxy — Chaquopy Edition
Runs in-process on Android (no Termux required).
Supports: opencode-go, deepseek, openai, anthropic, openrouter, google (Gemini)
Config path: /data/data/ru.valldun.ruslan/files/hermes/ruslan-provider.json

v3: Chaquopy-native, config reload, daemon thread server
"""
import json
import http.server
import urllib.request
import urllib.error
import os
import sys
import time
import threading
from pathlib import Path
PROVIDERS = {
    "opencode-go": {
        "baseUrl": "https://opencode.ai/zen/go",
        "model": "deepseek-v4-flash",
    },
    "deepseek": {
        "baseUrl": "https://api.deepseek.com",
        "model": "deepseek-chat",
    },
    "deepseek-flash": {
        "baseUrl": "https://api.deepseek.com",
        "model": "deepseek-chat",
    },
    "openai": {
        "baseUrl": "https://api.openai.com",
        "model": "gpt-4o-mini",
    },
    "openrouter": {
        "baseUrl": "https://openrouter.ai/api",
        "model": "deepseek/deepseek-chat",
    },
    "google": {
        "baseUrl": "https://generativelanguage.googleapis.com",
        "model": "gemini-2.0-flash",
        "pathStyle": "google",
    },
    "anthropic": {
        "baseUrl": "https://api.anthropic.com",
        "model": "claude-sonnet-4-20250514",
    },
    "yandex": {
        "baseUrl": "https://llm.api.cloud.yandex.net/foundationModels/v1",
        "model": "yandexgpt-lite",
        "pathStyle": "yandex",
    },
    "glm": {
        "baseUrl": "https://open.bigmodel.cn/api/paas/v4",
        "model": "glm-5.2",
    },
    "qwen": {
        "baseUrl": "https://dashscope.aliyuncs.com/compatible-mode/v1",
        "model": "qwen-plus",
    },
    "gigachat": {
        "baseUrl": "https://gigachat.devices.sberbank.ru/api/v1",
        "model": "GigaChat-Max",
        "pathStyle": "gigachat",
    },
    "grok": {
        "baseUrl": "https://api.x.ai/v1",
        "model": "grok-3-mini",
    },
    "mistral": {
        "baseUrl": "https://api.mistral.ai/v1",
        "model": "mistral-large-latest",
    },
    "perplexity": {
        "baseUrl": "https://api.perplexity.ai",
        "model": "sonar",
    },
    "ollama": {
        "baseUrl": "http://localhost:11434/v1",
        "model": "llama3.2",
    },
}

_config = None
_start_time = None
_request_count = 0
_last_error = None
_server_instance = None
_server_thread = None

# Browser-like User-Agent to avoid Cloudflare blocks on some providers
USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"


def _log(msg: str) -> None:
    if LOG_PATH:
        try:
            with open(LOG_PATH, "a") as f:
                f.write(f"[{time.strftime('%H:%M:%S')}] {msg}\n")
        except Exception:
            pass


def _load_config() -> dict:
    cfg = {"provider": "opencode-go", "apiKey": "", "model": "", "baseUrl": ""}
    if CONFIG_PATH and os.path.exists(CONFIG_PATH):
        try:
            with open(CONFIG_PATH) as f:
                cfg.update(json.load(f))
        except Exception:
            pass
    return cfg


def _resolve_config():
    """Resolve active provider config from JSON or built-in defaults."""
    global _config
    cfg = _load_config()
    prov = cfg.get("provider", "opencode-go")
    defaults = PROVIDERS.get(prov, PROVIDERS["opencode-go"])
    _config = {
        "provider": prov,
        "baseUrl": cfg.get("baseUrl") or defaults.get("baseUrl", ""),
        "model": cfg.get("model") or defaults.get("model", ""),
        "apiKey": cfg.get("apiKey", ""),
        "pathStyle": defaults.get("pathStyle", "openai"),
    }
    return _config


def _get_uptime() -> str:
    if _start_time is None:
        return "0с"
    elapsed = int(time.time() - _start_time)
    days, rem = divmod(elapsed, 86400)
    hours, rem = divmod(rem, 3600)
    mins, secs = divmod(rem, 60)
    parts = []
    if days:
        parts.append(f"{days}д")
    if hours:
        parts.append(f"{hours}ч")
    if mins:
        parts.append(f"{mins}м")
    if not parts:
        parts.append(f"{secs}с")
    return " ".join(parts)


def _build_url(path: str, cfg: dict) -> str:
    base = cfg["baseUrl"].rstrip('/')
    model = cfg["model"]
    api_key = cfg["apiKey"]
    style = cfg.get("pathStyle", "openai")

    if style == "google":
        if path in ("/v1/chat/completions", "/chat/completions"):
            return f"{base}/v1beta/models/{model}:generateContent?key={api_key}"
        elif path == "/v1/models":
            return f"{base}/v1beta/models?key={api_key}"
        return f"{base}{path}"
    elif style == "yandex":
        return f"{base}/completion"
    elif style == "gigachat":
        return f"{base}/chat/completions"
    else:
        if path == "/chat/completions":
            path = "/v1/chat/completions"
        # Deduplicate version prefix if base already has it (e.g. base=/v1, path=/v1/...)
        base_last = base.rsplit('/', 1)[-1]
        if base_last and path.startswith('/' + base_last):
            path = path[len(base_last) + 1:]  # remove e.g. /v1 from path start
        return f"{base}{path}"


def _convert_google_request(body: dict) -> dict:
    messages = body.get("messages", [])
    contents = []
    system_instruction = None
    for msg in messages:
        role = msg.get("role", "user")
        content = msg.get("content", "")
        if role == "system":
            system_instruction = {"parts": [{"text": content}]}
        elif role == "user":
            contents.append({"role": "user", "parts": [{"text": content}]})
        elif role == "assistant":
            contents.append({"role": "model", "parts": [{"text": content}]})
    result = {
        "contents": contents,
        "generationConfig": {
            "temperature": body.get("temperature", 0.7),
            "maxOutputTokens": body.get("max_tokens", 4096),
        },
    }
    if system_instruction:
        result["systemInstruction"] = system_instruction
    if body.get("stream"):
        result["generationConfig"]["stream"] = True
    return result


def _convert_google_response(google_resp: dict) -> dict:
    candidates = google_resp.get("candidates", [])
    if not candidates:
        return {
            "choices": [{
                "index": 0,
                "message": {"role": "assistant", "content": ""},
                "finish_reason": "stop",
            }]
        }
    candidate = candidates[0]
    parts = candidate.get("content", {}).get("parts", [])
    text = "".join(p.get("text", "") for p in parts)
    reason = candidate.get("finishReason", "STOP")
    reason_map = {"STOP": "stop", "MAX_TOKENS": "length", "SAFETY": "content_filter"}
    return {
        "choices": [{
            "index": 0,
            "message": {"role": "assistant", "content": text},
            "finish_reason": reason_map.get(reason, "stop"),
        }]
    }


def _convert_yandex_request(body: dict, cfg: dict) -> dict:
    """Convert OpenAI-format request to YandexGPT format."""
    messages = body.get("messages", [])
    ya_messages = []
    for msg in messages:
        role = msg.get("role", "user")
        text = msg.get("content", "")
        if role == "system":
            ya_messages.append({"role": "user", "text": text})
        elif role == "user":
            ya_messages.append({"role": "user", "text": text})
        elif role == "assistant":
            ya_messages.append({"role": "assistant", "text": text})
    api_key = cfg.get("apiKey", "")
    parts = api_key.split(":", 1)
    folder_id = parts[0] if len(parts) > 1 else ""
    model = cfg.get("model", "yandexgpt-lite")
    return {
        "modelUri": f"gpt://{folder_id}/{model}",
        "completionOptions": {
            "stream": False,
            "maxTokens": body.get("max_tokens", 1024),
        },
        "messages": ya_messages,
    }


def _convert_yandex_response(ya_resp: dict) -> dict:
    """Convert YandexGPT response to OpenAI-compatible format."""
    alternatives = ya_resp.get("alternatives", [])
    if not alternatives:
        return {
            "choices": [{
                "index": 0,
                "message": {"role": "assistant", "content": ""},
                "finish_reason": "stop",
            }]
        }
    alt = alternatives[0]
    text = alt.get("message", {}).get("text", "")
    status = alt.get("status", "ALTERNATIVE_STATUS_FINAL")
    finish = "stop" if "FINAL" in status else "length"
    return {
        "choices": [{
            "index": 0,
            "message": {"role": "assistant", "content": text},
            "finish_reason": finish,
        }]
    }


def _convert_gigachat_request(body: dict) -> dict:
    """Convert OpenAI-format request to GigaChat format."""
    messages = body.get("messages", [])
    gc_messages = []
    for msg in messages:
        role = msg.get("role", "user")
        content = msg.get("content", "")
        if role == "system":
            gc_messages.append({"role": "system", "content": content})
        elif role == "user":
            gc_messages.append({"role": "user", "content": content})
        elif role == "assistant":
            gc_messages.append({"role": "assistant", "content": content})
    return {
        "model": body.get("model", "GigaChat-Max"),
        "messages": gc_messages,
        "temperature": body.get("temperature", 0.7),
        "max_tokens": body.get("max_tokens", 1024),
    }


def _convert_gigachat_response(gc_resp: dict) -> dict:
    """Convert GigaChat response to OpenAI-compatible format."""
    choices = gc_resp.get("choices", [])
    if not choices:
        return {
            "choices": [{
                "index": 0,
                "message": {"role": "assistant", "content": ""},
                "finish_reason": "stop",
            }]
        }
    ch = choices[0]
    text = ch.get("message", {}).get("content", "")
    finish = ch.get("finish_reason", "stop")
    return {
        "choices": [{
            "index": 0,
            "message": {"role": "assistant", "content": text},
            "finish_reason": finish,
        }]
    }


class _Handler(http.server.BaseHTTPRequestHandler):
    """HTTP request handler for the proxy."""

    def do_GET(self):
        global _request_count
        _request_count += 1
        if self.path == "/v1/models":
            self._json_response({"data": [{"id": _config.get("model", ""), "object": "model"}]})
        elif self.path == "/health":
            self._json_response({
                "status": "ok",
                "provider": _config.get("provider", "unknown"),
                "model": _config.get("model", ""),
                "uptime": _get_uptime(),
                "requests": _request_count,
                "last_error": str(_last_error) if _last_error else None,
            })
        else:
            self.send_error(404)

    def do_POST(self):
        global _request_count, _last_error
        _request_count += 1

        if self.path not in ("/v1/chat/completions", "/chat/completions"):
            self.send_error(404)
            return

        length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(length)
        try:
            j = json.loads(body)
        except json.JSONDecodeError:
            self.send_error(400, "Invalid JSON")
            return

        cfg = _config  # read once
        if not j.get("model"):
            j["model"] = cfg.get("model", "")

        want_stream = j.get("stream", False)
        style = cfg.get("pathStyle", "openai")

        if style == "google":
            j = _convert_google_request(j)
            request_body = json.dumps(j).encode()
        elif style == "yandex":
            j = _convert_yandex_request(j, cfg)
            request_body = json.dumps(j).encode()
        elif style == "gigachat":
            j = _convert_gigachat_request(j)
            request_body = json.dumps(j).encode()
        else:
            request_body = json.dumps(j).encode()

        url = _build_url(self.path, cfg)
        req = urllib.request.Request(url, data=request_body)
        req.add_header("Content-Type", "application/json")
        req.add_header("User-Agent", USER_AGENT)
        api_key = cfg.get("apiKey", "")
        if style == "yandex":
            parts = api_key.split(":", 1)
            ya_key = parts[1] if len(parts) > 1 else api_key
            req.add_header("Authorization", f"Api-Key {ya_key}")
        elif api_key and style != "google":
            req.add_header("Authorization", f"Bearer {api_key}")

        try:
            resp = urllib.request.urlopen(req, timeout=120)
            if style == "google":
                raw = resp.read()
                converted = _convert_google_response(json.loads(raw))
                self._send_response(200, "application/json", json.dumps(converted).encode())
            elif style == "yandex":
                raw = resp.read()
                converted = _convert_yandex_response(json.loads(raw))
                self._send_response(200, "application/json", json.dumps(converted).encode())
            elif style == "gigachat":
                raw = resp.read()
                converted = _convert_gigachat_response(json.loads(raw))
                self._send_response(200, "application/json", json.dumps(converted).encode())
            elif want_stream:
                self.send_response(200)
                self.send_header("Content-Type", "text/event-stream")
                self.send_header("Cache-Control", "no-cache")
                self.send_header("Connection", "keep-alive")
                self.send_header("Access-Control-Allow-Origin", "*")
                self.end_headers()
                while True:
                    chunk = resp.read(4096)
                    if not chunk:
                        break
                    self.wfile.write(chunk)
                    self.wfile.flush()
                self.wfile.write(b"data: [DONE]\n\n")
                self.wfile.flush()
            else:
                raw = resp.read()
                ct = resp.headers.get("Content-Type", "application/json")
                self._send_response(200, ct, raw)
        except urllib.error.HTTPError as e:
            _last_error = f"HTTP {e.code}: {e.reason}"
            _log(f"HTTPError: {e.code} {e.reason}")
            error_body = e.read().decode(errors="replace") if e.fp else str(e)
            self._send_response(e.code, "application/json",
                               json.dumps({"error": {"message": error_body[:500]}}).encode())
        except Exception as e:
            _last_error = str(e)[:200]
            _log(f"Exception: {e}")
            self.send_error(502, str(e)[:200])

    def do_OPTIONS(self):
        self.send_response(204)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, Authorization")
        self.end_headers()

    def _json_response(self, data: dict):
        self._send_response(200, "application/json", json.dumps(data, indent=2).encode())

    def _send_response(self, code: int, content_type: str, body: bytes):
        self.send_response(code)
        self.send_header("Content-Type", content_type)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt, *args):
        pass  # suppress stderr


# --- Public API (called from Kotlin via Chaquopy) ---

def start_server(port: int = 9123, config_dir: str = "") -> str:
    """
    Start the proxy HTTP server in a daemon thread.
    Called from Kotlin via Python.getInstance().getModule("ruslan_proxy").callAttr("start_server", 9123, configDir)
    Returns: "ok" or error message.
    """
    global CONFIG_PATH, LOG_PATH, _config, _start_time, _server_thread, _server_instance

    if config_dir:
        CONFIG_PATH = os.path.join(config_dir, "ruslan-provider.json")
        LOG_PATH = os.path.join(config_dir, "proxy.log")
        os.makedirs(config_dir, exist_ok=True)
    else:
        # Fallback to app private files dir
        data_dir = os.environ.get("RUSLAN_DATA_DIR", "/data/data/ru.valldun.ruslan/files/hermes")
        CONFIG_PATH = os.path.join(data_dir, "ruslan-provider.json")
        LOG_PATH = os.path.join(data_dir, "proxy.log")
        os.makedirs(data_dir, exist_ok=True)

    _config = _resolve_config()
    _start_time = time.time()

    if _server_thread and _server_thread.is_alive():
        return "already_running"

    try:
        _server_instance = http.server.HTTPServer(("127.0.0.1", port), _Handler)
        _server_thread = threading.Thread(target=_server_instance.serve_forever, daemon=True)
        _server_thread.start()
        _log(f"Proxy v3 started: {_config['provider']} -> {_config['baseUrl']} :{port}")
        return "ok"
    except Exception as e:
        return f"error: {e}"


def stop_server() -> str:
    """Stop the proxy server. Called from Kotlin."""
    global _server_instance
    if _server_instance:
        try:
            _server_instance.shutdown()
            _server_instance.server_close()
            _server_instance = None
        except Exception as e:
            return f"error: {e}"
    return "ok"


def reload_config() -> str:
    """Reload provider config from disk. Call after user changes settings."""
    global _config
    _config = _resolve_config()
    _log(f"Config reloaded: {_config['provider']} -> {_config['baseUrl']}")
    return f"ok: {_config['provider']}/{_config['model']}"


def get_status() -> dict:
    """Return current proxy status as a dict (callable from Kotlin)."""
    return {
        "status": "ok" if (_server_thread and _server_thread.is_alive()) else "stopped",
        "provider": _config.get("provider", "") if _config else "",
        "model": _config.get("model", "") if _config else "",
        "uptime": _get_uptime(),
        "requests": _request_count,
    }


# --- Telegram Bot Polling ---

_telegram_thread = None
_telegram_running = False
_telegram_offset = 0
_telegram_bot_token = ""
_telegram_allowed_users = ""


def start_telegram_bot(token: str, allowed_users: str = "") -> str:
    """Start Telegram bot polling. Called from Kotlin."""
    global _telegram_thread, _telegram_running, _telegram_bot_token, _telegram_allowed_users, _telegram_offset
    if not token or not token.strip():
        _log("Telegram: empty token, not starting")
        return "error: empty token"
    _telegram_bot_token = token.strip()
    _telegram_allowed_users = allowed_users
    _telegram_running = True
    _telegram_offset = 0

    if _telegram_thread and _telegram_thread.is_alive():
        return "already_running"
    def _poll():
        global _telegram_offset
        while _telegram_running:
            try:
                url = f"https://api.telegram.org/bot{_telegram_bot_token}/getUpdates?offset={_telegram_offset}&timeout=10"
                req = urllib.request.Request(url)
                req.add_header("User-Agent", USER_AGENT)
                resp = urllib.request.urlopen(req, timeout=15)
                data = json.loads(resp.read())
                updates = data.get("result", [])
                for upd in updates:
                    _telegram_offset = upd["update_id"] + 1
                    msg = upd.get("message", {})
                    chat_id = msg.get("chat", {}).get("id")
                    text = msg.get("text", "")
                    username = msg.get("from", {}).get("username", "")
                    user_id = str(msg.get("from", {}).get("id", ""))
                    # Check allowed users
                    if _telegram_allowed_users:
                        allowed = [u.strip().lstrip("@") for u in _telegram_allowed_users.split(",")]
                        if username not in allowed and user_id not in allowed:
                            continue
                    # Forward to LLM
                    if text and chat_id:
                        llm_resp = _ask_llm(text)
                        _send_telegram_message(chat_id, llm_resp)
            except Exception as e:
                _log(f"Telegram poll error: {e}")
                time.sleep(5)

    _telegram_thread = threading.Thread(target=_poll, daemon=True)
    _telegram_thread.start()
    _log(f"Telegram bot started, allowed: {allowed_users}")
    return "ok"


def stop_telegram_bot() -> str:
    """Stop Telegram bot polling."""
    global _telegram_running
    _telegram_running = False
    return "ok"


def _ask_llm(text: str) -> str:
    """Send text to configured LLM and return response."""
    cfg = _config
    if not cfg:
        return "⚠ LLM не настроен"
    try:
        url = _build_url("/v1/chat/completions", cfg)
        body = json.dumps({
            "model": cfg.get("model", ""),
            "messages": [{"role": "user", "content": text}],
            "stream": False,
        }).encode()
        req = urllib.request.Request(url, data=body)
        req.add_header("Content-Type", "application/json")
        req.add_header("User-Agent", USER_AGENT)
        api_key = cfg.get("apiKey", "")
        style = cfg.get("pathStyle", "openai")
        if style == "yandex":
            parts = api_key.split(":", 1)
            ya_key = parts[1] if len(parts) > 1 else api_key
            req.add_header("Authorization", f"Api-Key {ya_key}")
        elif api_key and style != "google":
            req.add_header("Authorization", f"Bearer {api_key}")
        resp = urllib.request.urlopen(req, timeout=60)
        raw = resp.read()
        j = json.loads(raw)
        return j.get("choices", [{}])[0].get("message", {}).get("content", "пустой ответ")
    except Exception as e:
        return f"⚠ Ошибка: {e}"


def _send_telegram_message(chat_id: int, text: str) -> None:
    """Send message via Telegram Bot API. Split long messages (>4096)."""
    max_len = 4096
    chunks = []
    remaining = text
    while len(remaining) > max_len:
        # Prefer split on newline
        split_pos = remaining.rfind('\n', 0, max_len)
        if split_pos == -1:
            split_pos = max_len
        chunks.append(remaining[:split_pos])
        remaining = remaining[split_pos:].lstrip()
    chunks.append(remaining)

    for chunk in chunks:
        try:
            url = f"https://api.telegram.org/bot{_telegram_bot_token}/sendMessage"
            body = json.dumps({"chat_id": chat_id, "text": chunk}).encode()
            req = urllib.request.Request(url, data=body)
            req.add_header("Content-Type", "application/json")
            req.add_header("User-Agent", USER_AGENT)
            urllib.request.urlopen(req, timeout=10)
        except Exception as e:
            _log(f"Telegram send error: {e}")


# Allow running standalone (for testing outside Chaquopy)
if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 9123
    config_dir = sys.argv[2] if len(sys.argv) > 2 else ""
    result = start_server(port, config_dir)
    print(f"Server: {result}")
    if result == "ok":
        print(f"Proxy running on :{port}")
        # Block main thread
        try:
            while True:
                time.sleep(60)
        except KeyboardInterrupt:
            stop_server()
