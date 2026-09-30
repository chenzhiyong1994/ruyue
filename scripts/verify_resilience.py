"""Verify narrow UI, failed writes, recovery and restart on completed pair fixtures.

Only Android UI writes business data. Host API observations use GET exclusively.
Device settings and the single temporary reverse mapping are always restored.
"""

import argparse
import json
import re
import socket
import subprocess
import sys
import threading
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from http.client import HTTPConnection
from pathlib import Path
from urllib.parse import urlsplit

from verify_pair import ROOT, PACKAGE, Device, api, await_text, click, fill, select
from smoke_pact import default_adb
from verify_pair import local_api, now, preferences, require, save


def ignored_path(value):
    path = (ROOT / value).resolve()
    require(path.is_relative_to((ROOT / 'artifacts' / 'pact').resolve()),
            'Fixture and evidence must stay under artifacts/pact')
    result = subprocess.run(['git', 'check-ignore', '--quiet', str(path)], cwd=ROOT,
                            capture_output=True, check=False)
    require(result.returncode == 0, 'Fixture and evidence must be Git ignored')
    return path


def display_settings(device):
    size = device.command('shell', 'wm', 'size')
    density = device.command('shell', 'wm', 'density')
    font = device.command('shell', 'settings', 'get', 'system', 'font_scale').strip()
    require(re.fullmatch(r'null|\d+(?:\.\d+)?', font), 'Unexpected font_scale value')
    size_override = re.search(r'Override size: (\d+x\d+)', size)
    density_override = re.search(r'Override density: (\d+)', density)
    require('Physical size:' in size and 'Physical density:' in density, 'Cannot inspect original display')
    return {'size': size_override.group(1) if size_override else 'reset',
            'density': density_override.group(1) if density_override else 'reset',
            'fontScale': font, 'rawSize': size.strip(), 'rawDensity': density.strip()}


def restore_display(device, original):
    commands = [('shell', 'wm', 'size', original['size']),
                ('shell', 'wm', 'density', original['density'])]
    if original['fontScale'] == 'null':
        commands.append(('shell', 'settings', 'delete', 'system', 'font_scale'))
    else:
        commands.append(('shell', 'settings', 'put', 'system', 'font_scale', original['fontScale']))
    failures = []
    for command in commands:
        try:
            device.command(*command)
        except Exception:
            failures.append(' '.join(command[1:4]))
    require(not failures, 'Could not restore display settings: ' + ', '.join(failures))
    require(display_settings(device) == original, 'Display settings differ after restoration')


def failure_proxy(base):
    origin = urlsplit(local_api(base)[0])

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, format, *args):
            pass

        def drop(self):
            self.close_connection = True
            try:
                self.connection.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass
            self.connection.close()

        def do_POST(self):
            with self.server.count_lock:
                self.server.counts['droppedPosts'] += 1
            self.drop()

        def do_GET(self):
            if not self.path.startswith('/') or self.path.startswith('//'):
                self.drop()
                return
            connection = HTTPConnection(origin.hostname, origin.port or 80, timeout=15)
            try:
                headers = {'Authorization': self.headers.get('Authorization', '')}
                connection.request('GET', self.path, headers=headers)
                response = connection.getresponse()
                payload = response.read()
                self.send_response(response.status)
                self.send_header('Content-Type', response.getheader('Content-Type', 'application/json'))
                self.send_header('Content-Length', str(len(payload)))
                self.send_header('Cache-Control', 'no-store')
                self.end_headers()
                self.wfile.write(payload)
                with self.server.count_lock:
                    self.server.counts['forwardedGets'] += 1
            except Exception:
                self.drop()
            finally:
                connection.close()

    proxy = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
    proxy.daemon_threads = True
    proxy.count_lock = threading.Lock()
    proxy.counts = {'forwardedGets': 0, 'droppedPosts': 0}
    return proxy


