import json, uuid, zlib, struct, base64, os, sys, math, random

# python souvenir.py <racine> <dossier textures vanilla (assets/minecraft/textures/block)>
ROOT, VAN = sys.argv[1], sys.argv[2]
OUT = os.path.abspath(os.path.join(ROOT, "blockbench"))


def png(px):
    h, w = len(px), len(px[0])
    raw = b"".join(b"\x00" + bytes(c for p in row for c in p) for row in px)
    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b"")


def clamp(v):
    return max(0, min(255, int(v)))


textures = []


def add(name, data, w=16, h=16):
    path = os.path.join(OUT, "souvenir_" + name + ".png")
    with open(path, "wb") as f:
        f.write(data)
    textures.append({"path": path, "name": "souvenir_" + name + ".png", "folder": "block", "namespace": "mca_vie",
                     "id": str(len(textures)), "particle": len(textures) == 0, "width": w, "height": h,
                     "uv_width": 16, "uv_height": 16, "render_mode": "default", "visible": True, "saved": True,
                     "uuid": str(uuid.uuid4()), "source": "data:image/png;base64," + base64.b64encode(data).decode()})
    return len(textures) - 1


def vanilla(name, file):
    return add(name, open(os.path.join(VAN, file), "rb").read())


def portrait():
    # photo sepia ovale : silhouette d'un villageois, fond pâli (remplacée en jeu par le visage MCA du défunt)
    r = random.Random(5)
    px = []
    for y in range(16):
        row = []
        for x in range(16):
            dx, dy = (x - 7.5) / 7.5, (y - 7.5) / 7.5
            inside = dx * dx + dy * dy <= 1
            head = ((x - 7.5) / 3.2) ** 2 + ((y - 6) / 3.6) ** 2 <= 1
            body = y >= 10 and abs(x - 7.5) <= 2 + (y - 10) * 1.3
            if not inside:
                row.append((0, 0, 0, 0))
                continue
            v = 196 - 40 * (dx * dx + dy * dy) + r.uniform(-6, 6)
            if head or body:
                v = 92 + r.uniform(-6, 6) - (8 if body else 0)
            row.append((clamp(v * 1.0), clamp(v * 0.86), clamp(v * 0.66), 255))
        px.append(row)
    return png(px)


def flame():
    # flamme pâle, bleutée la nuit (les morts sont dehors)
    px = []
    for y in range(16):
        row = []
        for x in range(16):
            t = y / 15
            w = 3.5 * math.sin(math.pi * min(1, (y + 1) / 13)) + 0.5
            d = abs(x - 7.5)
            if d > w:
                row.append((0, 0, 0, 0))
                continue
            core = 1 - d / (w + 0.01)
            row.append((clamp(150 + 105 * core), clamp(200 + 55 * core), 255, 255) if t > 0.25 else (235, 245, 255, 255))
        px.append(row)
    return png(px)


def crepe():
    r = random.Random(9)
    px = [[(clamp(22 + r.uniform(-6, 6) + (10 if x % 3 == 0 else 0)),) * 3 + (255,) for x in range(16)] for _ in range(16)]
    return png(px)


def immortelles():
    # bouquet d'immortelles jaunes et blanches (sprite croisé)
    r = random.Random(3)
    px = [[(0, 0, 0, 0)] * 16 for _ in range(16)]
    for y in range(6, 16):
        for x in (6, 8, 10):
            if r.random() < 0.85:
                px[y][x + (1 if y < 9 and x == 10 else 0)] = (70, 110, 50, 255)
    for cx, cy, c in ((5, 4, (232, 196, 60, 255)), (8, 2, (246, 236, 210, 255)), (11, 4, (232, 196, 60, 255)), (7, 6, (220, 170, 40, 255)), (10, 7, (246, 236, 210, 255))):
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                if abs(dx) + abs(dy) < 2:
                    px[cy + dy][cx + dx] = c
    return png(px)


PLANK = vanilla("planches", "dark_oak_planks.png")
LOG = vanilla("chene_noir", "stripped_dark_oak_log.png")
GOLD = vanilla("dorure", "gold_block.png")
CANDLE = vanilla("bougie", "white_candle.png")
POT = vanilla("pot", "flower_pot.png")
PHOTO = add("portrait", portrait())
FLAME = add("flamme", flame())

FLOWERS = add("immortelles", immortelles())

elements = []
groups = {}


def box(group, name, a, b, tex, uvs=None, rot=None, faces=None):
    dx, dy, dz = b[0] - a[0], b[1] - a[1], b[2] - a[2]
    dims = {"north": (dx, dy), "south": (dx, dy), "east": (dz, dy), "west": (dz, dy), "up": (dx, dz), "down": (dx, dz)}
    fs = {}
    for f, (w, h) in dims.items():
        if faces is not None and f not in faces:
            continue
        u = (uvs or {}).get(f) or (uvs or {}).get("all") or [0, 0, min(16, max(w, 0.01)), min(16, max(h, 0.01))]
        fs[f] = {"uv": u, "texture": tex}
    e = {"name": name, "box_uv": False, "rescale": False, "locked": False, "from": list(a), "to": list(b),
         "autouv": 0, "color": 0, "origin": [(a[0] + b[0]) / 2, (a[1] + b[1]) / 2, (a[2] + b[2]) / 2],
         "rotation": [0, 0, 0], "faces": fs, "type": "cube", "uuid": str(uuid.uuid4())}
    if rot:
        e["origin"], e["rotation"] = rot
    elements.append(e)
    groups.setdefault(group, []).append(e["uuid"])


