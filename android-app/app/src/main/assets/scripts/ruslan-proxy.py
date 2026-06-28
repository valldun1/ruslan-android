"""Ruslan Agent API Proxy — bundles with the Android app.
Supports: opencode-go, deepseek, openai, anthropic, openrouter, google (Gemini)
Reads config from ~/.hermes/ruslan-provider.json
v2: Streaming (SSE chunked), health-check, multi-provider
"""
import json, http.server, urllib.request, os, sys, signal, time

CONFIG_PATH = os.path.expanduser("~/.hermes/ruslan-provider.json")
LOG_FILE = os.path.expanduser("~/.hermes/proxy.log")

PROVIDERS = {
    "opencode-go": {
        "baseUrl": "https://opencode.ai/zen/go/v1",
        "model": "deepseek-v4-flash",
    },
    "deepseek": {
        "baseUrl": "https://api.deepseek.com/v1",
        "model": "deepseek-chat",
    },
    "openai": {
        "baseUrl": "https://api.openai.com/v1",
        "model": "gpt-4o-mini",
    },
    "openrouter": {
        "baseUrl": "https://openrouter.ai/api/v1",
        "model": "deepseek/deepseek-chat",
    },
    "google": {
        "baseUrl": "https://generativelanguage.googleapis.com/v1beta",
        "model": "gemini-2.0-flash",
        "pathStyle": "google",  # special path format
    },
    "anthropic": {
        "baseUrl": "https://api.anthropic.com/v1",
        "model": "claude-sonnet-4-20250514",
    },
}


def log(msg: str) -> None:
    try:
        with open(LOG_FILE, "a") as f:
            f.write(f"[{time.strftime('%H:%M:%S')}] {msg}\n")
    except Exception:
        pass


def load_config() -> dict:
    cfg = {"provider": "opencode-go", "apiKey": "", "model": "", "baseUrl": ""}
    try:
        if os.path.exists(CONFIG_PATH):
            with open(CONFIG_PATH) as f:
                cfg.update(json.load(f))
    except Exception:
        pass
    return cfg


cfg = load_config()
prov = cfg.get("provider", "opencode-go")
defaults = PROVIDERS.get(prov, PROVIDERS["opencode-go"])
BASE_URL = cfg.get("baseUrl") or defaults.get("baseUrl", "")
DEFAULT_MODEL = cfg.get("model") or defaults.get("model", "")
API_KEY = cfg.get("apiKey", "")
PATH_STYLE = defaults.get("pathStyle", "openai")  # "openai" or "google"

_START_TIME = time.time()
_REQUEST_COUNT = 0
_LAST_ERROR = None


def get_uptime() -> str:
    elapsed = int(time.time() - _START_TIME)
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


def build_url(path: str) -> str:
    """Build the full URL based on provider's path style."""
    if PATH_STYLE == "google":
        # Google AI uses: /v1beta/models/{model}:generateContent?key={api_key}
        # But we maintain OpenAI-compatible paths in chat, so map:
        if path == "/v1/chat/completions" or path == "/chat/completions":
            return f"{BASE_URL}/models/{DEFAULT_MODEL}:generateContent?key={API_KEY}"
        elif path == "/v1/models":
            return f"{BASE_URL}/models?key={API_KEY}"
        return f"{BASE_URL}{path}"
    else:
        if path == "/chat/completions":
            path = "/v1/chat/completions"
        return f"{BASE_URL}{path}"


def convert_google_request(body: dict) -> dict:
    """Convert OpenAI-compatible request to Google Gemini format."""
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


def convert_google_response(google_resp: dict) -> dict:
    """Convert Google Gemini response to OpenAI-compatible format."""
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
    content_obj = candidate.get("content", {})
    parts = content_obj.get("parts", [])
    text = "".join(p.get("text", "") for p in parts)
    finish_reason = candidate.get("finishReason", "STOP")
    reason_map = {"STOP": "stop", "MAX_TOKENS": "length", "SAFETY": "content_filter"}
    return {
        "choices": [{
            "index": 0,
            "message": {"role": "assistant", "content": text},
            "finish_reason": reason_map.get(finish_reason, "stop"),
        }]
    }


class Handler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        global _REQUEST_COUNT
        _REQUEST_COUNT += 1

        if self.path == "/v1/models":
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            self.wfile.write(
                json.dumps(
                    {"data": [{"id": DEFAULT_MODEL, "object": "model"}]}
                ).encode()
            )
        elif self.path == "/health":
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            uptime = get_uptime()
            status = {
                "status": "ok",
                "provider": prov,
                "model": DEFAULT_MODEL,
                "uptime": uptime,
                "requests": _REQUEST_COUNT,
                "last_error": str(_LAST_ERROR) if _LAST_ERROR else None,
            }
            self.wfile.write(json.dumps(status, indent=2).encode())
        else:
            self.send_error(404)

    def do_POST(self):
        global _REQUEST_COUNT, _LAST_ERROR
        _REQUEST_COUNT += 1

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

        if not j.get("model"):
            j["model"] = DEFAULT_MODEL

        want_stream = j.get("stream", False)

        # Handle Google path style
        if PATH_STYLE == "google":
            j = convert_google_request(j)
            request_body = json.dumps(j).encode()
            url = build_url(self.path)
        else:
            request_body = json.dumps(j).encode()
            url = build_url(self.path)

        req = urllib.request.Request(url, data=request_body)
        req.add_header("Content-Type", "application/json")
        if API_KEY and PATH_STYLE != "google":
            req.add_header("Authorization", f"Bearer {API_KEY}")

        try:
            resp = urllib.request.urlopen(req, timeout=120)

            if PATH_STYLE == "google":
                # Google: read full response, convert to OpenAI format
                raw = resp.read()
                google_resp = json.loads(raw)
                converted = convert_google_response(google_resp)
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.send_header("Access-Control-Allow-Origin", "*")
                self.end_headers()
                self.wfile.write(json.dumps(converted).encode())
            elif want_stream:
                # Streaming: read chunks and forward as SSE
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
                # Non-streaming: read full response
                raw = resp.read()
                self.send_response(200)
                self.send_header("Content-Type", resp.headers.get(
                    "Content-Type", "application/json"
                ))
                self.send_header("Access-Control-Allow-Origin", "*")
                self.end_headers()
                self.wfile.write(raw)

        except urllib.error.HTTPError as e:
            _LAST_ERROR = f"HTTP {e.code}: {e.reason}"
            log(f"HTTPError: {e.code} {e.reason}")
            error_body = e.read().decode(errors="replace") if e.fp else str(e)
            self.send_response(e.code)
            self.send_header("Content-Type", "application/json")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            self.wfile.write(
                json.dumps({"error": {"message": error_body[:500]}}).encode()
            )
        except Exception as e:
            _LAST_ERROR = str(e)[:200]
            log(f"Exception: {e}")
            self.send_error(502, str(e)[:200])

    def do_OPTIONS(self):
        """CORS preflight."""
        self.send_response(204)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, Authorization")
        self.end_headers()

    def log_message(self, fmt, *args):
        pass  # suppress default stderr logging


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 9123
    log(f"Ruslan proxy v2 started: {prov} -> {BASE_URL} :{port} model={DEFAULT_MODEL}")
    print(
        f"Ruslan proxy v2: {prov} -> {BASE_URL} :{port} model={DEFAULT_MODEL}"
    )
    http.server.HTTPServer(("127.0.0.1", port), Handler).serve_forever()
