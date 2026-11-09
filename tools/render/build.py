"""Builds a pinball table as a Blender scene from the game's export (<id>.json + <id>-print.png) and renders it.
Usage: python build.py <dir> <id> <out.png> [samples] [width] [height]"""
import bpy, bmesh, json, math, sys, os, time
from mathutils import Vector, Matrix

D, ID, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
SAMPLES = int(sys.argv[4]) if len(sys.argv) > 4 else 64
RW = int(sys.argv[5]) if len(sys.argv) > 5 else 900
RH = int(sys.argv[6]) if len(sys.argv) > 6 else 1000
data = json.load(open(os.path.join(D, ID + ".json")))
W, L = data["width"], data["length"]
S = 0.0022  # metres per table unit
PAL = data["palette"]

bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
col = scene.collection


def P(x, y, h=0.0):
    return Vector((x * S, (L - y) * S, h * S))


def rgb(hexs, a=1.0):
    h = hexs.lstrip("#")
    c = [int(h[i:i + 2], 16) / 255 for i in (0, 2, 4)]
    c = [v / 12.92 if v <= 0.04045 else ((v + 0.055) / 1.055) ** 2.4 for v in c]
    return (c[0], c[1], c[2], a)


_mats = {}
CAT = "static"
groups = {}


def reg(o):
    groups.setdefault(CAT, []).append(o)
    return o


def mat(name, color=(0.8, 0.8, 0.8, 1), metal=0.0, rough=0.4, coat=0.0, trans=0.0, emit=None, estr=0.0, ior=1.45, alpha=1.0, spec=0.5):
    key = (name, tuple(color), metal, rough, coat, trans, tuple(emit) if emit else None, estr, alpha)
    if key in _mats:
        return _mats[key]
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    b = m.node_tree.nodes["Principled BSDF"]
    b.inputs["Base Color"].default_value = color
    b.inputs["Metallic"].default_value = metal
    b.inputs["Roughness"].default_value = rough
    b.inputs["Coat Weight"].default_value = coat
    b.inputs["Coat Roughness"].default_value = 0.03
    b.inputs["Transmission Weight"].default_value = trans
    b.inputs["IOR"].default_value = ior
    b.inputs["Alpha"].default_value = alpha
    if emit:
        b.inputs["Emission Color"].default_value = emit
        b.inputs["Emission Strength"].default_value = estr
    _mats[key] = m
    return m


def obj(name, me, m):
    o = bpy.data.objects.new(name, me)
    col.objects.link(o)
    if m:
        o.data.materials.append(m)
    return reg(o)


def smooth(o):
    for p in o.data.polygons:
        p.use_smooth = True


def cyl(name, x, y, r, h0, h1, m, seg=32, r2=None):
    bm = bmesh.new()
    bmesh.ops.create_cone(bm, cap_ends=True, segments=seg, radius1=r * S, radius2=(r2 if r2 is not None else r) * S, depth=(h1 - h0) * S)
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    o = obj(name, me, m)
    o.location = P(x, y, (h0 + h1) / 2)
    smooth(o)
    return o


def torus(name, x, y, h, R, r, m):
    bm = bmesh.new()
    me = bpy.data.meshes.new(name)
    segs, ring = 32, 12
    verts = []
    for i in range(segs):
        a = 2 * math.pi * i / segs
        row = []
        for j in range(ring):
            b = 2 * math.pi * j / ring
            rr = R + r * math.cos(b)
            row.append(bm.verts.new((rr * math.cos(a) * S, rr * math.sin(a) * S, r * math.sin(b) * S)))
        verts.append(row)
    for i in range(segs):
        for j in range(ring):
            bm.faces.new((verts[i][j], verts[(i + 1) % segs][j], verts[(i + 1) % segs][(j + 1) % ring], verts[i][(j + 1) % ring]))
    bm.to_mesh(me)
    o = obj(name, me, m)
    o.location = P(x, y, h)
    smooth(o)
    return o


def prism(name, pts, h0, h1, m, bevel=0.0):
    """A vertical extrusion of a 2D polygon (table units)."""
    bm = bmesh.new()
    vb = [bm.verts.new(P(x, y, h0)) for x, y in pts]
    vt = [bm.verts.new(P(x, y, h1)) for x, y in pts]
    n = len(pts)
    try:
        bm.faces.new(vb[::-1])
        bm.faces.new(vt)
    except ValueError:
        pass
    for i in range(n):
        j = (i + 1) % n
        bm.faces.new((vb[i], vb[j], vt[j], vt[i]))
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    o = obj(name, me, m)
    if bevel > 0:
        md = o.modifiers.new("b", "BEVEL")
        md.width = bevel * S
        md.segments = 3
        md.limit_method = "ANGLE"
    return o


def box_seg(name, ax, ay, bx, by, th, h0, h1, m, bevel=0.3):
    dx, dy = bx - ax, by - ay
    l = math.hypot(dx, dy)
    if l < 1e-6:
        return None
    nx, ny = -dy / l * th / 2, dx / l * th / 2
    ex, ey = dx / l * th / 2, dy / l * th / 2
    pts = [(ax + nx - ex, ay + ny - ey), (bx + nx + ex, by + ny + ey), (bx - nx + ex, by - ny + ey), (ax - nx - ex, ay - ny - ey)]
    return prism(name, pts, h0, h1, m, bevel)


