import re,glob,subprocess
J=r'C:\Users\user\.jdks\jdk-25.0.5+8\bin\javap'
CP=r'C:\Users\user\.gradle\caches\fabric-loom\minecraftMaven\net\minecraft\minecraft-merged-deobf\26.2\minecraft-merged-deobf-26.2.jar;C:\Users\user\Documents\mca 26.2\mca-fabric-8.1.11+26.2.jar'
cache={}
def bodies(cls):
    if cls not in cache:
        out=subprocess.run([J,'-p','-c','-cp',CP,cls],capture_output=True,text=True).stdout
        b={}; cur=None
        for l in out.splitlines():
            m=re.match(r'  \S.*?([\w$<>]+)\((.*)\)[^;]*;$',l)
            if m and not l.startswith('    '):
                n=m.group(1)
                if n==cls.split('.')[-1]: n='<init>'
                cur=n; b.setdefault(cur,'')
            elif cur: b[cur]+=l+'\n'
        cache[cls]=b
    return cache[cls]
for p in glob.glob('src/main/java/**/*.java',recursive=True):
    s=open(p,encoding='utf-8').read()
    if '@Mixin' not in s: continue
    imp={m.group(2):m.group(1)+m.group(2) for m in re.finditer(r'import ([\w.]+\.)(\w+);',s)}
    pkg=re.search(r'package ([\w.]+);',s).group(1)
    m=re.search(r'@Mixin\((?:value\s*=\s*)?\{?([\w.]+)\.class',s)
    if not m: continue
    t=m.group(1); target=imp.get(t.split('.')[0],pkg+'.'+t.split('.')[0])+('$'+'$'.join(t.split('.')[1:]) if '.' in t else '')
    if t.count('.')>1: target=t
    b=bodies(target)
    for am in re.finditer(r'@(Redirect|WrapOperation|ModifyArg|ModifyArgs|Inject|ModifyExpressionValue|WrapWithCondition|ModifyVariable)\((.*?)\)\s*\n\s*(?:@|private|public|protected|static)',s,re.S):
        body=am.group(2)
        mm=re.search(r'method\s*=\s*(\{[^}]*\}|"[^"]*")',body)
        if not mm: continue
        meths=[re.split(r'\(',x)[0] for x in re.findall(r'"([^"]+)"',mm.group(1))]
        for tg in re.findall(r'target\s*=\s*"(L[^"]+)"',body):
            owner,rest=tg[1:].split(';',1)
            name=re.split(r'[(:]',rest)[0]
            pat=name+':'+rest[len(name):] if '(' in rest else name+':'
            found=any(pat in b.get(mt,'') or (mt.endswith('*') and any(pat in v for k,v in b.items() if k.startswith(mt[:-1]))) for mt in meths)
            if not found: print(p.split('mcavie')[-1],meths,tg)
