#!/usr/bin/env python3
"""Check that the APK contains every original audio asset without alteration."""
import hashlib
import json
import pathlib
import sys
import zipfile
root = pathlib.Path(__file__).resolve().parent.parent
manifest = json.loads((root / 'docs/audio-manifest.json').read_text())
apk = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else root / 'dist/静听背景音-v1.4.1-release.apk'
with zipfile.ZipFile(apk) as archive:
    names = [n for n in archive.namelist() if n.startswith('assets/audio/') and n.endswith('.m4a')]
    assert len(names) == len(manifest) == 83
    for name, metadata in manifest.items():
        digest = hashlib.sha256(archive.read('assets/audio/' + name)).hexdigest()
        assert digest == metadata['sha256'], name
print('PASS: APK contains all 83 original audio assets; SHA-256 matches.')