def curve_tube(name, pts3, r, m, closed=False):
    cu = bpy.data.curves.new(name, "CURVE")
    cu.dimensions = "3D"
    cu.bevel_depth = r * S
    cu.bevel_resolution = 4
    sp = cu.splines.new("POLY")
    sp.points.add(len(pts3) - 1)
    for i, (x, y, h) in enumerate(pts3):
        v = P(x, y, h)
        sp.points[i].co = (v.x, v.y, v.z, 1)
    sp.use_cyclic_u = closed
    o = bpy.data.objects.new(name, cu)
    col.objects.link(o)
    o.data.materials.append(m)
    return reg(o)


def offset_poly(pts, d):
    """Offsets an open polyline sideways by d (positive = left of travel)."""
    out = []
    n = len(pts)
    for i in range(n):
        a = pts[max(0, i - 1)]
        b = pts[min(n - 1, i + 1)]
        dx, dy = b[0] - a[0], b[1] - a[1]
        l = math.hypot(dx, dy) or 1
        out.append((pts[i][0] - dy / l * d, pts[i][1] + dx / l * d) + tuple(pts[i][2:]))
    return out


def ribbon(name, pts, width, h0, h1, m):
    """A flat band of `width` along a 2D polyline, from h0 to h1 thick: plastics."""
    L1 = offset_poly(pts, width / 2)
    R1 = offset_poly(pts, -width / 2)
    poly = [(p[0], p[1]) for p in L1] + [(p[0], p[1]) for p in reversed(R1)]
    bm = bmesh.new()
    n = len(pts)
    top_l = [bm.verts.new(P(p[0], p[1], h1)) for p in L1]
    top_r = [bm.verts.new(P(p[0], p[1], h1)) for p in R1]
    bot_l = [bm.verts.new(P(p[0], p[1], h0)) for p in L1]
    bot_r = [bm.verts.new(P(p[0], p[1], h0)) for p in R1]
    for i in range(n - 1):
        bm.faces.new((top_l[i], top_r[i], top_r[i + 1], top_l[i + 1]))
        bm.faces.new((bot_l[i + 1], bot_r[i + 1], bot_r[i], bot_l[i]))
        bm.faces.new((top_l[i], top_l[i + 1], bot_l[i + 1], bot_l[i]))
        bm.faces.new((top_r[i + 1], top_r[i], bot_r[i], bot_r[i + 1]))
    bm.faces.new((top_l[0], bot_l[0], bot_r[0], top_r[0]))
    bm.faces.new((top_r[-1], bot_r[-1], bot_l[-1], top_l[-1]))
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    return obj(name, me, m)


def bezier_path(words, start, stop_word):
    """The points of a path in a table file line: x y, then L x y / C x1 y1 x2 y2 x y."""
    pts = []
    i = start
    cur = None
    def num(k):
        return float(words[k])
    while i < len(words):
        w = words[i]
        if w == stop_word:
            break
        if w == "L":
            cur = (num(i + 1), num(i + 2))
            pts.append(cur)
            i += 3
        elif w == "C":
            p0 = cur
            p1, p2, p3 = (num(i + 1), num(i + 2)), (num(i + 3), num(i + 4)), (num(i + 5), num(i + 6))
            for k in range(1, 13):
                t = k / 12
                u = 1 - t
                pts.append((u ** 3 * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t ** 3 * p3[0],
                            u ** 3 * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t ** 3 * p3[1]))
            cur = p3
            i += 7
        else:
            try:
                cur = (num(i), num(i + 1))
                pts.append(cur)
                i += 2
            except (ValueError, IndexError):
                break
    return pts


# ---------------------------------------------------------------- materials
img = bpy.data.images.load(os.path.join(D, ID + "-print.png"))
pf = bpy.data.materials.new("playfield")
pf.use_nodes = True
nt = pf.node_tree
bsdf = nt.nodes["Principled BSDF"]
tex = nt.nodes.new("ShaderNodeTexImage")
tex.image = img
tex.interpolation = "Cubic"
nt.links.new(tex.outputs["Color"], bsdf.inputs["Base Color"])
bsdf.inputs["Roughness"].default_value = 0.35
bsdf.inputs["Coat Weight"].default_value = 0.7
bsdf.inputs["Coat Roughness"].default_value = 0.12
# subtle wood-grain/scratch bump on the clear coat
noise = nt.nodes.new("ShaderNodeTexNoise")
noise.inputs["Scale"].default_value = 900
bump = nt.nodes.new("ShaderNodeBump")
bump.inputs["Strength"].default_value = 0.02
nt.links.new(noise.outputs["Fac"], bump.inputs["Height"])
nt.links.new(bump.outputs["Normal"], bsdf.inputs["Coat Normal"])

