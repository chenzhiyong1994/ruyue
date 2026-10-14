"""Packaging rejects stale Android metadata and preserves the selected APK."""

import contextlib
import hashlib
import io
import json
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest import mock

from scripts import package_pact


class PackagePactTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='pact-package-')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        app = self.root / 'android/app'
        app.mkdir(parents=True)
        (app / 'build.gradle').write_text("versionCode 13\nversionName '1.0.0'\n", encoding='utf-8')
        self.source = app / 'build/outputs/apk/debug/app-debug.apk'
        self.source.parent.mkdir(parents=True)
        with zipfile.ZipFile(self.source, 'w') as apk:
            apk.writestr('classes.dex', b'synthetic preview fixture')
        self.metadata = {
            'applicationId': 'cn.zhiya.pact.preview',
            'variantName': 'debug',
            'elements': [{'versionCode': 13, 'versionName': '1.0.0-preview-debug',
                          'outputFile': 'app-debug.apk'}],
        }
        self.output = self.root / 'artifacts/pact/1.0.0'

    def run_package(self, *args):
        (self.source.parent / 'output-metadata.json').write_text(
            json.dumps(self.metadata), encoding='utf-8')
        with (mock.patch.object(package_pact, '__file__', str(self.root / 'scripts/package_pact.py')),
              mock.patch('sys.argv', ['package_pact.py', *args]),
              mock.patch.object(package_pact.subprocess, 'check_output', side_effect=['a' * 40, '']),
              contextlib.redirect_stdout(io.StringIO())):
            package_pact.main()

    def test_preserves_apk_and_reports_installable_version(self):
        self.run_package()
        target = self.output / 'ruyue-1.0.0-preview.apk'
        self.assertEqual(self.source.read_bytes(), target.read_bytes())
        digest = hashlib.sha256(target.read_bytes()).hexdigest()
        manifest = json.loads((self.output / 'manifest.json').read_text(encoding='utf-8'))
        self.assertEqual(13, manifest['versionCode'])
        self.assertEqual('1.0.0-preview-debug', manifest['versionName'])
        self.assertEqual('debug', manifest['buildType'])
        self.assertEqual(digest, manifest['sha256'])
        self.assertEqual(f'{digest}  {target.name}\n',
                         (self.output / 'SHA256.txt').read_text(encoding='utf-8'))
        self.assertIn('docs/download.md', (self.output / 'README.txt').read_text(encoding='utf-8'))

    def test_rejects_stale_or_different_apk_metadata(self):
        element = self.metadata['elements'][0]
        for key, invalid in [('versionCode', 12), ('versionName', '1.4.3-preview-debug'),
                             ('outputFile', 'another.apk')]:
            original = element[key]
            with self.subTest(field=key):
                element[key] = invalid
                output = f'artifacts/pact/invalid-{key}'
                with self.assertRaisesRegex(SystemExit, 'metadata differs'):
                    self.run_package('--output', output)
                self.assertFalse((self.root / output).exists())
            element[key] = original

    def test_rejects_non_preview_build(self):
        for key, invalid in [('applicationId', 'cn.zhiya.pact'), ('variantName', 'release')]:
            original = self.metadata[key]
            with self.subTest(field=key):
                self.metadata[key] = invalid
                with self.assertRaisesRegex(SystemExit, 'metadata differs'):
                    self.run_package()
                self.assertFalse(self.output.exists())
            self.metadata[key] = original

    def test_rejects_legacy_classes_before_writing_deliverables(self):
        with zipfile.ZipFile(self.source, 'a') as apk:
            apk.writestr('classes2.dex', b'Lcn/zhiya/learning/Legacy;')
        with self.assertRaisesRegex(SystemExit, 'Legacy application classes'):
            self.run_package()
        self.assertFalse(self.output.exists())


if __name__ == '__main__':
    unittest.main()
