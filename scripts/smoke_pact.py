"""Run explicit UI stages only on task-owned Pact emulators; never clear user data."""

import argparse
import base64
import json
import os
import subprocess
import urllib.request
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = 'cn.zhiya.pact.preview'


class Device:
    def __init__(self, adb, serial, output):
        self.adb, self.serial, self.output = str(adb), serial, Path(output)
        name = self.command('shell', 'getprop', 'ro.boot.qemu.avd_name').strip()
        if not name.startswith('pact_'):
            raise RuntimeError('Refusing a device without a task-owned pact_ AVD name')
        self.output.mkdir(parents=True, exist_ok=True)

    def command(self, *args, timeout=45):
        result = subprocess.run([self.adb, '-s', self.serial, *args], capture_output=True,
                                text=True, encoding='utf-8', errors='replace', timeout=timeout)
        if result.returncode:
            raise RuntimeError(result.stdout + result.stderr)
        return result.stdout

    def stage(self, name, steps):
        encoded = base64.b64encode(json.dumps(steps, ensure_ascii=False).encode()).decode()
        output = self.command('shell', 'am', 'instrument', '-w', '-e', 'isolated', 'yes',
                              '-e', 'steps', encoded,
                              PACKAGE + '.test/cn.zhiya.pact.PactInstrumentation', timeout=240)
        (self.output / (name + '.txt')).write_text(output, encoding='utf-8')
        if 'FAIL:' in output or 'PASS:' not in output:
            self.capture(name + '-failure')
            raise RuntimeError('Android stage failed: ' + name + '\n' + output)
        self.capture(name)
        print(f'PASS: {self.serial} {name}', flush=True)

    def capture(self, name):
        result = subprocess.run([self.adb, '-s', self.serial, 'exec-out', 'run-as', PACKAGE, 'cat', 'files/pact-ui-capture.png'],
                                capture_output=True, timeout=30, check=True)
        if not result.stdout.startswith(b'\x89PNG\r\n\x1a\n'):
            raise RuntimeError('Instrumentation did not produce a valid UI screenshot')
        (self.output / (name + '.png')).write_bytes(result.stdout)


def api(base, path, body=None, token=None):
    request = urllib.request.Request(base + path,
        data=json.dumps(body).encode() if body is not None else None,
        headers={'Content-Type': 'application/json', **({'Authorization': 'Bearer ' + token} if token else {})})
    with urllib.request.urlopen(request, timeout=20) as response:
        return json.load(response)


def click(label): return {'op': 'click', 'label': label}
def fill(label, value): return {'op': 'fill', 'label': label, 'value': value}
def await_text(label): return {'op': 'await', 'label': label}
def select(label, value): return {'op': 'select', 'label': label, 'value': value}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--steps', required=True, help='JSON array of explicit PactInstrumentation UI steps')
    parser.add_argument('--name', default='ui-stage')
    parser.add_argument('--adb', default=str(Path(os.environ.get('ANDROID_HOME', '')) / 'platform-tools/adb.exe'))
    parser.add_argument('--evidence-dir', default='artifacts/pact/smoke')
    args = parser.parse_args()
    device = Device(args.adb, args.serial, ROOT / args.evidence_dir)
    steps = json.loads(Path(args.steps).read_text(encoding='utf-8-sig'))
    device.stage(args.name, steps)


if __name__ == '__main__':
    main()