M_STEEL = mat("steel", (0.75, 0.75, 0.78, 1), metal=1, rough=0.18)
M_CHROME = mat("chrome", (0.95, 0.95, 0.97, 1), metal=1, rough=0.04)
M_RAIL = mat("rail", (0.7, 0.7, 0.72, 1), metal=1, rough=0.3)
M_RUBBER = mat("rubber", (0.015, 0.015, 0.015, 1), rough=0.55)
M_WHITE_RUBBER = mat("wrubber", (0.8, 0.8, 0.78, 1), rough=0.5)
M_POST = mat("post", (0.9, 0.9, 0.95, 1), rough=0.15, trans=0.6)
M_WOOD = mat("cabinet", rgb("#2a1a12"), rough=0.35, coat=1)
M_BLACK = mat("black", (0.02, 0.02, 0.025, 1), rough=0.3)
M_HOLE = mat("hole", (0.0, 0.0, 0.0, 1), rough=1)
M_FLIPPER = mat("flipper", (0.92, 0.92, 0.9, 1), rough=0.25, coat=0.5)
M_FLIP_RUB = mat("fliprub", rgb("#c81e1e"), rough=0.5)
M_APRON = mat("apron", rgb("#26242e"), metal=0.6, rough=0.35)


def plastic(hexs, clear=False):
    c = rgb(hexs)
    if clear:
        return mat("clear_" + hexs, (c[0] * 0.5 + 0.5, c[1] * 0.5 + 0.5, c[2] * 0.5 + 0.5, 1), rough=0.05, trans=1.0, ior=1.49)
    return mat("plastic_" + hexs, c, rough=0.12, trans=0.75, ior=1.49, coat=0.6)


# ---------------------------------------------------------------- playfield + cabinet
bm = bmesh.new()
v = [bm.verts.new(P(x, y, 0)) for x, y in [(0, L), (W, L), (W, 0), (0, 0)]]
bm.faces.new(v)
uv = bm.loops.layers.uv.new()
for f in bm.faces:
    for lp in f.loops:
        co = lp.vert.co
        lp[uv].uv = (co.x / (W * S), co.y / (L * S))
me = bpy.data.meshes.new("playfield")
bm.to_mesh(me)
obj("playfield", me, pf)

# wooden side walls, the back arch and the cabinet beyond
rim = None
for p in data["parts"]:
    if p["kind"] == "rim":
        rim = bezier_path(p["words"], 1, None)
outer = [(-14, L + 4)] + [(x - (14 if x < W / 2 else -14) * (1 if True else 0), y - 14 * (1 if y < 60 else 0)) for x, y in rim[1:-1]] + [(W + 14, L + 4)]
# inner rim as walls 2 units thick, 26 high, dark painted wood with a gloss coat
for i in range(len(rim) - 1):
    (ax, ay), (bx, by) = rim[i], rim[i + 1]
    box_seg("rim%d" % i, ax, ay, bx, by, 3.0, 0, 26, M_WOOD, bevel=0.0)
# cabinet side rails (outside the playfield)
prism("railL", [(-16, L + 30), (-2, L + 30), (-2, -40), (-16, -40)], 0, 30, M_WOOD)
prism("railR", [(W + 2, L + 30), (W + 16, L + 30), (W + 16, -40), (W + 2, -40)], 0, 30, M_WOOD)
prism("back", [(-16, -2), (W + 16, -2), (W + 16, -40), (-16, -40)], 0, 30, M_WOOD)
# metal side rails on top of the cabinet
prism("sideRailL", [(-17, L + 30), (-1, L + 30), (-1, -40), (-17, -40)], 30, 33, M_RAIL, bevel=1.0)
prism("sideRailR", [(W + 1, L + 30), (W + 17, L + 30), (W + 17, -40), (W + 1, -40)], 30, 33, M_RAIL, bevel=1.0)
# fill outside the arch at the top corners
prism("cornerL", [(0, 64), (0, -2), (72, -2)], 0, 26, M_WOOD)
prism("cornerR", [(W, 64), (W, -2), (160, -2)], 0, 26, M_WOOD)
# front: lockdown bar
prism("front", [(-16, L + 2), (W + 16, L + 2), (W + 16, L + 30), (-16, L + 30)], 0, 30, M_WOOD)
prism("lockdown", [(-17, L + 2), (W + 17, L + 2), (W + 17, L + 14), (-17, L + 14)], 30, 36, M_CHROME, bevel=1.5)
# apron
prism("apronL", [(-4, 370), (84, 370), (98, L + 4), (-4, L + 4)], 0, 6, M_APRON, bevel=1.0)
prism("apronR", [(216, 370), (136, 370), (122, L + 4), (216, L + 4)], 0, 6, M_APRON, bevel=1.0)
# the plunger in the shooter lane: rubber tip, chrome rod, steel spring against the housing; it is drawn in the
# game from sprites at several pulls, so it is not in the base picture
# the rod's axis at the height of the ball's centre, so the tip strikes the ball square
LANE_X, TIP_Y, AXIS_H = 224, 346.5, 5.5
prism("plungerHousing", [(LANE_X - 6, 383), (LANE_X + 6, 383), (LANE_X + 6, 388), (LANE_X - 6, 388)], 0, 10, M_RAIL, bevel=0.8)
CAT = "plunger"
M_STEEL = mat("steel", (0.75, 0.75, 0.78, 1), metal=1, rough=0.22)
tip = curve_tube("plungerTip", [(LANE_X, TIP_Y, AXIS_H), (LANE_X, TIP_Y + 4, AXIS_H)], 3.8, M_RUBBER)
tip.data.use_fill_caps = True
rod = curve_tube("plungerRod", [(LANE_X, TIP_Y + 4, AXIS_H), (LANE_X, L + 4, AXIS_H)], 1.4, M_CHROME)
rod.data.use_fill_caps = True
coil = []
TURNS, C0, C1 = 13, 356.0, 382.5
for i in range(TURNS * 16 + 1):
    a = 2 * math.pi * i / 16
    coil.append((LANE_X + 3.3 * math.cos(a), C0 + (C1 - C0) * i / (TURNS * 16), AXIS_H + 3.3 * math.sin(a)))
