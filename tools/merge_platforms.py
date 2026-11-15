"""Fusionne le jar Fabric (variante universal) et le jar Forge de Better MCA en un seul jar.
Usage : python tools/merge_platforms.py fabric_universal.jar forge.jar out.jar
Chaque plateforme est déplacée sous fabric/ ou forge/ (comme le fait MCA)."""
import sys, os, re, zipfile, subprocess, glob, tempfile

here = os.path.dirname(os.path.abspath(__file__))
fab, forge, out = sys.argv[1:4]
g = os.path.expanduser('~/.gradle/caches/modules-2/files-2.1/org.ow2.asm')
cp = ';'.join(glob.glob(f'{g}/{n}/9.9.1/*/{n}-9.9.1.jar')[0] for n in ('asm', 'asm-commons', 'asm-tree'))
tmp = tempfile.mkdtemp()

def reloc(src, prefix):
    dst = os.path.join(tmp, prefix + '.jar')
    subprocess.run(['java', '-cp', cp, os.path.join(here, 'RelocateMca.java'), src, dst, prefix, 'com.maitreyoshi.mcavie'],
                   check=True, stderr=subprocess.DEVNULL)
    return zipfile.ZipFile(dst)

a, b = reloc(fab, 'fabric'), reloc(forge, 'forge')
MIX = re.compile(r'^mca_vie(\.[a-z]+)?\.mixins\.json$')
o = zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED)
seen = set()
def put(name, data):
    if name not in seen:
        seen.add(name); o.writestr(name, data)

for z, plat in ((a, 'fabric'), (b, 'forge')):
    for n in z.namelist():
        if n.endswith('/'): continue
        d = z.read(n)
        if MIX.match(n):
            n = n.replace('mca_vie', f'mca_vie.{plat}', 1)
        elif n == 'fabric.mod.json':
            t = d.decode('utf-8')
            d = re.sub(r'"(mca_vie(?:\.[a-z]+)?)\.mixins\.json"', r'"\1.fabric.mixins.json"', t).encode('utf-8')
            d = d.replace(b'.fabric.mixins', b'.mixins').replace(b'"mca_vie.mixins.json"', b'"mca_vie.fabric.mixins.json"')
            t = d.decode('utf-8')
            t = re.sub(r'"mca_vie\.(tree|treeclient)\.mixins\.json"', r'"mca_vie.fabric.\1.mixins.json"', t)
            d = t.encode('utf-8')
        elif n == 'META-INF/MANIFEST.MF':
            if plat != 'forge': continue
            t = d.decode('utf-8').replace('\r\n ', '')
            t = re.sub(r'mca_vie\.', 'mca_vie.forge.', t.split('MixinConfigs: ')[0]) if False else t
            t = re.sub(r'MixinConfigs: (.*)', lambda m: 'MixinConfigs: ' + ','.join(
                c.replace('mca_vie.', 'mca_vie.forge.', 1) for c in m.group(1).strip().split(',')), t)
            d = t.encode('utf-8')
        put(n, d)
o.close()
print('ok', out)
