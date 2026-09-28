import base64
import sqlite3
import tempfile
import unittest
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta, timezone
from pathlib import Path

from server import Service


class ServiceTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.now = datetime(2026, 9, 26, 8, tzinfo=timezone.utc)
        self.path = Path(self.temp.name) / 'test.sqlite3'
        self.service = Service(self.path, clock=lambda: self.now)
        self.counter = 0

    def tearDown(self):
        self.service.close()
        self.temp.cleanup()

    def call(self, method, path, body=None, token=None, status=200):
        result = self.service.handle(method, path, body, token)
        self.assertEqual(status, result.status, result.body)
        return result.body

    def register(self, handle='alice'):
        return self.call('POST', '/v1/register', {'handle': handle, 'password': 'correct horse battery', 'displayName': handle})

    def write(self, path, token, **body):
        self.counter += 1
        return self.call('POST', path, {'requestId': f'request-{self.counter}', **body}, token)

    def test_account_persists_and_logout_revokes_only_current_session(self):
        account = self.register()
        self.assertEqual('alice', account['user']['handle'])
        self.call('POST', '/v1/login', {'handle': 'alice', 'password': 'incorrect'}, status=401)
        login = self.call('POST', '/v1/login', {'handle': 'ALICE', 'password': 'correct horse battery'})
        self.service.close()
        self.service = Service(self.path, clock=lambda: self.now)
        self.assertEqual(account['user'], self.call('GET', '/v1/state', token=login['token'])['user'])
        self.call('POST', '/v1/logout', {}, account['token'])
        self.call('GET', '/v1/state', token=account['token'], status=401)
        self.call('GET', '/v1/state', token=login['token'])
        self.now += timedelta(days=31)
        self.call('GET', '/v1/state', token=login['token'], status=401)

    def test_account_validation_password_whitespace_and_duplicate(self):
        self.call('POST', '/v1/register', {'handle': 'ab', 'password': '1234567890', 'displayName': 'A'}, status=400)
        self.call('POST', '/v1/register', {'handle': 'space', 'password': ' 1234567890 ', 'displayName': 'A'})
        self.call('POST', '/v1/login', {'handle': 'space', 'password': '1234567890'}, status=401)
        self.call('POST', '/v1/login', {'handle': 'space', 'password': ' 1234567890 '})
        self.register()
        self.call('POST', '/v1/register', {'handle': 'alice', 'password': '1234567890', 'displayName': 'A'}, status=409)

    def pair(self):
        alice, bob = self.register(), self.register('bob')
        relationship = self.write('/v1/relationships', alice['token'], name='我们', type='couple', mode='shared')
        relationship = self.write('/v1/relationships/join', bob['token'], inviteCode=relationship['inviteCode'])
        return alice, bob, relationship

    def test_invitation_is_private_single_use_and_third_party_cannot_read(self):
        alice, bob, relation = self.pair()
        third = self.register('third')
        self.assertEqual('active', relation['status'])
        self.assertIsNone(relation['inviteCode'])
        self.assertEqual([], self.call('GET', '/v1/state', token=third['token'])['relationships'])
        self.assertEqual(2, len(self.call('GET', '/v1/state', token=alice['token'])['relationships'][0]['members']))
        self.call('POST', '/v1/relationships/' + relation['id'] + '/actions', {'requestId': 'intruder', 'expectedVersion': relation['version'], 'action': 'end'}, third['token'], status=404)
        invite = self.write('/v1/relationships', alice['token'], name='新关系', type='custom', mode='shared')
        self.call('POST', '/v1/relationships/join', {'requestId': 'self', 'inviteCode': invite['inviteCode']}, alice['token'], status=409)
        renewed = self.write('/v1/relationships/' + invite['id'] + '/actions', alice['token'], expectedVersion=invite['version'], action='renew_invite')
        self.call('POST', '/v1/relationships/join', {'requestId': 'old', 'inviteCode': invite['inviteCode']}, third['token'], status=404)
        self.now += timedelta(hours=25)
        self.call('POST', '/v1/relationships/join', {'requestId': 'expired', 'inviteCode': renewed['inviteCode']}, third['token'], status=409)

    def test_relationship_idempotency_and_proxy_child_identity(self):
        alice = self.register()
        body = {'requestId': 'once', 'name': '亲子', 'type': 'parent_child', 'mode': 'proxy', 'childName': '小芽'}
        first = self.call('POST', '/v1/relationships', body, alice['token'])
        self.assertEqual(first, self.call('POST', '/v1/relationships', body, alice['token']))
        self.assertEqual('active', first['status'])
        self.assertEqual('child', first['members'][1]['kind'])
        self.assertNotEqual(alice['user']['id'], first['members'][1]['id'])
        self.call('POST', '/v1/relationships', {**body, 'name': '修改内容'}, alice['token'], status=409)
        changed = self.write('/v1/relationships/' + first['id'] + '/actions', alice['token'], expectedVersion=first['version'], action='rename', name='爸爸')
        self.assertEqual('爸爸', changed['members'][0]['name'])
        self.assertEqual('小芽', changed['members'][1]['name'])

    def test_consumed_invitation_replays_original_join_but_rejects_new_joins(self):
        alice, bob, third = self.register(), self.register('bob'), self.register('third')
        invitation = self.write('/v1/relationships', alice['token'], name='我们', type='couple', mode='shared')
        request = {'requestId': 'join-once', 'inviteCode': invitation['inviteCode']}
        joined = self.call('POST', '/v1/relationships/join', request, bob['token'])
        self.assertEqual(joined, self.call('POST', '/v1/relationships/join', request, bob['token']))
        self.call('POST', '/v1/relationships/join', {**request, 'requestId': 'join-again'}, bob['token'], status=404)
        self.call('POST', '/v1/relationships/join', {**request, 'requestId': 'third-member'}, third['token'], status=404)
        self.assertEqual([joined], self.call('GET', '/v1/state', token=alice['token'])['relationships'])
        self.assertEqual([], self.call('GET', '/v1/state', token=third['token'])['relationships'])

    def agreement(self, alice, bob, relation, **overrides):
        return self.write('/v1/agreements', alice['token'], **{
            'relationshipId': relation['id'], 'executorId': bob['user']['id'],
            'title': '一起运动', 'conditions': '一起完成两次散步', 'reward': '一顿晚餐',
            'dueAt': '2026-10-01T08:00:00Z', 'targetCount': 2,
            'redeemWithinDays': 7, 'publish': True, **overrides})

    def action(self, item, actor, action, **fields):
        return self.write('/v1/agreements/' + item['id'] + '/actions', actor['token'], expectedVersion=item['version'], action=action, **fields)

    def bad_action(self, item, actor, action, status=409, **fields):
        self.counter += 1
        return self.call('POST', '/v1/agreements/' + item['id'] + '/actions', {'requestId': f'bad-{self.counter}', 'expectedVersion': item['version'], 'action': action, **fields}, actor['token'], status=status)

    def test_two_people_complete_cumulative_agreement_and_separately_confirm_reward(self):
        alice, bob, relation = self.pair()
        item = self.agreement(alice, bob, relation)
        self.bad_action(item, alice, 'accept', status=403)
        item = self.action(item, bob, 'accept')
        self.bad_action(item, bob, 'submit', note='已完成')
        item = self.action(item, bob, 'progress', amount=1, note='第一次')
        item = self.action(item, bob, 'progress', amount=1, note='第二次')
        self.bad_action(item, bob, 'progress', amount=1)
        item = self.action(item, bob, 'submit', note='两次完成')
        self.bad_action(item, bob, 'review', status=403, approved=True)
        item = self.action(item, alice, 'review', approved=False, note='补充时间')
        self.assertEqual('active', item['taskState'])
        item = self.action(item, bob, 'submit', note='周六、周日完成')
        item = self.action(item, alice, 'review', approved=True)
        self.assertEqual(('achieved', 'pending'), (item['taskState'], item['rewardState']))
        self.assertEqual('2026-10-03T08:00:00Z', item['rewardDueAt'])
        item = self.action(item, alice, 'give')
        self.assertEqual('offered', item['rewardState'])
        self.bad_action(item, alice, 'receive', status=403)
        item = self.action(item, bob, 'dispute', note='还没收到')
        self.assertEqual('pending', item['rewardState'])
        item = self.action(item, alice, 'give', note='今天一起吃饭')
        request = {'requestId': 'receive-once', 'expectedVersion': item['version'], 'action': 'receive'}
        item = self.call('POST', '/v1/agreements/' + item['id'] + '/actions', request, bob['token'])
        self.assertEqual(item, self.call('POST', '/v1/agreements/' + item['id'] + '/actions', request, bob['token']))
        self.assertEqual('received', item['rewardState'])
        self.assertEqual(1, sum(event['action'] == 'receive' for event in item['events']))
        self.bad_action(item, alice, 'give')
        self.assertFalse(any(event['proxy'] for event in item['events']))

    def test_draft_privacy_edit_history_decline_and_roles_can_reverse(self):
        alice, bob, relation = self.pair()
        draft = self.agreement(alice, bob, relation, publish=False)
        self.assertEqual([], self.call('GET', '/v1/state', token=bob['token'])['agreements'])
        self.bad_action(draft, bob, 'comment', status=404, note='猜测草稿 ID')
        draft = self.action(draft, alice, 'edit', changes={'title': '更新标题'})
        self.assertEqual('一起运动', draft['events'][-1]['details']['before']['title'])
        draft = self.action(draft, alice, 'publish')
        self.assertEqual(1, len(self.call('GET', '/v1/state', token=bob['token'])['agreements']))
        draft = self.action(draft, bob, 'decline')
        self.assertEqual('declined', draft['taskState'])
        reverse = self.agreement(bob, alice, relation, targetCount=1)
        reverse = self.action(reverse, alice, 'accept')
        reverse = self.action(reverse, alice, 'submit', note='完成')
        reverse = self.action(reverse, bob, 'review', approved=True)
        self.assertEqual('pending', reverse['rewardState'])

    def test_proxy_actions_retain_actual_actor_and_proxy_provenance(self):
        alice = self.register()
        relation = self.write('/v1/relationships', alice['token'], name='亲子', type='parent_child', mode='proxy', childName='孩子')
        child = {'user': {'id': relation['members'][1]['id']}}
        item = self.agreement(alice, child, relation, targetCount=1)
        item = self.action(item, alice, 'accept')
        item = self.action(item, alice, 'submit', note='家长代记')
        item = self.action(item, alice, 'review', approved=True)
        item = self.action(item, alice, 'give')
        item = self.action(item, alice, 'receive')
        self.assertEqual('proxy', item['confirmationMode'])
        self.assertTrue(item['events'][-1]['proxy'])
        self.assertEqual(alice['user']['id'], item['events'][-1]['actorId'])

    def test_expiry_requires_mutual_extension_and_submitted_work_stays_submitted(self):
        alice, bob, relation = self.pair()
        item = self.action(self.agreement(alice, bob, relation, targetCount=1), bob, 'accept')
        self.now += timedelta(days=6)
        state = self.call('GET', '/v1/state', token=bob['token'])
        self.assertEqual('expired', state['agreements'][0]['taskState'])
        self.bad_action(item, bob, 'submit', note='迟到')
        item = state['agreements'][0]
        item = self.action(item, alice, 'propose_amend', changes={'dueAt': '2026-10-05T08:00:00Z'}, note='延期')
        self.assertEqual('2026-10-01T08:00:00Z', item['dueAt'])
        self.bad_action(item, alice, 'resolve_proposal', status=403, approved=True)
        item = self.action(item, bob, 'resolve_proposal', approved=True)
        self.assertEqual('active', item['taskState'])
        item = self.action(item, bob, 'submit', note='现在完成')
        self.now += timedelta(days=7)
        self.assertEqual('submitted', self.call('GET', '/v1/state', token=bob['token'])['agreements'][0]['taskState'])
        item = self.action(item, alice, 'review', approved=True)
        self.assertEqual('achieved', item['taskState'])

    def test_pending_cancel_cannot_remove_earned_reward_and_ended_relation_can_settle(self):
        alice, bob, relation = self.pair()
        item = self.action(self.agreement(alice, bob, relation, targetCount=1), bob, 'accept')
        item = self.action(item, bob, 'propose_cancel', note='可能来不及')
        item = self.action(item, bob, 'submit', note='还是完成了')
        item = self.action(item, alice, 'review', approved=True)
        self.bad_action(item, alice, 'resolve_proposal', approved=True)
        item = self.action(item, alice, 'resolve_proposal', approved=False)
        relation = self.write('/v1/relationships/' + relation['id'] + '/actions', alice['token'], expectedVersion=relation['version'], action='end')
        self.bad_action(item, bob, 'comment', note='停止普通互动')
        item = self.action(item, alice, 'propose_amend', changes={'reward': '新的晚餐', 'rewardDueAt': '2026-10-09T08:00:00Z'})
        self.assertEqual('一顿晚餐', item['reward'])
        item = self.action(item, bob, 'resolve_proposal', approved=True)
        self.assertEqual('新的晚餐', item['reward'])
        self.assertEqual('achieved', item['taskState'])
        item = self.action(item, alice, 'give')
        item = self.action(item, bob, 'receive')
        self.bad_action(item, bob, 'propose_amend', changes={'reward': '清账后变更'})

    def test_amendment_preserves_snapshots_and_stale_version_rejected(self):
        alice, bob, relation = self.pair()
        item = self.action(self.agreement(alice, bob, relation), bob, 'accept')
        old = item
        item = self.action(item, bob, 'propose_amend', changes={'targetCount': 3, 'reward': '公园野餐'})
        self.bad_action(old, alice, 'comment', note='过期版本')
        item = self.action(item, alice, 'resolve_proposal', approved=True)
        self.assertEqual(3, item['targetCount'])
        self.assertEqual('一顿晚餐', item['events'][-1]['details']['before']['reward'])
        item = self.action(item, alice, 'propose_cancel', note='商议取消')
        item = self.action(item, bob, 'resolve_proposal', approved=True)
        self.assertEqual('cancelled', item['taskState'])

    def test_proxy_can_confirm_proposal_but_receipt_is_explicitly_proxy(self):
        alice = self.register()
        relation = self.write('/v1/relationships', alice['token'], name='亲子', type='parent_child', mode='proxy', childName='孩子')
        item = self.agreement(alice, {'user': {'id': relation['members'][1]['id']}}, relation, targetCount=1)
        item = self.action(item, alice, 'accept')
        item = self.action(item, alice, 'propose_amend', changes={'reward': '一起做蛋糕'})
        item = self.action(item, alice, 'resolve_proposal', approved=True)
        self.assertTrue(item['events'][-1]['proxy'])
        self.assertEqual('一起做蛋糕', item['reward'])

    def test_media_is_private_until_linked_and_invalid_files_are_rejected(self):
        alice, bob, relation = self.pair()
        third = self.register('third')
        image = base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a5RkAAAAASUVORK5CYII=')
        media = self.write('/v1/media', bob['token'], mime='image/png', data=base64.b64encode(image).decode())
        self.call('GET', '/v1/media/' + media['id'], token=alice['token'], status=404)
        self.assertEqual(image, self.call('GET', '/v1/media/' + media['id'], token=bob['token']))
        item = self.action(self.agreement(alice, bob, relation, targetCount=1), bob, 'accept')
        item = self.action(item, bob, 'submit', mediaIds=[media['id']])
        self.assertEqual(image, self.call('GET', '/v1/media/' + media['id'], token=alice['token']))
        self.call('GET', '/v1/media/' + media['id'], token=third['token'], status=404)
        self.call('POST', '/v1/media', {'requestId': 'bad-image', 'mime': 'image/jpeg', 'data': base64.b64encode(image).decode()}, bob['token'], status=400)
        self.call('POST', '/v1/media', {'requestId': 'bad-base64', 'mime': 'image/png', 'data': '???'}, bob['token'], status=400)
        self.call('POST', '/v1/media', {'requestId': 'oversize', 'mime': 'image/png', 'data': base64.b64encode(image + b'0' * (4 * 1024 * 1024)).decode()}, bob['token'], status=413)

    def test_login_rate_limit_expires(self):
        self.register()
        for _ in range(8):
            self.call('POST', '/v1/login', {'handle': 'alice', 'password': 'not the password'}, status=401)
        self.call('POST', '/v1/login', {'handle': 'alice', 'password': 'correct horse battery'}, status=429)
        self.now += timedelta(minutes=16)
        self.call('POST', '/v1/login', {'handle': 'alice', 'password': 'correct horse battery'})

    def test_concurrent_connections_cannot_issue_the_same_reward_twice(self):
        alice, bob, relation = self.pair()
        item = self.action(self.agreement(alice, bob, relation, targetCount=1), bob, 'accept')
        item = self.action(item, bob, 'submit', note='完成')
        item = self.action(item, alice, 'review', approved=True)
        second = Service(self.path, clock=lambda: self.now)
        try:
            path = '/v1/agreements/' + item['id'] + '/actions'
            with ThreadPoolExecutor(max_workers=2) as executor:
                futures = [executor.submit(service.handle, 'POST', path, {'requestId': f'give-{index}', 'expectedVersion': item['version'], 'action': 'give'}, alice['token']) for index, service in enumerate((self.service, second))]
                results = [future.result() for future in futures]
            self.assertEqual([200, 409], sorted(result.status for result in results))
            final = self.call('GET', '/v1/state', token=alice['token'])['agreements'][0]
            self.assertEqual('offered', final['rewardState'])
            self.assertEqual(1, sum(event['action'] == 'give' for event in final['events']))
        finally:
            second.close()

    def test_reward_state_events_and_retry_receipts_survive_service_restart(self):
        alice, bob, relation = self.pair()
        item = self.action(self.agreement(alice, bob, relation, targetCount=1), bob, 'accept')
        item = self.action(item, bob, 'submit', note='已完成')
        item = self.action(item, alice, 'review', approved=True)
        path = '/v1/agreements/' + item['id'] + '/actions'
        request = {'requestId': 'give-before-restart', 'expectedVersion': item['version'], 'action': 'give'}
        offered = self.call('POST', path, request, alice['token'])
        before = self.call('GET', '/v1/state', token=bob['token'])
        self.service.close()
        self.service = Service(self.path, clock=lambda: self.now)
        self.assertEqual(before, self.call('GET', '/v1/state', token=bob['token']))
        self.assertEqual(offered, self.call('POST', path, request, alice['token']))
        self.call('POST', path, {**request, 'note': '改变重试内容'}, alice['token'], status=409)
        received = self.action(offered, bob, 'receive')
        self.service.close()
        self.service = Service(self.path, clock=lambda: self.now)
        self.assertEqual(offered, self.call('POST', path, request, alice['token']))
        persisted = self.call('GET', '/v1/state', token=alice['token'])['agreements'][0]
        self.assertEqual(received, persisted)
        self.assertEqual('received', persisted['rewardState'])
        for action in ('give', 'receive'):
            self.assertEqual(1, sum(event['action'] == action for event in persisted['events']))

    def test_exact_deadline_allows_submission_but_later_time_requires_extension(self):
        alice, bob, relation = self.pair()
        item = self.action(self.agreement(alice, bob, relation, targetCount=1), bob, 'accept')
        self.now = datetime(2026, 10, 1, 8, tzinfo=timezone.utc)
        self.assertEqual('active', self.call('GET', '/v1/state', token=bob['token'])['agreements'][0]['taskState'])
        item = self.action(item, bob, 'submit', note='截止时提交')
        self.assertEqual('submitted', item['taskState'])

    def test_old_task_amendment_cannot_change_achievement_after_review(self):
        alice, bob, relation = self.pair()
        item = self.action(self.agreement(alice, bob, relation, targetCount=1), bob, 'accept')
        item = self.action(item, bob, 'propose_amend', changes={'conditions': '新的条件'})
        item = self.action(item, bob, 'submit', note='按原条件完成')
        item = self.action(item, alice, 'review', approved=True)
        self.bad_action(item, alice, 'resolve_proposal', approved=True)
        state = self.call('GET', '/v1/state', token=alice['token'])['agreements'][0]
        self.assertEqual(('achieved', 'pending'), (state['taskState'], state['rewardState']))
        self.assertEqual('一起完成两次散步', state['conditions'])

    def test_unknown_database_version_is_refused_without_modifying_file(self):
        path = Path(self.temp.name) / 'future.sqlite3'
        connection = sqlite3.connect(path)
        connection.execute('PRAGMA user_version = 99')
        connection.close()
        before = path.read_bytes()
        with self.assertRaisesRegex(RuntimeError, '数据库版本'):
            Service(path)
        self.assertEqual(before, path.read_bytes())

    def test_submitted_material_amendment_requires_new_submission_but_reward_change_does_not(self):
        alice, bob, relation = self.pair()
        item = self.action(self.agreement(alice, bob, relation, targetCount=1), bob, 'accept')
        item = self.action(item, bob, 'submit', note='原条件证据')
        item = self.action(item, alice, 'propose_amend', changes={'reward': '野餐'})
        item = self.action(item, bob, 'resolve_proposal', approved=True)
        self.assertEqual('submitted', item['taskState'])
        item = self.action(item, alice, 'propose_amend', changes={'targetCount': 2, 'conditions': '完成两次'})
        item = self.action(item, bob, 'resolve_proposal', approved=True)
        self.assertEqual('active', item['taskState'])
        self.assertIsNone(item['submission'])
        self.assertEqual('原条件证据', next(event for event in item['events'] if event['action'] == 'submit')['details']['submission']['note'])
        self.bad_action(item, alice, 'review', approved=True)
        self.bad_action(item, bob, 'submit', note='未追加进度')
        item = self.action(item, bob, 'progress', amount=2)
        item = self.action(item, bob, 'submit', note='追加进度后重提')
        item = self.action(item, alice, 'review', approved=True)
        self.assertEqual('achieved', item['taskState'])


if __name__ == '__main__':
    unittest.main()
