"""Synthetic Android registration, relationships, negotiation and proxy checks.

Every write goes through Android UI. HTTP is GET-only observation. Each run needs
a fresh ignored evidence directory; failed mutations are never silently replayed.
"""

import argparse
import os
import secrets
import subprocess
import sys
import uuid
from pathlib import Path

from smoke_pact import ROOT, PACKAGE, Device, api, await_text, click, fill, select
from verify_pair import local_api, now, preferences, require, save


class ScenarioVerification:
    def __init__(self, args):
        self.base, self.port = local_api(args.api)
        require(args.serial_a != args.serial_b, 'Two distinct device serials are required')
        self.output = (ROOT / args.evidence_dir).resolve()
        require(self.output.is_relative_to((ROOT / 'artifacts' / 'pact').resolve()),
                'Evidence must stay inside artifacts/pact')
        require(not self.output.exists() or not any(self.output.iterdir()),
                'Use a fresh evidence directory; this script never replays a previous run')
        self.fixture_path = self.output / 'fixture.json'
        ignored = subprocess.run(['git', 'check-ignore', '--quiet', str(self.fixture_path)],
                                 cwd=ROOT, capture_output=True, check=False)
        require(ignored.returncode == 0, 'Fixture credentials must be Git ignored')
        self.devices = {'a': Device(args.adb, args.serial_a, self.output / 'device-a'),
                        'b': Device(args.adb, args.serial_b, self.output / 'device-b')}
        for device in self.devices.values():
            for package in (PACKAGE, PACKAGE + '.test'):
                require('package:' in device.command('shell', 'pm', 'path', package),
                        'Preview and instrumentation APKs must be installed')
            device.command('reverse', f'tcp:{self.port}', f'tcp:{self.port}')
        suffix = uuid.uuid4().hex[:10]
        self.fixture = {
            'formatVersion': 1, 'id': suffix, 'createdAt': now(), 'api': self.base,
            'serials': {'a': args.serial_a, 'b': args.serial_b},
            'sharedName': '合成双方关系 ' + suffix,
            'proxyName': '合成代记关系 ' + suffix,
            'sharedTitle': '合成协商约定 ' + suffix,
            'proxyTitle': '合成代记约定 ' + suffix,
            'accounts': {key: {'handle': f'scenario_{suffix}_{key}',
                               'password': secrets.token_urlsafe(24),
                               'displayName': '合成家长' if key == 'a' else '合成伙伴'}
                         for key in ('a', 'b')},
            'completed': [], 'pending': None,
        }
        self.persist()

    def persist(self):
        save(self.fixture_path, self.fixture)

    def states(self):
        return {key: api(self.base, '/v1/state', token=account['token'])
                for key, account in self.fixture['accounts'].items() if account.get('token')}

    def one(self, key, collection, field, value):
        rows = [row for row in self.states()[key][collection] if row.get(field) == value]
        require(len(rows) == 1, 'Expected exactly one synthetic object')
        return rows[0]

    def agreement(self, proxy=False, task=None, reward='unearned', actions=None):
        title = self.fixture['proxyTitle' if proxy else 'sharedTitle']
        states = self.states()
        rows = [row for row in states['a']['agreements'] if row['title'] == title]
        require(len(rows) == 1, 'Expected exactly one UI-created agreement')
        item = rows[0]
        peer = [row for row in states['b']['agreements'] if row['id'] == item['id']]
        if proxy or item['taskState'] == 'draft':
            require(not peer, 'Private draft/proxy agreement leaked to the other account')
        else:
            require(peer == [item], 'Two accounts disagree on the shared agreement')
        if task:
            require((item['taskState'], item['rewardState']) == (task, reward),
                    'Unexpected task/reward state after UI action')
        require(item['confirmationMode'] == ('proxy' if proxy else 'independent'),
                'Wrong confirmation mode')
        if actions is not None:
            require([event['action'] for event in item['events']] == actions,
                    'UI produced a missing or duplicate business event')
            require(item['version'] == len(actions), 'Unexpected agreement version')
        return item

    def stage(self, name, key, steps, check=None):
        require(self.fixture['pending'] is None, 'A failed stage cannot be replayed')
        self.fixture['pending'] = {'name': name, 'device': key, 'at': now()}
        self.persist()
        save(self.output / (name + '-before.json'), self.states())
        try:
            self.devices[key].stage(name, steps)
            verified = check() if check else None
            save(self.output / (name + '-after.json'),
                 {'at': now(), 'states': self.states(), 'verification': verified})
            self.fixture['completed'].append(name)
            self.fixture['pending'] = None
            self.persist()
        except Exception as error:
            save(self.output / (name + '-failure.json'),
                 {'at': now(), 'error': str(error), 'replayAllowed': False})
            raise

    def register(self, key):
        device, account = self.devices[key], self.fixture['accounts'][key]
        existing = preferences(device)
        if existing.get('token'):
            old = api(self.base, '/v1/state', token=existing['token'])
            require(old['user']['handle'].startswith(('pair_', 'scenario_')),
                    'Existing session is not a recognized synthetic fixture; log out explicitly first')
            self.stage('00-logout-' + key, key,
                       [await_text('账户设置'), click('账户设置'), click('退出登录'), await_text('账号')])
        self.stage('01-connection-' + key, key,
                   [await_text('开发连接设置'), click('开发连接设置'),
                    fill('服务地址', f'http://127.0.0.1:{self.port}'), click('保存连接'), await_text('登录')])

        def verify():
            token = preferences(device).get('token', '')
            require(token, 'UI registration did not create a session')
            state = api(self.base, '/v1/state', token=token)
            require(state['user']['handle'] == account['handle'], 'UI registered the wrong identity')
            require(not state['relationships'] and not state['agreements'], 'Fresh account is not isolated')
            account.update(token=token, user=state['user'])
            self.persist()
            return {'userId': state['user']['id'], 'method': 'Android UI registration'}

        self.stage('02-register-' + key, key,
                   [await_text('账号'), click('创建账号'), await_text('昵称'),
                    fill('账号', account['handle']), fill('密码', account['password']),
                    fill('昵称', account['displayName']), click('创建账号'), await_text('账户设置')], verify)

    def open(self, proxy=False):
        title = self.fixture['proxyTitle' if proxy else 'sharedTitle']
        return [await_text('账户设置'), click('约定'), await_text('查看约定：' + title),
                click('查看约定：' + title)]

    def note(self, label, result, proxy=False):
        return self.open(proxy) + [click(label), fill('说明', '合成验收：' + label),
                                   click('确认操作'), await_text(result)]

    def create_steps(self, proxy=False, draft=False):
        return [await_text('账户设置'), click('约定'), click('创建约定'),
                select('所属关系', self.fixture['proxyName' if proxy else 'sharedName']),
                fill('约定标题', self.fixture['proxyTitle' if proxy else 'sharedTitle']),
                fill('达成条件', '合成条件：整理一本书'), fill('目标次数', '1'), fill('完成截止日期', ''),
                click('下一步：约定奖励'), fill('奖励内容', '合成奖励：一起阅读'), fill('兑现天数', '7'),
                click('下一步：确认约定'), click('存为草稿' if draft else '发出约定'),
                await_text(('草稿' if draft else '待接受') + ' · 达成后获得')]

    def shared(self):
        self.stage('03-create-shared', 'a',
                   [await_text('账户设置'), click('关系'), click('创建关系'),
                    fill('关系名称', self.fixture['sharedName']), select('关系类型', '情侣'),
                    select('关系模式', '双方账号'), click('确认创建'),
                    await_text('情侣 · 双方账号'), await_text('等待对方加入')])
        relation = self.one('a', 'relationships', 'name', self.fixture['sharedName'])
        require(relation['status'] == 'inviting' and relation['inviteCode'], 'UI did not create an invitation')
        require(not self.states()['b']['relationships'], 'Unjoined account sees a private relation')

        def joined():
            left = self.one('a', 'relationships', 'id', relation['id'])
            right = self.one('b', 'relationships', 'id', relation['id'])
            require(left == right and left['status'] == 'active' and len(left['members']) == 2,
                    'UI invitation join did not establish the two-account relationship')
            require(left['inviteCode'] is None, 'Joined invitation is still exposed')
            self.fixture['sharedRelationshipId'] = left['id']
            return left

        self.stage('04-join-shared', 'b',
                   [await_text('账户设置'), click('关系'), click('加入关系'),
                    fill('邀请码', relation['inviteCode']), click('确认加入'),
                    await_text('情侣 · 双方账号'), await_text('已建立')], joined)
        actions = ['create']
        self.stage('05-create-draft', 'a', self.create_steps(draft=True),
                   lambda: self.agreement(task='draft', actions=actions))
        for name, key, action, button, status in (
                ('06-publish', 'a', 'publish', '发布约定', 'proposed'),
                ('07-accept', 'b', 'accept', '接受约定', 'active')):
            actions.append(action)
            label = '待接受' if status == 'proposed' else '进行中'
            self.stage(name, key, self.note(button, label + ' · 达成后获得'),
                       lambda status=status: self.agreement(task=status, actions=actions))

        def pending():
            item = self.agreement(task='active', actions=actions)
            require(item['conditions'] == '合成条件：整理一本书', 'Unapproved terms changed')
            require(item['proposal']['changes'] == {'conditions': '合成新条件：整理两本书'},
                    'UI amendment does not contain the proposed change')
            return item

        actions.append('propose_amend')
        self.stage('08-propose-amendment', 'a', self.open() +
                   [click('提出修改'), fill('达成条件', '合成新条件：整理两本书'),
                    fill('修改说明', '合成双方协商'), click('提交修改提案'), await_text('等待协商确认')], pending)

        def amended():
            item = self.agreement(task='active', actions=actions)
            require(item['conditions'] == '合成新条件：整理两本书' and item['proposal'] is None,
                    'Peer approval did not apply new terms')
            return item

        actions.append('resolve_proposal')
        self.stage('09-approve-amendment', 'b', self.note('同意提案', '合成新条件：整理两本书'), amended)
        actions.append('propose_cancel')
        self.stage('10-propose-cancel', 'a', self.note('协商取消', '等待协商确认'),
                   lambda: self.agreement(task='active', actions=actions))
        actions.append('resolve_proposal')
        self.stage('11-approve-cancel', 'b', self.note('同意提案', '已取消 · 达成后获得'),
                   lambda: self.agreement(task='cancelled', actions=actions))

    def proxy(self):
        def created():
            relation = self.one('a', 'relationships', 'name', self.fixture['proxyName'])
            require(relation['mode'] == 'proxy' and relation['status'] == 'active', 'Wrong proxy relation')
            require(len([member for member in relation['members'] if member['kind'] == 'child']) == 1,
                    'Proxy relationship lacks a distinct child identity')
            require(all(row['id'] != relation['id'] for row in self.states()['b']['relationships']),
                    'Proxy relationship leaked to the peer account')
            self.fixture['proxyRelationshipId'] = relation['id']
            return relation

        self.stage('12-create-proxy', 'a',
                   [await_text('账户设置'), click('关系'), click('创建关系'),
                    fill('关系名称', self.fixture['proxyName']), select('关系类型', '亲子'),
                    select('关系模式', '家长代记'), fill('孩子称呼', '合成孩子'),
                    click('确认创建'), await_text('亲子 · 家长代记'), await_text('已建立')], created)
        actions = ['create']
        self.stage('13-proxy-propose', 'a', self.create_steps(proxy=True),
                   lambda: self.agreement(True, 'proposed', actions=actions))
        transitions = [
            ('14-proxy-accept', 'accept', 'active', 'unearned',
             self.note('接受约定', '进行中 · 达成后获得', True)),
            ('15-proxy-submit', 'submit', 'submitted', 'unearned', self.open(True) +
             [click('提交完成'), fill('完成说明', '合成孩子已整理，家长代记完成情况'),
              click('确认提交'), await_text('待验收 · 达成后获得')]),
            ('16-proxy-review', 'review', 'achieved', 'pending',
             self.note('验收通过', '已达成 · 奖励待兑现', True)),
            ('17-proxy-give', 'give', 'achieved', 'offered',
             self.note('奖励已给出', '已达成 · 已给出，待收讫', True)),
            ('18-proxy-receive', 'receive', 'achieved', 'received',
             self.note('确认已收到', '家长代记已结案', True)),
        ]
        for name, action, task, reward, steps in transitions:
            actions.append(action)
            self.stage(name, 'a', steps,
                       lambda task=task, reward=reward: self.agreement(True, task, reward, actions))
        item = self.agreement(True, 'achieved', 'received', actions)
        actor = self.fixture['accounts']['a']['user']['id']
        require(item['executorId'] != actor, 'Child identity was replaced by the parent')
        for event in item['events']:
            require(event['actorId'] == actor, 'Proxy event lost its actual parent actor')
            require(event['proxy'] == (event['action'] in ('accept', 'submit', 'receive')),
                    'Proxy provenance differs from the action actually performed')

    def filters(self):
        self.stage('19-shared-filter-navigation', 'a',
                   [await_text('账户设置'), click('关系'), click('查看范围'),
                    select('筛选关系', self.fixture['proxyName']), click('应用范围'), click('约定'),
                    await_text(self.fixture['proxyTitle']),
                    {'op': 'absent', 'label': self.fixture['sharedTitle']},
                    click('首页'), click('约定'), await_text(self.fixture['proxyTitle']),
                    {'op': 'absent', 'label': self.fixture['sharedTitle']},
                    click('奖励'), await_text('查看约定：' + self.fixture['proxyTitle']),
                    {'op': 'absent', 'label': '查看约定：' + self.fixture['sharedTitle']},
                    click('关系'), await_text(self.fixture['proxyName']),
                    {'op': 'absent', 'label': self.fixture['sharedName']},
                    click('查看范围'), select('筛选关系', '全部关系'), click('应用范围'), await_text(self.fixture['sharedName'])])

    def run(self):
        require(api(self.base, '/health') == {'status': 'ok', 'apiVersion': 1}, 'Unexpected service')
        self.register('a')
        self.register('b')
        self.shared()
        self.proxy()
        self.filters()
        save(self.output / 'report.json', {
            'status': 'passed', 'completedAt': now(), 'fixtureId': self.fixture['id'],
            'writeMethod': 'Android UI only, including registration and relationship invitation/join',
            'readMethod': 'Authenticated HTTP GET /v1/state',
            'completedStages': self.fixture['completed'], 'synthetic': True,
            'sharedAgreement': self.agreement(task='cancelled'),
            'proxyAgreement': self.agreement(True, 'achieved', 'received'),
            'limitations': ['Two isolated pact_ AVDs and synthetic identities; no real-user or production result.',
                            'Deadline, offline, narrow-screen and large-font checks are separate.'],
        })
        print('PASS: synthetic native scenarios; evidence: ' + str(self.output), flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial-a', required=True)
    parser.add_argument('--serial-b', required=True)
    parser.add_argument('--adb', default=str(Path(os.environ.get('ANDROID_HOME', '')) / 'platform-tools/adb.exe'))
    parser.add_argument('--api', default='http://127.0.0.1:8765')
    parser.add_argument('--evidence-dir', default='artifacts/pact/scenarios')
    args = parser.parse_args()
    try:
        ScenarioVerification(args).run()
    except Exception as error:
        print('FAIL: ' + str(error), file=sys.stderr, flush=True)
        raise SystemExit(1) from None


if __name__ == '__main__':
    main()
