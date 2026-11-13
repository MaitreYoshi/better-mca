import json, os, sys, shutil

ROOT = sys.argv[1]
BB = os.path.join(ROOT, "blockbench")
RES = os.path.join(ROOT, "src/main/resources")
ASSETS = os.path.join(RES, "assets/mca_vie")
TEXDIR = os.path.join(ASSETS, "textures/block/memorial")
os.makedirs(TEXDIR, exist_ok=True)
for f in os.listdir(TEXDIR):
    os.remove(os.path.join(TEXDIR, f))

model = json.load(open(os.path.join(BB, "monument.bbmodel")))
used = set(f["texture"] for e in model["elements"] for f in e["faces"].values())
keys = {}
for i, t in enumerate(model["textures"]):
    if i not in used:
        continue
    name = t["name"][len("monument_"):-4]
    shutil.copyfile(os.path.join(BB, t["name"]), os.path.join(TEXDIR, name + ".png"))
    keys[i] = ("t%d" % i, "mca_vie:block/memorial/" + name)

CHUNKS = 3
chunks = [[] for _ in range(CHUNKS)]
for e in model["elements"]:
    y0, y1 = e["from"][1], e["to"][1]
    k = int(y0 // 48)
    if y1 > (k + 1) * 48 + 1e-6:
        raise SystemExit("element crosses chunk: " + e["name"])
    base = 16 * (3 * k + 1)
    out = {"from": [e["from"][0], y0 - base, e["from"][2]], "to": [e["to"][0], y1 - base, e["to"][2]], "faces": {}}
    rot = e.get("rotation", [0, 0, 0])
    for axis, a in zip("xyz", rot):
        if a:
            o = e["origin"]
            out["rotation"] = {"angle": a, "axis": axis, "origin": [o[0], o[1] - base, o[2]]}
    for f, face in e["faces"].items():
        out["faces"][f] = {"uv": face["uv"], "texture": "#" + keys[face["texture"]][0]}
    for c in out["from"] + out["to"]:
        assert -16 <= c <= 32, (e["name"], out)
    chunks[k].append(out)

textures = dict(list(keys.values()) + [("particle", "mca_vie:block/memorial/calcaire")])
for k, elements in enumerate(chunks):
    json.dump({"parent": "minecraft:block/block", "ambientocclusion": False, "textures": textures, "elements": elements},
              open(os.path.join(ASSETS, "models/block/war_memorial_%d.json" % k), "w"), indent=1)
json.dump({"textures": {"particle": "mca_vie:block/memorial/calcaire"}}, open(os.path.join(ASSETS, "models/block/war_memorial_part.json"), "w"), indent=2)

items = []
for k, elements in enumerate(chunks):
    for e in elements:
        items.append(e)
json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "mca_vie:item/war_memorial"}},
          open(os.path.join(ASSETS, "models/item/war_memorial.json"), "w"), indent=2)

rots = {"west": 0, "north": 90, "east": 180, "south": 270}
multipart = []
masters = {1 * 9 + 4: 0, 4 * 9 + 4: 1, 7 * 9 + 4: 2}
for part, k in masters.items():
    for f, r in rots.items():
        apply = {"model": "mca_vie:block/war_memorial_%d" % k}
        if r:
            apply["y"] = r
        multipart.append({"when": {"part": str(part), "facing": f}, "apply": apply})
others = "|".join(str(p) for p in range(72) if p not in masters)
multipart.append({"when": {"part": others}, "apply": {"model": "mca_vie:block/war_memorial_part"}})
json.dump({"multipart": multipart}, open(os.path.join(ASSETS, "blockstates/war_memorial.json"), "w"), indent=2)

json.dump({"type": "minecraft:crafting_shaped", "category": "building", "pattern": ["QGQ", "CNC", "QGQ"],
           "key": {"Q": {"item": "minecraft:chiseled_quartz_block"}, "G": {"item": "minecraft:gold_ingot"},
                   "C": {"item": "minecraft:copper_block"}, "N": {"item": "minecraft:nether_star"}},
           "result": {"item": "mca_vie:war_memorial"}}, open(os.path.join(RES, "data/mca_vie/recipes/war_memorial.json"), "w"), indent=2)
print("ok", [len(c) for c in chunks])

import zlib, struct
def png(px):
    h, w = len(px), len(px[0])
    raw = b"".join(b"\x00" + bytes(c for p in row for c in p) for row in px)
    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b"")
ART = [
    "......LL........",
    ".....LLLL.......",
    "......LL........",
    ".....SSSS.......",
    "......SS........",
    ".....SSSS.......",
    ".....SSSS.......",
    ".....SSSS.......",
    ".....SGSS.......",
    "....SSSSSS......",
    "...KKKKKKKK.....",
    "...KYKKKKYK.....",
    "...KKKKKKKK.....",
    "..BBBBBBBBBB....",
    ".PPPPPPPPPPPP...",
    "................",
]
C = {"L": (236, 230, 214, 255), "S": (222, 214, 196, 255), "G": (214, 172, 58, 255), "K": (150, 140, 120, 255),
     "Y": (24, 25, 29, 255), "B": (190, 180, 160, 255), "P": (140, 136, 128, 255), ".": (0, 0, 0, 0)}
os.makedirs(os.path.join(ASSETS, "textures/item"), exist_ok=True)
open(os.path.join(ASSETS, "textures/item/war_memorial.png"), "wb").write(png([[C[c] for c in row] for row in ART]))
