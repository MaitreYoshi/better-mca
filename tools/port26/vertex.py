import re, sys

def add_import(s, imp):
    if imp in s:
        return s
    return re.sub(r'^(package [^;]+;\n)', r'\1\n' + imp, s, count=1, flags=re.M)

for p in sys.argv[1:]:
    s = open(p, encoding='utf-8').read()
    o = s
    s = re.sub(r'\.color\(([^(),]+), ([^(),]+), ([^(),]+), ([^(),]+)\)', r'.setColor(\1, \2, \3, \4)', s)
    s = re.sub(r'\.uv\(([^(),]+), ([^(),]+)\)', r'.setUv(\1, \2)', s)
    s = re.sub(r'\.uv2\(([^(),]+)\)', r'.setLight(\1)', s)
    s = re.sub(r'\.overlayCoords\(([^(),]+)\)', r'.setOverlay(\1)', s)
    s = re.sub(r'\.normal\(\w+, ([^(),]+), ([^(),]+), ([^(),]+)\)', r'.setNormal(\1, \2, \3)', s)
    s = s.replace('.endVertex()', '')
    s, k = re.subn(r'\bRenderType\.(text|textBackground|entitySolid|entityCutout|entityCutoutNoCull|entityTranslucent|entityTranslucentCull|textSeeThrough|textIntensity)\(', r'RenderTypes.\1(', s)
    if k:
        s = add_import(s, 'import net.minecraft.client.renderer.rendertype.RenderTypes;')
    s, k = re.subn(r'\bLightTexture\.(FULL_BRIGHT|FULL_SKY|pack|block|sky)\b', r'LightCoordsUtil.\1', s)
    if k:
        s = add_import(s, 'import net.minecraft.util.LightCoordsUtil;')
        s = s.replace('import net.minecraft.client.renderer.LightTexture;\n', '')
    if s != o:
        open(p, 'w', encoding='utf-8', newline='\n').write(s)
        print('ok', p.split('/')[-1])
