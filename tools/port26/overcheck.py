import re,glob,subprocess
J=r'C:\Users\user\.jdks\jdk-25.0.5+8\bin\javap'
CP=r'C:\Users\user\.gradle\caches\fabric-loom\minecraftMaven\net\minecraft\minecraft-merged-deobf\26.2\minecraft-merged-deobf-26.2.jar;C:\Users\user\Documents\mca 26.2\mca-fabric-8.1.11+26.2.jar'
cache={}
def chain(cls):
    names={}
    while cls and cls!='java.lang.Object':
        if cls not in cache:
            out=subprocess.run([J,'-p','-cp',CP,cls],capture_output=True,text=True).stdout
            cache[cls]=out
        out=cache[cls]
        for l in out.splitlines():
            m=re.search(r'\s([\w$]+)\(([^)]*)\)',l)
            if m: names.setdefault(m.group(1),set()).add(len([x for x in m.group(2).split(',') if x.strip()]))
        m=re.search(r'class \S+ extends ([\w.$]+)',out)
        cls=m.group(1) if m else None
    return names
for p in glob.glob('src/main/java/**/*.java',recursive=True):
    s=open(p,encoding='utf-8').read()
    imp={m.group(2):m.group(1)+m.group(2) for m in re.finditer(r'import ([\w.]+\.)(\w+);',s)}
    for cm in re.finditer(r'class (\w+)(?:<[^>{]*>)? extends ([\w.]+)',s):
        sup=cm.group(2); full=imp.get(sup.split('.')[0],sup)
        if not full.startswith(('net.minecraft','net.conczin','com.mojang')): continue
        names=chain(full)
        for mm in re.finditer(r'(@Override\s*\n\s*)?(?:public|protected)\s+(?:final\s+)?[\w<>\[\], ?.]+\s+(\w+)\(([^)]*)\)\s*(?:throws [\w, .]+)?\{',s[cm.end():]):
            ov,name,params=mm.groups()
            n=len([x for x in re.split(r',(?![^<]*>)',params) if x.strip()])
            if not ov and name in names and n not in names[name]:
                print(p.split('mcavie')[-1],cm.group(1),name,n,'vs',sorted(names[name]))
