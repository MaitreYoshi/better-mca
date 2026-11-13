import re,glob,subprocess
J=r'C:\Users\user\.jdks\jdk-25.0.5+8\bin\javap'
CP=r'C:\Users\user\.gradle\caches\fabric-loom\minecraftMaven\net\minecraft\minecraft-merged-deobf\26.2\minecraft-merged-deobf-26.2.jar;C:\Users\user\Documents\mca 26.2\mca-fabric-8.1.11+26.2.jar'
cache={}
def members(cls):
    if cls not in cache:
        out=subprocess.run([J,'-p','-cp',CP,cls],capture_output=True,text=True).stdout
        f=set();m=set()
        for l in out.splitlines():
            mm=re.search(r'([\w$]+)\(',l)
            if mm: m.add(mm.group(1) if mm.group(1)!=cls.split('.')[-1] else '<init>')
            else:
                fm=re.search(r'\s([\w$]+);$',l)
                if fm: f.add(fm.group(1))
        cache[cls]=(f,m,out)
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
    f,me,out=members(target)
    for am in re.finditer(r'@(Accessor|Invoker)(?:\(\s*(?:value\s*=\s*)?"([^"]+)"[^)]*\))?\s*\n(?:\s*@[^\n]*\n)*\s*(?:static\s+)?[\w<>?,\[\] .]+\s+([\w$]+)\(',s):
        kind,name,meth=am.groups()
        if not name:
            name=re.sub(r'^(mcavie\$)?(get|set|is|invoke|call)','',meth); name=name[0].lower()+name[1:]
        ok=(name in f) if kind=='Accessor' else (name in me)
        if not ok: print(p.split('mcavie')[-1],kind,name)
    for sm in re.finditer(r'@Shadow[^\n]*\n(?:\s*@[^\n]*\n)*\s*((?:public|private|protected|final|static|abstract|\s)+)?([\w<>?,\[\] .]+?)\s+([\w$]+)\s*([;(=])',s):
        name=sm.group(3); ismeth=sm.group(4)=='('
        ok=(name in me) if ismeth else (name in f)
        if not ok: print(p.split('mcavie')[-1],'Shadow',name)
