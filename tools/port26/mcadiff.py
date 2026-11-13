import re,glob,os,difflib,sys
OLD='C:/Users/user/Documents/minecraft-comes-alive/common/src/main/java/net/mca'
NEW='C:/Users/user/Documents/mca 26.2/mca-fabric-8.1.11+26.2.jar_Decompiler.com/net/conczin/mca'
def find(root,cls):
    for p in glob.glob(root+'/**/'+cls+'.java',recursive=True): return p
def body(src,name):
    out=[]
    for m in re.finditer(r'\n[ \t]*(?:@\w+\s*)*(?:(?:public|private|protected|static|final|synchronized|abstract)\s+)*[\w<>\[\],.? ]+\s'+re.escape(name)+r'\s*\([^)]*\)[^{;]*\{',src):
        i=m.end(); d=1
        while d and i<len(src):
            d+= {'{':1,'}':-1}.get(src[i],0); i+=1
        out.append(src[m.start():i])
    return out
def norm(t):
    t=re.sub(r'//.*','',t); t=re.sub(r'\bthis\.','',t); t=re.sub(r'\(\w+\)','',t)
    t=re.sub(r'var\d+','v',t); t=re.sub(r'\s+',' ',t)
    return t
for line in sys.stdin:
    cls,meths=line.strip().split(' ',1)
    o=find(OLD,cls.split('$')[0]); n=find(NEW,cls.split('$')[0])
    if not o or not n: print(cls,'MISSING',o,n); continue
    so=open(o,encoding='utf-8').read(); sn=open(n,encoding='utf-8').read()
    for m in meths.split(','):
        if m.startswith('<') or m.startswith('lambda'): continue
        bo=body(so,m); bn=body(sn,m)
        if not bo or not bn: print(cls,m,'old' if bo else 'NO-OLD','new' if bn else 'NO-NEW'); continue
        r=difflib.SequenceMatcher(None,norm(' '.join(bo)),norm(' '.join(bn))).ratio()
        print(f'{r:.2f} {cls}.{m} ({len(bo)}/{len(bn)})')
