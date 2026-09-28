"""Bounded JSON HTTP adapter. It never logs credentials or request bodies."""

import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from .service import Response


MAX_BODY = 6 * 1024 * 1024


def make_server(service, host='127.0.0.1', port=8765):
    class Handler(BaseHTTPRequestHandler):
        server_version = 'Pact/1'
        sys_version = ''

        def log_message(self, format, *args):
            pass

        def send_result(self, result):
            payload = result.body if isinstance(result.body, bytes) else json.dumps(result.body, ensure_ascii=False).encode('utf-8')
            self.send_response(result.status)
            self.send_header('Content-Type', result.headers.get('Content-Type', 'application/json; charset=utf-8'))
            self.send_header('Content-Length', str(len(payload)))
            self.send_header('Cache-Control', 'no-store')
            self.send_header('X-Content-Type-Options', 'nosniff')
            self.end_headers()
            self.wfile.write(payload)

        def error(self, status, code, message):
            self.send_result(Response(status, {'error': {'code': code, 'message': message}}))

        def process(self):
            self.connection.settimeout(15)
            try:
                body = None
                if self.command == 'POST':
                    if self.headers.get('Transfer-Encoding'):
                        self.error(400, 'invalid_encoding', '不支持此传输格式')
                        return
                    length = int(self.headers.get('Content-Length', '0'))
                    if length < 0:
                        raise ValueError()
                    if length > MAX_BODY:
                        self.error(413, 'body_too_large', '请求内容超过 6 MiB')
                        return
                    if self.headers.get_content_type() != 'application/json':
                        self.error(400, 'invalid_content_type', '请使用 JSON 请求')
                        return
                    raw = self.rfile.read(length)
                    if len(raw) != length:
                        raise ValueError()
                    body = json.loads(raw.decode('utf-8'))
                authorization = self.headers.get('Authorization', '')
                token = authorization[7:] if authorization.startswith('Bearer ') else None
                self.send_result(service.handle(self.command, self.path, body, token))
            except (ValueError, UnicodeError, RecursionError):
                self.error(400, 'invalid_json', '请求 JSON 格式不正确')
            except (TimeoutError, ConnectionError, BrokenPipeError):
                self.close_connection = True

        do_GET = process
        do_POST = process

    return ThreadingHTTPServer((host, port), Handler)