class ResilienceVerification:
    def __init__(self, args):
        self.output = ignored_path(args.evidence_dir)
        require(not self.output.exists(), 'Use a fresh evidence directory; preserve previous resilience evidence')
        fixture_path = ignored_path(args.fixture)
        self.fixture = json.loads(fixture_path.read_text(encoding='utf-8'))
        require(self.fixture.get('formatVersion') == 1
                and self.fixture.get('settlementMode') == 'issuer_final', 'Unsupported pair fixture')
        pair_report = json.loads((fixture_path.parent / 'report.json').read_text(encoding='utf-8'))
        require(pair_report.get('status') == 'passed'
                and pair_report.get('fixtureId') == self.fixture['id']
                and '08-give-final' in pair_report.get('completedStages', []),
                'A completed matching pair report is required')
        self.agreement_id = pair_report['agreement']['id']
        require(pair_report['agreement']['rewardState'] == 'received', 'Pair reward is not settled')
        self.base, self.port = local_api(self.fixture['api'])
        serials = self.fixture['serials']
        require(set(serials) == {'a', 'b'} and serials['a'] != serials['b'],
                'Two distinct pair fixture devices are required')
        self.devices = {key: Device(args.adb, serials[key], self.output / ('device-' + key))
                        for key in serials}
        for key, device in self.devices.items():
            prefs = preferences(device)
            require(prefs.get('baseUrl') == f'http://127.0.0.1:{self.port}',
                    'Device API origin differs from the pair reverse mapping')
            require(prefs.get('token'), 'Fixture device has no persisted session')
            user = api(self.base, '/v1/state', token=prefs['token'])['user']
            require(user['id'] == self.fixture['accounts'][key]['user']['id'],
                    'Device session does not match its synthetic fixture account')
        suffix = uuid.uuid4().hex[:16]
        self.note = '合成断网恢复条件 ' + suffix
        self.new_title = '合成断网恢复约定 ' + suffix
        self.report = {'status': 'running', 'startedAt': now(), 'fixtureId': self.fixture['id'],
                       'agreementId': self.agreement_id, 'syntheticNote': self.note, 'checks': {},
                       'businessMutationMethod': 'Android UI only; host API uses GET only',
                       'limitations': ['Synthetic pact_ AVDs; not real-user or production acceptance.',
                                       'POST transport failure is injected locally; GET remains online.',
                                       'Recovery opens a new form; this does not prove same-form requestId replay.',
                                       'Layout checks text width and screenshots; screenshots still need visual review.']}
        self.persist()

    def persist(self):
        save(self.output / 'report.json', self.report)

    def detail(self):
        return [await_text('账户设置'), click('约定'),
                await_text('查看约定：' + self.fixture['agreementTitle']),
                click('查看约定：' + self.fixture['agreementTitle'])]

    def states(self):
        result = {}
        for key, account in self.fixture['accounts'].items():
            state = api(self.base, '/v1/state', token=account['token'])
            require(state['user']['id'] == account['user']['id'], 'Fixture API identity changed')
            state.pop('serverTime', None)
            result[key] = state
        return result

    def create_steps(self):
        return [await_text('账户设置'), click('约定'), click('创建约定'),
                select('所属关系', self.fixture['relationshipName']), fill('约定标题', self.new_title),
                fill('达成条件', self.note), fill('目标次数', '1'), fill('完成截止日期', ''),
                click('下一步：约定奖励'), fill('奖励内容', '合成恢复奖励：一起安排活动'),
                fill('兑现天数', '7'), click('下一步：确认约定'), click('发出约定')]

    def current(self):
        items = []
        for key in ('a', 'b'):
            account = self.fixture['accounts'][key]
            state = api(self.base, '/v1/state', token=account['token'])
            require(state['user']['id'] == account['user']['id'], 'Fixture API identity changed')
            matches = [item for item in state['agreements'] if item['id'] == self.agreement_id]
            require(len(matches) == 1, 'Fixture agreement is missing or ambiguous')
            items.append(matches[0])
        require(items[0] == items[1], 'Accounts do not see the same authoritative agreement')
        require(items[0]['rewardState'] == 'received' and items[0]['taskState'] == 'achieved',
                'Completed pair state changed')
        return items[0]

    def narrow_layout(self):
        device = self.devices['a']
        original = display_settings(device)
        self.report['displayOriginal'] = original
        self.persist()
        try:
            device.command('shell', 'wm', 'size', '640x1280')
            device.command('shell', 'wm', 'density', '320')
            device.command('shell', 'settings', 'put', 'system', 'font_scale', '1.5')
            configured = display_settings(device)
            require((configured['size'], configured['density'], configured['fontScale']) ==
                    ('640x1280', '320', '1.5'), 'Narrow display settings were not applied')
            self.report['displayDuringCheck'] = configured
            home_entry = '查看全部约定'
            content = (home_entry, '查看约定：' + self.fixture['agreementTitle'],
                       '查看约定：' + self.fixture['agreementTitle'], self.fixture['relationshipName'])
            for index, (label, expected) in enumerate(zip(('首页', '约定', '奖励', '关系'), content), 1):
                device.stage(f'01-narrow-{index}', [await_text(home_entry), click(label),
                                                   await_text(expected), {'op': 'layout'}])
            device.stage('01-narrow-detail', self.detail() +
                         [await_text('已兑现'), {'op': 'layout'}])
            self.report['checks']['narrowLayout'] = {'status': 'passed', 'widthDp': 320,
                                                    'fontScale': 1.5, 'screens': 5}
        finally:
            restore_display(device, original)
            self.report['displayRestored'] = True
            self.persist()

    def reverse_destination(self):
        target = f'tcp:{self.port}'
        matches = [line.split()[-1] for line in self.devices['a'].command('reverse', '--list').splitlines()
                   if len(line.split()) >= 2 and line.split()[-2] == target]
        require(matches == [target], 'Expected original pair reverse mapping is missing or changed')
        return matches[0]

    def failed_write(self):
        device = self.devices['a']
        original = self.reverse_destination()
        before = self.states()
        save(self.output / '02-before.json', {'at': now(), 'states': before})
        proxy = failure_proxy(self.base)
        worker = threading.Thread(target=proxy.serve_forever, daemon=True)
        worker.start()
        try:
            device.command('reverse', f'tcp:{self.port}', f'tcp:{proxy.server_port}')
            device.stage('02-failed-write-input-retained', self.create_steps() +
                         [await_text('连接未完成。请检查网络后重试，输入已保留。'),
                          await_text(self.note)])
            after = self.states()
            require(before == after, 'Failed UI submission changed authoritative business state')
            require(proxy.counts['forwardedGets'] > 0 and proxy.counts['droppedPosts'] > 0,
                    'Proxy did not observe both GET and failed POST traffic')
            save(self.output / '02-after.json', {'at': now(), 'states': after})
            self.report['checks']['failedWrite'] = {'status': 'passed', **proxy.counts,
                'versionUnchanged': True, 'eventsUnchanged': True, 'inputRetained': True,
                'message': '连接未完成。请检查网络后重试，输入已保留。'}
        finally:
            try:
                device.command('reverse', f'tcp:{self.port}', original)
                require(self.reverse_destination() == original, 'Reverse mapping was not restored')
                self.report['reverseRestored'] = True
            finally:
                proxy.shutdown()
                proxy.server_close()
                worker.join(timeout=20)
                require(not worker.is_alive(), 'Local failure proxy did not stop')
                self.report['proxyStopped'] = True
                self.persist()
        return before

    def online_write(self, before):
        self.devices['a'].stage('03-online-single-agreement', self.create_steps() +
                               [await_text('待接受'), await_text(self.note)])
        after = self.states()
        created = []
        for key in ('a', 'b'):
            matches = [item for item in after[key]['agreements'] if item['title'] == self.new_title]
            require(len(matches) == 1, 'Recovery must create exactly one agreement visible to each account')
            item = matches[0]
            require(item['version'] == 1 and [event['action'] for event in item['events']] == ['create']
                    and item['conditions'] == self.note and item['taskState'] == 'proposed'
                    and item['rewardState'] == 'unearned', 'Unexpected recovery agreement or history')
            require(item['issuerId'] == self.fixture['accounts']['a']['user']['id']
                    and item['executorId'] == self.fixture['accounts']['b']['user']['id']
                    and item['events'][0]['actorId'] == item['issuerId'] and not item['events'][0]['proxy'],
                    'Recovery has wrong roles or provenance')
            remaining = dict(after[key])
            remaining['agreements'] = [row for row in remaining['agreements'] if row['id'] != item['id']]
            require(remaining == before[key], 'Recovery changed existing business state')
            created.append(item)
        require(created[0] == created[1], 'Accounts disagree on the recovery agreement')
        self.current()
        save(self.output / '03-after.json', {'at': now(), 'states': after})
        self.report['checks']['onlineRecovery'] = {'status': 'passed', 'agreementCount': 1,
            'bothAccountsAgree': True, 'retryMethod': 'New UI form after network restoration'}
        self.persist()

    def restart(self):
        before = self.states()
        for key, device in self.devices.items():
            device.command('shell', 'am', 'force-stop', PACKAGE)
            device.stage('04-restart-' + key, [await_text('账户设置'), click('约定'),
                         click('查看约定：' + self.new_title), await_text('待接受'), await_text(self.note), {'op': 'layout'}])
        require(self.states() == before, 'Restart changed authoritative business state')
        self.current()
        self.report['checks']['applicationRestart'] = {'status': 'passed', 'devices': list(self.devices),
                                                      'result': 'New agreement reloaded; original settled reward unchanged'}
        self.persist()

    def run(self):
        try:
            self.current()
            self.narrow_layout()
            before = self.failed_write()
            self.online_write(before)
            self.restart()
            self.report.update(status='passed', completedAt=now())
            self.persist()
            print('PASS: narrow UI, failed write, online recovery and restart; evidence: ' + str(self.output))
        except Exception as error:
            message = str(error)
            for account in self.fixture['accounts'].values():
                for key in ('token', 'password'):
                    if account.get(key):
                        message = message.replace(account[key], '[redacted]')
            self.report.update(status='failed', failedAt=now(), error=message)
            self.persist()
            raise RuntimeError(message) from None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--fixture', default='artifacts/pact/pair/fixture.json')
    parser.add_argument('--adb', default=default_adb())
    parser.add_argument('--evidence-dir', default='artifacts/pact/resilience')
    args = parser.parse_args()
    try:
        ResilienceVerification(args).run()
    except Exception as error:
        print('FAIL: ' + str(error), file=sys.stderr, flush=True)
        raise SystemExit(1) from None


if __name__ == '__main__':
    main()
