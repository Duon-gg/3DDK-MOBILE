"""Rebuild native 3DDK vector assets with Python's standard library."""
from pathlib import Path
import json
import xml.etree.ElementTree as ET

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
RES = ROOT / 'app/src/main/res'
BLUE, NAVY, WHITE = '#2563EB', '#173B88', '#FFFFFF'
# Three cells and a shared curved edge form a numeral 3.
MARK = [
    'M16 10H40Q44 10 44 14V26Q44 30 40 30H16Q10 30 10 24V16Q10 10 16 10Z',
    'M20 40H40Q44 40 44 44V56Q44 60 40 60H20Q14 60 14 54V46Q14 40 20 40Z',
    'M16 70H40Q44 70 44 74V86Q44 90 40 90H16Q10 90 10 84V76Q10 70 16 70Z',
    'M54 10H62C80 10 90 20 90 35Q90 44 82 50Q90 56 90 65C90 80 80 90 62 90H54Q50 90 50 86V74Q50 70 54 70H62Q70 70 70 65Q70 60 62 60H54Q50 60 50 56V44Q50 40 54 40H62Q70 40 70 35Q70 30 62 30H54Q50 30 50 26V14Q50 10 54 10Z',
]
# Original outlined lettering; no external font is needed to render the logo.
LETTERS = [
    'M0 0H23Q42 0 42 16Q42 26 34 30Q44 34 44 45Q44 64 23 64H0V51H22Q29 51 29 44Q29 37 22 37H6V25H22Q28 25 28 19Q28 13 22 13H0Z',
    'M56 0H76Q108 0 108 32Q108 64 76 64H56ZM71 14V50H76Q93 50 93 32Q93 14 76 14Z',
    'M120 0H140Q172 0 172 32Q172 64 140 64H120ZM135 14V50H140Q157 50 157 32Q157 14 140 14Z',
    'M184 0H199V25L220 0H238L212 31L240 64H221L199 38V64H184Z',
]

def write(name, content):
    p = HERE / name
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(content, encoding='utf-8')

def paths(items, color):
    return ''.join(f'<path fill="{color}" fill-rule="evenodd" d="{d}"/>' for d in items)

def svg(w, h, body, label='3DDK'):
    return f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}" role="img" aria-label="{label}"><title>{label}</title>{body}</svg>\n'

def vector(size, viewport, body):
    return f'<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="{size}dp" android:height="{size}dp" android:viewportWidth="{viewport}" android:viewportHeight="{viewport}">\n{body}\n</vector>\n'

def android_paths(color):
    return '\n'.join(f' <path android:fillColor="{color}" android:pathData="{d}"/>' for d in MARK)

for variant, color, text in [('primary', BLUE, NAVY), ('white', WHITE, WHITE), ('black', '#111827', '#111827')]:
    write(f'logo/mark-{variant}.svg', svg(100, 100, paths(MARK, color)))
    write(f'logo/wordmark-{variant}.svg', svg(240, 64, paths(LETTERS, text)))
    write(f'logo/lockup-{variant}.svg', svg(390, 100, paths(MARK, color) + f'<g transform="translate(130 18)">{paths(LETTERS, text)}</g>'))
    write(f'logo/stacked-{variant}.svg', svg(240, 210, f'<g transform="translate(60 0) scale(1.2)">{paths(MARK,color)}</g><g transform="translate(0 146)">{paths(LETTERS,text)}</g>'))

for name, radius in [('app-icon', 22), ('store-icon', 0)]:
    write(f'icons/{name}.svg', svg(100, 100, f'<rect width="100" height="100" rx="{radius}" fill="{BLUE}"/><g transform="translate(15 15) scale(.7)">{paths(MARK, WHITE)}</g>'))
write('icons/favicon.svg', svg(100, 100, f'<rect width="100" height="100" rx="22" fill="{BLUE}"/><g transform="translate(5 5) scale(.9)">{paths(MARK, WHITE)}</g>'))

legacy = vector(48, 100, f'<path android:fillColor="{BLUE}" android:pathData="M22 0H78Q100 0 100 22V78Q100 100 78 100H22Q0 100 0 78V22Q0 0 22 0Z"/><group android:scaleX=".7" android:scaleY=".7" android:translateX="15" android:translateY="15">{android_paths(WHITE)}</group>')
foreground = vector(108, 108, f'<group android:scaleX=".56" android:scaleY=".56" android:translateX="26" android:translateY="26">{android_paths(WHITE)}</group>')
notification = vector(24, 100, android_paths(WHITE))
adaptive = '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n <background android:drawable="@color/mobile_primary"/>\n <foreground android:drawable="@drawable/ic_brand_foreground"/>\n <monochrome android:drawable="@drawable/ic_brand_monochrome"/>\n</adaptive-icon>\n'
for relative, content in {
    'drawable/ic_app.xml': legacy,
    'drawable/ic_brand_foreground.xml': foreground,
    'drawable/ic_brand_monochrome.xml': foreground,
    'drawable/ic_notification.xml': notification,
    'drawable-v26/ic_launcher.xml': adaptive,
}.items():
    write('android/' + relative, content)
    (RES / relative).write_text(content, encoding='utf-8')

# Export the existing app's filled icons so the kit matches the shipped UI.
ns = '{http://schemas.android.com/apk/res/android}'
ui_names = ['table', 'cards', 'search', 'close', 'bell', 'person', 'team', 'record_next', 'record_previous', 'table_options']
for name in ui_names:
    source = RES / f'drawable/ic_{name}.xml'
    tree = ET.fromstring(source.read_text(encoding='utf-8'))
    body = ''
    for p in tree.findall('path'):
        fill = NAVY if p.get(ns + 'fillColor') else 'none'
        rule = p.get(ns + 'fillType', 'nonZero').lower()
        stroke = f' stroke="{NAVY}" stroke-width="{p.get(ns + "strokeWidth", "1")}"' if p.get(ns + 'strokeColor') else ''
        body += f'<path fill="{fill}" fill-rule="{rule}"{stroke} d="{p.get(ns + "pathData")}"/>'
    write(f'icons/ui/{name}.svg', svg(24, 24, body, name.replace('_', ' ')))

tokens = {'name': '3DDK', 'colors': {'primary': BLUE, 'onPrimary': WHITE, 'ink': NAVY, 'container': '#EAF1FF', 'text': '#202124', 'secondaryText': '#555960', 'border': '#E4E7EC', 'surface': WHITE}, 'font': {'android': 'sans-serif', 'web': 'Roboto, Arial, sans-serif'}, 'icon': {'grid': 24, 'style': 'filled', 'minimumTouchTarget': 48}}
write('tokens.json', json.dumps(tokens, indent=2) + '\n')

# Structural checks cover the generated assets and adaptive safe zone.
for p in HERE.rglob('*.svg'):
    root = ET.fromstring(p.read_text(encoding='utf-8'))
    assert root.get('viewBox'), p
for p in (HERE / 'android').rglob('*.xml'):
    ET.fromstring(p.read_text(encoding='utf-8'))
assert ((90 - 50) * .56) ** 2 * 2 < 33 ** 2
assert len(list((HERE / 'icons/ui').glob('*.svg'))) == 10
print('3DDK: SVG/XML parsed; 10 UI icons exported; adaptive mark fits safe circle.')
