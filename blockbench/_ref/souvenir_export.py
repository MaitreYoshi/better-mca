import json, os, sys, shutil

# python souvenir_export.py <racine> : souvenir.bbmodel -> modèle, textures, blockstate, item, recette, loot de l'autel du souvenir
ROOT = sys.argv[1]
BB = os.path.join(ROOT, "blockbench")
RES = os.path.join(ROOT, "src/main/resources")
ASSETS = os.path.join(RES, "assets/mca_vie")
TEXDIR = os.path.join(ASSETS, "textures/block/ghost_altar")
os.makedirs(TEXDIR, exist_ok=True)
for f in os.listdir(TEXDIR):
    os.remove(os.path.join(TEXDIR, f))

model = json.load(open(os.path.join(BB, "souvenir.bbmodel")))
used = set(f["texture"] for e in model["elements"] for f in e["faces"].values())
keys = {}
for i, t in enumerate(model["textures"]):
    if i in used:
        name = t["name"][len("souvenir_"):-4]
        shutil.copyfile(os.path.join(BB, t["name"]), os.path.join(TEXDIR, name + ".png"))
        keys[i] = ("t%d" % i, "mca_vie:block/ghost_altar/" + name)

elements = []
for e in model["elements"]:
    out = {"from": e["from"], "to": e["to"], "faces": {}}
    for axis, a in zip("xyz", e.get("rotation", [0, 0, 0])):
        if a:
            out["rotation"] = {"angle": a, "axis": axis, "origin": e["origin"]}
    for f, face in e["faces"].items():
        out["faces"][f] = {"uv": face["uv"], "texture": "#" + keys[face["texture"]][0]}
    elements.append(out)
textures = dict(list(keys.values()) + [("particle", "mca_vie:block/ghost_altar/planches")])
json.dump({"parent": "minecraft:block/block", "ambientocclusion": False, "textures": textures, "elements": elements,
           "display": {"gui": {"rotation": [30, 225, 0], "scale": [0.8, 0.8, 0.8]}}},
          open(os.path.join(ASSETS, "models/block/ghost_altar.json"), "w"), indent=1)
json.dump({"parent": "mca_vie:block/ghost_altar"}, open(os.path.join(ASSETS, "models/item/ghost_altar.json"), "w"), indent=2)
rots = {"north": 0, "east": 90, "south": 180, "west": 270}
variants = {}
for f, r in rots.items():
    v = {"model": "mca_vie:block/ghost_altar"}
    if r:
        v["y"] = r
    variants["facing=" + f] = v
json.dump({"variants": variants}, open(os.path.join(ASSETS, "blockstates/ghost_altar.json"), "w"), indent=2)
json.dump({"type": "minecraft:crafting_shaped", "category": "building", "pattern": ["CGC", "PFP"],
           "key": {"C": {"item": "minecraft:candle"}, "G": {"item": "minecraft:gold_nugget"},
                   "P": {"item": "minecraft:dark_oak_planks"}, "F": {"item": "minecraft:item_frame"}},
           "result": {"item": "mca_vie:ghost_altar"}}, open(os.path.join(RES, "data/mca_vie/recipes/ghost_altar.json"), "w"), indent=2)
json.dump({"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "mca_vie:ghost_altar"}],
           "conditions": [{"condition": "minecraft:survives_explosion"}]}]},
          open(os.path.join(RES, "data/mca_vie/loot_tables/blocks/ghost_altar.json"), "w"), indent=2)
print("ok", len(elements))
