"""Renders the layers the game composes a table from, into <out dir>/<id>/:
base.png (every lamp off), lamp-<name>.png (what each lamp adds when lit, cropped), over.png (ramps, wire forms,
plastics, toys: laid over a ball that rolls beneath them), depth.png and overdepth.png (16-bit distance from the
camera in millimetres, of everything a ball can roll behind, and of the ramps and plastics alone),
flip-<id>-<k>.png (each flipper at FRAMES angles), drop-<id>.png (each drop target standing), plunger-<k>.png
(the plunger at PULLS pulls), probes.png (what a chrome ball sees, on a grid), table.txt (the camera and where
each picture goes; table.json says the same for people).
Usage: python passes.py <export dir> <id> <out dir> [samples]; RW and RH set the size (1236 x 1200)."""
import os, sys, json, math, time
import numpy as np
import OpenEXR
os.environ["NO_RENDER"] = "1"
EXPORT, TID, OUTROOT = sys.argv[1], sys.argv[2], sys.argv[3]
SAMPLES = int(sys.argv[4]) if len(sys.argv) > 4 else 128
RW, RH = int(os.environ.get("RW", 1236)), int(os.environ.get("RH", 1200))
sys.argv = ["build", EXPORT, TID, os.devnull, str(SAMPLES), str(RW), str(RH)]
exec(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "build.py")).read())

OUT = os.path.join(OUTROOT, TID)
os.makedirs(OUT, exist_ok=True)
SCRATCH = os.path.join(OUT, "_tmp")
os.makedirs(SCRATCH, exist_ok=True)
meta = {"id": TID, "width": RW, "height": RH, "scale": S, "length": L, "lamps": {}, "flippers": {}, "drops": {}}
scene.render.use_persistent_data = True


def cat_objects(prefix):
    return [o for k, v in groups.items() if k.startswith(prefix) for o in v]


dynamic = cat_objects("flip:") + cat_objects("drop:") + cat_objects("captive") + cat_objects("plunger")
for o in dynamic:
    o.hide_render = True

# ---------------------------------------------------------------- camera
# fitted so that the playfield's corners land where the game's 640 x 400 screen puts them: the table region is
# x 0..412, y 0..400, and the trapezoid centred at 206 from y 30 (240 wide) to y 352 (352 wide)
from mathutils import Euler, Matrix
QW = float(os.environ.get("QW", 412))
QUAD = [(0, 0, 86, 30), (W, 0, 326, 30), (W, L, 382, 352), (0, L, 30, 352)]
dg = bpy.context.evaluated_depsgraph_get()


def cam_world(ey, ez, pitch):
    return Matrix.Translation(P(W / 2, ey, ez)) @ Euler((pitch, 0, 0)).to_matrix().to_4x4()


def residuals(v, shift):
    """Where the corners land, by Blender's pinhole (36 mm sensor fitted to the wider side), minus the targets."""
    ey, ez, pitch, lens = v
    view = cam_world(ey, ez, pitch).inverted()
    f = lens / 36 * max(RW, RH)
    k = RW / QW
    r = []
    for x, y, qx, qy in QUAD:
        c = view @ Vector((x * S, (L - y) * S, 0, 1))
        px = RW / 2 + f * c.x / -c.z
        py = RH / 2 - (f * c.y / -c.z + shift * max(RW, RH))
        r += [px - qx * k, py - qy * k]
    return np.array(r)


# a plane seen by a camera is a homography: a line of cameras sees the floor alike; the one without lens
# shift is the most natural (verticals converge as the eye expects)
shift = float(os.environ.get("CAM_SHIFT", 0))
v = np.array([780.0, 490.0, 0.83, 82.0])
for _ in range(200):
    r = residuals(v, shift)
    J = np.zeros((8, 4))
    for j in range(4):
        d = np.zeros(4)
        d[j] = 1e-5 * max(1, abs(v[j]))
        J[:, j] = (residuals(v + d, shift) - r) / d[j]
    v = v + 0.5 * np.linalg.lstsq(J, -r, rcond=None)[0]
err = float(np.abs(residuals(v, shift)).max())
ey, ez, pitch, lens = v
cam.lens, cam.shift_y = lens, shift
co.matrix_world = cam_world(ey, ez, pitch)
bpy.context.view_layer.update()
dg = bpy.context.evaluated_depsgraph_get()
_m = co.calc_matrix_camera(dg, x=RW, y=RH) @ co.matrix_world.inverted()
for x, y, qx, qy in QUAD:
    c = _m @ Vector((x * S, (L - y) * S, 0, 1))
    print("CORNER", x, y, "->", round((c.x / c.w + 1) / 2 * RW, 2), round((1 - c.y / c.w) / 2 * RH, 2), "want", qx * RW / QW, qy * RW / QW)
