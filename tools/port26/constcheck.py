import re,glob,subprocess
J=r'C:\Users\user\.jdks\jdk-25.0.5+8\bin\javap'
CP=r'C:\Users\user\.gradle\caches\fabric-loom\minecraftMaven\net\minecraft\minecraft-merged-deobf\26.2\minecraft-merged-deobf-26.2.jar;C:\Users\user\Documents\mca 26.2\mca-fabric-8.1.11+26.2.jar'
cache={}
def pool(cls):
    if cls not in cache:
        out=subprocess.run([J,'-v','-p','-c','-cp',CP,cls],capture_output=True,text=True).stdout
        cache[cls]=out
    return cache[cls]
def mbody(out,name):
    m=re.search(r'\n  [^\n]*\b'+re.escape(name)+r'\([^\n]*\n(.*?)(?=\n  \S|\Z)',out,re.S)
    return m.group(1) if m else ''
for p in glob.glob('src/main/java/**/*.java',recursive=True):
    s=open(p,encoding='utf-8').read()
    if '@Mixin' not in s: continue
    imp={m.group(2):m.group(1)+m.group(2) for m in re.finditer(r'import ([\w.]+\.)(\w+);',s)}
    pkg=re.search(r'package ([\w.]+);',s).group(1)
    m=re.search(r'@Mixin\((?:value\s*=\s*)?\{?([\w.]+)\.class',s)
    if not m: continue
    t=m.group(1); target=imp.get(t.split('.')[0],pkg+'.'+t.split('.')[0])+('$'+'$'.join(t.split('.')[1:]) if '.' in t else '')
    if t.count('.')>1: target=t
    out=pool(target)
    if not (target.startswith('net.conczin') ): 
        pass
    lits=set(re.findall(r'"([A-Za-z0-9_.:/-]+)"',s))
    for l in sorted(lits):
        if l.startswith(('L','net/','mca_vie','mcavie','gui.mca_vie','msg.mca_vie')) or '$' in l or '(' in l: continue
        if re.match(r'^(HEAD|TAIL|RETURN|INVOKE|FIELD|NEW|CONSTANT|JUMP|STORE|LOAD|INVOKE_ASSIGN)$',l): continue
        if l in out: continue
        if re.search(r'method\s*=\s*[^)]*"'+re.escape(l)+'"',s): continue
        print(p.split('mcavie')[-1],'|',target.split('.')[-1],'|',l)
    for cm in re.finditer(r'@ModifyConstant\(method\s*=\s*(\{[^}]*\}|"[^"]*")\s*,\s*constant\s*=\s*@Constant\((\w+)\s*=\s*([^)]+)\)',s):
        for meth in re.findall(r'"([^"(]+)',cm.group(1)):
            body=mbody(out,meth); v=cm.group(3).strip()
            ok = (('bipush        '+v) in body or ('sipush        '+v) in body or ('iconst_'+v) in body or (' '+v+'\n') in body or re.search(r'(int|double|float|long) '+re.escape(v.rstrip('dDfF'))+r'[dfl]?\b',body))
            if not ok: print(p.split('mcavie')[-1],'| CONST',meth,v)
    for sm in re.finditer(r'args\s*=\s*"stringValue=([^"]+)"',s):
        if sm.group(1) not in out: print(p.split('mcavie')[-1],'| SLICE STRING',sm.group(1))
