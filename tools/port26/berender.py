"""Rendus de blocs-entités : MultiBufferSource -> Buffers, render(...) via SimpleBlockEntityRenderer, textes soumis."""
import re, sys

def add_import(s, imp):
    if imp in s:
        return s
    return re.sub(r'^(package [^;]+;\n)', r'\1\n' + imp, s, count=1, flags=re.M)

for p in sys.argv[1:]:
    s = open(p, encoding='utf-8').read()
    o = s
    s = s.replace('import net.minecraft.client.renderer.MultiBufferSource;\n', '')
    s = s.replace('MultiBufferSource consumers', 'Buffers consumers')
    s = s.replace('MultiBufferSource vertexConsumers', 'Buffers vertexConsumers')
    s = re.sub(r'implements BlockEntityRenderer<(\w+)> \{', r'extends SimpleBlockEntityRenderer<\1> {', s)
    s = re.sub(r'(\n\s*)@Override(\s*\n\s*)public void render\((\w+) (\w+), float tickDelta, PoseStack matrices, Buffers consumers, int light, int overlay\)',
               r'\1@Override\2protected void render(\3 \4, float tickDelta, PoseStack matrices, Buffers consumers, int light, int overlay)', s)
    # textes : text.drawInBatch(seq, x, y, color, shadow, matrix, consumers, mode, bg, light)
    s = re.sub(r'(\w+)\.drawInBatch\(([^;]*?), ([^,;]+), ([^,;]+), ([^,;]+), (true|false), [^,;]+, consumers,\s*([^,;]+), ([^,;]+), ([^,;()]+)\);',
               r'consumers.text(\2, \3, \4, \5, \6, \7, \8, \9);', s)
    s = re.sub(r'(\w+)\.drawInBatch8xOutline\(([^;]*?), ([^,;]+), ([^,;]+), ([^,;]+), ([^,;]+), [^,;]+, consumers, ([^,;()]+)\);',
               r'consumers.outlined(\2, \3, \4, \5, \6, \7);', s)
    s = re.sub(r'\n\s*RenderTypeRegistry\.register\([^;]*\);', '', s)
    s = s.replace('import dev.architectury.registry.client.rendering.RenderTypeRegistry;\n', '')
    if s != o:
        s = add_import(s, 'import com.maitreyoshi.mcavie.compat.client.Buffers;')
        if 'SimpleBlockEntityRenderer' in s:
            s = add_import(s, 'import com.maitreyoshi.mcavie.compat.client.SimpleBlockEntityRenderer;')
        open(p, 'w', encoding='utf-8', newline='\n').write(s)
        print('ok', p.split('/')[-1])