print("CAMERA eye y %.1f h %.1f pitch %.2f lens %.2f shift %.3f max err %.4f px" % (ey, ez, math.degrees(pitch), lens, shift, err))
if os.environ.get("CAM_ONLY"):
    scene.render.filepath = os.path.join(OUT, "camera.png")
    bpy.ops.render.render(write_still=True)
    sys.exit(0)
dg = bpy.context.evaluated_depsgraph_get()
proj = co.calc_matrix_camera(dg, x=RW, y=RH)
M = proj @ co.matrix_world.inverted()
meta["matrix"] = [list(r) for r in M]
meta["eye"] = list(co.matrix_world.translation)
meta["right"] = list(co.matrix_world.to_3x3().col[0])
meta["up"] = list(co.matrix_world.to_3x3().col[1])
meta["back"] = list(co.matrix_world.to_3x3().col[2])


def to_px(x, y, h):
    v = M @ Vector((x * S, (L - y) * S, h * S, 1))
    return ((v.x / v.w + 1) / 2 * RW, (1 - v.y / v.w) / 2 * RH)


def read_exr(path):
    f = OpenEXR.File(path)
    out = {}
    for part in f.parts:
        for name, ch in part.channels.items():
            out[name] = np.flipud(np.array(ch.pixels)) if False else np.array(ch.pixels)
    return out


def save_display(arr, path):
    """Saves a linear float image (h, w, 3|4) through the scene's view transform, as Blender shows it."""
    h, w = arr.shape[:2]
    if arr.shape[2] == 3:
        arr = np.concatenate([arr, np.ones((h, w, 1), np.float32)], axis=2)
    img = bpy.data.images.new("tmp", w, h, alpha=True, float_buffer=True)
    img.pixels.foreach_set(np.flipud(arr).astype(np.float32).ravel())
    scene.render.image_settings.media_type = "IMAGE"; scene.render.image_settings.file_format = "PNG"
    scene.render.image_settings.color_mode = "RGBA"
    # 8 bits: a 16-bit PNG would be read back linearised, not as the display values it holds
    scene.render.image_settings.color_depth = "8"
    img.save_render(path, scene=scene)
    bpy.data.images.remove(img)


def load_png(path):
    img = bpy.data.images.load(path)
    w, h = img.size
    a = np.array(img.pixels[:], np.float32).reshape(h, w, 4)
    bpy.data.images.remove(img)
    return np.flipud(a)


def png_write(arr8, path):
    """Writes an 8-bit array (h, w, 3|4) as it is, without any colour transform."""
    import zlib, struct
    h, w, c = arr8.shape
    arr8 = np.ascontiguousarray(arr8.astype(np.uint8))
    raw = b"".join(b"\x00" + arr8[r].tobytes() for r in range(h))

    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6 if c == 4 else 2, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def render_exr(path):
    ims = scene.render.image_settings
    ims.media_type = "MULTI_LAYER_IMAGE"
    try:
        ims.file_format = "OPEN_EXR_MULTILAYER"
    except TypeError:
        ims.file_format = "OPEN_EXR"
    scene.render.image_settings.color_depth = "32"
    scene.render.filepath = path
    t = time.time()
    bpy.ops.render.render(write_still=True)
    print("  rendered", os.path.basename(path), round(time.time() - t, 1), "s", flush=True)
    return read_exr(path)


def rgba(ch, layer="ViewLayer.Combined"):
    names = [n for n in ch if n.startswith(layer + ".") or n == layer]
    if layer in ch:  # interleaved RGBA array
        return ch[layer]
    raise KeyError(layer)


# ONLY=plunger renders the plunger alone again, against the base picture already rendered
ONLY = os.environ.get("ONLY", "")
if ONLY:
    meta = json.load(open(os.path.join(OUT, "table.json")))
    base8 = (load_png(os.path.join(OUT, "base.png"))[..., :3] * 255).round()
    saved = []
    for k, objs in groups.items():
        if k.startswith("lamp:") and not k[5:].startswith("pop"):
            for o in objs:
                for m in o.data.materials:
                    b = m.node_tree.nodes.get("Principled BSDF")
                    if b and b.inputs["Emission Strength"].default_value > 1:
                        b.inputs["Emission Strength"].default_value = 0.1
    for o in scene.objects:
        if o.type == "LIGHT" and str(o.get("group", "")).startswith("lamp:"):
            o.data.energy = 0
