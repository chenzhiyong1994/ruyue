"""Package the validated preview APK without including any private runtime data."""

import argparse
import hashlib
import json
import re
import shutil
import subprocess
import zipfile
from pathlib import Path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', help='Defaults to artifacts/pact/<Android version>')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    build = (root / 'android/app/build.gradle').read_text(encoding='utf-8')
    version = re.search(r"versionName\s+'([0-9]+\.[0-9]+\.[0-9]+)'", build).group(1)
    version_code = int(re.search(r'versionCode\s+([0-9]+)', build).group(1))
    source = root / 'android/app/build/outputs/apk/debug/app-debug.apk'
    if not source.is_file():
        raise SystemExit('Build and validate the preview APK before packaging.')
    metadata = json.loads((source.parent / 'output-metadata.json').read_text(encoding='utf-8'))
    if (metadata.get('applicationId') != 'cn.zhiya.pact.preview'
            or metadata.get('variantName') != 'debug'
            or len(metadata.get('elements', [])) != 1
            or metadata['elements'][0].get('versionName') != version + '-preview-debug'
            or metadata['elements'][0].get('versionCode') != version_code
            or metadata['elements'][0].get('outputFile') != source.name):
        raise SystemExit('Built APK metadata differs from the current preview version; rebuild before packaging.')
    with zipfile.ZipFile(source) as apk:
        names = apk.namelist()
        if any(name.startswith('assets/lessons/') or name.endswith(('curriculum.json', 'progress-v2.bin')) for name in names):
            raise SystemExit('Legacy content detected in APK; packaging stopped.')
        for name in names:
            if name.endswith('.dex') and b'Lcn/zhiya/learning/' in apk.read(name):
                raise SystemExit('Legacy application classes detected in APK; packaging stopped.')
    output = (root / (args.output or f'artifacts/pact/{version}')).resolve()
    if not output.is_relative_to(root / 'artifacts' / 'pact'):
        raise SystemExit('Output must be inside artifacts/pact.')
    output.mkdir(parents=True, exist_ok=True)
    target = output / f'ruyue-{version}-preview.apk'
    shutil.copyfile(source, target)
    digest = hashlib.sha256(target.read_bytes()).hexdigest()
    (output / 'SHA256.txt').write_text(f'{digest}  {target.name}\n', encoding='utf-8')
    revision = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip()
    dirty = bool(subprocess.check_output(['git', 'status', '--porcelain'], cwd=root, text=True).strip())
    manifest = {'brand': '如约', 'version': version + '-preview', 'package': 'cn.zhiya.pact.preview',
                'versionName': version + '-preview-debug', 'versionCode': version_code,
                'buildType': 'debug',
                'apk': target.name, 'sha256': digest, 'baseCommit': revision,
                'workingTreeChanges': dirty, 'apiVersion': 1,
                'deployment': 'local development service required; no public deployment'}
    (output / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    (output / 'README.txt').write_text(
        f'如约 {version} 开发预览\n\n'
        '需要运行同一仓库的服务：\n'
        'python -m server --host 127.0.0.1 --port 8765 --database .local/pact/pact.sqlite3\n\n'
        '模拟器默认连接 http://10.0.2.2:8765。USB 真机可用 adb reverse tcp:8765 tcp:8765，'
        '并在调试连接设置中填写 http://127.0.0.1:8765。两位用户必须连接同一服务。\n'
        '这是调试签名预览，不包含公网托管、系统推送、账号找回或商店发布。\n'
        '下载、安装和升级说明见仓库中的 docs/download.md。\n',
        encoding='utf-8')
    print(f'PASS: packaged {target.relative_to(root)}; SHA-256 {digest}; legacy assets/classes absent')


if __name__ == '__main__':
    main()
