#!/usr/bin/env python3
"""Prevent removed media-app code and dependencies from returning to the lab host."""
from pathlib import Path
import argparse
import zipfile
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument('--apk', type=Path)
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
source = root / 'app/src/main/java/com/lagradost/cloudstream3'
for forbidden in ['MainActivity.kt', 'MainActivityScreen.kt', 'actions', 'services', 'receivers',
                  'syncproviders', 'ui/player', 'ui/account', 'ui/download', 'ui/home',
                  'ui/library', 'ui/result', 'ui/search', 'ui/setup', 'ui/subtitles']:
    path = source / forbidden
    assert not path.exists() or (path.is_dir() and not any(path.rglob('*.kt'))), path
text = (root / 'app/build.gradle.kts').read_text()
for dependency in ['bundles.media3', 'bundles.nextlib', 'libs.video', 'libs.torrentserver',
                   'libs.biometric', 'libs.qrcode.kotlin', 'libs.previewseekbar.media3',
                   'libs.anime.db', 'libs.shimmer', 'libs.zipline', 'libs.work.runtime.ktx']:
    assert dependency not in text, dependency
android = '{http://schemas.android.com/apk/res/android}'
manifest = ET.parse(root / 'app/src/main/AndroidManifest.xml').getroot()
assert {e.get(android + 'name') for e in manifest.findall('uses-permission')} == {
    'android.permission.INTERNET', 'android.permission.ACCESS_NETWORK_STATE'
}
application = manifest.find('application')
assert [e.get(android + 'name') for e in application.findall('activity')] == ['.lab.ExtensionLabActivity']
assert not application.findall('service')
assert not application.findall('receiver')
menu = ET.parse(root / 'app/src/main/res/menu/extension_lab_menu.xml').getroot()
assert len(menu.findall('item')) == 2
nav = ET.parse(root / 'app/src/main/res/navigation/extension_lab_navigation.xml').getroot()
for node in nav:
    name = node.get(android + 'name')
    if name and name.startswith('com.lagradost.cloudstream3.'):
        target = source / (name.removeprefix('com.lagradost.cloudstream3.').replace('.', '/') + '.kt')
        assert target.exists(), target
assert not (root / 'desktopApp').exists()
assert not (root / 'docs/build.gradle.kts').exists()
# Every XML file must remain parseable after resource pruning.
for path in (root / 'app/src/main/res').rglob('*.xml'):
    ET.parse(path)
print('PASS: extension-only source, dependencies, manifest, two-tab navigation and resource XML')

if args.apk:
    with zipfile.ZipFile(args.apk) as apk:
        for name in apk.namelist():
            assert not any(token in name.lower() for token in ['libffmpeg', 'libavcodec', 'libavformat']), name
            if name.endswith('.dex'):
                dex = apk.read(name)
                for token in [b'Lcom/lagradost/cloudstream3/MainActivity;',
                              b'Lcom/lagradost/cloudstream3/ui/player/',
                              b'Lcom/lagradost/cloudstream3/ui/account/',
                              b'Landroidx/media3/exoplayer/']:
                    assert token not in dex, f'{token!r} remains in {name}'
    print('PASS: release APK contains no legacy player/account activity classes or FFmpeg binaries')
