#!/usr/bin/env python3
"""Package the built APK and complete corresponding source, including the full NNUE."""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
from hashlib import sha256
import shutil
import re

root = Path(__file__).resolve().parents[1]
out = root / 'artifacts'
out.mkdir(exist_ok=True)
version = re.search(r'versionName\s*=\s*"([^"]+)"', (root / 'app/build.gradle.kts').read_text()).group(1)
apk = out / f'ChessDude-{version}-debug-arm64.apk' 
shutil.copy2(root / 'app/build/outputs/apk/debug/app-debug.apk', apk)
source = out / f'ChessDude-{version}-AndroidStudio-source.zip'
excluded = {'.git', '.gradle', '.kotlin', '.idea', 'build', 'jniLibs', '__pycache__'}
with ZipFile(source, 'w', ZIP_DEFLATED, compresslevel=6) as archive:
    for path in sorted(root.rglob('*')):
        relative = path.relative_to(root)
        if not path.is_file() or excluded.intersection(relative.parts):
            continue
        if path.name in {'local.properties', '.DS_Store'}:
            continue
        if relative.parts[0] == 'artifacts' and (path.suffix in {'.apk', '.zip'} or path.name == 'SHA256SUMS'):
            continue
        archive.write(path, Path('ChessDude') / relative)
with (out / 'SHA256SUMS').open('w') as checksums:
    for path in (apk, source):
        checksums.write(f'{sha256(path.read_bytes()).hexdigest()}  {path.name}\n')
        print(f'{path}: {path.stat().st_size:,} bytes')
print((out / 'SHA256SUMS').read_text())

# Keep links from earlier deliveries pointing at the latest verified build.
shutil.copy2(apk, out / 'OfflineChess-debug-arm64.apk')
shutil.copy2(source, out / 'OfflineChess-AndroidStudio-source.zip')
