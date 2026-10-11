#!/usr/bin/env python3
"""Actual background process termination, task restoration and durable cart checks.

Run separately from instrumentation. It operates only the explicit serial and
this lab package. UI coordinates are read from the current accessibility tree.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
from fresh_android_xml import read_ui_xml
import re
import shutil
import subprocess
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument('--serial', required=True)
parser.add_argument('--adb', default=shutil.which('adb') or str(Path(os.environ.get('ANDROID_HOME', str(Path.home() / 'Library/Android/sdk'))) / 'platform-tools/adb'))
parser.add_argument('--output', type=Path, default=Path(__file__).resolve().parents[1] / 'evidence/local-process-restoration')
args = parser.parse_args()
args.output.mkdir(parents=True, exist_ok=True)
for helper_name in ('common_goods_ui.py', 'fresh_android_xml.py'):
    helper = Path(__file__).with_name(helper_name)
    if helper.is_file():
        (args.output / helper_name).write_bytes(helper.read_bytes())
PACKAGE = 'staff.android.lifetimes'
commands = []


def adb(*parts, check=True, binary=False):
    command = [args.adb, '-s', args.serial, *map(str, parts)]
    result = subprocess.run(command, capture_output=True, check=False)
    commands.append({'args': list(parts), 'exit_code': result.returncode})
    if check and result.returncode:
        raise RuntimeError(result.stderr.decode() + result.stdout.decode())
    return result.stdout if binary else result.stdout.decode().strip()


def tree():
    content = read_ui_xml(adb, '/sdcard/chapter7-restoration.xml', {PACKAGE})
    return ET.fromstring(content), content

def find(root, tag):
    return next((n for n in root.iter('node') if n.get('resource-id') == tag), None)


def swipe(up):
    root, _ = tree()
    region = next((n for n in root.iter('node') if n.get('scrollable') == 'true'), None)
    if region is None:
        return
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', region.get('bounds')))
    low, high = y1 + (y2-y1)*4//5, y1 + (y2-y1)//5
    adb('shell', 'input', 'swipe', (x1+x2)//2, low if up else high,
        (x1+x2)//2, high if up else low, 180)


def scroll_to_top():
    for _ in range(5):
        swipe(False)


def expect(tag, text=None, timeout=12):
    end = time.monotonic() + timeout
    last = None
    attempts = 0
    while time.monotonic() < end:
        root, _ = tree()
        last = find(root, tag)
        if last is not None and (text is None or last.get('text') == text):
            return last
        attempts += 1
        if attempts == 4:
            scroll_to_top()
        else:
            swipe(True)
    raise AssertionError((tag, text, None if last is None else last.attrib))


def tap(tag):
    if tag in ('reset', 'external', 'stock-event'):
        panel = expect('lab-controls')
        if panel.get('text') != 'Hide lab controls' and panel.get('content-desc') != 'Hide lab controls':
            # The button's text can be a descendant in the native accessibility tree.
            labels = [n.get('text') for n in panel.iter('node')]
            if 'Hide lab controls' not in labels:
                tap('lab-controls')
    node = expect(tag)
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds')))
    assert x2 > x1 and y2 > y1, node.attrib
    adb('shell', 'input', 'tap', (x1 + x2) // 2, (y1 + y2) // 2)


def enter(raw):
    tap('draft')
    adb('shell', 'input', 'keyevent', 'KEYCODE_MOVE_END')
    adb('shell', 'input', 'keyevent', *(['KEYCODE_DEL'] * 32))
    if raw:
        adb('shell', 'input', 'text', raw)
    adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    expect('draft', raw)


def capture(label):
    # Diagnostics are deliberately outside the shopping surface. Read the record
    # in its panel, then return to the editor for the visual evidence.
    panel = expect('lab-controls')
    labels = [n.get('text') for n in panel.iter('node')]
    if 'Hide lab controls' not in labels:
        tap('lab-controls')
    record = expect('record').get('text')
    _, record_xml = tree()
    (args.output / f'{label}-diagnostics.xml').write_text(record_xml)
    (args.output / f'{label}-diagnostics.png').write_bytes(adb('exec-out', 'screencap', '-p', binary=True))
    scroll_to_top()
    root, xml = tree()
    (args.output / f'{label}.xml').write_text(xml)
    (args.output / f'{label}.png').write_bytes(adb('exec-out', 'screencap', '-p', binary=True))
    values = {tag: (None if find(root, tag) is None else find(root, tag).get('text'))
              for tag in ['accepted', 'draft', 'conflict']}
    values['record'] = record
    return values


def kill_and_resume(label):
    before = adb('shell', 'pidof', PACKAGE)
    assert before, 'No running process to terminate'
    adb('shell', 'input', 'keyevent', 'KEYCODE_HOME')
    # Give the host its real stop/capture transition before targeting background PID.
    time.sleep(1)
    # am kill only targets a process Android currently considers safe to kill.
    # A recently stopped app can remain protected briefly; retry, never force-stop.
    end = time.monotonic() + 40
    while time.monotonic() < end and adb('shell', 'pidof', PACKAGE, check=False):
        adb('shell', 'am', 'kill', PACKAGE)
        time.sleep(1)
    assert not adb('shell', 'pidof', PACKAGE, check=False), 'Process did not terminate'
    adb('shell', 'am', 'start', '-n', PACKAGE + '/.MainActivity')
    expect('accepted')
    after = adb('shell', 'pidof', PACKAGE)
    assert after and after != before, (before, after)
    return {'old_pid': before, 'new_pid': after, 'state': capture(label)}


result = {'started_utc': datetime.now(timezone.utc).isoformat(), 'serial': args.serial,
          'scope': 'Background am kill preserves task; distinct from force-stop, task dismissal and StateRestorationTester',
          'scenarios': [], 'passed': False}
try:
    adb('shell', 'am', 'start', '-n', PACKAGE + '/.MainActivity')
    expect('accepted')
    for raw, label in [('3', 'raw-three-restored'), ('', 'empty-draft-restored')]:
        tap('reset'); expect('accepted', 'Accepted: 2')
        tap('edit'); enter(raw)
        before = capture(label + '-before')
        after = kill_and_resume(label)
        assert after['state']['accepted'] == 'Accepted: 2', after
        assert after['state']['draft'] == raw, after
        assert after['state']['record'] == 'Revision 0; confirmations 0', after
        result['scenarios'].append({'name': label, 'before': before, **after})
    tap('reset'); expect('accepted', 'Accepted: 2')
    tap('edit'); enter('3'); tap('confirm')
    expect('accepted', 'Accepted: 3')
    before = capture('accepted-three-before')
    after = kill_and_resume('accepted-three-restored')
    assert after['state']['accepted'] == 'Accepted: 3', after
    assert after['state']['draft'] is None, after
    assert after['state']['record'] == 'Revision 1; confirmations 1', after
    result['scenarios'].append({'name': 'accepted-three-restored', 'before': before, **after})
    result['passed'] = True
finally:
    result['commands'] = commands
    result['finished_utc'] = datetime.now(timezone.utc).isoformat()
    result['artifacts_sha256'] = {p.name: hashlib.sha256(p.read_bytes()).hexdigest()
                                  for p in args.output.glob('*') if p.suffix in ('.xml', '.png')}
    (args.output / 'results.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps({k: result[k] for k in ['serial', 'passed', 'scenarios']}, indent=2))
