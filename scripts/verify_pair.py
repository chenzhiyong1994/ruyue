"""Verify a two-device Pact lifecycle through real UI; never clear device data.

API writes prepare only synthetic accounts and their private relationship. Every
agreement/progress/review/reward write is performed through PactInstrumentation.
Credentials and checkpoints stay under an ignored artifacts/pact directory.
"""

import argparse
import ipaddress
import json
import secrets
import subprocess
import sys
import time
import uuid
import xml.etree.ElementTree as ET
from datetime import datetime, timezone
from pathlib import Path
from urllib.error import HTTPError
from urllib.parse import urlsplit

from smoke_pact import ROOT, PACKAGE, Device, api, await_text, click, fill, select, default_adb


def now():
    return datetime.now(timezone.utc).isoformat(timespec='seconds')


def save(path, value):
    temporary = path.with_suffix(path.suffix + '.tmp')
    temporary.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    temporary.replace(path)


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def local_api(value):
    parsed = urlsplit(value)
    try:
        loopback = parsed.hostname == 'localhost' or ipaddress.ip_address(parsed.hostname or '').is_loopback
    except ValueError:
        loopback = False
    require(parsed.scheme == 'http' and loopback and not parsed.username and not parsed.password
            and parsed.path in ('', '/') and not parsed.query and not parsed.fragment,
            '--api must be an HTTP loopback origin without credentials or a path')
    return value.rstrip('/'), parsed.port or 80


def preferences(device):
    """Read session presence only; login and logout still go through actual UI."""
    try:
        raw = device.command('shell', 'run-as', PACKAGE, 'cat', 'shared_prefs/pact-session.xml')
    except RuntimeError as error:
        if 'No such file' in str(error):
            return {}
        raise
    return {element.attrib['name']: element.text or '' for element in ET.fromstring(raw)
            if element.tag == 'string'}