spring = curve_tube("plungerSpring", coil, 0.55, M_STEEL)
CAT = "static"

# ---------------------------------------------------------------- parts
lights = []  # (x, y, h, color, power) for the GI and lamps
lit = set()

flips = {f["id"]: f for f in data["flippers"]}


def heights_of(layer):
    if layer == 0:
        return 0.0
    for p in data["parts"]:
        if p["kind"] == "floor" and int(p["nums"][0]) == layer:
            return p["nums"][5]
    return 0.0


for idx, p in enumerate(data["parts"]):
    k, n, names, cols = p["kind"], p["nums"], p["names"], p["colors"]
    pid = names[0] if names else k
    z = heights_of(p["layer"])
    CAT = ("over" if k in ("ramp", "tube", "track", "plastic", "plasticoval") else "flip:" + pid if k == "flipper"
           else "drop:" + pid if k == "drop" else "captive" if k == "captive" else "lamp:" + pid if k == "bumper" else "static")
    nm = "%s_%d" % (pid, idx)
    if k == "wall":
        pts = list(zip(n[0::2], n[1::2]))
        for i in range(len(pts) - 1):
            box_seg(nm + "_%d" % i, pts[i][0], pts[i][1], pts[i + 1][0], pts[i + 1][1], 2.2, z, z + 9, M_STEEL, bevel=0.4)
    elif k == "band":
        pts = list(zip(n[0::2], n[1::2]))
        # a rubber ring stretched round posts at the ends
        loop = offset_poly([(x, y) for x, y in pts], 2.4) + list(reversed(offset_poly([(x, y) for x, y in pts], -2.4)))
        curve_tube(nm, [(x, y, z + 5) for x, y in loop], 1.1, M_RUBBER, closed=True)
        for x, y in pts:
            cyl(nm + "p", x, y, 1.6, z, z + 11, M_POST)
            cyl(nm + "c", x, y, 2.0, z + 11, z + 12.5, M_STEEL)
    elif k == "post":
        x, y = n[0], n[1]
        cyl(nm, x, y, 1.6, z, z + 11, M_POST)
        cyl(nm + "c", x, y, 2.1, z + 11, z + 12.5, M_STEEL)
        torus(nm + "r", x, y, z + 5, 2.0, 0.9, M_RUBBER)
    elif k == "bumper":
        x, y, r = n[0], n[1], n[2]
        c = cols[1] if len(cols) > 1 and cols[1] else PAL[3]
        top = cols[2] if len(cols) > 2 and cols[2] else "#ffffff"
        cyl(nm + "base", x, y, r * 1.05, z, z + 1.5, M_BLACK)
        cyl(nm + "skirt", x, y, r * 1.15, z + 1.5, z + 3, mat("skirt", rgb(c), rough=0.2, trans=0.6))
        cyl(nm + "body", x, y, r * 0.75, z + 3, z + 10, mat("bbody", (0.9, 0.9, 0.9, 1), rough=0.15, trans=0.9, emit=rgb("#fff0d0"), estr=0.6))
        cyl(nm + "ring", x, y, r * 0.95, z + 6, z + 7, M_CHROME)
        cyl(nm + "cap", x, y, r, z + 10, z + 14, mat("cap_" + c, rgb(c), rough=0.1, trans=0.7, emit=rgb(c), estr=0.8), r2=r * 0.85)
        cyl(nm + "capt", x, y, r * 0.6, z + 14, z + 14.6, mat("capt_" + top, rgb(top), rough=0.3, emit=rgb(top), estr=1.0))
        lights.append((x, y, z + 8, c, 1.2, "lamp:" + pid))
    elif k == "sling":
        a, b, c3 = (n[0], n[1]), (n[2], n[3]), (n[4], n[5])
        tri = [a, b, c3]
        cx, cy = sum(t[0] for t in tri) / 3, sum(t[1] for t in tri) / 3
        # rubber around three posts
        loop = []
        for i in range(3):
            px, py = tri[i]
            dx, dy = px - cx, py - cy
            l = math.hypot(dx, dy)
            loop.append((px + dx / l * 2.2, py + dy / l * 2.2))
        curve_tube(nm + "rub", [(x, y, z + 5) for x, y in loop + [loop[0]]], 1.2, M_WHITE_RUBBER)
        for px, py in tri:
            cyl(nm + "p", px, py, 1.6, z, z + 13, M_POST)
        # plastic cover, slightly bigger than the triangle
        cov = [(cx + (t[0] - cx) * 1.1, cy + (t[1] - cy) * 1.1) for t in tri]
        prism(nm + "cov", cov, z + 13, z + 13.6, plastic(PAL[6], clear=True), bevel=0.3)
        prism(nm + "wall", tri, z, z + 9, mat("slingwall", (0.85, 0.85, 0.85, 1), rough=0.3))
        lights.append((cx, cy, z + 7, "#ffd8a0", 0.6, "gi"))
    elif k == "flipper":
        f = flips.get(pid)
        if not f:
            continue
        a = f["rest"]
        tx, ty = f["px"] + math.cos(a) * f["length"], f["py"] + math.sin(a) * f["length"]
        # capsule outline
        pts = []
        for i in range(17):
            t = a + math.pi / 2 + math.pi * i / 16
            pts.append((f["px"] + math.cos(t) * f["base"], f["py"] + math.sin(t) * f["base"]))
        for i in range(17):
            t = a - math.pi / 2 + math.pi * i / 16
            pts.append((tx + math.cos(t) * f["tip"], ty + math.sin(t) * f["tip"]))
        prism(nm, pts, z + 0.5, z + 6.5, M_FLIPPER, bevel=0.5)
        rub = [(x, y, z + 3.5) for x, y in pts]
        curve_tube(nm + "rub", rub + [rub[0]], 1.0, M_FLIP_RUB)
        cyl(nm + "pin", f["px"], f["py"], 1.4, z + 6.5, z + 8, M_CHROME)
    elif k in ("drop", "standup"):
        x, y, deg = n[0], n[1], n[2]
        c = cols[1] if len(cols) > 1 and cols[1] else PAL[7]
        a = math.radians(deg)
        dx, dy = math.cos(a) * 4, math.sin(a) * 4
        m = mat("tgt_" + c, rgb(c), rough=0.25, coat=0.8)
        box_seg(nm, x - dx, y - dy, x + dx, y + dy, 1.6, z, z + 8, m, bevel=0.3)
        if k == "standup":
            box_seg(nm + "leg", x - dx * 0.2, y - dy * 0.2, x + dx * 0.2, y + dy * 0.2, 1.0, z, z + 3, M_STEEL)
    elif k == "saucer":
        x, y = n[0], n[1]
        cyl(nm, x, y, 5.2, z - 0.5, z + 0.3, M_HOLE)
        torus(nm + "rim", x, y, z + 0.3, 5.4, 0.6, M_CHROME)
    elif k == "hole":
        cyl(nm, n[0], n[1], n[2], z - 0.5, z + 0.2, M_HOLE)
    elif k == "spinner":
        x, y = n[0], n[1]
        box_seg(nm + "a", x - 7, y, x - 6, y, 1.0, z, z + 12, M_STEEL)
        box_seg(nm + "b", x + 6, y, x + 7, y, 1.0, z, z + 12, M_STEEL)
        box_seg(nm + "plate", x - 6, y, x + 6, y, 0.5, z + 3, z + 11, M_CHROME)
    elif k == "gate":
        box_seg(nm, n[0], n[1], n[2], n[3], 0.6, z + 2, z + 7, M_STEEL)
    elif k == "lanes":
        x0, y, cnt, step = n[0], n[1], int(n[2]), n[3]
        for i in range(cnt + 1):
            x = x0 + i * step
            box_seg(nm + "g%d" % i, x, y, x, y + 16, 2.0, z, z + 9, M_STEEL)
            cyl(nm + "p%d" % i, x, y, 1.6, z, z + 11, M_POST)
            torus(nm + "r%d" % i, x, y, z + 5, 2.0, 0.9, M_RUBBER)
    elif k == "plastic":
        width = n[1]
        pts = list(zip(n[2::2], n[3::2]))
        c1 = cols[0] if cols and cols[0] else PAL[6]
        ribbon(nm, [(x, y) for x, y in pts], width, z + 14, z + 15, plastic(c1))
        for x, y in (pts[0], pts[-1]):
            cyl(nm + "so", x, y, 1.4, z, z + 14, M_POST)
            cyl(nm + "nut", x, y, 1.6, z + 15, z + 16, M_CHROME)
        lights.append((sum(q[0] for q in pts) / len(pts), sum(q[1] for q in pts) / len(pts), z + 6, "#ffd8a0", 0.8, "gi"))
    elif k == "floor":
        layer, x0, y0, w, h = n[:5]
        fh = n[5] if len(n) > 5 else 12
        prism(nm, [(x0, y0), (x0 + w, y0), (x0 + w, y0 + h), (x0, y0 + h)], 0, fh, mat("floor", rgb(cols[0] if cols and cols[0] else "#303030"), rough=0.3, coat=1))
    elif k == "tube" and "track" in p:
        c = cols[0] if cols and cols[0] else PAL[5]
        cc = rgb(c)
        glass = mat("tube_" + c, (cc[0] * 0.4 + 0.6, cc[1] * 0.4 + 0.6, cc[2] * 0.4 + 0.6, 1), rough=0.02, trans=1.0, ior=1.45)
        curve_tube(nm, [(x, y, h + 5.5) for x, y, h in p["track"]], 7.0, glass)
        for i in range(2, len(p["track"]) - 1, 6):
            x, y, h = p["track"][i]
            torus(nm + "band%d" % i, x, y, h + 5.5, 7.2, 0.6, M_CHROME).rotation_euler = (math.pi / 2, 0, 0)
            if h > 4:
                box_seg(nm + "leg%d" % i, x, y, x + 0.01, y, 1.2, 0, h - 1.5, M_STEEL, bevel=0)
    elif k == "track" and "track" in p:
        pts = p["track"]
        if pid == "lift":
            curve_tube(nm, [(x, y, h + 5.5) for x, y, h in pts], 7.0, mat("lift", (0.75, 0.85, 1.0, 1), rough=0.02, trans=1.0, ior=1.45))
        else:
            for side in (4.0, -4.0):
                off = offset_poly(pts, side)
                curve_tube(nm + "w%s" % side, [(x, y, h + 2.5) for x, y, h in off], 0.5, M_CHROME)
    elif k == "plasticoval":
        hh, x0, y0, w, h = n[:5]
        c1 = cols[0] if cols and cols[0] else PAL[7]
        pts = [(x0 + w / 2 + math.cos(t) * w / 2, y0 + h / 2 + math.sin(t) * h / 2) for t in [2 * math.pi * i / 40 for i in range(40)]]
        prism(nm, pts, z + hh, z + hh + 1, plastic(c1), bevel=0.3)
        for x in (x0 + 4, x0 + w - 4):
            cyl(nm + "so", x, y0 + h / 2, 1.4, z, z + hh, M_POST)
    elif k == "captive":
        o = bpy.data.meshes.new("cap")
        bm2 = bmesh.new()
        bmesh.ops.create_uvsphere(bm2, u_segments=48, v_segments=24, radius=5.5 * S)
        bm2.to_mesh(o)
        ob = obj(nm, o, M_CHROME)
        ob.location = P(n[0], n[1], z + 5.5)
        smooth(ob)
    elif k == "spindisc":
        x, y = n[0], n[1]
        r = float(p["options"].get("r", 12))
        cyl(nm, x, y, r, z, z + 0.4, mat("spindisc", (0.8, 0.82, 0.86, 1), metal=1, rough=0.25))
        cyl(nm + "hub", x, y, 2, z + 0.4, z + 1.2, M_CHROME)
    elif k in ("ramp", "tube") and "track" in p:
        tr = p["track"]
        ws = p.get("wireStart", -1)
        c = cols[0] if cols and cols[0] else PAL[6]
        width = n[0]
        plastic_pts = tr[:ws + 1] if ws > 0 else tr
        wire_pts = tr[ws:] if ws > 0 else []
        # the plastic: a floor and two walls
        bm = bmesh.new()
        Lw = offset_poly(plastic_pts, width / 2 + 1)
        Rw = offset_poly(plastic_pts, -width / 2 - 1)
        th, wall = 0.8, 9
        rows = []
        for (lx, ly, lh), (rx, ry, rh) in zip(Lw, Rw):
            rows.append([bm.verts.new(P(lx, ly, lh + wall)), bm.verts.new(P(lx, ly, lh - th)), bm.verts.new(P(rx, ry, rh - th)), bm.verts.new(P(rx, ry, rh + wall)),
                         bm.verts.new(P(rx, ry, rh + wall)), bm.verts.new(P(rx, ry, rh))])
        for i in range(len(rows) - 1):
            a, b = rows[i], rows[i + 1]
            for j in range(3):
                bm.faces.new((a[j], a[j + 1], b[j + 1], b[j]))
        bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
        me = bpy.data.meshes.new(nm)
        bm.to_mesh(me)
        cc = rgb(c)
        o = obj(nm, me, mat("ramp_" + c, (cc[0] * 0.6 + 0.4, cc[1] * 0.6 + 0.4, cc[2] * 0.6 + 0.4, 1), rough=0.03, trans=1.0, ior=1.49))
        sol = o.modifiers.new("s", "SOLIDIFY")
        sol.thickness = 0.8 * S
        smooth(o)
        # metal supports under the plastic
        for i in range(4, len(plastic_pts) - 1, 8):
            x, y, h = plastic_pts[i]
            if h > 4:
                for side in (1, -1):
                    q = offset_poly(plastic_pts, side * (width / 2 + 1))[i]
                    box_seg(nm + "leg%d%d" % (i, side), q[0], q[1], q[0] + 0.01, q[1] + 0.01, 1.2, 0, h - 0.8, M_STEEL, bevel=0)
        # the wire form: two rails at ball height, one beneath, hoops every so often
        if wire_pts:
            for side, dz in ((4.6, 3.0), (-4.6, 3.0), (0, -1.0)):
                off = offset_poly(wire_pts, side)
                curve_tube(nm + "w%s" % side, [(x, y, h + dz) for x, y, h in off], 0.55, M_CHROME)
            for i in range(0, len(wire_pts), 6):
                x, y, h = wire_pts[i]
                a = offset_poly(wire_pts, 5.2)[i]
                b = offset_poly(wire_pts, -5.2)[i]
                curve_tube(nm + "hoop%d" % i, [(a[0], a[1], h + 3.5), (x, y, h - 1.5), (b[0], b[1], h + 3.5)], 0.4, M_CHROME)
                if h > 6:
                    box_seg(nm + "wleg%d" % i, x, y, x + 0.01, y, 1.0, 0, h - 1.5, M_STEEL, bevel=0)