else:
    # ---------------------------------------------------------------- 1. base and the lamps
    vl = scene.view_layers[0]
    vl.use_pass_z = True
    lampgroups = {}
    for k, objs in groups.items():
        if k.startswith("lamp:"):
            g = "L_" + "".join(c if c.isalnum() else "_" for c in k[5:])
            vl.lightgroups.add(name=g)
            lampgroups[k[5:]] = g
            for o in objs:
                o.lightgroup = g
    for o in scene.objects:
        if o.type == "LIGHT" and str(o.get("group", "")).startswith("lamp:"):
            o.lightgroup = lampgroups[o["group"][5:]]
    print("lamps:", len(lampgroups), flush=True)
    ch = render_exr(os.path.join(SCRATCH, "main.exr"))
    print(sorted(ch.keys())[:8], flush=True)
    lit_lin = ch["ViewLayer.Combined"][..., :3].astype(np.float32)
    lamp_lin = {name: ch["ViewLayer.Combined_" + g][..., :3].astype(np.float32) for name, g in lampgroups.items() if "ViewLayer.Combined_" + g in ch}
    # ---------------------------------------------------------------- the rest is lit as with every lamp off
    saved = []
    for k, objs in groups.items():
        if k.startswith("lamp:") and not k[5:].startswith("pop") :
            for o in objs:
                for m in o.data.materials:
                    b = m.node_tree.nodes.get("Principled BSDF")
                    if b and b.inputs["Emission Strength"].default_value > 1:
                        saved.append((b.inputs["Emission Strength"], b.inputs["Emission Strength"].default_value))
                        b.inputs["Emission Strength"].default_value = 0.1
    for o in scene.objects:
        if o.type == "LIGHT" and str(o.get("group", "")).startswith("lamp:"):
            saved.append((o.data, o.data.energy))
            o.data.energy = 0
    for o in scene.objects:
        o.lightgroup = ""
    vl.use_pass_z = True

    ch = render_exr(os.path.join(SCRATCH, "base.exr"))
    base_lin = ch["ViewLayer.Combined"][..., :3].astype(np.float32)
    save_display(base_lin, os.path.join(OUT, "base.png"))
    base8 = (load_png(os.path.join(OUT, "base.png"))[..., :3] * 255).round()


    def blur(a, k=2):
        pad = np.pad(a, ((k, k), (k, k), (0, 0)), mode="edge")
        n = (2 * k + 1) ** 2
        return sum(pad[k + dy:k + dy + a.shape[0], k + dx:k + dx + a.shape[1]] for dy in range(-k, k + 1) for dx in range(-k, k + 1)) / n


    # the denoised light of all lamps together, shared out by each lamp's (noisy, so blurred) part of it
    total = np.maximum(lit_lin - base_lin, 0)
    smooth = {n: blur(a) for n, a in lamp_lin.items()}
    whole = sum(smooth.values()) + 1e-6
    for name in lamp_lin:
        lin = total * (smooth[name] / whole)
        p = os.path.join(SCRATCH, "l.png")
        save_display(base_lin + lin, p)
        lit8 = (load_png(p)[..., :3] * 255).round()
        diff = np.clip(lit8 - base8, 0, 255)
        mag = diff.max(axis=2)
        if mag.max() < 3:
            continue
        ys, xs = np.where(mag > max(4.0, mag.max() * 0.06))
        y0, y1, x0, x1 = ys.min(), ys.max() + 1, xs.min(), xs.max() + 1
        crop = diff[y0:y1, x0:x1]
        png_write(crop.astype(np.uint8), os.path.join(OUT, "lamp-%s.png" % name))
        meta["lamps"][name] = [int(x0), int(y0)]
    print("lamp images:", len(meta["lamps"]), flush=True)
    # ---------------------------------------------------------------- 2. depth of the opaque scene
    over = groups.get("over", [])
    for o in over:
        o.hide_render = True
    scene.cycles.samples = 1
    scene.cycles.use_denoising = False
    ch = render_exr(os.path.join(SCRATCH, "depth.exr"))
    z = ch["ViewLayer.Depth.Z"] if "ViewLayer.Depth.Z" in ch else ch[[n for n in ch if n.endswith("Depth.Z") or n.endswith(".Z")][0]]
    z = np.minimum(z, 60.0)
    z16 = (z * 1000).round().clip(0, 65535).astype(np.uint16)
    import zlib, struct
    def png16(gray, path):
        h, w = gray.shape
        raw = b"".join(b"\x00" + gray[r].astype(">u2").tobytes() for r in range(h))
        def chunk(t, d):
            return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
        with open(path, "wb") as f:
            f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 16, 0, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))
    png16(z16, os.path.join(OUT, "depth.png"))
    print("depth range", float(z.min()), float(z.max()), flush=True)
    # the depth of the ramps, wires, plastics and toys alone: in front of a ball they cover it, behind it not
    hidden = [o for o in scene.objects if o.type in ("MESH", "CURVE") and not o.hide_render]
    for o in hidden:
        o.hide_render = True
    for o in over:
        o.hide_render = False
    ch = render_exr(os.path.join(SCRATCH, "overdepth.exr"))
    z = ch["ViewLayer.Depth.Z"] if "ViewLayer.Depth.Z" in ch else ch[[n for n in ch if n.endswith(".Z")][0]]
    png16((np.minimum(z, 60.0) * 1000).round().clip(0, 65535).astype(np.uint16), os.path.join(OUT, "overdepth.png"))
    for o in hidden:
        o.hide_render = False

    # ---------------------------------------------------------------- 3. over: ramps, wires, plastics, toys alone
    scene.cycles.samples = max(32, SAMPLES // 2)
    scene.cycles.use_denoising = True
    scene.render.film_transparent = True
    scene.cycles.film_transparent_glass = True
    over_set = set(over)
    for o in scene.objects:
        if o.type in ("MESH", "CURVE") and o not in over_set and not o.hide_render:
            o.is_holdout = True
    scene.render.image_settings.media_type = "IMAGE"; scene.render.image_settings.file_format = "PNG"
    scene.render.image_settings.color_mode = "RGBA"
    scene.render.image_settings.color_depth = "8"
    scene.render.filepath = os.path.join(OUT, "over.png")
    bpy.ops.render.render(write_still=True)
    print("  rendered over", flush=True)


# ---------------------------------------------------------------- 4. flippers, drop targets, plunger: each in its box
# The whole scene is rendered in the part's box with the part in place; what differs from the base picture (the
# part, its shadow, its reflection in the floor) becomes the sprite, the rest stays transparent.
for o in scene.objects:
    o.is_holdout = False
scene.render.film_transparent = False
scene.cycles.film_transparent_glass = False
scene.cycles.samples = SAMPLES
scene.render.image_settings.color_mode = "RGB"


def sprite(objs, path, box):
    for o in objs:
        o.hide_render = False
    x0, y0, x1, y1 = box
    scene.render.use_border = True
    scene.render.use_crop_to_border = True
    scene.render.border_min_x = max(0, x0 / RW)
    scene.render.border_max_x = min(1, x1 / RW)
    scene.render.border_min_y = max(0, 1 - y1 / RH)
    scene.render.border_max_y = min(1, 1 - y0 / RH)
    tmp = os.path.join(SCRATCH, "sprite.png")
    scene.render.filepath = tmp
    bpy.ops.render.render(write_still=True)
    for o in objs:
        o.hide_render = True
    ox, oy = int(math.floor(scene.render.border_min_x * RW)), int(math.floor((1 - scene.render.border_max_y) * RH))
    a = (load_png(tmp)[..., :3] * 255).round()
    h, w = a.shape[:2]
    b = base8[oy:oy + h, ox:ox + w]
    h, w = min(h, b.shape[0]), min(w, b.shape[1])
    a, b = a[:h, :w], b[:h, :w]
    diff = np.abs(a - b).max(axis=2)
    alpha = np.clip((diff - 5) / 10, 0, 1)
    yy, xx = np.mgrid[0:h, 0:w]
    edge = np.minimum(np.minimum(xx, w - 1 - xx), np.minimum(yy, h - 1 - yy))
    alpha = alpha * np.clip(edge / 3, 0, 1)
    png_write(np.concatenate([a, alpha[..., None] * 255], axis=2).round().astype(np.uint8), path)
    return [ox, oy]


if not ONLY:
    K = int(os.environ.get("FRAMES", 12))
    for fid, f in flips.items():
        objs = groups.get("flip:" + fid, [])
        if not objs:
            continue
        reach = f["length"] + f["base"] + 4
        pts = [to_px(f["px"] + dx, f["py"] + dy, h) for dx in (-reach, reach) for dy in (-reach, reach) for h in (0, 10)]
        box = (min(p[0] for p in pts) - 4, min(p[1] for p in pts) - 4, max(p[0] for p in pts) + 4, max(p[1] for p in pts) + 4)
        pivot = P(f["px"], f["py"], 0)
        orig = {o: o.matrix_world.copy() for o in objs}
        frames = []
        for i in range(K):
            a = f["rest"] + (f["up"] - f["rest"]) * i / (K - 1)
            d = a - f["rest"]
            R = Matrix.Translation(pivot) @ Matrix.Rotation(-d, 4, "Z") @ Matrix.Translation(-pivot)
            for o in objs:
                o.matrix_world = R @ orig[o]
            off = sprite(objs, os.path.join(OUT, "flip-%s-%d.png" % (fid, i)), box)
            frames.append(off)
        for o in objs:
            o.matrix_world = orig[o]
        meta["flippers"][fid] = {"frames": K, "offset": frames[0], "rest": f["rest"], "up": f["up"]}
        print("  flipper", fid, flush=True)
    for k, objs in groups.items():
        if not k.startswith("drop:"):
            continue
        did = k[5:]
        p = next(q for q in data["parts"] if q["kind"] == "drop" and q["names"] and q["names"][0] == did)
        x, y = p["nums"][0], p["nums"][1]
        z = heights_of(p["layer"])
        pts = [to_px(x + dx, y + dy, z + h) for dx in (-8, 8) for dy in (-6, 6) for h in (0, 12)]
        box = (min(q[0] for q in pts) - 4, min(q[1] for q in pts) - 4, max(q[0] for q in pts) + 4, max(q[1] for q in pts) + 4)
        meta["drops"][did] = sprite(objs, os.path.join(OUT, "drop-%s.png" % did), box)
# the plunger at KP pulls: tip and rod move back by up to TRAVEL units, the spring is squeezed against the housing
KP, TRAVEL = int(os.environ.get("PULLS", 8)), 12.0
pobjs = groups.get("plunger", [])
if pobjs:
    pts = [to_px(LANE_X + dx, y, h) for dx in (-7, 7) for y in (TIP_Y - 4, L + 6) for h in (0, 10)]
    box = (min(p[0] for p in pts) - 4, min(p[1] for p in pts) - 4, max(p[0] for p in pts) + 4, max(p[1] for p in pts) + 4)
    orig = {o: o.matrix_world.copy() for o in pobjs}
    sp = next(o for o in pobjs if o.name.startswith("plungerSpring"))
    end = P(LANE_X, C1, AXIS_H)
    for i in range(KP):
        back = TRAVEL * i / (KP - 1)
        for o in pobjs:
            if o is sp:
                k = (C1 - C0 - back) / (C1 - C0)
                o.matrix_world = Matrix.Translation(end) @ Matrix.Diagonal((1, k, 1, 1)) @ Matrix.Translation(-end) @ orig[o]
            else:
                o.matrix_world = Matrix.Translation((0, -back * S, 0)) @ orig[o]
        off = sprite(pobjs, os.path.join(OUT, "plunger-%d.png" % i), box)
    for o in pobjs:
        o.matrix_world = orig[o]
    meta["plunger"] = {"frames": KP, "offset": off, "travel": TRAVEL}
    print("  plunger", flush=True)
scene.render.use_border = False

if not ONLY:
    # ---------------------------------------------------------------- 5. what a chrome ball reflects: panoramas on a grid
    # with every lamp lit, as a ball in play mostly sees them
    for thing, value in saved:
        if hasattr(thing, "energy"):
            thing.energy = value
        else:
            thing.default_value = value
    for o in scene.objects:
        o.is_holdout = False
    for o in over:
        o.hide_render = False
    scene.render.film_transparent = False
    # the room the machine stands in, as a ball sees it through the glass: dim, with two fluorescent tubes above
    world.node_tree.nodes["Background"].inputs["Color"].default_value = (0.03, 0.028, 0.035, 1)
    world.node_tree.nodes["Background"].inputs["Strength"].default_value = 1.0
    M_TUBE = mat("tube", (1, 1, 1, 1), emit=(0.95, 0.97, 1.0, 1), estr=float(os.environ.get("TUBE", 6)))
    for tx in (W * 0.28, W * 0.72):
        t = prism("tube%d" % int(tx), [(tx - 3, 40), (tx + 3, 40), (tx + 3, 330), (tx - 3, 330)], 150, 152, M_TUBE)
        for attr in ("visible_diffuse", "visible_glossy", "visible_transmission", "visible_volume_scatter", "visible_shadow"):
            setattr(t, attr, False)
    # and the lit backbox standing over the head of the table
    M_BOX = mat("backboxlight", (1, 1, 1, 1), emit=(1.0, 0.82, 0.62, 1), estr=float(os.environ.get("BOXLIGHT", 1.5)))
    bb = bpy.data.meshes.new("backboxlight")
    bb.from_pydata([P(-10, -45, 60), P(W + 10, -45, 60), P(W + 10, -60, 200), P(-10, -60, 200)], [], [(0, 1, 2, 3)])
    bbo = obj("backboxlight", bb, M_BOX)
    for attr in ("visible_diffuse", "visible_glossy", "visible_transmission", "visible_volume_scatter", "visible_shadow"):
        setattr(bbo, attr, False)
    GX, GY, PW, PH = 6, 10, 64, 32
    pcam = bpy.data.cameras.new("probe")
    pcam.type = "PANO"
    try:
        pcam.panorama_type = "EQUIRECTANGULAR"
    except Exception:
        pcam.cycles.panorama_type = "EQUIRECTANGULAR"
    po = bpy.data.objects.new("probe", pcam)
    col.objects.link(po)
    po.rotation_euler = (math.pi / 2, 0, -math.pi / 2)
    scene.camera = po
    scene.render.resolution_x, scene.render.resolution_y = PW, PH
    scene.cycles.samples = 24
    atlas = np.zeros((GY * PH, GX * PW, 3), np.uint8)
    for gy in range(GY):
        for gx in range(GX):
            x = 16 + (200 - 16) * gx / (GX - 1)
            y = 30 + (350 - 30) * gy / (GY - 1)
            po.location = P(x, y, 5.5)
            scene.render.filepath = os.path.join(SCRATCH, "probe.png")
            bpy.ops.render.render(write_still=True)
            a = load_png(scene.render.filepath)[..., :3]
            atlas[gy * PH:(gy + 1) * PH, gx * PW:(gx + 1) * PW] = (a * 255).round().astype(np.uint8)
    png_write(atlas, os.path.join(OUT, "probes.png"))
    meta["probes"] = {"nx": GX, "ny": GY, "w": PW, "h": PH, "x0": 16, "x1": 200, "y0": 30, "y1": 350}
json.dump(meta, open(os.path.join(OUT, "table.json"), "w"), indent=1)
print("DONE", OUT, flush=True)
# the same, as lines the game reads without a JSON library
with open(os.path.join(OUT, "table.txt"), "w") as f:
    f.write("# made by passes.py: the rendered layers of the table %s\n" % TID)
    f.write("size %d %d\n" % (RW, RH))
    f.write("world %r %r %r\n" % (S, W, L))
    f.write("matrix %s\n" % " ".join(repr(v) for r in meta["matrix"] for v in r))
    f.write("eye %s\n" % " ".join(repr(v) for v in meta["eye"]))
    f.write("basis %s\n" % " ".join(repr(v) for v in meta["right"] + meta["up"] + meta["back"]))
    for n, (x, y) in sorted(meta["lamps"].items()):
        f.write("lamp %s %d %d\n" % (n, x, y))
    for n, fl in sorted(meta["flippers"].items()):
        f.write("flipper %s %d %d %d %r %r\n" % (n, fl["frames"], fl["offset"][0], fl["offset"][1], fl["rest"], fl["up"]))
    for n, (x, y) in sorted(meta["drops"].items()):
        f.write("drop %s %d %d\n" % (n, x, y))
    if "plunger" in meta:
        f.write("plunger %d %d %d %r\n" % (meta["plunger"]["frames"], meta["plunger"]["offset"][0], meta["plunger"]["offset"][1], meta["plunger"]["travel"]))
    pr = meta["probes"]
    f.write("probes %d %d %d %d %r %r %r %r\n" % (pr["nx"], pr["ny"], pr["w"], pr["h"], pr["x0"], pr["x1"], pr["y0"], pr["y1"]))
