import base64
import json
import tempfile
import threading
import unittest
from http.client import HTTPConnection
from pathlib import Path

from server import Service
from server.http import make_server


class HttpTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.service = Service(Path(self.temp.name) / 'http.sqlite3')
        self.server = make_server(self.service, port=0)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()

    def tearDown(self):
        self.server.shutdown()
        self.thread.join()
        self.server.server_close()
        self.service.close()
        self.temp.cleanup()

    def request(self, method, path, body=None, token=None, headers=None):
        connection = HTTPConnection('127.0.0.1', self.server.server_port, timeout=5)
        request_headers = {'Content-Type': 'application/json', **(headers or {})}
        if token:
            request_headers['Authorization'] = 'Bearer ' + token
        payload = json.dumps(body).encode() if isinstance(body, dict) else body
        connection.request(method, path, payload, request_headers)
        response = connection.getresponse()
        result = (response.status, dict(response.headers), response.read())
        connection.close()
        return result

    def test_health_login_and_authentication_over_http(self):
        status, headers, data = self.request('GET', '/health')
        self.assertEqual(200, status)
        self.assertEqual({'status': 'ok', 'apiVersion': 1}, json.loads(data))
        status, _, data = self.request('POST', '/v1/register', {'handle': 'alice', 'password': 'password 12345', 'displayName': '家长'})
        self.assertEqual(200, status, data)
        token = json.loads(data)['token']
        self.assertEqual(200, self.request('GET', '/v1/state', token=token)[0])
        self.assertEqual(401, self.request('GET', '/v1/state')[0])
        self.assertEqual(200, self.request('POST', '/v1/login', {'handle': 'alice', 'password': 'password 12345'})[0])

    def test_malformed_and_oversized_json_are_rejected(self):
        self.assertEqual(400, self.request('POST', '/v1/register', b'{broken')[0])
        self.assertEqual(400, self.request('POST', '/v1/register', b'[]')[0])
        self.assertEqual(413, self.request('POST', '/v1/register', b'', headers={'Content-Length': str(6 * 1024 * 1024 + 1)})[0])

    def test_shared_reward_lifecycle_and_media_over_real_http(self):
        def post(path, body, token=None):
            status, _, payload = self.request('POST', path, body, token)
            self.assertEqual(200, status, payload)
            return json.loads(payload)

        alice = post('/v1/register', {'handle': 'alice', 'password': 'strong password', 'displayName': '小林'})
        bob = post('/v1/register', {'handle': 'bob', 'password': 'strong password', 'displayName': '小陈'})
        relation = post('/v1/relationships', {'requestId': 'relation', 'name': '我们', 'type': 'couple', 'mode': 'shared'}, alice['token'])
        relation = post('/v1/relationships/join', {'requestId': 'join', 'inviteCode': relation['inviteCode']}, bob['token'])
        item = post('/v1/agreements', {'requestId': 'create', 'relationshipId': relation['id'], 'executorId': bob['user']['id'], 'title': '一起散步', 'conditions': '散步一次', 'reward': '一起吃晚餐', 'dueAt': None, 'targetCount': 1, 'redeemWithinDays': 7, 'publish': True}, alice['token'])
        image = base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a5RkAAAAASUVORK5CYII=')
        media = post('/v1/media', {'requestId': 'upload', 'mime': 'image/png', 'data': base64.b64encode(image).decode()}, bob['token'])
        for action, actor, fields, expected_task, expected_reward in (
            ('accept', bob, {}, 'active', 'unearned'),
            ('submit', bob, {'mediaIds': [media['id']]}, 'submitted', 'unearned'),
            ('review', alice, {'approved': True}, 'achieved', 'pending'),
            ('give', alice, {}, 'achieved', 'offered'),
            ('receive', bob, {}, 'achieved', 'received'),
        ):
            item = post('/v1/agreements/' + item['id'] + '/actions', {'requestId': action, 'expectedVersion': item['version'], 'action': action, **fields}, actor['token'])
            self.assertEqual((expected_task, expected_reward), (item['taskState'], item['rewardState']))
        status, headers, payload = self.request('GET', '/v1/media/' + media['id'], token=alice['token'])
        self.assertEqual(200, status)
        self.assertEqual('image/png', headers['Content-Type'])
        self.assertEqual('no-store', headers['Cache-Control'])
        self.assertEqual(image, payload)


if __name__ == '__main__':
    unittest.main()
