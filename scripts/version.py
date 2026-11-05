import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
NS = '{http://schemas.android.com/apk/res/android}'
root = ET.parse(Path(__file__).resolve().parents[1] / 'AndroidManifest.xml').getroot()
version = root.attrib[NS + 'versionName']
if not re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+', version):
    raise SystemExit('versionName must be major.minor.patch')
if len(sys.argv) == 2 and sys.argv[1] != 'v' + version:
    raise SystemExit('Release tag does not match AndroidManifest.xml versionName')
print(version)
