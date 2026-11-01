#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/opt/homebrew/share/android-commandlinetools}}"
APK=app/build/outputs/apk/debug/app-debug.apk
[[ -f "$APK" ]]
"$SDK/build-tools/35.0.0/apksigner" verify --verbose "$APK"
"$SDK/build-tools/35.0.0/aapt" dump badging "$APK" | head -n 6
python3 - <<'PY'
from zipfile import ZipFile
from hashlib import sha256
from pathlib import Path
p=Path('app/build/outputs/apk/debug/app-debug.apk')
with ZipFile(p) as z:
    abis=sorted(set(n.split('/')[1] for n in z.namelist() if n.startswith('lib/')))
    assert abis==['arm64-v8a'],abis
    assert 'lib/arm64-v8a/libstockfish.so' in z.namelist()
    net=z.read('assets/nn-1a298aa575a0.nnue')
    assert sha256(net).hexdigest()=='1a298aa575a085434d29027978dc36867fe9c5bcea9376654b7a8eba1e52dfc2'
    print('APK:',p.resolve())
    print('Bytes:',p.stat().st_size,'ABIs:',','.join(abis))
    print('Network SHA-256:',sha256(net).hexdigest())
print('APK SHA-256:',sha256(p.read_bytes()).hexdigest())
PY