# ---------------------------------------------------------------- toys
CAT = "over"
for idx, p in enumerate(data["parts"]):
    if p["kind"] != "toy":
        continue
    pid = p["names"][0]; x, y = p["nums"][0], p["nums"][1]; r = p["nums"][2] if len(p["nums"]) > 2 else 16
    if pid == "ferris":
        # an upright Ferris wheel facing the player
        cx, cy, ch = x, y, r + 10
        stand = mat("stand", rgb("#e8e0f0"), metal=0.8, rough=0.25)
        for side in (-4, 4):
            curve_tube("ferrisleg%d" % side, [(cx - r * 0.7, cy + side, 0), (cx, cy + side, ch), (cx + r * 0.7, cy + side, 0)], 1.0, stand)
        for side in (-3, 3):
            pts = [(cx + math.cos(t) * r, cy + side, ch + math.sin(t) * r) for t in [2 * math.pi * i / 48 for i in range(49)]]
            curve_tube("ferrisring%d" % side, pts, 0.7, mat("ring", rgb(PAL[5]), metal=0.5, rough=0.3))
        for i in range(12):
            t = 2 * math.pi * i / 12
            curve_tube("spoke%d" % i, [(cx, cy, ch), (cx + math.cos(t) * r, cy, ch + math.sin(t) * r)], 0.35, stand)
            gx, gz = cx + math.cos(t) * r, ch + math.sin(t) * r
            gc = [PAL[3], PAL[5], PAL[7], PAL[6]][i % 4]
            g = cyl("gondola%d" % i, gx, cy, 2.2, gz - 4.5, gz - 1.0, mat("gond_" + gc, rgb(gc), rough=0.3, coat=1))
            bulb = cyl("bulb%d" % i, gx, cy - 3.4, 0.7, gz - 0.3, gz + 0.9, mat("bulb", (1, 0.9, 0.7, 1), emit=(1, 0.85, 0.6, 1), estr=12))
        cyl("hub", cx, cy, 2.5, ch - 1, ch + 1, M_CHROME)
        lights.append((cx, cy - 6, ch, "#ffcc88", 3.0, "gi"))
    elif pid == "kraken":
        skin = mat("kraken", rgb(PAL[6]), rough=0.35, coat=0.6, trans=0.2)
        head = cyl("krakenhead", x, y - 4, 13, 0, 1, skin)
        bpy.ops.mesh.primitive_uv_sphere_add(segments=48, ring_count=24, radius=1, location=P(x, y - 6, 14))
        hd = bpy.context.object
        hd.scale = (12 * S, 11 * S, 14 * S)
        hd.data.materials.append(skin)
        smooth(hd)
        for ex in (-5, 5):
            bpy.ops.mesh.primitive_uv_sphere_add(segments=24, ring_count=12, radius=2.6 * S, location=P(x + ex, y + 3, 16))
            e = bpy.context.object
            e.data.materials.append(mat("eye", (0.95, 0.95, 0.85, 1), rough=0.1, emit=(1, 0.95, 0.6, 1), estr=2))
        for i in range(6):
            a = math.pi * (0.15 + 0.7 * i / 5)
            pts = []
            for k2 in range(12):
                t = k2 / 11
                rr = 8 + 16 * t
                pts.append((x + math.cos(a) * rr * (1 if i < 3 else 1) * (-1 if i < 3 else 1) * 0 + math.cos(math.pi - a) * rr,
                            y + 2 + math.sin(a) * rr * 0.55 + 3 * math.sin(t * 6 + i),
                            max(1.2, 8 * (1 - t))))
            curve_tube("tentacle%d" % i, pts, 2.2 * (1.0 - 0.0), skin).data.bevel_depth = 1.8 * S
    elif pid == "satellite":
        metal = mat("satmetal", (0.85, 0.85, 0.9, 1), metal=1, rough=0.2)
        gold = mat("foil", (1.0, 0.75, 0.3, 1), metal=1, rough=0.35)
        cyl("satmast", x + r * 0.9, y - 6, 1.0, 0, 22, metal)
        cyl("satbody", x + r * 0.9, y - 6, 4, 22, 30, gold)
        cyl("satdish", x + r * 0.9, y - 6, 8, 30, 33, metal, r2=2)
        for side in (-1, 1):
            prism("satpanel%d" % side, [(x + r * 0.9 + side * 5, y - 8), (x + r * 0.9 + side * 18, y - 8), (x + r * 0.9 + side * 18, y - 4), (x + r * 0.9 + side * 5, y - 4)], 25, 26, mat("panel", (0.05, 0.1, 0.35, 1), metal=0.6, rough=0.15))

