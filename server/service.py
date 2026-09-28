"""Transactional API boundary shared by HTTP and behavioral tests."""

import base64
import binascii
import hashlib
import hmac
import json
import re
import secrets
import sqlite3
import threading
import uuid
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone
from pathlib import Path


@dataclass
class Response:
    status: int
    body: object
    headers: dict = field(default_factory=dict)


class ApiError(Exception):
    def __init__(self, status, code, message):
        self.status, self.code, self.message = status, code, message


def fail(status, code, message):
    raise ApiError(status, code, message)


def encode(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':'))


def new_id():
    return str(uuid.uuid4())


def timestamp(value):
    return value.astimezone(timezone.utc).isoformat(timespec='seconds').replace('+00:00', 'Z')


def parse_time(value):
    if not isinstance(value, str):
        fail(400, 'invalid_time', '时间必须为带时区的 ISO 8601 格式')
    try:
        result = datetime.fromisoformat(value.replace('Z', '+00:00'))
        if result.tzinfo is None:
            raise ValueError()
        return result.astimezone(timezone.utc)
    except ValueError:
        fail(400, 'invalid_time', '时间必须为带时区的 ISO 8601 格式')


def string(body, key, maximum, required=True):
    value = body.get(key, '')
    if not isinstance(value, str) or len(value) > maximum or (required and not value.strip()):
        fail(400, 'invalid_field', f'{key} 内容为空或长度不符合要求')
    return value.strip()


def integer(body, key, low, high):
    value = body.get(key)
    if type(value) is not int or not low <= value <= high:
        fail(400, 'invalid_field', f'{key} 必须为 {low} 至 {high} 的整数')
    return value


def boolean(body, key):
    value = body.get(key)
    if type(value) is not bool:
        fail(400, 'invalid_field', f'{key} 必须为布尔值')
    return value


