"""Review indexed Git blobs without printing matched credential values.

Run after git add. Standard library only; never reads excluded local recordings.
"""
from pathlib import Path, PurePosixPath
import json
import re
import struct
import subprocess
import sys

ROOT = Path(__file__).resolve().parent.parent
ROOT_FILES = {'.gitignore', '.gitattributes', 'README.md', 'SECURITY.md', 'tools/check-upload.py'}
APP_FILES = {
    'AndroidManifest.xml', 'build.ps1', 'check-combat-policy.py',
    'check-profile-flow.py', 'check-entrance-movement.py',
    'check-login-flow.py', 'check-ultimate-wait.py',
}
RULES = {
    'possible phone number': re.compile(r'(?<!\d)1[3-9]\d{9}(?!\d)'),
    'email address': re.compile(r'[A-Za-z0-9_.+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}'),
    'access token': re.compile(
        r'(?:gh[pousr]_[A-Za-z0-9_]{20,}|github_pat_[A-Za-z0-9_]{20,}'
        r'|sk-[A-Za-z0-9_-]{20,}|AKIA[0-9A-Z]{16}'
        r'|eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,})'),
    'private key': re.compile(r'-----BEGIN (?:[A-Z ]+)?PRIVATE KEY-----'),
    'credential assignment': re.compile(
        r'''(?i)(?:password|passwd|pwd|secret|token|api_key|phone)\s*[=:]\s*["'][^"'\r\n]{3,}["']'''),
    'exported login preference': re.compile(
        r'''<string\s+name=["'](?:ciphertext|phoneCiphertext|phoneIv|iv)["']'''),
    'personal Windows path': re.compile(r'(?i)[A-Z]:[\\/]+Users[\\/]+[^\\/\s"\x27]+'),
    'personal capture identifier': re.compile(r'wxid_[A-Za-z0-9_]+'),
    'embedded URL credentials': re.compile(r'https?://[^\s/@:]+:[^\s/@]+@'),
}


def allowed(name):
    if name in ROOT_FILES:
        return True
    parts = PurePosixPath(name).parts
    if any(p in {'recordings', 'shared_prefs', 'test-build', 'dist', 'build', '__pycache__'} for p in parts):
        return False
    if not name.startswith('android-app/'):
        return False
    sub = name[len('android-app/'):]
    suffix = PurePosixPath(sub).suffix
    return (sub in APP_FILES or sub.startswith('src/') and suffix == '.java'
            or sub.startswith('res/') and suffix == '.xml'
            or sub.startswith('assets/') and suffix in {'.json', '.png'}
            or sub == 'assets/world-chest-samples.dat')


def png_problem(data):
    if data[:8] != b'\x89PNG\r\n\x1a\n':
        return 'invalid PNG'
    pos = 8
    while pos + 12 <= len(data):
        size = struct.unpack_from('>I', data, pos)[0]
        kind = data[pos + 4:pos + 8]
        end = pos + size + 12
        if end > len(data):
            return 'truncated PNG'
        if kind in {b'tEXt', b'zTXt', b'iTXt', b'eXIf'}:
            return 'PNG metadata needs manual review'
        if kind == b'IHDR':
            width, height = struct.unpack_from('>II', data, pos + 8)
            if width > 1000 or height > 1000:
                return 'large screenshot needs manual review'
        if kind == b'IEND':
            return None if end == len(data) else 'PNG has trailing data'
        pos = end
    return 'PNG has no valid end'


def samples_problem(data):
    try:
        magic, groups = struct.unpack_from('>II', data)
        if magic != 0x42534331 or groups != 9:
            return 'unexpected reference header'
        pos = 8
        for _ in range(groups):
            count = struct.unpack_from('>I', data, pos)[0]
            pos += 4
            if not 0 < count <= 1000:
                return 'unexpected reference count'
            for _ in range(count):
                x, y, red, green, blue = struct.unpack_from('>iiiii', data, pos)
                pos += 20
                if not (0 <= x < 2560 and 0 <= y < 1440 and all(0 <= c <= 255 for c in (red, green, blue))):
                    return 'unexpected reference coordinates or colors'
        return None if pos == len(data) else 'reference has trailing data'
    except struct.error:
        return 'truncated reference data'


def indexed_files():
    raw = subprocess.check_output(['git', 'ls-files', '--stage', '-z'], cwd=ROOT)
    for entry in raw.split(b'\0'):
        if not entry:
            continue
        metadata, path = entry.split(b'\t', 1)
        mode, oid, stage = metadata.decode('ascii').split()
        name = path.decode('utf-8')
        if mode not in {'100644', '100755'} or stage != '0':
            raise ValueError('Unmerged, linked or non-regular indexed file: ' + name)
        yield name, oid


def main():
    entries = list(indexed_files())
    if not entries:
        raise ValueError('No indexed files; run git add first')
    request = ''.join(oid + '\n' for _, oid in entries).encode('ascii')
    packed = subprocess.check_output(['git', 'cat-file', '--batch'], input=request, cwd=ROOT)
    pos = 0
    findings = []
    total = 0
    for name, oid in entries:
        end = packed.index(b'\n', pos)
        header_oid, kind, size = packed[pos:end].decode('ascii').split()
        size = int(size)
        data = packed[end + 1:end + 1 + size]
        pos = end + 2 + size
        total += size
        if header_oid != oid or kind != 'blob' or len(data) != size:
            raise ValueError('Unexpected Git object response')
        if not allowed(name):
            findings.append((name, 0, 'outside upload allowlist'))
            continue
        if len(data) > 2 * 1024 * 1024:
            findings.append((name, 0, 'oversized file needs review'))
        if name.endswith('.png'):
            problem = png_problem(data)
            if problem:
                findings.append((name, 0, problem))
            continue
        if name.endswith('.dat'):
            problem = samples_problem(data)
            if problem:
                findings.append((name, 0, problem))
            continue
        content = data.decode('utf-8-sig')
        if name.endswith('.json'):
            json.loads(content)
        for number, line in enumerate(content.splitlines(), 1):
            for label, pattern in RULES.items():
                if pattern.search(line):
                    findings.append((name, number, label))
    for name, number, label in findings:
        print(f'{name}:{number}: {label}')
    if findings:
        print(f'FAILED: {len(findings)} potential issue(s); matched values are not printed.')
        return 1
    print(f'PASS: {len(entries)} indexed files, {total} bytes; no heuristic findings.')
    print('Image pixels and unrecognized secret formats still require human review.')
    return 0


if __name__ == '__main__':
    try:
        sys.exit(main())
    except (ValueError, UnicodeError, subprocess.CalledProcessError, OSError):
        print('FAILED: unable to complete indexed-file review; no file contents printed.')
        sys.exit(2)
