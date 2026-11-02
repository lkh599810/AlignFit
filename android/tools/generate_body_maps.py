"""Generate the body map SVG assets (android/app/src/main/assets/body_maps).

Each view has a smooth silhouette (<polygon id="shape_body">, plus
<polygon id="shape_arm"> for side views) built from hand-placed outline points
with Catmull-Rom smoothing. Region polygons are coarse boxes that the app clips
to the silhouette, so region ids stay stable while the outline looks organic.
Region polygons with data-layer="arm" are clipped to shape_arm and drawn on top.

Run: python android/tools/generate_body_maps.py
"""
import os

OUT_DIR = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "body_maps")


def catmull_rom(points, samples=8):
    n = len(points)
    out = []
    for i in range(n):
        p0, p1, p2, p3 = points[i - 1], points[i], points[(i + 1) % n], points[(i + 2) % n]
        for s in range(samples):
            t = s / samples
            t2, t3 = t * t, t * t * t
            out.append(tuple(
                0.5 * (2 * p1[k] + (-p0[k] + p2[k]) * t + (2 * p0[k] - 5 * p1[k] + 4 * p2[k] - p3[k]) * t2
                       + (-p0[k] + 3 * p1[k] - 3 * p2[k] + p3[k]) * t3)
                for k in range(2)))
    return out


def mirror_x(points):
    return [(200 - x, y) for x, y in points]


def fmt(points):
    return " ".join(f"{x:.1f},{y:.1f}" for x, y in points)


# ── Front/back silhouette: left half (screen-left), from neck down to crotch ──
HALF_OUTLINE = [
    (91, 50), (90, 64), (80, 70), (66, 76), (56, 82), (51, 92),
    (49, 108), (48, 135), (47, 165), (45, 195), (44, 225), (42, 240), (43, 256), (48, 266),
    (53, 258), (55, 242), (55, 228), (57, 200), (59, 168), (62, 135), (65, 112), (68, 102),
    (71, 115), (73, 140), (74, 160), (72, 185), (69, 205), (68, 230), (70, 262), (73, 300),
    (76, 330), (76, 345), (74, 365), (78, 395), (81, 410), (79, 422), (76, 431), (96, 432),
    (95, 420), (93, 405), (94, 380), (95, 350), (97, 320), (98, 280), (100, 252),
]
FRONT_OUTLINE = HALF_OUTLINE + list(reversed(mirror_x(HALF_OUTLINE)))[1:]

# ── Side silhouette (facing screen-left): torso + legs, and the arm separately ──
SIDE_BODY_OUTLINE = [
    (90, 50), (88, 66), (80, 78), (72, 96), (68, 115), (69, 135), (72, 150), (71, 170),
    (73, 190), (72, 210), (70, 232), (72, 260), (77, 295), (82, 322), (83, 340), (86, 370),
    (88, 395), (88, 410), (75, 418), (60, 424), (56, 430), (60, 433), (108, 433), (111, 425),
    (110, 410), (108, 395), (114, 372), (116, 355), (115, 338), (117, 322), (122, 295),
    (126, 265), (130, 240), (132, 222), (127, 205), (122, 188), (121, 170), (127, 150),
    (131, 125), (128, 104), (124, 85), (115, 72), (109, 64), (107, 50),
]
SIDE_ARM_OUTLINE = [
    (103, 72), (94, 75), (89, 84), (87, 100), (88, 130), (90, 168), (91, 200), (93, 232),
    (91, 250), (94, 268), (98, 274), (102, 266), (104, 248), (102, 234), (105, 200),
    (108, 168), (111, 135), (113, 100), (112, 84),
]


def box(x0, y0, x1, y1):
    return [(x0, y0), (x1, y0), (x1, y1), (x0, y1)]


A = 65.5  # arm / torso boundary in front-back views


def front_back_regions(view):
    """Regions for the screen-left half; mirrored for the right half."""
    torso = {
        "front": [("chest", 67, 148), ("abdomen", 148, 192), ("hip", 192, 252)],
        "back": [("upper_back", 67, 148), ("lower_back", 148, 180), ("waist", 180, 202),
                 ("glute", 202, 252)],
    }[view]
    thigh = "front_thigh" if view == "front" else "back_thigh"
    left = [
        ("shoulder", [(0, 67), (90, 67), (90, 98), (A, 100), (0, 100)]),
        ("upper_arm", box(0, 100, A, 165)),
        ("forearm", box(0, 165, A, 228)),
        ("hand_wrist", box(0, 228, A, 290)),
    ]
    for name, y0, y1 in torso:
        if name in ("chest", "upper_back"):
            left.append((name, [(A, 100), (90, 98), (90, 67), (100, 67), (100, y1), (A, y1)]))
        else:
            left.append((name, box(A, y0, 100, y1)))
    left += [
        (thigh, [(A, 252), (100, 252), (100, 322), (0, 322), (0, 290), (A, 290)]),
        ("knee", box(0, 322, 100, 352)),
        ("calf", box(0, 352, 100, 402)),
        ("ankle_foot", box(0, 402, 100, 440)),
    ]
    regions = [(f"{view}_neck", box(70, 49, 130, 67), "body")]
    for name, pts in left:
        regions.append((f"{view}_left_{name}", pts, "body"))
        regions.append((f"{view}_right_{name}", list(reversed(mirror_x(pts))), "body"))
    return regions