class Service:
    """One SQLite connection guarded by a lock; writes use BEGIN IMMEDIATE."""

    def __init__(self, database, clock=None):
        self.clock = clock or (lambda: datetime.now(timezone.utc))
        self.lock = threading.RLock()
        if str(database) != ':memory:':
            Path(database).parent.mkdir(parents=True, exist_ok=True)
        self.db = sqlite3.connect(str(database), check_same_thread=False, timeout=30, isolation_level=None)
        self.db.row_factory = sqlite3.Row
        schema_version = self.db.execute('PRAGMA user_version').fetchone()[0]
        if schema_version not in (0, 1):
            self.db.close()
            raise RuntimeError('不支持此数据库版本；原文件未被修改')
        self.db.execute('PRAGMA foreign_keys = ON')
        self.db.execute('PRAGMA journal_mode = WAL')
        self.db.executescript('''
            CREATE TABLE IF NOT EXISTS users (
                id TEXT PRIMARY KEY, handle TEXT UNIQUE NOT NULL, name TEXT NOT NULL,
                salt BLOB NOT NULL, password BLOB NOT NULL);
            CREATE TABLE IF NOT EXISTS sessions (
                hash TEXT PRIMARY KEY, user_id TEXT NOT NULL REFERENCES users(id), expires TEXT NOT NULL);
            CREATE TABLE IF NOT EXISTS login_limits (
                handle TEXT PRIMARY KEY, attempts INTEGER NOT NULL, until_at TEXT NOT NULL);
            CREATE TABLE IF NOT EXISTS relationships (id TEXT PRIMARY KEY, data TEXT NOT NULL);
            CREATE TABLE IF NOT EXISTS agreements (id TEXT PRIMARY KEY, relationship_id TEXT NOT NULL REFERENCES relationships(id), data TEXT NOT NULL);
            CREATE TABLE IF NOT EXISTS receipts (
                actor TEXT NOT NULL, request_id TEXT NOT NULL, fingerprint TEXT NOT NULL, result TEXT NOT NULL,
                PRIMARY KEY(actor, request_id));
            CREATE TABLE IF NOT EXISTS media (
                id TEXT PRIMARY KEY, owner TEXT NOT NULL REFERENCES users(id), mime TEXT NOT NULL, data BLOB NOT NULL);
            CREATE TABLE IF NOT EXISTS media_links (
                media_id TEXT NOT NULL REFERENCES media(id), agreement_id TEXT NOT NULL REFERENCES agreements(id),
                PRIMARY KEY(media_id, agreement_id));
        ''')
        self.db.execute('PRAGMA user_version = 1')

    def close(self):
        with self.lock:
            self.db.close()

    def handle(self, method, path, body=None, token=None):
        with self.lock:
            try:
                if not isinstance(path, str) or not isinstance(method, str):
                    fail(400, 'invalid_request', '无效请求')
                if body is None:
                    body = {}
                if not isinstance(body, dict):
                    fail(400, 'invalid_request', '请求内容必须是 JSON 对象')
                self.now = self.clock().astimezone(timezone.utc)
                self.at = timestamp(self.now)
                self.db.execute('BEGIN IMMEDIATE')
                result = self._dispatch(method, path, body, token)
                self.db.commit()
                return result if isinstance(result, Response) else Response(200, result)
            except ApiError as error:
                self.db.rollback()
                return Response(error.status, {'error': {'code': error.code, 'message': error.message}})
            except (TypeError, ValueError, OverflowError, RecursionError):
                self.db.rollback()
                return Response(400, {'error': {'code': 'invalid_request', 'message': '请求内容格式不正确'}})
            except Exception:
                self.db.rollback()
                return Response(500, {'error': {'code': 'internal_error', 'message': '服务暂时不可用，请重试'}})

    def _user(self, row):
        return {'id': row['id'], 'handle': row['handle'], 'displayName': row['name']}

    def _auth(self, token):
        if not isinstance(token, str) or not token or len(token) > 256:
            fail(401, 'unauthorized', '请重新登录')
        digest = hashlib.sha256(token.encode()).hexdigest()
        row = self.db.execute('SELECT users.* FROM users JOIN sessions ON users.id=sessions.user_id WHERE sessions.hash=? AND sessions.expires>?', (digest, self.at)).fetchone()
        if not row:
            fail(401, 'unauthorized', '登录已失效，请重新登录')
        return self._user(row)

    def _session(self, user):
        token = secrets.token_urlsafe(32)
        self.db.execute('INSERT INTO sessions VALUES (?,?,?)', (hashlib.sha256(token.encode()).hexdigest(), user['id'], timestamp(self.now + timedelta(days=30))))
        return {'token': token, 'user': user}

    def _account(self, action, body):
        handle = string(body, 'handle', 32).lower()
        password = body.get('password')
        if not re.fullmatch(r'[a-z0-9_]{3,32}', handle) or not isinstance(password, str) or not 1 <= len(password) <= 128:
            fail(400, 'invalid_credentials', '账号或密码格式不符合要求')
        if action == 'register':
            if len(password) < 10:
                fail(400, 'invalid_password', '密码需为 10 至 128 个字符')
            name = string(body, 'displayName', 40)
            if self.db.execute('SELECT 1 FROM users WHERE handle=?', (handle,)).fetchone():
                fail(409, 'handle_taken', '此账号已被注册')
            salt = secrets.token_bytes(16)
            digest = hashlib.pbkdf2_hmac('sha256', password.encode(), salt, 600_000)
            user = {'id': new_id(), 'handle': handle, 'displayName': name}
            self.db.execute('INSERT INTO users VALUES (?,?,?,?,?)', (user['id'], handle, name, salt, digest))
            return self._session(user)
        limit = self.db.execute('SELECT * FROM login_limits WHERE handle=?', (handle,)).fetchone()
        if limit and limit['until_at'] > self.at and limit['attempts'] >= 8:
            fail(429, 'login_limited', '尝试过于频繁，请稍后再试')
        row = self.db.execute('SELECT * FROM users WHERE handle=?', (handle,)).fetchone()
        candidate = hashlib.pbkdf2_hmac('sha256', password.encode(), row['salt'] if row else b'unknown-account!', 600_000)
        if not row or not hmac.compare_digest(candidate, row['password']):
            attempts = limit['attempts'] + 1 if limit and limit['until_at'] > self.at else 1
            self.db.execute('INSERT OR REPLACE INTO login_limits VALUES (?,?,?)', (handle, attempts, timestamp(self.now + timedelta(minutes=15))))
            # Failed attempts must persist even though the response is an error.
            self.db.commit()
            fail(401, 'invalid_credentials', '账号或密码不正确')
        self.db.execute('DELETE FROM login_limits WHERE handle=?', (handle,))
        return self._session(self._user(row))

    def _dispatch(self, method, path, body, token):
        if method == 'GET' and path == '/health':
            return {'status': 'ok', 'apiVersion': 1}
        if method == 'POST' and path in ('/v1/register', '/v1/login'):
            return self._account(path.rsplit('/', 1)[-1], body)
        user = self._auth(token)
        if method == 'POST' and path == '/v1/logout':
            self.db.execute('DELETE FROM sessions WHERE hash=?', (hashlib.sha256(token.encode()).hexdigest(),))
            return {'ok': True}
        if method == 'GET' and path == '/v1/state':
            relationships = [json.loads(row['data']) for row in self.db.execute('SELECT data FROM relationships')]
            relationships = [item for item in relationships if self._member(item, user['id'])]
            ids = {item['id'] for item in relationships}
            agreements = [json.loads(row['data']) for row in self.db.execute('SELECT data FROM agreements')]
            agreements = [self._agreement_view(item) for item in agreements if item['relationshipId'] in ids and (item['taskState'] != 'draft' or item['issuerId'] == user['id'])]
            return {'user': user, 'relationships': [self._relationship_view(item, user['id']) for item in relationships], 'agreements': agreements, 'serverTime': self.at}
        match = re.fullmatch(r'/v1/media/([^/]+)', path)
        if method == 'GET' and match:
            return self._read_media(match[1], user['id'])
        if method == 'POST':
            request_id = string(body, 'requestId', 128)
            fingerprint = hashlib.sha256(encode({'method': method, 'path': path, 'body': body}).encode()).hexdigest()
            receipt = self.db.execute('SELECT * FROM receipts WHERE actor=? AND request_id=?', (user['id'], request_id)).fetchone()
            if receipt:
                if receipt['fingerprint'] != fingerprint:
                    fail(409, 'request_reused', '同一请求编号不可用于不同内容')
                return json.loads(receipt['result'])
            result = self._write(path, body, user)
            self.db.execute('INSERT INTO receipts VALUES (?,?,?,?)', (user['id'], request_id, fingerprint, encode(result)))
            return result
        fail(404, 'not_found', '未找到请求的内容')

    def _member(self, relationship, user_id):
        return any(item['id'] == user_id and item['kind'] == 'account' for item in relationship['members'])

    def _relationship_view(self, relationship, user_id):
        result = dict(relationship)
        if relationship['ownerId'] != user_id or relationship['status'] != 'inviting':
            result['inviteCode'] = None
            result['inviteExpiresAt'] = None
        return result

    def _relationship(self, relationship_id, user_id):
        if not isinstance(relationship_id, str):
            fail(400, 'invalid_relationship', '请选择关系')
        row = self.db.execute('SELECT data FROM relationships WHERE id=?', (relationship_id,)).fetchone()
        relationship = json.loads(row['data']) if row else None
        if not relationship or not self._member(relationship, user_id):
            fail(404, 'not_found', '未找到关系')
        return relationship

    def _save_relationship(self, relationship):
        self.db.execute('INSERT INTO relationships VALUES (?,?) ON CONFLICT(id) DO UPDATE SET data=excluded.data', (relationship['id'], encode(relationship)))

    def _version(self, item, body):
        if type(body.get('expectedVersion')) is not int:
            fail(400, 'version_required', '操作需要当前版本')
        if item['version'] != body['expectedVersion']:
            fail(409, 'version_conflict', '内容已更新，请刷新后重试')

    def _write(self, path, body, user):
        if path == '/v1/media':
            return self._upload_media(body, user['id'])
        if path == '/v1/agreements':
            return self._create_agreement(body, user)
        match = re.fullmatch(r'/v1/agreements/([^/]+)/actions', path)
        if match:
            return self._action(match[1], body, user)
        if path == '/v1/relationships':
            name = string(body, 'name', 80)
            kind, mode = body.get('type'), body.get('mode')
            if kind not in ('parent_child', 'couple', 'custom') or mode not in ('shared', 'proxy'):
                fail(400, 'invalid_relationship', '关系类型或模式无效')
            if mode == 'proxy' and kind != 'parent_child':
                fail(400, 'invalid_relationship', '仅亲子关系支持家长代记')
            members = [{'id': user['id'], 'name': user['displayName'], 'kind': 'account'}]
            if mode == 'proxy':
                members.append({'id': new_id(), 'name': string(body, 'childName', 40), 'kind': 'child'})
            relationship = {'id': new_id(), 'name': name, 'type': kind, 'mode': mode,
                'status': 'active' if mode == 'proxy' else 'inviting', 'version': 1, 'ownerId': user['id'], 'members': members,
                'inviteCode': secrets.token_urlsafe(18) if mode == 'shared' else None,
                'inviteExpiresAt': timestamp(self.now + timedelta(hours=24)) if mode == 'shared' else None, 'createdAt': self.at}
            self._save_relationship(relationship)
            return self._relationship_view(relationship, user['id'])
        if path == '/v1/relationships/join':
            code = string(body, 'inviteCode', 100)
            matches = [json.loads(row['data']) for row in self.db.execute('SELECT data FROM relationships')]
            relationship = next((item for item in matches if item['inviteCode'] == code and item['status'] == 'inviting'), None)
            if not relationship:
                fail(404, 'invite_not_found', '邀请不存在或已使用')
            if relationship['ownerId'] == user['id']:
                fail(409, 'self_invite', '不能加入自己创建的邀请')
            if parse_time(relationship['inviteExpiresAt']) <= self.now:
                fail(409, 'invite_expired', '邀请已过期，请对方重新生成')
            relationship['members'].append({'id': user['id'], 'name': user['displayName'], 'kind': 'account'})
            relationship.update(status='active', inviteCode=None, inviteExpiresAt=None, version=relationship['version'] + 1)
            self._save_relationship(relationship)
            return self._relationship_view(relationship, user['id'])
        match = re.fullmatch(r'/v1/relationships/([^/]+)/actions', path)
        if match:
            relationship = self._relationship(match[1], user['id'])
            self._version(relationship, body)
            action = body.get('action')
            if relationship['status'] == 'ended':
                fail(409, 'relationship_ended', '关系已解除')
            if action == 'end':
                relationship.update(status='ended', inviteCode=None, inviteExpiresAt=None)
            elif action == 'rename':
                name = string(body, 'name', 40)
                next(item for item in relationship['members'] if item['id'] == user['id'])['name'] = name
            elif action == 'renew_invite':
                if relationship['ownerId'] != user['id'] or relationship['status'] != 'inviting':
                    fail(409, 'invalid_state', '当前关系不能重新邀请')
                relationship.update(inviteCode=secrets.token_urlsafe(18), inviteExpiresAt=timestamp(self.now + timedelta(hours=24)))
            else:
                fail(400, 'invalid_action', '未知操作')
            relationship['version'] += 1
            self._save_relationship(relationship)
            return self._relationship_view(relationship, user['id'])
        fail(404, 'not_found', '未找到请求的内容')

    def _terms(self, body, partial=False, achieved=False):
        if not isinstance(body, dict) or (partial and not body):
            fail(400, 'invalid_changes', '请填写需要修改的内容')
        allowed = {'reward', 'rewardDueAt'} if achieved else {'title', 'conditions', 'reward', 'dueAt', 'targetCount', 'redeemWithinDays'}
        if partial and not set(body).issubset(allowed):
            fail(400, 'invalid_changes', '包含不能修改的字段')
        result = {}
        for key in allowed:
            if partial and key not in body:
                continue
            if key in ('title', 'conditions', 'reward'):
                result[key] = string(body, key, 100 if key == 'title' else 2000)
            elif key in ('dueAt', 'rewardDueAt'):
                if body.get(key) is None:
                    if key == 'rewardDueAt':
                        fail(400, 'invalid_time', '奖励兑现日期不能留空')
                    result[key] = None
                else:
                    result[key] = timestamp(parse_time(body[key]))
            elif key == 'targetCount':
                result[key] = integer(body, key, 1, 10000)
            elif key == 'redeemWithinDays':
                result[key] = integer(body, key, 1, 365)
        return result

    def _future_due(self, item):
        if item['dueAt'] is not None and parse_time(item['dueAt']) <= self.now:
            fail(409, 'deadline_passed', '完成期限已到，请先协商延期')

    def _agreement_view(self, item):
        result = dict(item)
        if result['taskState'] == 'active' and result['dueAt'] is not None and parse_time(result['dueAt']) < self.now:
            result['taskState'] = 'expired'
        return result

    def _agreement(self, agreement_id, user_id):
        row = self.db.execute('SELECT data FROM agreements WHERE id=?', (agreement_id,)).fetchone()
        if not row:
            fail(404, 'not_found', '未找到约定')
        item = json.loads(row['data'])
        relationship = self._relationship(item['relationshipId'], user_id)
        if item['taskState'] == 'draft' and item['issuerId'] != user_id:
            fail(404, 'not_found', '未找到约定')
        return self._agreement_view(item), relationship

    def _save_agreement(self, item):
        self.db.execute('INSERT INTO agreements VALUES (?,?,?) ON CONFLICT(id) DO UPDATE SET data=excluded.data', (item['id'], item['relationshipId'], encode(item)))

    def _event(self, item, action, user_id, proxy=False, note='', details=None):
        item['events'].append({'id': new_id(), 'action': action, 'actorId': user_id, 'proxy': proxy, 'at': self.at, 'note': note, 'details': details or {}})

    def _create_agreement(self, body, user):
        relationship = self._relationship(body.get('relationshipId'), user['id'])
        if relationship['status'] != 'active':
            fail(409, 'relationship_inactive', '关系尚未生效或已解除')
        executor = body.get('executorId')
        if executor == user['id'] or not any(member['id'] == executor for member in relationship['members']):
            fail(400, 'invalid_executor', '请选择此关系中的另一名成员')
        terms = self._terms(body)
        publish = boolean(body, 'publish')
        if publish:
            self._future_due(terms)
        item = {'id': new_id(), 'relationshipId': relationship['id'], 'issuerId': user['id'], 'executorId': executor,
            **terms, 'taskState': 'proposed' if publish else 'draft', 'rewardState': 'unearned', 'version': 1,
            'progressCount': 0, 'progress': [], 'submission': None, 'achievedAt': None, 'rewardDueAt': None,
            'proposal': None, 'events': [], 'confirmationMode': 'proxy' if relationship['mode'] == 'proxy' else 'independent',
            'createdAt': self.at, 'updatedAt': self.at}
        self._event(item, 'create', user['id'], details={'terms': terms, 'published': publish})
        self._save_agreement(item)
        return item

    def _role(self, item, relationship, user_id, role):
        if role == 'issuer' and user_id == item['issuerId']:
            return False
        if role == 'executor':
            if user_id == item['executorId']:
                return False
            if relationship['mode'] == 'proxy' and user_id == relationship['ownerId']:
                return True
        fail(403, 'wrong_role', '当前身份不能执行此操作')

    def _require_state(self, item, *states):
        if item['taskState'] not in states:
            fail(409, 'invalid_state', '约定当前状态不支持此操作')

    def _media_ids(self, body, item, user_id):
        ids = body.get('mediaIds', [])
        if not isinstance(ids, list) or len(ids) > 4 or any(not isinstance(media_id, str) for media_id in ids) or len(set(ids)) != len(ids):
            fail(400, 'invalid_media', '一次最多附加 4 张不同图片')
        for media_id in ids:
            row = self.db.execute('SELECT owner FROM media WHERE id=?', (media_id,)).fetchone()
            linked = self.db.execute('SELECT 1 FROM media_links WHERE media_id=? AND agreement_id=?', (media_id, item['id'])).fetchone()
            if not row or (row['owner'] != user_id and not linked):
                fail(404, 'media_not_found', '图片不存在或无权使用')
            self.db.execute('INSERT OR IGNORE INTO media_links VALUES (?,?)', (media_id, item['id']))
        return ids

    def _action(self, agreement_id, body, user):
        item, relationship = self._agreement(agreement_id, user['id'])
        self._version(item, body)
        action = body.get('action')
        if relationship['status'] == 'ended':
            if item['taskState'] != 'achieved' or action not in ('give', 'receive', 'dispute', 'propose_amend', 'resolve_proposal'):
                fail(409, 'relationship_ended', '关系已解除，仅可处理既有奖励')
        proxy, details = False, {}
        note = string(body, 'note', 2000, required=False)
        if action in ('edit', 'publish', 'withdraw', 'review', 'give'):
            proxy = self._role(item, relationship, user['id'], 'issuer')
        elif action in ('accept', 'decline', 'progress', 'submit', 'receive', 'dispute'):
            proxy = self._role(item, relationship, user['id'], 'executor')
        if action == 'edit':
            self._require_state(item, 'draft', 'proposed')
            changes = self._terms(body.get('changes'), partial=True)
            details = {'before': {key: item[key] for key in changes}, 'after': changes}
            item.update(changes)
            if item['taskState'] == 'proposed':
                self._future_due(item)
        elif action == 'publish':
            self._require_state(item, 'draft')
            self._future_due(item)
            item['taskState'] = 'proposed'
        elif action == 'accept':
            self._require_state(item, 'proposed')
            self._future_due(item)
            item['taskState'] = 'active'
        elif action == 'decline':
            self._require_state(item, 'proposed')
            item['taskState'] = 'declined'
        elif action == 'withdraw':
            self._require_state(item, 'draft', 'proposed')
            item['taskState'] = 'cancelled'
        elif action == 'progress':
            self._require_state(item, 'active')
            amount = integer(body, 'amount', 1, 10000)
            if item['progressCount'] + amount > item['targetCount']:
                fail(409, 'progress_exceeded', '累计进度不能超过目标次数')
            progress = {'id': new_id(), 'amount': amount, 'note': note, 'mediaIds': self._media_ids(body, item, user['id']), 'actorId': user['id'], 'proxy': proxy, 'at': self.at}
            item['progress'].append(progress)
            item['progressCount'] += amount
            details = {'progress': progress}
        elif action == 'submit':
            self._require_state(item, 'active')
            media_ids = self._media_ids(body, item, user['id'])
            if not note and not media_ids:
                fail(400, 'evidence_required', '请填写完成说明或附上图片')
            if item['targetCount'] > 1 and item['progressCount'] < item['targetCount']:
                fail(409, 'progress_incomplete', '累计次数尚未达标')
            item['submission'] = {'note': note, 'mediaIds': media_ids, 'actorId': user['id'], 'proxy': proxy, 'at': self.at}
            item['taskState'] = 'submitted'
            details = {'submission': item['submission']}
        elif action == 'review':
            self._require_state(item, 'submitted')
            approved = boolean(body, 'approved')
            if approved:
                if item['targetCount'] > 1 and item['progressCount'] < item['targetCount']:
                    fail(409, 'progress_incomplete', '累计次数尚未达标，请先补充进度并重新提交')
                item.update(taskState='achieved', rewardState='pending', achievedAt=self.at, rewardDueAt=timestamp(self.now + timedelta(days=item['redeemWithinDays'])))
            else:
                if not note:
                    fail(400, 'note_required', '退回需说明需要补充的内容')
                item['taskState'] = 'active'
                item = self._agreement_view(item)
            details = {'approved': approved}
        elif action in ('give', 'receive', 'dispute'):
            self._require_state(item, 'achieved')
            expected = 'pending' if action == 'give' else 'offered'
            if item['rewardState'] != expected:
                fail(409, 'invalid_reward_state', '奖励状态已变化，请刷新')
            if action == 'dispute' and not note:
                fail(400, 'note_required', '请说明尚未收到的情况')
            item['rewardState'] = {'give': 'offered', 'receive': 'received', 'dispute': 'pending'}[action]
        elif action == 'comment':
            if item['taskState'] == 'draft' or not note:
                fail(400, 'note_required', '请填写约定说明')
        elif action in ('propose_amend', 'propose_cancel'):
            self._require_state(item, 'active', 'submitted', 'expired', 'achieved')
            if item['proposal'] is not None:
                fail(409, 'proposal_pending', '请先处理当前协商')
            if item['rewardState'] == 'received':
                fail(409, 'already_settled', '奖励已收讫，不能重新修改')
            kind = 'amend' if action == 'propose_amend' else 'cancel'
            if kind == 'cancel':
                if item['taskState'] == 'achieved':
                    fail(409, 'already_achieved', '已达成约定不能取消奖励')
                if not note:
                    fail(400, 'note_required', '请说明取消原因')
                changes = {}
            else:
                changes = self._terms(body.get('changes'), partial=True, achieved=item['taskState'] == 'achieved')
                self._validate_changes(item, changes)
            proxy = relationship['mode'] == 'proxy'
            item['proposal'] = {'id': new_id(), 'kind': kind, 'by': user['id'], 'changes': changes, 'note': note, 'at': self.at}
            details = {'proposal': dict(item['proposal'])}
        elif action == 'resolve_proposal':
            proposal = item['proposal']
            if proposal is None:
                fail(409, 'no_proposal', '没有待确认的协商')
            proxy = relationship['mode'] == 'proxy'
            if proposal['by'] == user['id'] and not proxy:
                fail(403, 'self_confirmation', '需要另一方确认协商')
            approved = boolean(body, 'approved')
            details = {'proposal': proposal, 'approved': approved}
            if approved:
                if item['rewardState'] == 'received':
                    fail(409, 'already_settled', '奖励已收讫，不能修改')
                if proposal['kind'] == 'cancel':
                    self._require_state(item, 'active', 'submitted', 'expired')
                    item['taskState'] = 'cancelled'
                else:
                    self._require_state(item, 'active', 'submitted', 'expired', 'achieved')
                    if item['taskState'] == 'achieved' and not set(proposal['changes']).issubset({'reward', 'rewardDueAt'}):
                        fail(409, 'proposal_obsolete', '约定已达成，原条款修改提案不能再生效')
                    changes = self._terms(proposal['changes'], partial=True, achieved=item['taskState'] == 'achieved')
                    self._validate_changes(item, changes)
                    details.update(before={key: item[key] for key in changes}, after=changes)
                    material_change = item['taskState'] == 'submitted' and any(key in changes and item[key] != changes[key] for key in ('conditions', 'targetCount'))
                    item.update(changes)
                    if material_change:
                        details['previousSubmission'] = item['submission']
                        item.update(taskState='active', submission=None)
                        item = self._agreement_view(item)
                    if item['taskState'] == 'expired' and (item['dueAt'] is None or parse_time(item['dueAt']) > self.now):
                        item['taskState'] = 'active'
            item['proposal'] = None
        else:
            fail(400, 'invalid_action', '未知操作')
        item['version'] += 1
        item['updatedAt'] = self.at
        self._event(item, action, user['id'], proxy, note, details)
        self._save_agreement(item)
        return item

    def _validate_changes(self, item, changes):
        if 'targetCount' in changes and changes['targetCount'] < item['progressCount']:
            fail(409, 'progress_exceeded', '目标次数不能低于已记录进度')
        if 'dueAt' in changes and changes['dueAt'] is not None and parse_time(changes['dueAt']) <= self.now:
            fail(409, 'deadline_passed', '协商的新完成期限必须在未来')

    def _upload_media(self, body, user_id):
        mime, data = body.get('mime'), body.get('data')
        if mime not in ('image/jpeg', 'image/png') or not isinstance(data, str):
            fail(400, 'invalid_media', '仅支持 JPEG 或 PNG 图片')
        if len(data) > 4 * ((4 * 1024 * 1024 + 2) // 3):
            fail(413, 'media_too_large', '每张图片不能超过 4 MiB')
        try:
            raw = base64.b64decode(data, validate=True)
        except (ValueError, binascii.Error):
            fail(400, 'invalid_media', '图片内容格式不正确')
        if len(raw) > 4 * 1024 * 1024:
            fail(413, 'media_too_large', '每张图片不能超过 4 MiB')
        valid = raw.startswith(b'\x89PNG\r\n\x1a\n') if mime == 'image/png' else raw.startswith(b'\xff\xd8\xff')
        if not valid:
            fail(400, 'invalid_media', '图片签名与文件类型不匹配')
        media_id = new_id()
        self.db.execute('INSERT INTO media VALUES (?,?,?,?)', (media_id, user_id, mime, raw))
        return {'id': media_id, 'mime': mime, 'size': len(raw)}

    def _read_media(self, media_id, user_id):
        row = self.db.execute('SELECT * FROM media WHERE id=?', (media_id,)).fetchone()
        if not row:
            fail(404, 'not_found', '未找到图片')
        allowed = row['owner'] == user_id
        if not allowed:
            links = self.db.execute('SELECT agreements.data FROM agreements JOIN media_links ON agreements.id=media_links.agreement_id WHERE media_links.media_id=?', (media_id,)).fetchall()
            for link in links:
                item = json.loads(link['data'])
                relationship_row = self.db.execute('SELECT data FROM relationships WHERE id=?', (item['relationshipId'],)).fetchone()
                if relationship_row and self._member(json.loads(relationship_row['data']), user_id) and (item['taskState'] != 'draft' or item['issuerId'] == user_id):
                    allowed = True
                    break
        if not allowed:
            fail(404, 'not_found', '未找到图片')
        return Response(200, row['data'], {'Content-Type': row['mime'], 'Cache-Control': 'no-store'})
