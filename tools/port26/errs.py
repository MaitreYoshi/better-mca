"""Affiche les erreurs compactes : fichier:ligne, message, symbole, code source de la ligne."""
import re, sys

log = open(sys.argv[1], encoding='utf-8', errors='replace').read().split('\n')
pat = sys.argv[2] if len(sys.argv) > 2 else ''
head = re.compile(r'^(.*mcavie[/\\])(.*\.java):(\d+): error: (.*)$')
seen = set()
for i, l in enumerate(log):
    m = head.match(l)
    if not m or not re.search(pat, m.group(2).replace('\\', '/')):
        continue
    sym = ''
    for k in range(i + 1, min(i + 6, len(log))):
        if head.match(log[k]):
            break
        s = re.search(r'symbol: +(.*)', log[k])
        if s:
            sym = ' [' + s.group(1).strip() + ']'
        r = re.search(r'required: (.*)', log[k])
        if r:
            sym += ' req ' + r.group(1).strip()[:60]
    key = (m.group(2), m.group(3), m.group(4)[:50])
    if key in seen:
        continue
    seen.add(key)
    src = log[i + 1].strip()[:150] if i + 1 < len(log) else ''
    print(m.group(2).replace('\\', '/') + ':' + m.group(3) + ' ' + m.group(4)[:90] + sym + '\n    ' + src)
