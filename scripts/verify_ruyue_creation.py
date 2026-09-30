"""Inspect Ruyue's three-step creation UI without sending or saving an agreement.

Uses an already completed synthetic pair fixture and device A's existing login.
Host API calls are GET-only. All account, relationship and agreement data must
remain identical; only the response's volatile serverTime clock is excluded.
Display overrides are restored even when a UI assertion fails.
"""

import argparse
import json
import re
import sys
from pathlib import Path

from smoke_pact import ROOT, PACKAGE, Device, api, await_text, click, fill, select, default_adb
from verify_pair import local_api, now, preferences, require, save
from verify_resilience import display_settings, ignored_path, restore_display


SYNTHETIC = {
    'title': '周末一起散步',
    'conditions': '合成体验：饭后一起散步 20 分钟。小事也认真，彼此都安心。',
    'count': '4',
    'due': '2099-12-31',
    'reward': '合成体验：周末一起吃一顿喜欢的晚餐。',
    'days': '7',
}
NEXT_REWARD = '下一步：约定奖励'
NEXT_REVIEW = '下一步：确认约定'
FORBIDDEN_CLICKS = {'发出约定', '存为草稿', '发布约定', '保存草稿', '确认操作'}


class CreationVerification:
    def __init__(self, args):
        self.output = ignored_path(args.evidence_dir)
        self.fixture_path = ignored_path(args.fixture)
        require(not (self.output / 'report.json').exists(),
                'Creation evidence already exists; choose a fresh ignored evidence directory')
        self.fixture = json.loads(self.fixture_path.read_text(encoding='utf-8'))
        require(self.fixture.get('formatVersion') == 1, 'Unsupported pair fixture')
        suffix = self.fixture.get('id', '')
        require(re.fullmatch(r'[0-9a-f]{12}', suffix) is not None, 'Unexpected synthetic fixture identity')
        require(self.fixture.get('pending') is None, 'Pair verification is still in progress')
        pair_report = json.loads((self.fixture_path.parent / 'report.json').read_text(encoding='utf-8'))
        require(pair_report.get('status') == 'passed' and pair_report.get('fixtureId') == suffix
                and '08-give-final' in pair_report.get('completedStages', []),
                'A completed matching pair report is required before this read-only check')
        accounts = self.fixture.get('accounts', {})
        for key in ('a', 'b'):
            account = accounts.get(key, {})
            require(account.get('handle') == f'pair_{suffix}_{key}' and account.get('token')
                    and account.get('user', {}).get('id'), 'Fixture account is not an identified pair_ synthetic account')
        require(accounts['a']['user']['id'] != accounts['b']['user']['id'],
                'The pair accounts must be distinct')
        serials = self.fixture.get('serials', {})
        require(serials.get('a') and serials.get('b') and serials['a'] != serials['b'],
                'Two distinct fixture serials are required')
        self.base, self.port = local_api(self.fixture['api'])
        self.device = Device(args.adb, serials['a'], self.output / 'device-a')
        for package in (PACKAGE, PACKAGE + '.test'):
            require('package:' in self.device.command('shell', 'pm', 'path', package),
                    'Preview app and instrumentation must already be installed')
        prefs = preferences(self.device)
        require(prefs.get('baseUrl') == f'http://127.0.0.1:{self.port}' and prefs.get('token'),
                'Device A must retain its existing pair login and local API origin')
        self.device_token = prefs['token']
        target = f'tcp:{self.port}'
        mappings = [line.split()[-1] for line in self.device.command('reverse', '--list').splitlines()
                    if len(line.split()) >= 2 and line.split()[-2] == target]
        require(mappings == [target], 'The existing pair reverse mapping is missing or changed')
        relation = self.fixture.get('relationship', {})
        require(relation.get('id') and relation.get('status') == 'active'
                and relation.get('mode') == 'shared', 'An active shared fixture relationship is required')
        self.relationship_id = relation['id']
        self.relationship_name = relation.get('name', '')
        require(self.relationship_name == self.fixture.get('relationshipName'), 'Fixture relationship names disagree')
        self.report = {
            'status': 'running', 'startedAt': now(), 'fixtureId': suffix,
            'relationshipId': self.relationship_id, 'serial': serials['a'],
            'syntheticValues': SYNTHETIC, 'checks': {},
            'businessMutationMethod': 'None: no submit, no draft, no host API POST',
            'stateComparison': 'Complete GET /v1/state payload for both accounts, excluding serverTime only',
            'limitations': ['Synthetic pact_ AVD; not real-user or production acceptance.',
                            'Layout checks text width; the saved screenshots still require visual review.',
                            'Creation completion is intentionally not exercised by this script.'],
        }

    def persist(self):
        save(self.output / 'report.json', self.report)

    def current(self):
        snapshots = {}
        for key in ('a', 'b'):
            account = self.fixture['accounts'][key]
            state = api(self.base, '/v1/state', token=account['token'])
            require(state['user']['id'] == account['user']['id'] and state['user']['handle'] == account['handle'],
                    'Fixture API identity changed')
            matches = [item for item in state.get('relationships', []) if item.get('id') == self.relationship_id]
            require(len(matches) == 1 and matches[0]['status'] == 'active'
                    and matches[0]['mode'] == 'shared' and matches[0]['name'] == self.relationship_name,
                    'Fixture relationship is missing, inactive, renamed or ambiguous')
            expected_members = {self.fixture['accounts'][member]['user']['id'] for member in ('a', 'b')}
            require({member['id'] for member in matches[0]['members']} == expected_members,
                    'Fixture relationship membership changed')
            state.pop('serverTime', None)
            snapshots[key] = state
        device_state = api(self.base, '/v1/state', token=self.device_token)
        require(device_state['user']['id'] == self.fixture['accounts']['a']['user']['id']
                and device_state['user']['handle'] == self.fixture['accounts']['a']['handle'],
                'Device A login does not match the synthetic issuer account')
        return snapshots

    def first_page(self):
        home_entry = '查看全部约定'
        return [await_text(home_entry), click('约定'), click('创建约定'),
                await_text('创建第 1 步，共 3 步'), select('所属关系', self.relationship_name),
                fill('约定标题', SYNTHETIC['title']), fill('达成条件', SYNTHETIC['conditions']),
                fill('目标次数', SYNTHETIC['count']), fill('完成截止日期', SYNTHETIC['due'])]

    @staticmethod
    def reward_fields():
        return [await_text('创建第 2 步，共 3 步'), fill('奖励内容', SYNTHETIC['reward']),
                fill('兑现天数', SYNTHETIC['days'])]

    @staticmethod
    def first_values():
        return [await_text('创建第 1 步，共 3 步'), await_text(SYNTHETIC['title']),
                await_text(SYNTHETIC['conditions']), await_text(SYNTHETIC['count']),
                await_text(SYNTHETIC['due']), await_text(NEXT_REWARD)]

    @staticmethod
    def reward_values():
        return [await_text('创建第 2 步，共 3 步'), await_text(SYNTHETIC['reward']),
                await_text(SYNTHETIC['days']), await_text(NEXT_REVIEW)]

    @staticmethod
    def preview_values():
        return [await_text('创建第 3 步，共 3 步'), await_text(SYNTHETIC['title']),
                await_text(SYNTHETIC['conditions']), await_text(SYNTHETIC['reward']),
                await_text(SYNTHETIC['count'] + ' 次 · ' + SYNTHETIC['due'] + ' 前'),
                await_text('达成后 ' + SYNTHETIC['days'] + ' 天内'),
                await_text('发出约定'), await_text('存为草稿')]

    def page_steps(self, page):
        steps = self.first_page()
        if page == 1:
            # Returning to the page also scrolls its header into the screenshot.
            return steps + [click(NEXT_REWARD), click('上一步')] + self.first_values() + [{'op': 'layout'}]
        steps += [click(NEXT_REWARD)] + self.reward_fields()
        if page == 2:
            return steps + [click('上一步')] + self.first_values() + [click(NEXT_REWARD)] \
                + self.reward_values() + [{'op': 'layout'}]
        return steps + [click(NEXT_REVIEW)] + self.preview_values() + [{'op': 'layout'}]

    def stage(self, name, steps, before):
        require(all(step['op'] in {'await', 'click', 'fill', 'select', 'back', 'layout'} for step in steps),
                'Unexpected operation in this non-submitting UI check')
        require(not any(step['op'] == 'click' and step.get('label') in FORBIDDEN_CLICKS for step in steps),
                'Creation verification must never click a business-write action')
        require(self.current() == before, 'Authoritative data changed before UI stage ' + name)
        self.device.stage(name, steps)
        require(self.current() == before, 'Non-submitting UI stage changed authoritative data: ' + name)
        self.report['checks'][name] = {'status': 'passed', 'stateUnchanged': True,
                                       'screenshot': 'device-a/' + name + '.png', 'steps': len(steps)}
        self.persist()

    def run(self):
        original = display_settings(self.device)
        self.report['displayOriginal'] = original
        before = self.current()
        save(self.output / 'before.json', {'at': now(), 'states': before})
        self.persist()
        failures = []
        try:
            self.device.command('shell', 'wm', 'size', 'reset')
            self.device.command('shell', 'wm', 'density', 'reset')
            self.device.command('shell', 'settings', 'put', 'system', 'font_scale', '1.0')
            normal = display_settings(self.device)
            require(normal['size'] == 'reset' and normal['density'] == 'reset'
                    and float(normal['fontScale']) == 1.0, 'Normal display settings were not applied')
            self.report['normalDisplay'] = normal
            for page in (1, 2, 3):
                self.stage(f'01-normal-step-{page}', self.page_steps(page), before)
            for mode, action in (('header', click('返回')), ('system', {'op': 'back'})):
                steps = self.page_steps(3) + [dict(action)] + self.reward_values() \
                    + [dict(action)] + self.first_values() + [{'op': 'layout'}]
                self.stage('02-' + mode + '-back-retains-values', steps, before)
            self.device.command('shell', 'wm', 'size', '640x1280')
            self.device.command('shell', 'wm', 'density', '320')
            self.device.command('shell', 'settings', 'put', 'system', 'font_scale', '1.5')
            narrow = display_settings(self.device)
            require((narrow['size'], narrow['density'], narrow['fontScale']) == ('640x1280', '320', '1.5'),
                    '320dp / 1.5 font display settings were not applied')
            self.report['narrowDisplay'] = narrow
            for page in (1, 2, 3):
                self.stage(f'03-narrow-step-{page}', self.page_steps(page), before)
        except (Exception, KeyboardInterrupt) as error:
            failures.append(str(error) or type(error).__name__)
        finally:
            try:
                restore_display(self.device, original)
                self.report['displayRestored'] = True
            except Exception as error:
                self.report['displayRestored'] = False
                failures.append('Display restoration: ' + str(error))
            try:
                after = self.current()
                save(self.output / 'after.json', {'at': now(), 'states': after})
                require(after == before, 'The full authoritative business state changed during creation checks')
                self.report['allBusinessStateUnchanged'] = True
            except Exception as error:
                self.report['allBusinessStateUnchanged'] = False
                failures.append('Final GET comparison: ' + str(error))
            if failures:
                message = '\n'.join(failures)
                for account in self.fixture['accounts'].values():
                    for key in ('token', 'password'):
                        if account.get(key):
                            message = message.replace(account[key], '[redacted]')
                self.report.update(status='failed', failedAt=now(), error=message)
            else:
                self.report.update(status='passed', completedAt=now())
            self.persist()
        if failures:
            raise RuntimeError(self.report['error'])
        print('PASS: creation previews, retained back navigation, 320dp / 1.5 font; no business writes; evidence: '
              + str(self.output), flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--fixture', default='artifacts/pact/ruyue/pair/fixture.json')
    parser.add_argument('--adb', default=default_adb())
    parser.add_argument('--evidence-dir', default='artifacts/pact/ruyue/creation')
    args = parser.parse_args()
    try:
        CreationVerification(args).run()
    except Exception as error:
        print('FAIL: ' + str(error), file=sys.stderr, flush=True)
        raise SystemExit(1) from None


if __name__ == '__main__':
    main()