# ---------------------------------------------------------------- inserts (lamps)
import random
random.seed(3)
for i, lmp in enumerate(data["lamps"]):
    if lmp["shape"] == "bumper":
        continue
    on = True
    CAT = "lamp:" + lmp["name"]
    c = rgb(lmp["color"])
    z = heights_of(lmp["layer"])
    m = mat("ins_%s_%d" % (lmp["color"], on), c, rough=0.1, trans=0.3, emit=c, estr=(3.0 if on else 0.1), coat=1)
    if lmp["shape"] == "round":
        cyl("lamp_" + lmp["name"], lmp["x"], lmp["y"], lmp["size"], z + 0.02, z + 0.12, m)
    elif lmp["shape"] == "insert":
        a = math.radians(lmp["deg"] - 90)
        ca, sa = math.cos(a), math.sin(a)
        tri = [(0, -4.6), (3.2, 2.6), (-3.2, 2.6)]
        pts = [(lmp["x"] + px * math.cos(a + math.pi / 2) - py * math.sin(a + math.pi / 2),
                lmp["y"] + px * math.sin(a + math.pi / 2) + py * math.cos(a + math.pi / 2)) for px, py in tri]
        prism("lamp_" + lmp["name"], pts, z + 0.02, z + 0.12, m)
    elif lmp["shape"] == "star":
        pts = []
        for j in range(10):
            rr = lmp["size"] * (0.55 if j % 2 == 0 else 0.25)
            t = -math.pi / 2 + j * math.pi / 5
            pts.append((lmp["x"] + math.cos(t) * rr, lmp["y"] + math.sin(t) * rr))
        prism("lamp_star", pts, z + 0.02, z + 0.12, m)
    if on:
        lights.append((lmp["x"], lmp["y"], z + 1.5, lmp["color"], 0.08, "lamp:" + lmp["name"]))
