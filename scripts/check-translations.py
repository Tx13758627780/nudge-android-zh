#!/usr/bin/env python3
"""Validate Simplified Chinese resource coverage and Java/Android format arguments."""
from collections import Counter
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1] / 'app/src/main/res'
FORMAT = re.compile(r'%(?:(\d+)\$)?[-#+ 0,(]*\d*(?:\.\d+)?([a-zA-Z%])')


def arguments(text):
    implicit = 0
    result = Counter()
    for explicit, kind in FORMAT.findall(text or ''):
        if kind in ('%', 'n'):
            continue
        implicit += 1
        result[(int(explicit) if explicit else implicit, kind)] += 1
    return result


def load(directory):
    entries = {}
    for path in sorted(directory.glob('*.xml')):
        for item in ET.parse(path).getroot():
            if item.tag not in ('string', 'plurals', 'string-array'):
                continue
            name = item.attrib['name']
            if name in entries:
                raise ValueError(f'Duplicate {name} in {path}')
            entries[name] = item
    return entries


def validate():
    english = load(ROOT / 'values')
    chinese = load(ROOT / 'values-zh-rCN')
    errors = []
    for name in sorted(english.keys() - chinese.keys()):
        if english[name].attrib.get('translatable') != 'false':
            errors.append(f'Missing Chinese resource: {name}')
    for name in sorted(chinese.keys() - english.keys()):
        errors.append(f'Missing default resource: {name}')
    for name in sorted(english.keys() & chinese.keys()):
        en, zh = english[name], chinese[name]
        if en.tag != zh.tag:
            errors.append(f'Resource type mismatch: {name}')
        elif en.tag == 'string':
            if en.attrib.get('formatted') != 'false' and arguments(''.join(en.itertext())) != arguments(''.join(zh.itertext())):
                errors.append(f'Format argument mismatch: {name}')
        elif en.tag == 'string-array':
            if len(en) != len(zh):
                errors.append(f'Array length mismatch: {name}')
            for index, (a, b) in enumerate(zip(en, zh)):
                if arguments(''.join(a.itertext())) != arguments(''.join(b.itertext())):
                    errors.append(f'Array format mismatch: {name}[{index}]')
        else:
            en_items = {item.attrib['quantity']: item for item in en}
            zh_items = {item.attrib['quantity']: item for item in zh}
            if 'other' not in zh_items:
                errors.append(f'Chinese plural lacks other: {name}')
            for quantity, item in zh_items.items():
                default = en_items.get(quantity, en_items['other'])
                if arguments(''.join(default.itertext())) != arguments(''.join(item.itertext())):
                    errors.append(f'Plural format mismatch: {name}/{quantity}')
    if errors:
        for error in errors:
            print(error, file=sys.stderr)
        return 1
    print(f'Chinese translation check passed: {len(chinese)} resources; keys and format arguments match.')
    return 0


if __name__ == '__main__':
    sys.exit(validate())
