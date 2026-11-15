import json, uuid, zlib, struct, base64, random, os, sys, math

ROOT = sys.argv[1]
OUT = os.path.abspath(os.path.join(ROOT, "blockbench"))
for f in os.listdir(OUT):
    if f.startswith("monument_") and f.endswith(".png"):
        os.remove(os.path.join(OUT, f))


def png(px):
    h, w = len(px), len(px[0])
    raw = b"".join(b"\x00" + bytes(c for p in row for c in p) for row in px)
    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b"")


def clamp(v):
    return max(0, min(255, int(v)))


def col(c, n=0):
    return (clamp(c[0] + n), clamp(c[1] + n), clamp(c[2] + n), 255)


def noise_grid(seed, n, cell):
    r = random.Random(seed)
    g = [[r.uniform(-1, 1) for _ in range(n // cell + 2)] for _ in range(n // cell + 2)]
    def f(x, y):
        gx, gy = x / cell, y / cell
        x0, y0 = int(gx), int(gy)
        tx, ty = gx - x0, gy - y0
        tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
        a = g[y0][x0] * (1 - tx) + g[y0][x0 + 1] * tx
        b = g[y0 + 1][x0] * (1 - tx) + g[y0 + 1][x0 + 1] * tx
        return a * (1 - ty) + b * ty
    return f


def marble(n=96):
    r = random.Random(1)
    f1, f2 = noise_grid(2, n, 24), noise_grid(3, n, 8)
    px = []
    for y in range(n):
        row = []
        for x in range(n):
            v = math.sin((x * 0.7 + y * 0.35) / 9.0 + 4 * f1(x, y) + 1.5 * f2(x, y))
            vein = max(0, 1 - abs(v) * 7)
            base = 236 + 6 * f2(x, y) + r.uniform(-3, 3)
            c = (base - 70 * vein, base - 68 * vein, base - 60 * vein - 6)
            row.append(col(c))
        px.append(row)
    return px


def ashlar(n=96, base=(205, 192, 160), course=16, seed=4):
    r = random.Random(seed)
    f = noise_grid(seed, n, 12)
    tint = {}
    px = []
    for y in range(n):
        row = []
        c_i = y // course
        off = (course * 1.5) if c_i % 2 else 0
        for x in range(n):
            b_i = int((x + off) // (course * 2))
            key = (c_i, b_i)
            if key not in tint:
                tint[key] = r.uniform(-12, 12)
            joint = y % course == 0 or (x + off) % (course * 2) < 1
            nval = tint[key] + 10 * f(x, y) + r.uniform(-5, 5)
            if joint:
                row.append(col((base[0] - 60, base[1] - 60, base[2] - 55), r.uniform(-4, 4)))
            else:
                if (y % course == 1) or (x + off) % (course * 2) < 2:
                    nval += 10
                row.append(col(base, nval))
        px.append(row)
    return px


def granite(n=96, base=(92, 94, 100), seed=7):
    r = random.Random(seed)
    f = noise_grid(seed, n, 10)
    px = []
    for y in range(n):
        row = []
        for x in range(n):
            nv = 8 * f(x, y) + r.uniform(-6, 6)
            c = base
            p = r.random()
            if p < 0.08:
                c = (base[0] + 40, base[1] + 40, base[2] + 40)
            elif p < 0.18:
                c = (base[0] - 30, base[1] - 30, base[2] - 28)
            row.append(col(c, nv))
        px.append(row)
    return px


def pavers(n=96):
    r = random.Random(9)
    f = noise_grid(9, n, 12)
    px = []
    tint = {}
    for y in range(n):
        row = []
        for x in range(n):
            k = (x // 24, y // 24)
            if k not in tint:
                tint[k] = r.uniform(-14, 14)
            if x % 24 == 0 or y % 24 == 0:
                row.append(col((92, 88, 80), r.uniform(-5, 5)))
            else:
                row.append(col((150, 146, 138), tint[k] + 10 * f(x, y) + r.uniform(-6, 6)))
        px.append(row)
    return px


def bronze(n=32):
    r = random.Random(11)
    f = noise_grid(12, n, 6)
    px = []
    for y in range(n):
        row = []
        for x in range(n):
            pat = f(x, y)
            if pat > 0.25:
                c = (78, 128, 108)
            elif pat > 0.05:
                c = (98, 112, 82)
            else:
                c = (112, 84, 54)
            row.append(col(c, r.uniform(-10, 10) - 18 * (y / n)))
        px.append(row)
    return px


def gold(n=16):
    r = random.Random(5)
    return [[col((214, 172, 58), r.uniform(-18, 18) + (14 if (x + y) % 7 == 0 else 0)) for x in range(n)] for y in range(n)]


def leaves(n=16):
    r = random.Random(3)
    px = []
    for y in range(n):
        row = []
        for x in range(n):
            if (x + y) % 4 == 0:
                row.append(col((140, 120, 50), r.uniform(-15, 15)))
            else:
                row.append(col((58, 104, 44), r.uniform(-18, 18)))
        px.append(row)
    return px


def flat(c, n=16, var=6, seed=0):
    r = random.Random(seed)
    return [[col(c, r.uniform(-var, var)) for _ in range(n)] for _ in range(n)]


GOLDC = (214, 172, 58, 255)


def plaque_names(n=32):
    px = [[col((22, 24, 28), random.Random(x * 99 + y).uniform(-3, 3)) for x in range(n)] for y in range(n)]
    for i in range(n):
        px[0][i] = px[n - 1][i] = px[i][0] = px[i][n - 1] = GOLDC
    for line in range(0):
        y = 2 + line * 3
        for x in range(3, 5):
            px[y][x] = px[y + 1][x] = (180, 170, 150, 255)
        for x in range(7, 7 + 14 + (line * 5) % 9):
            if x % 5:
                px[y][x] = (214, 190, 120, 255)
    return px


def plaque_title(n=32):
    px = marble(n)
    for y in range(10, 22):
        for x in range(2, 30):
            if y in (10, 21) or x in (2, 29):
                px[y][x] = GOLDC
    for x in range(5, 27):
        if x % 4 != 0:
            px[14][x] = px[15][x] = px[16][x] = (176, 140, 44, 255)
    return px


def cross(n=32):
    px = [[(0, 0, 0, 0)] * n for _ in range(n)]
    for y in range(n):
        for x in range(n):
            arm = (11 <= x <= 20 and 2 <= y <= 29) or (2 <= x <= 29 and 11 <= y <= 20)
            if arm:
                edge = x in (11, 20, 2, 29) or y in (2, 29, 11, 20)
                px[y][x] = col((180, 140, 44)) if edge else col((222, 182, 70), (x - y) % 3 * 6)
            if (x - 15.5) ** 2 + (y - 15.5) ** 2 <= 16:
                px[y][x] = col((40, 70, 160))
    return px


textures = []


def add_tex(name, px):
    data = png(px)
    path = os.path.join(OUT, "monument_" + name + ".png")
    with open(path, "wb") as f:
        f.write(data)
    textures.append({"path": path, "name": "monument_" + name + ".png", "folder": "block", "namespace": "mca_vie",
                     "id": str(len(textures)), "particle": len(textures) == 0, "width": len(px[0]), "height": len(px),
                     "uv_width": 16, "uv_height": 16, "render_mode": "default", "visible": True, "saved": True,
                     "uuid": str(uuid.uuid4()), "source": "data:image/png;base64," + base64.b64encode(data).decode()})
    return len(textures) - 1


def marble(n=96):
    r = random.Random(1)
    f1, f2 = noise_grid(2, n, 32), noise_grid(3, n, 6)
    px = []
    for y in range(n):
        row = []
        for x in range(n):
            v = math.sin((x * 0.5 + y * 0.9) / 7.0 + 2.2 * f1(x, y))
            vein = max(0, 1 - abs(v) * 14) * (0.5 + 0.5 * f1(y, x))
            base = 226 + 4 * f2(x, y) + r.uniform(-2, 2)
            row.append(col((base - 34 * vein, base - 33 * vein, base - 28 * vein - 4)))
        px.append(row)
    return px


def bronze(n=16, face=False):
    r = random.Random(11)
    px = []
    for y in range(n):
        row = []
        for x in range(n):
            n_ = r.uniform(-7, 7)
            if r.random() < 0.06:
                c = (70, 104, 88)
            else:
                c = (96, 70, 44)
            row.append(col(c, n_ + (8 if (x + y) % 9 == 0 else 0)))
        px.append(row)
    if face:
        dark = col((48, 34, 22))
        light = col((132, 100, 62))
        for x in (3, 4, 11, 12):
            px[6][x] = dark
        for x in range(2, 6):
            px[5][x] = light
        for x in range(10, 14):
            px[5][x] = light
        for y in range(6, 10):
            px[y][7] = px[y][8] = light
        for x in range(4, 12):
            px[11][x] = dark
        px[12][4] = px[12][11] = dark
    return px


FONT = {
    "A": ["010", "101", "111", "101", "101"], "U": ["101", "101", "101", "101", "111"], "X": ["101", "101", "010", "101", "101"],
    "E": ["111", "100", "110", "100", "111"], "N": ["101", "111", "111", "111", "101"], "F": ["111", "100", "110", "100", "100"],
    "T": ["111", "010", "010", "010", "010"], "S": ["011", "100", "010", "001", "110"], "D": ["110", "101", "101", "101", "110"],
    "M": ["101", "111", "111", "101", "101"], "O": ["010", "101", "101", "101", "010"], "R": ["110", "101", "110", "101", "101"],
    "P": ["110", "101", "110", "100", "100"], "L": ["100", "100", "100", "100", "111"], "C": ["011", "100", "100", "100", "011"],
    "1": ["010", "110", "010", "010", "111"], "9": ["111", "101", "111", "001", "111"], "4": ["101", "101", "111", "001", "001"],
    "8": ["111", "101", "111", "101", "111"], "-": ["000", "000", "111", "000", "000"], " ": ["000"] * 5}

STONE_C = (222, 214, 196)


def limestone(n=64, seed=21):
    r = random.Random(seed)
    f = noise_grid(seed, n, 8)
    return [[col(STONE_C, 7 * f(x, y) + r.uniform(-4, 4) - (10 if r.random() < 0.03 else 0)) for x in range(n)] for y in range(n)]


def engrave(px, text, cx, y, scale=1):
    w = len(text) * 4 * scale - scale
    x0 = cx - w // 2
    for i, ch in enumerate(text):
        g = FONT[ch]
        for gy in range(5):
            for gx in range(3):
                if g[gy][gx] == "1":
                    for sy in range(scale):
                        for sx in range(scale):
                            X, Y = x0 + (i * 4 + gx) * scale + sx, y + gy * scale + sy
                            px[Y][X] = col((96, 88, 74))
                            if Y + 1 < len(px) and px[Y + 1][X][0] > 150:
                                px[Y + 1][X] = col((238, 232, 218))


def inscription():
    px = limestone(64, 22)
    engrave(px, "AUX ENFANTS", 32, 6)
    engrave(px, "DE", 32, 15)
    for x in range(14, 50):
        px[27][x] = col((180, 170, 150))
    engrave(px, "MORTS", 32, 36)
    engrave(px, "POUR LA FRANCE", 32, 46)
    return px


def date_band():
    px = limestone(64, 23)
    engrave(px, "1914-1918", 32, 24, 1)
    for x in range(2, 62):
        px[20][x] = px[33][x] = col((170, 160, 140))
    return px


def relief():
    n = 64
    px = limestone(n, 24)
    lit, dark = (246, 240, 226), (150, 140, 120)

    def dot(x, y, c):
        if 0 <= x < n and 0 <= y < n:
            px[y][x] = col(c)

    def blob(x, y, rad=1):
        for dy in range(-rad, rad + 1):
            for dx in range(-rad, rad + 1):
                if dx * dx + dy * dy <= rad * rad:
                    dot(x + dx + 1, y + dy + 1, dark)
        for dy in range(-rad, rad + 1):
            for dx in range(-rad, rad + 1):
                if dx * dx + dy * dy <= rad * rad:
                    dot(x + dx, y + dy, lit)

    for y in range(8, 60):
        blob(32, y, 1)
    for x in range(24, 41):
        blob(x, 14, 1)
    blob(32, 9, 2)
    for t in range(0, 50):
        x = int(12 + t * 0.32 + 6 * math.sin(t / 16))
        y = 58 - t
        blob(x, y, 1)
        if t % 3 == 0 and t > 4:
            for k in range(1, 8 - t // 9):
                dot(x - k, y - k // 2, lit)
                dot(x - k + 1, y - k // 2 + 1, dark)
                dot(x + k, y - k // 2, lit)
                dot(x + k + 1, y - k // 2 + 1, dark)
    for t in range(0, 40):
        x = int(50 - t * 0.2 - 3 * math.sin(t / 10))
        y = 58 - t
        blob(x, y, 1)
        if t % 7 == 3:
            blob(x + 4, y - 1, 2)
            blob(x - 4, y - 2, 2)
    for x in range(10, 54):
        blob(x, 58, 1)
    return px


def egg_dart():
    px = limestone(32, 25)
    for x in range(32):
        px[2][x] = px[29][x] = col((170, 160, 140))
        for y in range(8, 24):
            ex = (x % 8) - 3.5
            d = (ex / 3.2) ** 2 + ((y - 15.5) / 7.5) ** 2
            if 0.75 < d <= 1.0:
                px[y][x] = col((140, 130, 112))
            elif d <= 0.75 and ex < 0:
                px[y][x] = col((240, 234, 220))
    return px


def medallion():
    px = [[(0, 0, 0, 0)] * 32 for _ in range(32)]
    for y in range(32):
        for x in range(32):
            d = math.hypot(x - 15.5, y - 15.5)
            if d <= 15:
                if d > 11:
                    leaf = (math.atan2(y - 15.5, x - 15.5) * 6) % 2 < 1
                    px[y][x] = col((236, 230, 214) if leaf else (176, 166, 146))
                else:
                    px[y][x] = col((212, 204, 186))
                    if abs(x - 15.5) < 2 and 6 < y < 26 or abs(y - 15.5) < 2 and 6 < x < 26:
                        px[y][x] = col((244, 238, 224))
    return px


def feathers(n=32):
    px = limestone(n, 26)
    for y in range(n):
        for x in range(n):
            if (y + (x // 6) * 3) % 6 == 0:
                px[y][x] = col((170, 160, 140))
            elif x % 6 == 0:
                px[y][x] = col((190, 182, 164))
    return px


def coq_head(n=16, eye_x=4):
    px = limestone(n, 27)
    for y, x in ((6, eye_x),):
        px[y][x] = col((70, 64, 54))
        px[y][x + 1] = col((70, 64, 54))
    return px


BZ = (112, 82, 50)
BZ_D = (66, 46, 28)
BZ_L = (168, 128, 76)
BZ_P = (84, 112, 92)


def bz(seed, top_light=True, n=16):
    r = random.Random(seed)
    px = []
    for y in range(n):
        row = []
        for x in range(n):
            edge = min(x, y, n - 1 - x, n - 1 - y)
            shade = (10 if top_light else 0) * (1 - y / n) - (14 if edge == 0 else 6 if edge == 1 else 0)
            c = BZ_P if (edge == 0 and r.random() < 0.25) else BZ
            row.append(col(c, shade + r.uniform(-4, 4)))
        px.append(row)
    return px


def paint(px, pts, c):
    for x, y in pts:
        px[y][x] = col(c)


def coat():
    px = bz(31)
    for y in range(2, 15, 3):
        paint(px, [(5, y), (10, y)], BZ_L)
        paint(px, [(5, y + 1), (10, y + 1)], BZ_D)
    for y in range(16):
        paint(px, [(8, y)], BZ_D if y % 4 else BZ)
    for x in range(1, 15):
        paint(px, [(x, 1)], BZ_L)
    return px


def legs():
    px = bz(32)
    for y in range(16):
        for x in range(16):
            if (x + y * 2) % 6 == 0:
                px[y][x] = col(BZ_D, 10)
            elif (x + y * 2) % 6 == 1:
                px[y][x] = col(BZ_L, -10)
    return px


def boots():
    px = bz(33)
    for y in range(16):
        for x in range(16):
            px[y][x] = col(BZ_D, 10 + (6 if y < 3 else 0))
    for x in range(16):
        px[0][x] = col(BZ_L, -20)
    return px


def sleeve():
    px = bz(34)
    for y in range(12, 14):
        for x in range(16):
            px[y][x] = col(BZ_D, 8)
    for y in range(14, 16):
        for x in range(16):
            px[y][x] = col(BZ_L, -8)
    for x in range(16):
        px[3 + x % 2][x] = col(BZ_D, 14)
    return px


def helmet():
    px = bz(35)
    for x in range(16):
        px[15][x] = px[14][x] = col(BZ_D, 6)
        px[1][x] = col(BZ_L)
    for y in range(0, 8):
        px[y][7] = px[y][8] = col(BZ_L, 6)
    return px


def belt():
    px = [[col(BZ_D, 12 + random.Random(x + y * 16).uniform(-4, 4)) for x in range(16)] for y in range(16)]
    for y in range(4, 12):
        for x in range(6, 10):
            px[y][x] = col(BZ_L, 10 if (x in (6, 9) or y in (4, 11)) else -30)
    return px


def face():
    px = bz(36, top_light=False)
    skin = (128, 94, 58)
    for y in range(16):
        for x in range(16):
            px[y][x] = col(skin, random.Random(x * 7 + y).uniform(-4, 4) - (8 if x in (0, 15) else 0))
    paint(px, [(x, 5) for x in (3, 4, 5, 10, 11, 12)], BZ_D)
    paint(px, [(4, 6), (11, 6)], (40, 28, 16))
    paint(px, [(3, 6), (5, 6), (10, 6), (12, 6)], BZ_L)
    paint(px, [(7, y) for y in range(6, 10)] + [(8, y) for y in range(6, 10)], BZ_L)
    paint(px, [(7, 10), (8, 10)], BZ_D)
    paint(px, [(x, 11) for x in range(4, 12)], BZ_D)
    paint(px, [(4, 12), (11, 12), (5, 12), (10, 12)], BZ_D)
    paint(px, [(x, 13) for x in range(6, 10)], BZ_L)
    paint(px, [(x, 15) for x in range(3, 13)], BZ_D)
    return px


def blank_plate(n=32):
    px = limestone(n, 40)
    for i in range(1, n - 1):
        px[1][i] = px[n - 2][i] = px[i][1] = px[i][n - 2] = col((168, 158, 138))
        px[2][i] = px[i][2] = col((240, 234, 220))
    return px


MARBLE = add_tex("marbre", marble())
STONE = add_tex("pierre", ashlar())
GRANITE = add_tex("granit", granite())
PAVE = add_tex("dallage", pavers())
BRONZE = add_tex("bronze", bronze())
GOLD = add_tex("or", gold())
LEAF = add_tex("laurier", leaves())
IRON = add_tex("fer", flat((48, 48, 54), var=8))
CLOTH = add_tex("ruban", flat((44, 72, 168), var=10))
NAMES = add_tex("plaque_noms", plaque_names())
TITLE = add_tex("plaque_titre", plaque_title())
CROSS = add_tex("croix", cross())

FACE = add_tex("visage", face())
LIME = add_tex("calcaire", limestone(96))
DATES = add_tex("dates", blank_plate())
INSCR = add_tex("inscription", blank_plate())
RELIEF = add_tex("relief", relief())
OVOLO = add_tex("oves", egg_dart())
MEDAL = add_tex("medaillon", medallion())
FEATH = add_tex("plumes", feathers())
CHEAD = add_tex("tete_coq_n", coq_head(eye_x=10))
CHEAD_S = add_tex("tete_coq_s", coq_head(eye_x=4))
T_COAT = add_tex("capote", coat())
T_LEGS = add_tex("bandes_molletieres", legs())
T_BOOT = add_tex("brodequins", boots())
T_ARM = add_tex("manches", sleeve())
T_HELM = add_tex("casque", helmet())
T_BELT = add_tex("ceinturon", belt())
T_SKIN = add_tex("peau", bz(37))
BIG = (LIME, MARBLE, STONE, GRANITE, PAVE)
FULL = (T_SKIN, T_COAT, T_LEGS, T_BOOT, T_ARM, T_HELM, T_BELT, NAMES, TITLE, CROSS, FACE, DATES, INSCR, RELIEF, MEDAL, CHEAD, CHEAD_S)
elements = []
groups = {}


def uv(w, h, tex):
    if tex == OVOLO:
        return [0, 4, 16, 12]
    if tex in FULL:
        return [0, 0, 16, 16]
    if tex in BIG:
        return [0, 0, min(16, round(w / 3, 3)), min(16, round(h / 3, 3))]
    return [0, 0, min(16, w), min(16, h)]


def box(group, name, a, b, tex, rot=None, faces_tex=None):
    dx, dy, dz = b[0] - a[0], b[1] - a[1], b[2] - a[2]
    dims = {"north": (dx, dy), "south": (dx, dy), "east": (dz, dy), "west": (dz, dy), "up": (dx, dz), "down": (dx, dz)}
    faces = {}
    for f, (w, h) in dims.items():
        t = (faces_tex or {}).get(f, tex)
        faces[f] = {"uv": uv(w, h, t), "texture": t}
    e = {"name": name, "box_uv": False, "rescale": False, "locked": False, "from": list(a), "to": list(b),
         "autouv": 0, "color": 0, "origin": [(a[0] + b[0]) / 2, (a[1] + b[1]) / 2, (a[2] + b[2]) / 2],
         "rotation": [0, 0, 0], "faces": faces, "type": "cube", "uuid": str(uuid.uuid4())}
    if rot:
        e["origin"], e["rotation"] = rot
    elements.append(e)
    groups.setdefault(group, []).append(e["uuid"])


def sq(group, name, half, y0, y1, tex, cx=8, cz=8):
    box(group, name, (cx - half, y0, cz - half), (cx + half, y1, cz + half), tex)


# dallage, bornes, chaines
box("dallage", "dallage", (-16, 0, -16), (32, 1, 32), PAVE)
for x0 in (-15.5, 28.5):
    for z0 in (-15.5, 28.5):
        box("dallage", "borne", (x0, 1, z0), (x0 + 3, 8, z0 + 3), STONE)
        box("dallage", "borne_tete", (x0 - 0.5, 8, z0 - 0.5), (x0 + 3.5, 9, z0 + 3.5), MARBLE)
        box("dallage", "borne_boule", (x0 + 0.5, 9, z0 + 0.5), (x0 + 2.5, 10.5, z0 + 2.5), MARBLE)
box("dallage", "chaine_arriere", (30.25, 6.5, -13), (30.75, 7, 29), IRON)
box("dallage", "chaine_n", (-13, 6.5, -14.75), (29, 7, -14.25), IRON)
box("dallage", "chaine_s", (-13, 6.5, 30.25), (29, 7, 30.75), IRON)

# marches
box("marches", "marche_1", (-13, 1, -13), (29, 3, 29), STONE)
box("marches", "marche_2", (-10, 3, -10), (26, 5, 26), STONE)

# socle
sq("socle", "plinthe", 15, 5, 8, GRANITE)
sq("socle", "de", 14, 8, 38, STONE)
for cx in (-6, 22):
    for cz in (-6, 22):
        box("socle", "pilastre", (cx - 1.5, 8, cz - 1.5), (cx + 1.5, 38, cz + 1.5), GRANITE)
sq("socle", "corniche_1", 16, 38, 40, GRANITE)
sq("socle", "corniche_2", 14.5, 40, 42, MARBLE)
for z0, z1 in ((-6.25, -6), (22, 22.25)):
    box("plaques", "cadre", (-3.5, 10, z0), (19.5, 36.5, z1), GOLD)
for z0, z1 in ((-6.5, -6.25), (22.25, 22.5)):
    box("plaques", "noms", (-3, 10.5, z0), (19, 36, z1), NAMES)

# obelisque sculpte
box("fut", "bandeau_dates", (-3, 42, -3), (19, 48, 19), LIME, faces_tex={"west": DATES})
sq("fut", "bandeau_haut", 11.5, 48, 49, LIME)
half, y = 8.0, 49.0
while y < 86:
    sq("fut", "fut", half, y, min(86, y + 4.75), LIME)
    y += 4.75
    half -= 0.25
box("fut", "relief_palme_epee", (-0.35, 52, 2), (-0.25, 70, 14), RELIEF)
box("fut", "inscription", (1.15, 73, 2.5), (1.25, 84, 13.5), INSCR)
sq("fut", "chapiteau_bas", 7.5, 86, 87, LIME)
box("fut", "chapiteau", (0, 87, 0), (16, 90, 16), LIME,
    faces_tex={f: OVOLO for f in ("north", "south", "east", "west")})
sq("fut", "chapiteau_haut", 8.5, 90, 91, LIME)
box("fut", "medaillon", (-0.6, 86.5, 5), (-0.1, 92, 11), MEDAL)
sq("fut", "de_haut", 6.5, 91, 96, LIME)
sq("fut", "de_haut_2", 6.25, 96, 99, LIME)
sq("fut", "de_haut_corniche", 7, 99, 100, LIME)

# coq gaulois
C = "coq"
sq(C, "socle_coq", 4.5, 100, 101.5, LIME)
box(C, "socle_coq_45", (3.5, 100, 3.5), (12.5, 101.5, 12.5), LIME, rot=([8, 100.75, 8], [0, 45, 0]))
box(C, "patte_g", (7, 101.5, 6.3), (8, 103.5, 7.3), LIME)
box(C, "patte_d", (7, 101.5, 8.7), (8, 103.5, 9.7), LIME)
box(C, "corps", (4.5, 103, 5), (12, 109, 11), LIME, faces_tex={"north": FEATH, "south": FEATH})
box(C, "poitrail", (3.2, 104.5, 5.5), (6, 110.5, 10.5), LIME)
box(C, "cou", (3.5, 109.5, 6.5), (6.5, 113.5, 9.5), FEATH)
box(C, "tete", (2.6, 113, 6.8), (6, 116, 9.2), LIME, faces_tex={"north": CHEAD, "south": CHEAD_S})
box(C, "bec", (1.2, 114, 7.5), (2.6, 114.8, 8.5), LIME)
box(C, "crete", (3, 116, 7.6), (5.8, 117.6, 8.4), LIME)
box(C, "barbillon", (2.8, 112, 7.6), (3.6, 113.2, 8.4), LIME)
box(C, "queue_1", (11, 107, 6.8), (13, 115, 9.2), FEATH, rot=([11.5, 107, 8], [0, 0, -22.5]))
box(C, "queue_2", (12.5, 108, 7.2), (14.5, 116, 8.8), FEATH, rot=([12.5, 108, 8], [0, 0, -45]))
for side in (-1, 1):
    def zz(a, b):
        return (8 + side * a, 8 + side * b) if side > 0 else (8 + side * b, 8 + side * a)
    z0, z1 = zz(2.5, 4.5)
    box(C, "aile", (5.5, 105, z0), (11, 114, z1), FEATH, rot=([8, 105, 8 + side * 3], [side * 22.5, 0, 0]))
    z0, z1 = zz(5.5, 7.5)
    box(C, "aile_haut", (6, 110, z0), (11.5, 119, z1), FEATH, rot=([8, 110, 8 + side * 6], [side * 45, 0, 0]))

# poilu en bronze, face ouest, proportions de joueur
S = "statue"
box(S, "socle_statue", (-15.5, 1, 2), (-8, 8, 14), GRANITE)
box(S, "socle_statue_haut", (-16, 8, 1.5), (-7.5, 9, 14.5), MARBLE)
box(S, "jambe_g", (-13.5, 9, 5), (-10.5, 18, 8), T_LEGS)
box(S, "jambe_d", (-13.5, 9, 8), (-10.5, 18, 11), T_LEGS)
box(S, "brodequin_g", (-14, 9, 4.8), (-10.5, 10.5, 8), T_BOOT)
box(S, "brodequin_d", (-14, 9, 8), (-10.5, 10.5, 11.2), T_BOOT)
box(S, "pans_capote", (-13.8, 14, 4.7), (-10.2, 18.5, 11.3), T_COAT)
box(S, "corps", (-13.5, 18, 5), (-10.5, 27, 11), T_COAT)
box(S, "ceinturon", (-13.8, 19, 4.8), (-10.2, 20, 11.2), T_BELT)
box(S, "cartouchiere_g", (-14.1, 19.5, 5.5), (-13.6, 21.5, 7.5), T_BELT)
box(S, "cartouchiere_d", (-14.1, 19.5, 8.5), (-13.6, 21.5, 10.5), T_BELT)
box(S, "musette", (-10.5, 18.5, 5.5), (-9.3, 25.5, 10.5), T_COAT)
box(S, "bras_g", (-13.5, 18, 2), (-10.5, 27, 5), T_ARM)
box(S, "bras_d", (-13.5, 18, 11), (-10.5, 27, 14), T_ARM)
box(S, "fusil", (-14.6, 9.2, 14), (-13.6, 33, 14.8), IRON)
box(S, "baionnette", (-14.4, 33, 14.1), (-13.8, 36.5, 14.7), IRON)
box(S, "tete", (-15, 27, 5), (-9, 33, 11), T_SKIN, faces_tex={"west": FACE})
box(S, "casque", (-15.4, 31.5, 4.6), (-8.6, 33.6, 11.4), T_HELM)
box(S, "cimier", (-13, 33.6, 7.4), (-11, 34.4, 8.6), T_HELM)
box(S, "visiere", (-16, 31.3, 5), (-15.4, 31.8, 11), T_HELM)
box(S, "couvre_nuque", (-8.6, 31, 4.8), (-8.1, 31.8, 11.2), T_HELM)

outliner = [{"name": g, "origin": [8, 8, 8], "color": 0, "uuid": str(uuid.uuid4()), "export": True,
             "mirror_uv": False, "isOpen": False, "locked": False, "visibility": True, "autouv": 0, "children": c}
            for g, c in groups.items()]

model = {"meta": {"format_version": "4.5", "model_format": "java_block", "box_uv": False}, "name": "monument_aux_morts",
         "parent": "", "ambientocclusion": True, "front_gui_light": False, "visible_box": [3, 8, 3],
         "resolution": {"width": 16, "height": 16}, "elements": elements, "outliner": outliner, "textures": textures}
with open(os.path.join(OUT, "monument.bbmodel"), "w") as f:
    json.dump(model, f)
print(len(elements), "elements")