class PairVerification:
    def __init__(self, args):
        self.args = args
        self.base, self.port = local_api(args.api)
        require(args.serial_a != args.serial_b, 'Two different device serials are required')
        self.output = (ROOT / args.evidence_dir).resolve()
        require(self.output.is_relative_to((ROOT / 'artifacts' / 'pact').resolve()),
                'Evidence must stay under the ignored artifacts/pact directory')
        self.fixture_path = self.output / 'fixture.json'
        ignored = subprocess.run(['git', 'check-ignore', '--quiet', str(self.fixture_path)], cwd=ROOT,
                                 capture_output=True, check=False)
        require(ignored.returncode == 0, 'Refusing to store fixture credentials outside Git ignored output')
        # Device performs the mandatory pact_ AVD-name check before any setup write.
        self.devices = {'a': Device(args.adb, args.serial_a, self.output / 'device-a'),
                        'b': Device(args.adb, args.serial_b, self.output / 'device-b')}
        for device in self.devices.values():
            require('package:' in device.command('shell', 'pm', 'path', PACKAGE), 'Preview APK is not installed')
            require('package:' in device.command('shell', 'pm', 'path', PACKAGE + '.test'), 'Instrumentation APK is not installed')
        if self.fixture_path.exists():
            require(args.resume, 'Fixture already exists; use --resume or choose a new evidence directory')
            self.fixture = json.loads(self.fixture_path.read_text(encoding='utf-8'))
            require(self.fixture.get('formatVersion') == 1, 'Unsupported fixture format')
            require(self.fixture.get('settlementMode') == 'issuer_final',
                    'This fixture predates final issuer settlement; preserve it and use a fresh directory')
            require(self.fixture['api'] == self.base, 'Resume must use the same API origin')
            require(self.fixture['serials'] == {'a': args.serial_a, 'b': args.serial_b},
                    'Resume must use the original two device serials')
        else:
            require(not args.resume, '--resume requires an existing fixture.json')
            suffix = uuid.uuid4().hex[:12]
            self.fixture = {
                'formatVersion': 1, 'settlementMode': 'issuer_final', 'id': suffix, 'createdAt': now(), 'api': self.base,
                'serials': {'a': args.serial_a, 'b': args.serial_b},
                'setup': 'API registers synthetic accounts and creates/joins their private relationship; UI performs login and every agreement mutation.',
                'relationshipName': '双端合成验收 ' + suffix,
                'agreementTitle': '合成散步约定 ' + suffix,
                'accounts': {key: {'handle': f'pair_{suffix}_{key}',
                                   'displayName': '合成给予方' if key == 'a' else '合成执行方',
                                   'password': secrets.token_urlsafe(24)} for key in ('a', 'b')},
                'completed': {}, 'attempts': {}, 'pending': None,
            }
            self.persist()

    def persist(self):
        save(self.fixture_path, self.fixture)

    def prepare(self):
        require(api(self.base, '/health') == {'status': 'ok', 'apiVersion': 1}, 'Unexpected API health/version')
        for key, account in self.fixture['accounts'].items():
            if not account.get('token'):
                body = {field: account[field] for field in ('handle', 'password', 'displayName')}
                try:
                    result = api(self.base, '/v1/register', body)
                except HTTPError as error:
                    if error.code != 409:
                        raise
                    # A crash after registration can leave its unique synthetic handle registered.
                    result = api(self.base, '/v1/login', {field: account[field] for field in ('handle', 'password')})
                account.update(result)
                self.persist()
            current = api(self.base, '/v1/state', token=account['token'])
            require(current['user']['handle'] == account['handle'], 'Fixture account identity does not match')
        issuer, executor = (self.fixture['accounts'][key] for key in ('a', 'b'))
        if not self.fixture.get('relationship'):
            relation = api(self.base, '/v1/relationships', {
                'requestId': str(uuid.uuid5(uuid.NAMESPACE_URL, self.fixture['id'] + ':relationship')),
                'name': self.fixture['relationshipName'], 'type': 'couple', 'mode': 'shared',
            }, issuer['token'])
            self.fixture['relationship'] = relation
            self.persist()
        if self.fixture['relationship']['status'] != 'active':
            relation = api(self.base, '/v1/relationships/join', {
                'requestId': str(uuid.uuid5(uuid.NAMESPACE_URL, self.fixture['id'] + ':join')),
                'inviteCode': self.fixture['relationship']['inviteCode'],
            }, executor['token'])
            self.fixture['relationship'] = relation
            self.persist()
        for device in self.devices.values():
            device.command('reverse', f'tcp:{self.port}', f'tcp:{self.port}')
        save(self.output / 'setup.json', {
            'at': now(), 'setupMethod': self.fixture['setup'],
            'syntheticAccountIds': [issuer['user']['id'], executor['user']['id']],
            'relationshipId': self.fixture['relationship']['id'],
            'deviceApi': f'http://127.0.0.1:{self.port}', 'deviceDataCleared': False,
        })

    def login(self, key):
        stage_name = '01-login-' + key
        device, account = self.devices[key], self.fixture['accounts'][key]
        prefs = preferences(device)
        if stage_name in self.fixture['completed']:
            token = prefs.get('token', '')
            require(token, 'Saved UI login is missing; do not skip an unverified session')
            current = api(self.base, '/v1/state', token=token)
            require(current['user']['id'] == account['user']['id'], 'Device account changed since the completed login stage')
            print('SKIP verified login: ' + key, flush=True)
            return
        if prefs.get('token'):
            device.stage('00-logout-existing-' + key, [await_text('账户设置'), click('账户设置'),
                         click('退出登录'), await_text('账号')])
        device_url = f'http://127.0.0.1:{self.port}'
        device.stage('00-connection-' + key, [await_text('开发连接设置'), click('开发连接设置'),
                     fill('服务地址', device_url), click('保存连接'), await_text('登录')])
        device.stage(stage_name, [fill('账号', account['handle']), fill('密码', account['password']),
                     click('登录'), await_text('账户设置'), click('关系'), await_text(self.fixture['relationshipName'])])
        token = preferences(device).get('token', '')
        require(token, 'UI login did not persist a session')
        current = api(self.base, '/v1/state', token=token)
        require(current['user']['id'] == account['user']['id'], 'UI logged in as an unexpected account')
        self.fixture['completed'][stage_name] = {'at': now(), 'method': 'actual Android UI login',
                                                  'userId': current['user']['id']}
        self.persist()

    def current(self):
        snapshots = {}
        for key in ('a', 'b'):
            account = self.fixture['accounts'][key]
            snapshots[key] = api(self.base, '/v1/state', token=account['token'])
            require(snapshots[key]['user']['id'] == account['user']['id'], 'GET returned the wrong account')
        matches = [item for item in snapshots['a']['agreements']
                   if item['relationshipId'] == self.fixture['relationship']['id']
                   and item['title'] == self.fixture['agreementTitle']]
        require(len(matches) <= 1, 'More than one agreement matches this fixture; refusing ambiguous replay')
        item = matches[0] if matches else None
        other = [entry for entry in snapshots['b']['agreements']
                 if item is not None and entry['id'] == item['id']]
        if item is not None:
            require(len(other) == 1 and other[0] == item, 'Two accounts do not see the same authoritative agreement')
            require(item['issuerId'] == self.fixture['accounts']['a']['user']['id']
                    and item['executorId'] == self.fixture['accounts']['b']['user']['id'], 'Agreement roles changed')
        return item

    @staticmethod
    def signature(item):
        if item is None:
            return None
        return {field: item[field] for field in ('id', 'version', 'taskState', 'rewardState', 'progressCount', 'events')}

    def verify(self, expected):
        item = self.current()
        if expected is None:
            require(item is None, 'Agreement already exists before its UI create stage')
            return None
        require(item is not None, 'UI action has not created the expected agreement')
        task, reward, progress, actions = expected
        require((item['taskState'], item['rewardState'], item['progressCount']) == (task, reward, progress),
                f'Unexpected task/reward/progress: {item["taskState"]}/{item["rewardState"]}/{item["progressCount"]}')
        require([event['action'] for event in item['events']] == actions, 'Event sequence differs from real UI stages')
        require(item['version'] == len(actions), 'One successful UI action must produce exactly one version')
        require(item['targetCount'] == 2 and item['confirmationMode'] == 'independent', 'Fixture terms/mode changed')
        issuer = self.fixture['accounts']['a']['user']['id']
        executor = self.fixture['accounts']['b']['user']['id']
        for event in item['events']:
            actor = issuer if event['action'] in ('create', 'review', 'give') else executor
            require(event['actorId'] == actor and not event['proxy'], 'Wrong actor or unexpected proxy confirmation')
        if 'submit' in actions:
            require(item['submission'] and len(item['submission']['mediaIds']) == 1,
                    'The submitted synthetic photo is missing')
        if 'review' in actions:
            require(item['achievedAt'] and item['rewardDueAt'], 'Achievement or reward deadline is missing')
            require(next(event for event in item['events'] if event['action'] == 'review')['details']['approved'] is True,
                    'Review was not approved by the issuer')
        return item

    def stage(self, name, key, steps, before, after):
        if name in self.fixture['completed']:
            print('SKIP completed UI stage: ' + name, flush=True)
            return
        # If a failed stage committed anything, stop here instead of replaying it.
        # Its screenshot/log and before/after observations remain available.
        before_item = self.verify(before)
        pending = self.fixture.get('pending')
        if pending:
            require(pending['name'] == name and pending['before'] == self.signature(before_item),
                    'A partially completed stage changed server state; inspect its evidence before continuing')
        attempt = self.fixture['attempts'].get(name, 0) + 1
        self.fixture['attempts'][name] = attempt
        evidence_name = name + f'-attempt-{attempt:02d}'
        self.fixture['pending'] = {'name': name, 'device': key, 'at': now(),
                                   'before': self.signature(before_item), 'evidenceName': evidence_name}
        self.persist()
        save(self.output / (evidence_name + '-before.json'), {'at': now(), 'agreement': before_item})
        try:
            self.devices[key].stage(evidence_name, steps)
            # UI awaits normally imply completion; bounded GET retries only observe.
            last_error = None
            for _ in range(8):
                try:
                    item = self.verify(after)
                    last_error = None
                    break
                except RuntimeError as error:
                    last_error = error
                    time.sleep(0.25)
            if last_error:
                raise last_error
            save(self.output / (evidence_name + '-after.json'), {'at': now(), 'agreement': item})
            self.fixture['completed'][name] = {'at': now(), 'device': key, 'method': 'Android UI mutation + GET verification',
                                               'evidenceName': evidence_name, 'after': self.signature(item)}
            self.fixture['pending'] = None
            self.persist()
        except Exception as error:
            observed = None
            try:
                observed = self.current()
            except Exception:
                pass
            save(self.output / (evidence_name + '-failure.json'), {'at': now(), 'error': str(error),
                                                                   'observedAgreement': observed,
                                                                   'resumeRule': 'Retry is allowed only while the authoritative pre-stage state is unchanged.'})
            raise

    def open_detail(self):
        return [await_text('账户设置'), click('约定'), await_text('查看约定：' + self.fixture['agreementTitle']),
                click('查看约定：' + self.fixture['agreementTitle'])]

    def note_steps(self, label, note, result):
        return self.open_detail() + [click(label), fill('说明', note), click('确认操作'), await_text(result)]

    def run(self):
        self.prepare()
        self.login('a')
        self.login('b')
        actions = ['create']
        current = ('proposed', 'unearned', 0, list(actions))
        create_steps = [await_text('账户设置'), click('约定'), click('创建约定'),
                        select('所属关系', self.fixture['relationshipName']),
                        click('下一步：约定奖励'), await_text('请为约定填写 1–100 字的标题。'),
                        fill('约定标题', self.fixture['agreementTitle']),
                        fill('达成条件', '合成验收：记录两次散步并提交一张合成图片'),
                        fill('目标次数', '2'), fill('完成截止日期', ''), click('下一步：约定奖励'),
                        fill('奖励内容', '合成验收：一起安排一次晚餐'), fill('兑现天数', '7'),
                        click('下一步：确认约定'), await_text(self.fixture['agreementTitle']),
                        click('上一步'), await_text('合成验收：一起安排一次晚餐'),
                        click('上一步'), await_text(self.fixture['agreementTitle']),
                        click('下一步：约定奖励'), click('下一步：确认约定'), click('发出约定'),
                        await_text('待接受')]
        self.stage('02-create-agreement', 'a', create_steps, None, current)
        transitions = [
            ('03-accept', 'b', 'accept', 'active', 'unearned', 0,
             self.note_steps('接受约定', '合成执行方接受双方条件', '待完成')),
            ('04-progress-first', 'b', 'progress', 'active', 'unearned', 1,
             self.open_detail() + [click('记录进度'), fill('本次次数', '1'), fill('进度说明', '合成第一次散步'),
                                   click('保存进度'), await_text('进度：1 / 2 次')]),
            ('05-progress-second', 'b', 'progress', 'active', 'unearned', 2,
             self.open_detail() + [click('记录进度'), fill('本次次数', '1'), fill('进度说明', '合成第二次散步'),
                                   click('保存进度'), await_text('进度：2 / 2 次')]),
            ('06-submit-with-photo', 'b', 'submit', 'submitted', 'unearned', 2,
             self.open_detail() + [click('提交完成'), fill('完成说明', '两次合成记录已经完成，附合成图片'),
                                   {'op': 'photo', 'label': '添加照片'}, await_text('已添加 1 张照片'),
                                   click('确认提交'), await_text('待验收')]),
            ('07-review', 'a', 'review', 'achieved', 'pending', 2,
             self.note_steps('验收通过', '合成给予方确认达成', '待兑现')),
            ('08-give-final', 'a', 'give', 'achieved', 'received', 2,
             self.note_steps('标记已兑现', '合成兑现方已实际完成整份奖励', '已兑现')),
        ]
        for name, key, action, task, reward, progress, steps in transitions:
            actions.append(action)
            following = (task, reward, progress, list(actions))
            self.stage(name, key, steps, current, following)
            current = following
        final = self.verify(current)
        for key, device in self.devices.items():
            device.stage('09-final-rewards-' + key,
                         [await_text('账户设置'), click('奖励'), await_text('查看约定：' + self.fixture['agreementTitle']),
                          click('查看约定：' + self.fixture['agreementTitle']), await_text('已兑现'),
                          {'op': 'absent', 'label': '确认已收到'}, {'op': 'absent', 'label': '尚未收到'},
                          {'op': 'absent', 'label': '留下说明'},
                          {'op': 'layout'}])
        report = {'status': 'passed', 'completedAt': now(), 'fixtureId': self.fixture['id'],
                  'setupMethod': self.fixture['setup'], 'businessMutationMethod': 'Android UI only',
                  'verificationMethod': 'Authenticated GET /v1/state from both accounts after every business stage',
                  'agreement': final, 'completedStages': list(self.fixture['completed']),
                  'limitations': ['Synthetic data on two isolated pact_ AVDs; not a production or real-user result.']}
        save(self.output / 'report.json', report)
        print('PASS: complete two-device UI lifecycle; evidence: ' + str(self.output), flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial-a', required=True)
    parser.add_argument('--serial-b', required=True)
    parser.add_argument('--adb', default=default_adb())
    parser.add_argument('--api', default='http://127.0.0.1:8765')
    parser.add_argument('--evidence-dir', default='artifacts/pact/pair')
    parser.add_argument('--resume', action='store_true', help='Reuse fixture and skip verified completed stages; never replay a partial mutation')
    args = parser.parse_args()
    try:
        PairVerification(args).run()
    except Exception as error:
        print('FAIL: ' + str(error), file=sys.stderr, flush=True)
        raise SystemExit(1) from None


if __name__ == '__main__':
    main()
