"""Ruslan Agent API Proxy — bundles with the Android app.
Supports: deepseek, openai, anthropic, openrouter
Reads config from ~/.hermes/ruslan-provider.json
"""
import json, http.server, urllib.request, os, sys, signal

CONFIG_PATH = os.path.expanduser('~/.hermes/ruslan-provider.json')

PROVIDERS = {
    'deepseek':   {'baseUrl': 'https://api.deepseek.com/v1',           'model': 'deepseek-chat'},
    'openai':     {'baseUrl': 'https://api.openai.com/v1',             'model': 'gpt-4o'},
    'openrouter': {'baseUrl': 'https://openrouter.ai/api/v1',          'model': 'deepseek-chat'},
}

def load_config():
    cfg = {'provider': 'deepseek', 'apiKey': '', 'model': '', 'baseUrl': ''}
    try:
        if os.path.exists(CONFIG_PATH):
            with open(CONFIG_PATH) as f:
                cfg.update(json.load(f))
    except Exception:
        pass
    return cfg

cfg = load_config()
prov = cfg.get('provider', 'deepseek')
defaults = PROVIDERS.get(prov, {})
BASE_URL = cfg.get('baseUrl') or defaults.get('baseUrl', '')
DEFAULT_MODEL = cfg.get('model') or defaults.get('model', '')
API_KEY = cfg.get('apiKey', '')

class Handler(http.server.BaseHTTPRequestHandler):
    def do_POST(self):
        if self.path not in ('/v1/chat/completions', '/chat/completions'):
            self.send_error(404)
            return
        length = int(self.headers.get('Content-Length', 0))
        body = self.rfile.read(length)
        try:
            j = json.loads(body)
            if not j.get('model'):
                j['model'] = DEFAULT_MODEL
            body = json.dumps(j).encode()
        except:
            pass
        req = urllib.request.Request(BASE_URL + self.path, data=body)
        req.add_header('Content-Type', 'application/json')
        if API_KEY:
            req.add_header('Authorization', f'Bearer {API_KEY}')
        try:
            resp = urllib.request.urlopen(req, timeout=60)
            self.send_response(200)
            self.send_header('Content-Type', resp.headers.get('Content-Type', 'application/json'))
            self.end_headers()
            self.wfile.write(resp.read())
        except urllib.error.HTTPError as e:
            self.send_response(e.code)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(json.dumps({'error': {'message': str(e)}}).encode())
        except Exception as e:
            self.send_error(502, str(e))

    def do_GET(self):
        if self.path == '/v1/models':
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(json.dumps({'data': [{'id': DEFAULT_MODEL, 'object': 'model'}]}).encode())
        else:
            self.send_error(404)

    def log_message(self, fmt, *args):
        pass

if __name__ == '__main__':
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 9123
    print(f'Ruslan proxy: {prov} -> {BASE_URL} :{port} model={DEFAULT_MODEL}')
    http.server.HTTPServer(('127.0.0.1', port), Handler).serve_forever()
