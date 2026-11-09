# Rendering the tables

The game draws each table from pictures that Blender rendered once, here, and that live in
`src/main/resources/eu/amadeos/games/pinball/render/<id>/`. The table file stays the truth: the physics reads it,
and these tools turn it into a 3D scene and render it from the player's eye.

```
tools/render/render.sh lantern-fair          # 128 samples, about a quarter of an hour on four cores
tools/render/render.sh coral-deep 32         # a quick look
```

Render a table again whenever its table file changes where something stands.

## What it takes

Python 3.13 with Blender as a module, OpenEXR and numpy. No Blender installation is needed:

```
python3.13 -m venv ~/bpyenv
~/bpyenv/bin/pip install bpy==5.2.3 openexr numpy
export BLENDER_PY=~/bpyenv/bin/python
```

## The steps

1. `./gradlew renderExport -Ptable=<id> -Pout=<folder>` writes `<id>-print.png`, the printed playfield seen from
   above with nothing standing on it, and `<id>.json`, every part, flipper, wall and lamp of the table.
2. `build.py` builds the scene from those two: the playfield with its print under a clear coat, the walls,
   rubbers, posts, pop bumpers, slingshots, ramps with their wire forms, plastics, toys, the cabinet, the apron,
   the lockdown bar and the plunger, and a light under every lamp.
3. `passes.py` puts the camera where the game's screen puts the playfield (the corners of the table land exactly
   on the trapezoid of `TableView.QUAD`) and renders the layers the game composes a frame from:

| File | What it is |
|---|---|
| `base.png` | The whole machine with every lamp off |
| `lamp-<name>.png` | What one lamp adds to the picture when it is lit, cut to where it adds anything |
| `over.png` | Ramps, wire forms, plastics and toys alone, with their transparency |
| `depth.png`, `overdepth.png` | How far each pixel is from the camera (16 bits, millimetres), for everything a ball can roll behind, and for the ramps and plastics alone |
| `flip-<id>-<k>.png` | Each flipper at twelve angles from rest to up, with its shadow and its mirror image in the floor |
| `drop-<id>.png` | Each drop target standing |
| `plunger-<k>.png` | The plunger at eight pulls |
| `probes.png` | What a chrome ball sees around it, as small panoramas on a grid over the table |
| `table.txt` | The camera's matrix and where each picture goes |

The game (`RenderedView`) copies `base.png`, adds the lit lamps, lays over the standing drop targets, the
flippers at their angles and the plunger at its pull, then draws every ball pixel by pixel: each pixel of the
sphere mirrors what the nearest probes see in that direction, a pixel is left out where the depth picture has
something nearer, and the ramps and plastics are laid over a ball that rolls beneath them.
