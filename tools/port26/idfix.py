import os, sys

BS = chr(92)
root_dir = sys.argv[1]
key = 'new Identifier('
n = 0
for root, _, fs in os.walk(root_dir):
    for f in fs:
        if not f.endswith('.java'):
            continue
        p = os.path.join(root, f)
        s = open(p, encoding='utf-8').read()
        out = []
        i = 0
        while True:
            j = s.find(key, i)
            if j < 0:
                out.append(s[i:])
                break
            out.append(s[i:j])
            k = j + len(key)
            depth = 1
            commas = 0
            q = None
            while depth:
                c = s[k]
                if q:
                    if c == BS:
                        k += 1
                    elif c == q:
                        q = None
                elif c in '"\'':
                    q = c
                elif c == '(':
                    depth += 1
                elif c == ')':
                    depth -= 1
                elif c == ',' and depth == 1:
                    commas += 1
                k += 1
            args = s[j + len(key):k - 1]
            out.append(('Identifier.fromNamespaceAndPath(' if commas else 'Identifier.parse(') + args + ')')
            n += 1
            i = k
        new = ''.join(out)
        if new != s:
            open(p, 'w', encoding='utf-8', newline='\n').write(new)
print(n)