def side_regions(prefix):
    body = [
        ("neck", box(40, 49, 150, 68)),
        ("shoulder", box(40, 68, 150, 100)),
        ("chest", box(40, 100, 99, 152)),
        ("upper_back", box(99, 100, 150, 152)),
        ("abdomen", box(40, 152, 99, 206)),
        ("lower_back", box(99, 152, 150, 186)),
        ("waist", box(99, 186, 150, 206)),
        ("hip", box(40, 206, 100, 252)),
        ("glute", box(100, 206, 150, 252)),
        ("thigh", box(40, 252, 150, 322)),
        ("knee", box(40, 322, 150, 352)),
        ("calf", box(40, 352, 150, 405)),
        ("ankle_foot", box(40, 405, 150, 440)),
    ]
    arm = [
        ("shoulder", box(80, 60, 120, 100)),
        ("upper_arm", box(80, 100, 120, 168)),
        ("forearm", box(80, 168, 120, 234)),
        ("hand_wrist", box(80, 234, 120, 280)),
    ]
    return ([(f"{prefix}_{n}", p, "body") for n, p in body] +
            [(f"{prefix}_{n}", p, "arm") for n, p in arm])


def write_svg(file_name, title, note, head, shapes, regions):
    lines = [
        '<?xml version="1.0" encoding="UTF-8"?>',
        "<!--",
        f"  AlignFit body map: {title}. Generated by android/tools/generate_body_maps.py.",
        f"  {note}",
        "  shape_* polygons are the visible silhouette; region polygons are clipped to it",
        '  (data-layer="arm" regions clip to shape_arm and are drawn on top).',
        "-->",
        '<svg xmlns="http://www.w3.org/2000/svg" width="200" height="440" viewBox="0 0 200 440">',
        f"  <title>AlignFit {title}</title>",
        '  <g fill="#E4ECEB" stroke="#FFFFFF" stroke-width="1.2" stroke-linejoin="round">',
    ]
    for shape_id, pts in shapes:
        lines.append(f'    <polygon id="{shape_id}" points="{fmt(catmull_rom(pts))}"/>')
    cx, cy, rx, ry = head
    lines.append(f'    <ellipse cx="{cx}" cy="{cy}" rx="{rx}" ry="{ry}"/>')
    for rid, pts, layer in regions:
        layer_attr = ' data-layer="arm"' if layer == "arm" else ""
        lines.append(f'    <polygon id="{rid}"{layer_attr} fill="none" points="{fmt(pts)}"/>')
    lines += ["  </g>", "</svg>", ""]
    with open(os.path.join(OUT_DIR, file_name), "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines))


def main():
    mirror_note = "Mirror convention: screen-left = the user's own left side."
    write_svg("body_front.svg", "front body map", mirror_note + " (like looking in a mirror)",
              (100, 30, 17, 22), [("shape_body", FRONT_OUTLINE)], front_back_regions("front"))
    write_svg("body_back.svg", "back body map", mirror_note + " (natural when viewed from behind)",
              (100, 30, 17, 22), [("shape_body", FRONT_OUTLINE)], front_back_regions("back"))
    write_svg("body_left.svg", "left side body map", "Facing screen-left.",
              (98, 30, 16, 21),
              [("shape_body", SIDE_BODY_OUTLINE), ("shape_arm", SIDE_ARM_OUTLINE)],
              side_regions("left_side"))

    def mirror_regions(regions):
        return [(rid.replace("left_side_", "right_side_"), list(reversed(mirror_x(p))), layer)
                for rid, p, layer in regions]

    write_svg("body_right.svg", "right side body map", "Facing screen-right.",
              (102, 30, 16, 21),
              [("shape_body", list(reversed(mirror_x(SIDE_BODY_OUTLINE)))),
               ("shape_arm", list(reversed(mirror_x(SIDE_ARM_OUTLINE))))],
              mirror_regions(side_regions("left_side")))


if __name__ == "__main__":
    main()
