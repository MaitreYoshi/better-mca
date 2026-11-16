"""Fusionne le jar Fabric et le jar NeoForge de Better MCA 26.2 en un seul jar.
Usage : python tools/merge_loaders.py fabric.jar neoforge.jar sortie.jar
Pas de relocalisation : MCA 8.1.11 garde le meme package net.conczin.mca sur les deux loaders
(classes communes identiques, classes de loader dans .fabric / .neoforge) et nos classes de loader
sont deja dans com.maitreyoshi.mcavie.fabric / .neoforge. Union des deux jars ; echoue si un fichier
commun differe (hors MANIFEST)."""
import sys, zipfile

fab, neo, out = sys.argv[1:4]
a, b = zipfile.ZipFile(fab), zipfile.ZipFile(neo)
A = {i.filename: i for i in a.infolist() if not i.is_dir()}
B = {i.filename: i for i in b.infolist() if not i.is_dir()}
bad = [k for k in A.keys() & B.keys() if A[k].CRC != B[k].CRC and k != 'META-INF/MANIFEST.MF']
if bad:
    sys.exit('fichiers communs differents : %s' % bad)
with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as o:
    for k in sorted(A.keys() | B.keys()):
        o.writestr(k, (a if k in A else b).read(k))
for need in ('fabric.mod.json', 'META-INF/neoforge.mods.toml'):
    assert need in A.keys() | B.keys(), need
print('ok', out, len(A.keys() | B.keys()), 'fichiers')
