"""Sauvegarde 26.x : ValueInput/ValueOutput (via Nbt.in/out), données synchronisées en Builder, getUpdateTag(registres)."""
import os, re, sys

def add_import(s, imp):
    if imp in s:
        return s
    return re.sub(r'^(package [^;]+;\n)', r'\1\n' + imp, s, count=1, flags=re.M)

n = 0
for root, _, fs in os.walk(sys.argv[1]):
    for f in fs:
        if not f.endswith('.java'):
            continue
        p = os.path.join(root, f)
        s = open(p, encoding='utf-8').read()
        o = s
        s = re.sub(r'(protected|public) void saveAdditional\(CompoundTag (\w+)\) \{',
                   r'\1 void saveAdditional(ValueOutput mcavieOutput) {\n        CompoundTag \2 = Nbt.out(mcavieOutput);', s)
        s = re.sub(r'super\.saveAdditional\(\w+\)', 'super.saveAdditional(mcavieOutput)', s)
        s = re.sub(r'public void load\(CompoundTag (\w+)\) \{',
                   r'protected void loadAdditional(ValueInput mcavieInput) {\n        CompoundTag \1 = Nbt.in(mcavieInput);', s)
        s = re.sub(r'super\.load\(\w+\);', 'super.loadAdditional(mcavieInput);', s)
        s = re.sub(r'(protected|public) void addAdditionalSaveData\(CompoundTag (\w+)\) \{',
                   r'\1 void addAdditionalSaveData(ValueOutput mcavieOutput) {\n        CompoundTag \2 = Nbt.out(mcavieOutput);', s)
        s = re.sub(r'super\.addAdditionalSaveData\(\w+\)', 'super.addAdditionalSaveData(mcavieOutput)', s)
        s = re.sub(r'(protected|public) void readAdditionalSaveData\(CompoundTag (\w+)\) \{',
                   r'\1 void readAdditionalSaveData(ValueInput mcavieInput) {\n        CompoundTag \2 = Nbt.in(mcavieInput);', s)
        s = re.sub(r'super\.readAdditionalSaveData\(\w+\)', 'super.readAdditionalSaveData(mcavieInput)', s)
        s = re.sub(r'public CompoundTag getUpdateTag\(\) \{', 'public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {', s)
        if 'getUpdateTag(net.minecraft.core.HolderLookup.Provider registries)' in s:
            s = s.replace('saveWithoutMetadata()', 'saveWithoutMetadata(registries)')
        s = re.sub(r'protected void defineSynchedData\(\) \{', 'protected void defineSynchedData(SynchedEntityData.Builder builder) {', s)
        if 'SynchedEntityData.Builder builder' in s:
            s = s.replace('super.defineSynchedData();', 'super.defineSynchedData(builder);')
            s = re.sub(r'(?:this\.)?entityData\.define\(', 'builder.define(', s)
            s = add_import(s, 'import net.minecraft.network.syncher.SynchedEntityData;')
        if s != o:
            if 'mcavieOutput' in s:
                s = add_import(s, 'import net.minecraft.world.level.storage.ValueOutput;')
            if 'mcavieInput' in s:
                s = add_import(s, 'import net.minecraft.world.level.storage.ValueInput;')
            if 'Nbt.' in s:
                s = add_import(s, 'import com.maitreyoshi.mcavie.compat.Nbt;')
            open(p, 'w', encoding='utf-8', newline='\n').write(s)
            n += 1
print('fichiers:', n)