CAT = "static"

# ---------------------------------------------------------------- the ball, in the shooter lane and one on the field
for bx, by in ():
    s = bpy.data.meshes.new("ball")
    bm = bmesh.new()
    bmesh.ops.create_uvsphere(bm, u_segments=48, v_segments=24, radius=5.5 * S)
    bm.to_mesh(s)
    o = obj("ball", s, M_CHROME)
    o.location = P(bx, by, 5.5)
    smooth(o)

# ---------------------------------------------------------------- lights
for x, y, h, c, pw, grp in lights:
    bpy.ops.object.light_add(type="POINT", location=P(x, y, h))
    lo = bpy.context.object
    lo["group"] = grp
    lo.data.color = rgb(c)[:3]
    lo.data.energy = pw
    lo.data.shadow_soft_size = 1.5 * S
# GI string along both sides (warm bulbs under the guides)
for y in range(30, 330, 30):
    for x in (28, W - 38):
        bpy.ops.object.light_add(type="POINT", location=P(x, y, 3))
        lo = bpy.context.object
        lo.data.color = (1.0, 0.78, 0.5)
        lo.data.energy = float(os.environ.get("GI", 0.14))
        lo["group"] = "gi"
        lo.data.shadow_soft_size = 1 * S
# the backbox light over the top of the table, soft
bpy.ops.object.light_add(type="AREA", location=P(W / 2, -10, 140))
lo = bpy.context.object
lo.data.size = W * S
lo.data.size_y = 40 * S
lo.data.shape = "RECTANGLE"
lo.data.energy = 0.0
lo.data.color = (0.85, 0.85, 1.0)
lo.rotation_euler = (math.radians(-10), 0, 0)
# the room: very dark with a hint of blue, and a soft key light from above the player
world = bpy.data.worlds.new("w")
scene.world = world
world.use_nodes = True
world.node_tree.nodes["Background"].inputs["Color"].default_value = (0.004, 0.004, 0.008, 1)
world.node_tree.nodes["Background"].inputs["Strength"].default_value = 3.0
bpy.ops.object.light_add(type="AREA", location=P(W / 2, L + 120, 220))
lo = bpy.context.object
lo.data.size = 0.5
lo.data.energy = 10
lo.rotation_euler = (math.radians(30), 0, 0)

# ---------------------------------------------------------------- camera: standing at the front of the cabinet
cam = bpy.data.cameras.new("cam")
cam.lens = float(os.environ.get("LENS", 33))
cam.shift_y = float(os.environ.get("SHIFT", -0.17))
co = bpy.data.objects.new("cam", cam)
col.objects.link(co)
eye = P(W / 2, L + 60, 255)
target = P(W / 2, L * 0.43, 0)
co.location = eye
co.rotation_euler = (target - eye).to_track_quat("-Z", "Y").to_euler()
scene.camera = co

scene.render.engine = "CYCLES"
scene.cycles.device = "CPU"
scene.cycles.samples = SAMPLES
scene.cycles.use_denoising = True
scene.cycles.max_bounces = 8
scene.cycles.transmission_bounces = 8
scene.render.resolution_x = RW
scene.render.resolution_y = RH
scene.view_settings.view_transform = "AgX"
scene.view_settings.look = "AgX - Medium High Contrast"
scene.render.filepath = OUT
if not os.environ.get("NO_RENDER"):
    t = time.time()
    bpy.ops.render.render(write_still=True)
    print("RENDERED", OUT, round(time.time() - t, 1), "s")
