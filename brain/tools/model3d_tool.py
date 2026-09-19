"""3D model generator (trimesh) - GLB (dekhne ke liye) + STL (printing ke liye)."""
import time

import numpy as np
import trimesh
from trimesh.transformations import rotation_matrix

from ..config import settings

PALETTE = {
    "red": "#ff5252", "blue": "#4fc3f7", "green": "#66bb6a", "orange": "#ffa726",
    "yellow": "#ffee58", "purple": "#b388ff", "grey": "#9e9e9e", "gray": "#9e9e9e",
    "black": "#424242", "white": "#fafafa", "cyan": "#4dd0e1", "pink": "#f48fb1",
    "brown": "#8d6e63",
}

# simple primitives
_B, _C, _K, _S, _T = (trimesh.creation.box, trimesh.creation.cylinder,
                      trimesh.creation.cone, trimesh.creation.icosphere,
                      trimesh.creation.torus)


def _box(w, h, d):
    return _B([w, h, d])


def _rgba(color: str) -> np.ndarray:
    """Hex color -> RGBA uint8 [4]."""
    c = (color or "#4fc3f7").lstrip("#")
    if len(c) == 3:
        c = "".join(ch * 2 for ch in c)
    if len(c) == 6:
        c += "ff"
    return np.array([int(c[i:i + 2], 16) for i in (0, 2, 4, 6)], dtype=np.uint8)


def _paint(mesh, color: str):
    arr = np.tile(_rgba(color), (len(mesh.faces), 1))
    mesh.visual.face_colors = arr
    return mesh


def _prism(w, h, d):
    """Triangular roof (ridge upar ke)."""
    v = np.array([
        [-w / 2, 0, -d / 2], [w / 2, 0, -d / 2], [w / 2, 0, d / 2], [-w / 2, 0, d / 2],
        [-w / 2, h, 0], [w / 2, h, 0],
    ])
    f = np.array([
        [0, 2, 1], [0, 3, 2],
        [0, 1, 5], [0, 5, 4],
        [2, 3, 4], [2, 4, 5],
        [1, 2, 5], [3, 0, 4],
    ])
    m = trimesh.Trimesh(v, f)
    m.fix_normals()
    return m


# ---------------- recipes ----------------
def _rocket(s=1.0, color="#ff5252"):
    parts = []
    body = _paint(_C(0.30 * s, 1.1 * s), color)
    body.apply_translation([0, -0.1 * s, 0])
    nose = _paint(_K(0.30 * s, 0.45 * s), "#ffa726")
    nose.apply_translation([0, 0.775 * s, 0])
    tail = _paint(_C(0.16 * s, 0.12 * s), "#424242")
    tail.apply_translation([0, -0.71 * s, 0])
    parts += [body, nose, tail]
    for ang in (0, 90, 180, 270):
        fin = _paint(_box(0.05 * s, 0.35 * s, 0.30 * s), "#ff7043")
        fin.apply_translation([0, -0.55 * s, 0.35 * s])
        fin.apply_transform(rotation_matrix(np.radians(ang), [0, 1, 0]))
        parts.append(fin)
    return parts


def _car(s=1.0, color="#4fc3f7"):
    parts = []
    body = _paint(_box(1.5 * s, 0.35 * s, 0.7 * s), color)
    body.apply_translation([0, 0.3 * s, 0])
    cabin = _paint(_box(0.85 * s, 0.32 * s, 0.62 * s), "#b3e5fc")
    cabin.apply_translation([-0.1 * s, 0.62 * s, 0])
    parts += [body, cabin]
    for x in (0.5 * s, -0.5 * s):
        for z in (0.28 * s, -0.28 * s):
            wheel = _paint(_C(0.16 * s, 0.09 * s), "#212121")
            wheel.apply_transform(rotation_matrix(np.radians(90), [1, 0, 0]))
            wheel.apply_translation([x, 0.16 * s, z])
            parts.append(wheel)
    return parts


def _house(s=1.0, color="#ffa726"):
    parts = []
    base = _paint(_box(1.2 * s, 0.8 * s, 1.0 * s), color)
    base.apply_translation([0, 0.4 * s, 0])
    roof = _paint(_prism(1.4 * s, 0.5 * s, 1.15 * s), "#ff5252")
    roof.apply_translation([0, 0.8 * s, 0])
    door = _paint(_box(0.22 * s, 0.45 * s, 0.03 * s), "#5d4037")
    door.apply_translation([0, 0.225 * s, 0.51 * s])
    win = _paint(_box(0.25 * s, 0.25 * s, 0.03 * s), "#b3e5fc")
    win.apply_translation([0.35 * s, 0.5 * s, 0.51 * s])
    parts += [base, roof, door, win]
    return parts


