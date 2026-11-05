"""Check source packaging and security invariants; no device connection required."""
from pathlib import Path
import hashlib
import re
import posixpath
import struct
import subprocess
import tarfile
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
NS = '{http://schemas.android.com/apk/res/android}'
manifest = ET.parse(ROOT / 'AndroidManifest.xml').getroot()
permissions = {p.attrib[NS + 'name'] for p in manifest.findall('uses-permission')}
assert permissions == {'android.permission.RECEIVE_BOOT_COMPLETED', 'android.permission.FOREGROUND_SERVICE', 'android.permission.POST_NOTIFICATIONS'}, 'Unexpected APK permission'
app = manifest.find('application')
assert app.attrib[NS + 'allowBackup'] == 'false'
assert app.find('service').attrib[NS + 'exported'] == 'false'
assert manifest.attrib['package'] == 'org.gsisms.guard'
assert int(manifest.attrib[NS + 'versionCode']) > 0
for line in (ROOT / 'third_party/checksums.sha256').read_text().splitlines():
    expected, path = line.split(maxsplit=1)
    assert hashlib.sha256((ROOT / path).read_bytes()).hexdigest() == expected, f'Payload/source hash mismatch: {path}'
elf = (ROOT / 'payload/lib/arm64-v8a/libphhsu.so').read_bytes()
assert elf[:6] == b'\x7fELF\x01\x01' and struct.unpack_from('<H', elf, 18)[0] == 40, 'Expected tested ARM32 PHH client'
with tarfile.open(ROOT / 'third_party/phh-superuser-source.tar.gz') as archive:
    for member in archive.getmembers():
        path = Path(member.name)
        assert not path.is_absolute() and '..' not in path.parts, 'Unsafe upstream archive path'
        assert not member.isdev(), 'Unexpected upstream device entry'
        if member.issym() or member.islnk():
            target = posixpath.normpath(posixpath.join(str(path.parent), member.linkname)) if member.issym() else posixpath.normpath(member.linkname)
            assert not member.linkname.startswith('/') and target.split('/')[0] == path.parts[0], 'Unsafe upstream archive link'

# Scan tracked public files only; never print matching secrets or source lines.
if (ROOT / '.git').exists():
    files = [ROOT / p.decode() for p in subprocess.check_output(['git', 'ls-files', '-z'], cwd=ROOT).split(b'\0') if p]
else:
    files = [p for p in ROOT.rglob('*') if p.is_file() and 'build' not in p.relative_to(ROOT).parts]
credential = re.compile(rb'(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|cfat_[A-Za-z0-9]{20,}|-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----)')
for file in files:
    assert file.suffix not in {'.jks', '.keystore', '.p12', '.db', '.log'}, f'Private/runtime file tracked: {file.relative_to(ROOT)}'
    assert not credential.search(file.read_bytes()), f'Credential found: {file.relative_to(ROOT)}'
apk = ROOT / 'build/GSI-SMS-Guard.apk'
if apk.exists():
    with zipfile.ZipFile(apk) as archive:
        names = set(archive.namelist())
        assert {'classes.dex', 'AndroidManifest.xml', 'lib/arm64-v8a/libphhsu.so'} <= names
        assert not any(n.endswith(('.jks', '.keystore', '.db', '.log')) for n in names)
print('Manifest, payload, upstream archive and public-file checks passed')
