"""NBT 26.x : getX(clé) renvoie Optional ; on passe aux variantes avec valeur par défaut (même comportement qu'en 1.20)."""
import os, re, sys

ARG = r'("(?:[^"\\]|\\.)*"|[A-Z][A-Z0-9_]*|[a-z]\w*(?:\.[a-z]\w*)*(?:\(\))?|\d+)'
DEFAULTS = {'getInt': '0', 'getLong': '0L', 'getString': '""', 'getBoolean': 'false', 'getFloat': '0f',
            'getDouble': '0d', 'getShort': '(short) 0', 'getByte': '(byte) 0'}
n = 0
for root, _, fs in os.walk(sys.argv[1]):
    for f in fs:
        if not f.endswith('.java'):
            continue
        p = os.path.join(root, f)
        s = open(p, encoding='utf-8').read()
        if 'CompoundTag' not in s and 'ListTag' not in s:
            continue
        o = s
        for name, d in DEFAULTS.items():
            s, k = re.subn(r'\.' + name + r'\(' + ARG + r'\)', lambda m: '.' + name + 'Or(' + m.group(1) + ', ' + d + ')', s)
            n += k
        s, k = re.subn(r'\.getCompound\(' + ARG + r'\)', r'.getCompoundOrEmpty(\1)', s); n += k
        s, k = re.subn(r'\.getList\(' + ARG + r',\s*[^()]*?\)', r'.getListOrEmpty(\1)', s); n += k
        s, k = re.subn(r'\.getIntArray\(' + ARG + r'\)', r'.getIntArray(\1).orElse(new int[0])', s); n += k
        s, k = re.subn(r'\.getLongArray\(' + ARG + r'\)', r'.getLongArray(\1).orElse(new long[0])', s); n += k
        if s != o:
            open(p, 'w', encoding='utf-8', newline='\n').write(s)
print(n)
