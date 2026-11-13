"""Remplacements globaux sûrs pour 26.2 (avec imports)."""
import os, re, sys

RULES = [
    (r'\bBlockTags\.(SAPLINGS|COAL_ORES|REDSTONE_ORES|LAPIS_ORES|DIAMOND_ORES|EMERALD_ORES)\b', r'BlockItemTags.\1.block()', 'import net.minecraft.tags.BlockItemTags;'),
    (r'\bEntityType\.([A-Z][A-Z_]+)\b', r'EntityTypes.\1', 'import net.minecraft.world.entity.EntityTypes;'),
    (r'\.isClientSide\b(?!\()', '.isClientSide()', None),
    (r'\bMobEffects\.MOVEMENT_SLOWDOWN\b', 'MobEffects.SLOWNESS', None),
    (r'\.getBoolean\(GameRules\.RULE_MOBGRIEFING\)', '.get(GameRules.MOB_GRIEFING)', None),
    (r'\bgetMinBuildHeight\(\)', 'getMinY()', None),
    (r'\bgetMaxBuildHeight\(\)', 'getMaxY()', None),
    (r'getGameProfile\(\)\.getName\(\)', 'getGameProfile().name()', None),
    (r'getGameProfile\(\)\.getId\(\)', 'getGameProfile().id()', None),
    (r'\bisNight\(\)', 'isDarkOutside()', None),
    (r'\bisDay\(\)', 'isBrightOutside()', None),
    (r'\.hasPermissions\(\s*[0-4]\s*\)', '.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)', 'import net.minecraft.server.permissions.Permissions;'),
    (r'\bMemoryModuleTypeMCA\.([A-Z_]+)\.get\(\)', r'MemoryModuleTypeMCA.\1', None),
    (r'([A-Za-z_]\w*(?:\.\w+\(\))*)\.hasResident\(([^()]*(?:\([^()]*\))?)\)', r'\1.getResidentsUUIDs().anyMatch(\2::equals)', None),
    (r'\.withPosition\(((?:[a-z]\w*)(?:\.\w+\(\))?)\)', r'.withPosition(net.minecraft.world.phys.Vec3.atBottomCenterOf(\1))', None),
    (r'NbtUtils\.writeBlockPos\(', 'Nbt.pos(', 'import com.maitreyoshi.mcavie.compat.Nbt;'),
    (r'NbtUtils\.readBlockPos\(', 'Nbt.readPos(', 'import com.maitreyoshi.mcavie.compat.Nbt;'),
    (r'(?:net\.minecraft\.nbt\.)?NbtUtils\.createUUID\(', 'com.maitreyoshi.mcavie.compat.Nbt.uuidTag(', None),
    (r'(?:net\.minecraft\.nbt\.)?NbtUtils\.loadUUID\(', 'com.maitreyoshi.mcavie.compat.Nbt.uuidOf(', None),
    (r'getVillagerData\(\)\.setLevel\(', 'getVillagerData().withLevel(', None),
    (r'getVillagerData\(\)\.getLevel\(\)', 'getVillagerData().level()', None),
    (r'getVillagerData\(\)\.getProfession\(\)', 'getVillagerData().profession().value()', None),
    (r'\.isSolidRender\(\w+, \w+\)', '.isSolidRender()', None),
]

def add_import(s, imp):
    if imp in s:
        return s
    return re.sub(r'^(package [^;]+;\n)', r'\1\n' + imp, s, count=1, flags=re.M)

n = 0
for root, _, fs in os.walk(sys.argv[1]):
    for f in fs:
        if not f.endswith('.java') or f in ('Nbt.java',):
            continue
        p = os.path.join(root, f)
        s = open(p, encoding='utf-8').read()
        o = s
        for pat, rep, imp in RULES:
            s, k = re.subn(pat, rep, s)
            if k:
                n += k
                if imp:
                    s = add_import(s, imp)
        if s != o:
            open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('remplacements:', n)
