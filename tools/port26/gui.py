"""Interfaces 26.x : blit avec pipeline, pose 2D, info-bulles, ChunkPos, blend, shift."""
import os, re, sys

def split_args(s, start):
    """Renvoie (liste d'arguments, index après la parenthèse fermante) à partir de l'index après '('."""
    depth = 0; q = None; k = start; args = []; cur = start
    while True:
        c = s[k]
        if q:
            if c == '\\':
                k += 1
            elif c == q:
                q = None
        elif c in '"\'':
            q = c
        elif c in '([{':
            depth += 1
        elif c in ')]}':
            if depth == 0:
                args.append(s[cur:k].strip())
                return args, k + 1
            depth -= 1
        elif c == ',' and depth == 0:
            args.append(s[cur:k].strip())
            cur = k + 1
        k += 1

def fix_blit(s):
    out = []; i = 0
    for m in re.finditer(r'\.blit\(', s):
        if m.start() < i:
            continue
        args, end = split_args(s, m.end())
        if not args or 'RenderPipeline' in args[0] or 'Pipelines' in args[0]:
            continue
        P = 'net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED'
        if len(args) == 11:
            new = [P, args[0], args[1], args[2], args[5], args[6], args[3], args[4], args[7], args[8], args[9], args[10]]
        elif len(args) == 9:
            new = [P] + args
        elif len(args) == 7:
            new = [P] + args + ['256', '256']
        else:
            continue
        out.append(s[i:m.start()] + '.blit(' + ', '.join(new) + ')')
        i = end
    out.append(s[i:])
    return ''.join(out)

def fix_pose(s):
    # pose().translate/scale à trois arguments -> deux (pile 2D)
    def rep(m):
        args, end = split_args(m.string, m.end())
        return None
    out = []; i = 0
    for m in re.finditer(r'\.pose\(\)\.(translate|scale)\(', s):
        if m.start() < i:
            continue
        args, end = split_args(s, m.end())
        if len(args) == 3:
            out.append(s[i:m.start()] + '.pose().' + m.group(1) + '(' + args[0] + ', ' + args[1] + ')')
            i = end
    out.append(s[i:])
    s = ''.join(out)
    s = re.sub(r'\.pose\(\)\.pushPose\(\)', '.pose().pushMatrix()', s)
    s = re.sub(r'\.pose\(\)\.popPose\(\)', '.pose().popMatrix()', s)
    return s

n = 0
for root, _, fs in os.walk(sys.argv[1]):
    for f in fs:
        if not f.endswith('.java'):
            continue
        p = os.path.join(root, f)
        s = open(p, encoding='utf-8').read()
        if 'GuiGraphicsExtractor' not in s:
            continue
        o = s
        s = fix_blit(s)
        s = fix_pose(s)
        s = re.sub(r'(\w+)\.renderTooltip\((\w+), ', r'\1.setTooltipForNextFrame(\2, ', s)
        s = re.sub(r'(\w+)\.renderComponentTooltip\((\w+), ([^;]+?), (\w+), (\w+)\);', r'\1.setTooltipForNextFrame(\2, \3, java.util.Optional.empty(), \4, \5);', s)
        s = re.sub(r'\n\s*RenderSystem\.(enableBlend|disableBlend|defaultBlendFunc)\(\);', '', s)
        s = re.sub(r'(?<![\w.])(Screen\.)?hasShiftDown\(\)', 'net.minecraft.client.Minecraft.getInstance().hasShiftDown()', s)
        if s != o:
            open(p, 'w', encoding='utf-8', newline='\n').write(s)
            n += 1
print('fichiers:', n)