def _chair(s=1.0, color="#ffa726"):
    parts = []
    seat = _paint(_box(0.6 * s, 0.06 * s, 0.6 * s), color)
    seat.apply_translation([0, 0.32 * s, 0])
    back = _paint(_box(0.6 * s, 0.55 * s, 0.06 * s), color)
    back.apply_translation([0, 0.6 * s, -0.27 * s])
    parts += [seat, back]
    for x in (0.25 * s, -0.25 * s):
        for z in (0.25 * s, -0.25 * s):
            leg = _paint(_box(0.05 * s, 0.32 * s, 0.05 * s), "#8d6e63")
            leg.apply_translation([x, 0.16 * s, z])
            parts.append(leg)
    return parts


def _cube(s=1.0, color="#4fc3f7"):
    return [_paint(_box(0.7 * s, 0.7 * s, 0.7 * s), color)]


def _sphere(s=1.0, color="#66bb6a"):
    return [_paint(_S(0.4 * s), color)]


def _cylinder(s=1.0, color="#ffa726"):
    return [_paint(_C(0.3 * s, 0.8 * s), color)]


def _cone(s=1.0, color="#b388ff"):
    return [_paint(_K(0.35 * s, 0.8 * s), color)]


def _torus(s=1.0, color="#4dd0e1"):
    return [_paint(_T(0.4 * s, 0.12 * s), color)]


def _combo(s=1.0, color="#4fc3f7"):
    parts = []
    m1 = _paint(_box(0.4 * s, 0.4 * s, 0.4 * s), PALETTE["red"])
    m1.apply_translation([-0.55, -0.2, -0.3])
    m2 = _paint(_S(0.28 * s), PALETTE["green"])
    m2.apply_translation([0.1, -0.1, 0.0])
    m3 = _paint(_C(0.3 * s, 0.6 * s), PALETTE["orange"])
    m3.apply_translation([0.55, -0.1, -0.2])
    m4 = _paint(_K(0.35 * s, 0.5 * s), PALETTE["purple"])
    m4.apply_translation([-0.2, 0.35, 0.4])
    m5 = _paint(_T(0.4 * s, 0.12 * s), PALETTE["cyan"])
    m5.apply_translation([0.4, 0.15, 0.45])
    parts += [m1, m2, m3, m4, m5]
    return parts


RECIPES = {
    "rocket": _rocket, "car": _car, "house": _house, "chair": _chair,
    "cube": _cube, "sphere": _sphere, "cylinder": _cylinder, "cone": _cone,
    "torus": _torus, "combo": _combo,
}


def _safe_name(s: str, limit: int = 40) -> str:
    s = "".join(ch for ch in (s or "") if ch.isalnum() or ch in " -_").strip()
    return s.replace(" ", "_")[:limit] or "model"


def build_model3d(name: str = "model", shape: str = "rocket", color: str = "#4fc3f7") -> dict:
    shape = (shape or "rocket").lower().strip()
    if color and color.lower() in PALETTE:
        color = PALETTE[color.lower()]
    fn = RECIPES.get(shape)
    if fn is None:
        raise ValueError(f"Shape '{shape}' nahi milta. Available: {', '.join(RECIPES)}")

    parts = fn()
    scene = trimesh.Scene(parts)

    stamp = time.strftime("%Y%m%d_%H%M%S")
    stl = settings.out_dir / f"{_safe_name(name)}_{shape}_{stamp}.stl"
    glb = settings.out_dir / f"{_safe_name(name)}_{shape}_{stamp}.glb"

    # STL hamesha export karo (printing ke liye)
    trimesh.util.concatenate(parts).export(str(stl))

    # GLB best-effort (agar koi optional dependency miss ho to sirf STL chalega)
    files = [str(stl)]
    glb_ok = True
    try:
        scene.export(str(glb))
        files.insert(0, str(glb))
    except Exception:
        glb_ok = False
        try:
            glb.unlink()
        except OSError:
            pass

    return {
        "glb": str(glb) if glb_ok else "",
        "stl": str(stl),
        "shape": shape,
        "name": (glb.name if glb_ok else stl.name),
        "files": files,
        "summary": f"3D model ban gaya: {stl.name}. Shape: {shape}"
        + ("" if glb_ok else " (GLB export skip hua, STL diya)"),
    }
