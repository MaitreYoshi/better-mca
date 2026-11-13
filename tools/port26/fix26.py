"""Corrections guidées par les erreurs javac : applique une règle sur la ligne signalée."""
import re, sys, collections

log = open(sys.argv[1], encoding='utf-8', errors='replace').read().split('\n')
stop = next((i for i, l in enumerate(log) if l.startswith('* What went wrong')), len(log))
log = log[:stop]

head = re.compile(r'^(.*\.java):(\d+): error: (.*)$')
entries = []
for i, l in enumerate(log):
    m = head.match(l)
    if not m:
        continue
    sym = None
    for k in range(i + 1, min(i + 6, len(log))):
        s = re.search(r'symbol: +(method|variable|class) ([A-Za-z_0-9]+)', log[k])
        if s:
            sym = (s.group(1), s.group(2))
            break
        if head.match(log[k]):
            break
    entries.append((m.group(1), int(m.group(2)), m.group(3), sym))

REC = r'([A-Za-z_][\w]*(?:\(\))?(?:\.[A-Za-z_]\w*(?:\([^()]*\))?)*)'

def rec_helper(line, name, helper):
    # x.y().name(args -> Helper.name(x.y(), args
    return re.sub(REC + r'\.' + name + r'\(', lambda m: helper + '.' + name + '(' + m.group(1) + ', ', line)

SIMPLE = {
    'drawString': 'text', 'drawCenteredString': 'centeredText', 'renderOutline': 'outline',
    'renderItem': 'item', 'renderFakeItem': 'fakeItem', 'renderItemDecorations': 'itemDecorations',
    'drawWordWrap': 'textWithWordWrap', 'pushPose': 'pushMatrix', 'popPose': 'popMatrix',
    'getAllKeys': 'keySet', 'serverLevel': 'level', 'location': 'identifier',
    'getDayTime': 'getOverworldClockTime', 'isSameItemSameTags': 'isSameItemSameComponents',
}

def fix(line, kind, name):
    if kind == 'method' and name in SIMPLE:
        return re.sub(r'\b' + name + r'\(', SIMPLE[name] + '(', line)
    if kind == 'method' and name == 'displayClientMessage':
        line = re.sub(r'\.displayClientMessage\((.*),\s*true\)', r'.sendOverlayMessage(\1)', line)
        return re.sub(r'\.displayClientMessage\((.*),\s*false\)', r'.sendSystemMessage(\1)', line)
    if kind == 'method' and name == 'getServer':
        if '.getServer()' in line:
            return line.replace('.getServer()', '.level().getServer()')
        return re.sub(r'(?<![\w.])getServer\(\)', 'level().getServer()', line)
    if kind == 'method' and name in ('getUUID', 'hasUUID', 'putUUID'):
        return rec_helper(line, name, 'Nbt')
    if kind == 'method' and name in ('hasTag', 'getTag'):
        return re.sub(REC + r'\.' + name + r'\(\)', lambda m: 'Nbt.' + ('hasTag' if name == 'hasTag' else 'tag') + '(' + m.group(1) + ')', line)
    if kind == 'method' and name == 'hasPermission':
        return re.sub(r'\.hasPermission\(\s*[0-4]\s*\)', '.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)', line)
    if kind == 'variable' and name == 'screen':
        return re.sub(r'\.screen(?!\()', '.gui.screen()', line)
    if kind == 'method' and name == 'setScreen':
        return re.sub(r'(?<!gui)\.setScreen\(', '.gui.setScreen(', line)
    if kind == 'method' and name == 'getSharedSpawnPos':
        return line.replace('getSharedSpawnPos()', 'getRespawnData().pos()')
    if kind == 'method' and name == 'vertex':
        return re.sub(r'\.vertex\(', '.addVertex(', line)
    if kind == 'method' and name == 'getCenter':
        return re.sub(REC + r'\.getCenter\(\)', r'net.minecraft.world.phys.Vec3.atCenterOf(\1)', line)
    if kind == 'method' and name == 'toLong':
        return re.sub(r'\.toLong\(\)', '.pack()', line)
    if kind == 'method' and name == 'getFrameTime':
        return re.sub(r'(\w+)\.getFrameTime\(\)', r'\1.getDeltaTracker().getGameTimeDeltaPartialTick(false)', line)
    if kind == 'method' and name == 'getAsString':
        return re.sub(r'\.getAsString\(\)', '.asString().orElse("")', line)
    return None

def fix_msg(line, msg):
    if 'random has protected access' in msg:
        return re.sub(r'\.random\b(?!\()', '.getRandom()', line)
    if 'Optional<Reference<' in msg and 'cannot be converted' in msg:
        return re.sub(r'(BuiltInRegistries\.\w+)\.get\(', r'\1.getValue(', line)
    if 'method kill in class' in msg:
        return re.sub(r'(\w+)\.kill\(\)', r'\1.kill((net.minecraft.server.level.ServerLevel) \1.level())', line)
    if 'no suitable method found for sendParticles(ServerPlayer' in msg:
        return re.sub(r'sendParticles\((\w+), ([^,]+), (true|false),', r'sendParticles(\1, \2, \3, false,', line)
    if 'no suitable method found for playSound' in msg and 'Reference<SoundEvent>' in msg:
        return re.sub(r'(SoundEvents\.[A-Z_]+)(?!\.value)', r'\1.value()', line)
    m = re.match(r'(\w+) has private access in ChunkPos', msg)
    if m:
        return re.sub(r'\.' + m.group(1) + r'\b(?!\()', '.' + m.group(1) + '()', line)
    if 'no suitable constructor found for ItemStack(Optional<Reference<Item>>)' in msg:
        return re.sub(r'(BuiltInRegistries\.ITEM)\.get\(', r'\1.getValue(', line)
    if 'ItemStack cannot be converted to ItemCost' in msg:
        return re.sub(r'new MerchantOffer\(new ItemStack\(', 'new MerchantOffer(new net.minecraft.world.item.trading.ItemCost(', line)
    return None

files = collections.defaultdict(list)
for f, ln, msg, sym in entries:
    if sym:
        files[f].append((ln, sym))
    else:
        files[f].append((ln, ('msg', msg)))

changed = 0
needs = collections.defaultdict(set)
for f, items in files.items():
    lines = open(f, encoding='utf-8').read().split('\n')
    for ln, (kind, name) in items:
        old = lines[ln - 1]
        new = fix_msg(old, name) if kind == 'msg' else fix(old, kind, name)
        if new and new != old:
            lines[ln - 1] = new
            changed += 1
            if 'Nbt.' in new:
                needs[f].add('import com.maitreyoshi.mcavie.compat.Nbt;')
            if 'Permissions.' in new:
                needs[f].add('import net.minecraft.server.permissions.Permissions;')
    text = '\n'.join(lines)
    for imp in needs[f]:
        if imp not in text and not f.endswith('Nbt.java'):
            text = re.sub(r'^(package [^;]+;\n)', r'\1\n' + imp, text, count=1, flags=re.M)
    open(f, 'w', encoding='utf-8', newline='\n').write(text)
print('lignes corrigées:', changed)
