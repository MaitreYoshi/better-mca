"""Écrans 26.x : render -> extractRenderState, événements souris/clavier en objets."""
import os, re, sys

IMPORTS = {
    'MouseButtonEvent': 'import net.minecraft.client.input.MouseButtonEvent;',
    'KeyEvent': 'import net.minecraft.client.input.KeyEvent;',
    'CharacterEvent': 'import net.minecraft.client.input.CharacterEvent;',
}
n = 0
for root, _, fs in os.walk(sys.argv[1]):
    for f in fs:
        if not f.endswith('.java'):
            continue
        p = os.path.join(root, f)
        s = open(p, encoding='utf-8').read()
        o = s
        s = re.sub(r'void render\(GuiGraphicsExtractor (\w+), int (\w+), int (\w+), float (\w+)\)',
                   r'void extractRenderState(GuiGraphicsExtractor \1, int \2, int \3, float \4)', s)
        s = s.replace('super.render(', 'super.extractRenderState(')
        # renderBackground(ctx) -> extractBackground(ctx, mx, my, d) en reprenant les noms de la méthode englobante
        def bg(m):
            before = s[:m.start()]
            sig = list(re.finditer(r'extractRenderState\(GuiGraphicsExtractor \w+, int (\w+), int (\w+), float (\w+)\)', before))
            if not sig:
                return m.group(0)
            a, b, c = sig[-1].groups()
            return 'extractBackground(' + m.group(1) + ', ' + a + ', ' + b + ', ' + c + ')'
        s = re.sub(r'(?<![\w.])renderBackground\((\w+)\)', bg, s)

        def click(m):
            mod, name, x, y, b = m.groups()
            extra = ', boolean doubleClick' if name == 'mouseClicked' else ''
            return (mod + ' boolean ' + name + '(MouseButtonEvent event' + extra + ') {\n'
                    + '        double ' + x + ' = event.x();\n        double ' + y + ' = event.y();\n        int ' + b + ' = event.button();')
        s = re.sub(r'(public|protected) boolean (mouseClicked|mouseReleased)\(double (\w+), double (\w+), int (\w+)\) \{', click, s)
        s = re.sub(r'super\.mouseClicked\(\w+, \w+, \w+\)', 'super.mouseClicked(event, doubleClick)', s)
        s = re.sub(r'super\.mouseReleased\(\w+, \w+, \w+\)', 'super.mouseReleased(event)', s)

        def drag(m):
            mod, x, y, b, dx, dy = m.groups()
            return (mod + ' boolean mouseDragged(MouseButtonEvent event, double ' + dx + ', double ' + dy + ') {\n'
                    + '        double ' + x + ' = event.x();\n        double ' + y + ' = event.y();\n        int ' + b + ' = event.button();')
        s = re.sub(r'(public|protected) boolean mouseDragged\(double (\w+), double (\w+), int (\w+), double (\w+), double (\w+)\) \{', drag, s)
        s = re.sub(r'super\.mouseDragged\(\w+, \w+, \w+, (\w+), (\w+)\)', r'super.mouseDragged(event, \1, \2)', s)

        s = re.sub(r'boolean mouseScrolled\(double (\w+), double (\w+), double (\w+)\)', r'boolean mouseScrolled(double \1, double \2, double scrollX, double \3)', s)
        s = re.sub(r'super\.mouseScrolled\((\w+), (\w+), (\w+)\)', r'super.mouseScrolled(\1, \2, scrollX, \3)', s)

        def key(m):
            mod, k, sc, md = m.groups()
            return (mod + ' boolean keyPressed(KeyEvent event) {\n        int ' + k + ' = event.key();\n        int ' + sc
                    + ' = event.scancode();\n        int ' + md + ' = event.modifiers();')
        s = re.sub(r'(public|protected) boolean keyPressed\(int (\w+), int (\w+), int (\w+)\) \{', key, s)
        s = re.sub(r'super\.keyPressed\(\w+, \w+, \w+\)', 'super.keyPressed(event)', s)

        def char(m):
            mod, c, md = m.groups()
            return mod + ' boolean charTyped(CharacterEvent event) {\n        char ' + c + ' = (char) event.codepoint();\n        int ' + md + ' = 0;'
        s = re.sub(r'(public|protected) boolean charTyped\(char (\w+), int (\w+)\) \{', char, s)
        s = re.sub(r'super\.charTyped\(\w+, \w+\)', 'super.charTyped(event)', s)

        if s != o:
            for cls, imp in IMPORTS.items():
                if cls + ' event' in s and imp not in s:
                    s = re.sub(r'^(package [^;]+;\n)', r'\1\n' + imp, s, count=1, flags=re.M)
            open(p, 'w', encoding='utf-8', newline='\n').write(s)
            n += 1
print('fichiers:', n)
