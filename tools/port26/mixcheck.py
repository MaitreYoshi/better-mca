import re,glob,subprocess,sys,os
J=r'C:\Users\user\.jdks\jdk-25.0.5+8\bin\javap'
CP=r'C:\Users\user\.gradle\caches\fabric-loom\minecraftMaven\net\minecraft\minecraft-merged-deobf\26.2\minecraft-merged-deobf-26.2.jar;C:\Users\user\Documents\mca 26.2\mca-fabric-8.1.11+26.2.jar'
cache={}
def members(cls):
    if cls not in cache:
        r=subprocess.run([J,'-p','-s','-cp',CP,cls],capture_output=True,text=True)
        names=set(); descs=set(); last=None
        for l in r.stdout.splitlines():
            m=re.search(r'([\w$<>]+)\(.*\)',l)
            if m and not l.strip().startswith('descriptor'):
                last=m.group(1); n=last
                if n==cls.split('.')[-1]: n='<init>'
                names.add(n); last=n
            m2=re.match(r'\s+descriptor: (.*)',l)
            if m2 and last: descs.add(last+m2.group(1)); last=None
            m3=re.search(r'\s([\w$]+);$',l)
            if m3 and '(' not in l: names.add(m3.group(1))
        names.add('<clinit>')
        cache[cls]=(r.returncode==0,names,descs)
    return cache[cls]
imps={}
for p in glob.glob('src/main/java/**/mixin/**/*.java',recursive=True):
    s=open(p,encoding='utf-8').read()
    imp={m.group(2):m.group(1)+m.group(2) for m in re.finditer(r'import ([\w.]+\.)(\w+);',s)}
    pkg=re.search(r'package ([\w.]+);',s).group(1)
    m=re.search(r'@Mixin\((?:value\s*=\s*)?\{?([\w.]+)\.class',s)
    tm=re.search(r'@Mixin\(targets\s*=\s*"([^"]+)"',s)
    if tm: target=tm.group(1).replace('/','.')
    elif m:
        t=m.group(1); target=imp.get(t.split('.')[0],pkg+'.'+t.split('.')[0])+('$'+'$'.join(t.split('.')[1:]) if '.' in t else '')
        if t.count('.')>1: target=t
    else: continue
    ok,names,descs=members(target)
    if not ok: print(p,'TARGET?',target); continue
    for mm in re.finditer(r'method\s*=\s*(\{[^}]*\}|"[^"]*")',s):
        for meth in re.findall(r'"([^"]+)"',mm.group(1)):
            n=re.split(r'[(]',meth)[0]
            if n not in names: print(p,'METHOD?',target,meth)
            elif '(' in meth and not any(d==meth or d.startswith(meth) for d in descs): print(p,'DESC?',target,meth)
    for t in re.findall(r'target\s*=\s*"(L[\w/$]+;[^"]+)"',s):
        owner,rest=t[1:].split(';',1)
        n=re.split(r'[(:]',rest)[0]
        ok2,nm,ds=members(owner.replace('/','.'))
        if not ok2: print(p,'OWNER?',t)
        elif n not in nm: print(p,'INVOKE?',t)
        elif '(' in rest and rest not in ds: print(p,'IDESC?',t)
    for t in re.findall(r'@(?:Shadow|Accessor|Invoker)[^\n]*\n\s*(?:@\w+\s*\n\s*)*(?:[\w<>?,\[\] ]+\s)?(\w+)\s*[(;=]',s): pass

def params_of(cls,name):
    r=subprocess.run([J,'-p','-cp',CP,cls],capture_output=True,text=True)
    out=[]
    for l in r.stdout.splitlines():
        m=re.search(r'\s(?:<[^>]*>\s)?[\w.$<>\[\]?, ]*?\b'+re.escape(name)+r'\((.*)\)',l) if name!='<init>' else re.search(re.escape(cls)+r'\((.*)\)',l)
        if m:
            ps=re.sub(r'<[^<>]*>','',re.sub(r'<[^<>]*>','',re.sub(r'<[^<>]*>','',m.group(1))))
            out.append([x.strip().split('.')[-1].replace('$','.') for x in ps.split(',') if x.strip()])
    return out
print('--- signatures @Inject')
for p in glob.glob('src/main/java/**/mixin/**/*.java',recursive=True):
    s=open(p,encoding='utf-8').read()
    imp={m.group(2):m.group(1)+m.group(2) for m in re.finditer(r'import ([\w.]+\.)(\w+);',s)}
    pkg=re.search(r'package ([\w.]+);',s).group(1)
    m=re.search(r'@Mixin\((?:value\s*=\s*)?\{?([\w.]+)\.class',s)
    if not m: continue
    t=m.group(1); target=imp.get(t.split('.')[0],pkg+'.'+t.split('.')[0])+('$'+'$'.join(t.split('.')[1:]) if '.' in t else '')
    for im in re.finditer(r'@Inject\(method\s*=\s*"([^"(]+)(\([^"]*\))?"[^\n]*\)\s*\n(?:\s*@[^\n]*\n)*\s*(?:private|public|protected)?\s*(?:static\s+)?[\w<>?,\[\] .]+\s+[\w$]+\(([^)]*)\)',s):
        name,desc,hp=im.group(1),im.group(2),im.group(3)
        hps=[re.sub(r'@\w+(\([^)]*\))?\s*','',x).strip() for x in re.split(r',(?![^<]*>)',hp) if x.strip()]
        hps=[re.sub(r'<.*>','',x.rsplit(' ',1)[0]).split('.')[-1] for x in hps]
        hps=[x for x in hps if not x.startswith('CallbackInfo')]
        if desc or not hps and True: 
            if desc: continue
        sigs=params_of(target,name)
        if not sigs: continue
        if hps and not any(len(hps)==len(sg) and all(a==b or len(b)<=2 for a,b in zip(hps,sg)) for sg in sigs):
            print(p.split('mcavie')[-1],name,hps,'VS',sigs[:2])