# Face avant vers le nord (z = 0), dos contre le mur (z = 16).
# panneau de fond en chêne noir, coiffé d'un petit fronton
box("panneau", "fond", (3, 2, 15), (13, 14, 16), PLANK, uvs={"north": [3, 2, 13, 14], "south": [3, 2, 13, 14]})
box("panneau", "fronton", (4, 14, 15), (12, 15, 16), PLANK)
box("panneau", "fronton_haut", (6, 15, 15), (10, 16, 16), PLANK)
box("panneau", "moulure_g", (2.5, 2, 14.5), (3.5, 14, 16), LOG, uvs={"all": [0, 0, 1, 12]})
box("panneau", "moulure_d", (12.5, 2, 14.5), (13.5, 14, 16), LOG, uvs={"all": [0, 0, 1, 12]})

# console et ses deux consoles-équerres
box("console", "tablette", (2, 4, 10), (14, 5, 16), LOG, uvs={"up": [2, 0, 14, 6], "down": [2, 0, 14, 6], "north": [2, 4, 14, 5]})
for x in (4, 11):
    box("console", "equerre", (x, 1.5, 12.5), (x + 1, 4, 16), PLANK, uvs={"all": [x, 0, x + 1, 3]})
    box("console", "equerre_biais", (x, 2.5, 11.5), (x + 1, 3.5, 15), PLANK, rot=([x + 0.5, 3, 13.25], [-45, 0, 0]))

# cadre doré et photo du défunt
box("cadre", "cadre_haut", (5, 12.5, 14), (11, 13.5, 15), GOLD)
box("cadre", "cadre_bas", (5, 6, 14), (11, 7, 15), GOLD)
box("cadre", "cadre_g", (5, 7, 14), (6, 12.5, 15), GOLD)
box("cadre", "cadre_d", (10, 7, 14), (11, 12.5, 15), GOLD)
box("cadre", "photo", (6, 7, 14.5), (10, 12.5, 14.5), PHOTO, uvs={"north": [0, 0, 16, 16]}, faces=["north"])

# petite croix au-dessus
box("croix", "croix_mat", (7.5, 13.5, 14.5), (8.5, 16, 15), LOG, uvs={"all": [0, 0, 1, 2.5]})
box("croix", "croix_bras", (6.75, 14.75, 14.5), (9.25, 15.5, 15), LOG, uvs={"all": [0, 0, 2.5, 0.75]})

# deux bougies sur la tablette
for x in (2.75, 11.75):
    box("bougies", "bougie", (x, 5, 11.5), (x + 1.5, 9, 13), CANDLE,
        uvs={"north": [0, 8, 2, 14], "south": [0, 8, 2, 14], "east": [0, 8, 2, 14], "west": [0, 8, 2, 14], "up": [0, 6, 2, 8], "down": [0, 14, 2, 16]})
    for a in (45, -45):
        box("bougies", "flamme", (x + 0.25, 9, 12.25), (x + 1.25, 10.5, 12.25), FLAME,
            uvs={"north": [0, 0, 16, 16], "south": [0, 0, 16, 16]}, rot=([x + 0.75, 9, 12.25], [0, a, 0]), faces=["north", "south"])

# petit vase d'immortelles au centre
box("vase", "vase", (7, 5, 11), (9, 7, 13), POT, uvs={"all": [5, 10, 7, 12], "up": [5, 5, 7, 7]})
for a in (45, -45):
    box("vase", "immortelles", (6.5, 6.5, 12), (9.5, 9.5, 12), FLOWERS, uvs={"north": [0, 0, 16, 16], "south": [0, 0, 16, 16]},
        rot=([8, 6.5, 12], [0, a, 0]), faces=["north", "south"])

outliner = [{"name": g, "origin": [8, 8, 8], "color": 0, "uuid": str(uuid.uuid4()), "export": True,
             "mirror_uv": False, "isOpen": False, "locked": False, "visibility": True, "autouv": 0, "children": c}
            for g, c in groups.items()]
model = {"meta": {"format_version": "4.5", "model_format": "java_block", "box_uv": False}, "name": "autel_du_souvenir",
         "parent": "", "ambientocclusion": False, "front_gui_light": False, "visible_box": [1, 1, 0],
         "resolution": {"width": 16, "height": 16}, "elements": elements, "outliner": outliner, "textures": textures}
json.dump(model, open(os.path.join(OUT, "souvenir.bbmodel"), "w"))
print(len(elements), "elements")
