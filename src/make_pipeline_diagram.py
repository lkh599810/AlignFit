"""Draw the model-pipeline block diagram for the presentation.

Output: figures/fig0_pipeline.png

A left-to-right "snake" flow that mirrors the real pipeline:
  video -> MediaPipe pose -> 69-d summary features (feature DB)
        -> models (baseline / MLP / 1D-CNN)
        -> predictions (movement quality + exercise class)
        -> recommendation linker (recommendation DB) -> homecare output.
Run:  python -m src.make_pipeline_diagram
"""
from __future__ import annotations

import os

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyArrowPatch, FancyBboxPatch

from src import config

FIG_DIR = os.path.join(config.ROOT, "figures")

TEAL = "#2f8f83"
TEAL_SOFT = "#e4f1ef"
BLUE = "#5b8def"
ORANGE = "#e08a3c"
GREY = "#6b7682"

BOX_W, BOX_H = 3.0, 1.55


def _box(ax, cx, cy, title, subtitle, edge=TEAL, face="white", title_size=11):
    x, y = cx - BOX_W / 2, cy - BOX_H / 2
    ax.add_patch(FancyBboxPatch(
        (x, y), BOX_W, BOX_H, boxstyle="round,pad=0.06,rounding_size=0.18",
        linewidth=1.8, edgecolor=edge, facecolor=face, mutation_aspect=1))
    ax.text(cx, cy + 0.28, title, ha="center", va="center",
            fontsize=title_size, fontweight="bold", color="#1f2933")
    ax.text(cx, cy - 0.34, subtitle, ha="center", va="center",
            fontsize=8.0, color=GREY)


def _arrow(ax, x1, y1, x2, y2):
    ax.add_patch(FancyArrowPatch(
        (x1, y1), (x2, y2), arrowstyle="-|>", mutation_scale=18,
        linewidth=1.8, color="#3a4750", shrinkA=2, shrinkB=2))


def build():
    os.makedirs(FIG_DIR, exist_ok=True)
    fig, ax = plt.subplots(figsize=(14, 7.4))
    ax.set_xlim(0, 14)
    ax.set_ylim(0, 7.6)
    ax.axis("off")

    y_top, y_bot = 5.4, 1.9
    xs = [2.1, 5.5, 8.9, 12.3]   # column centres

    # ---- Row 1 (left -> right) -------------------------------------------
    _box(ax, xs[0], y_top, "1. Input video",
         "exercise clip (MobiPhysio)", edge=GREY)
    _box(ax, xs[1], y_top, "2. Pose extraction",
         "MediaPipe PoseLandmarker\n(per-frame 2D landmarks)")
    _box(ax, xs[2], y_top, "3. Feature aggregation",
         "joint angles -> 69-d summary\n(mean/std/range/max + L/R symmetry)")
    _box(ax, xs[3], y_top, "4. Feature DB",
         "sqlite summary_features\n(subject-wise split)")

    for i in range(3):
        _arrow(ax, xs[i] + BOX_W / 2, y_top, xs[i + 1] - BOX_W / 2, y_top)

    # connector: row1 right -> row2 right (down)
    _arrow(ax, xs[3], y_top - BOX_H / 2, xs[3], y_bot + BOX_H / 2)

    # ---- Row 2 (right -> left) -------------------------------------------
    _box(ax, xs[3], y_bot, "5. Models",
         "baseline / MLP / 1D-CNN\n(PyTorch, CPU)", edge=BLUE)
    _box(ax, xs[2], y_bot, "6. Predictions",
         "movement quality (correct / attention)\n+ exercise class (E01-E09)", edge=BLUE)
    _box(ax, xs[1], y_bot, "7. Recommendation",
         "exercise -> region + pattern\n-> recommendation DB lookup", edge=ORANGE)
    _box(ax, xs[0], y_bot, "8. Homecare output",
         "exercises + YouTube\n+ non-diagnostic caution", edge=ORANGE,
         face=TEAL_SOFT)

    for i in (3, 2, 1):
        _arrow(ax, xs[i] - BOX_W / 2, y_bot, xs[i - 1] + BOX_W / 2, y_bot)

    # ---- Title + framing notes ------------------------------------------
    ax.text(7, 7.15, "AlignFit - Movement-quality model pipeline",
            ha="center", fontsize=15, fontweight="bold")

    # offline (training) vs live (inference) brackets
    ax.text(7.0, 6.45, "Training (offline): UI-PRMD + MobiPhysio  →  feature DB  →  "
            "model training / tuning / ablation",
            ha="center", fontsize=8.5, color=GREY, style="italic")
    ax.text(7.0, 0.55, "Live demo: FastAPI  POST /predict  +  WebView  GET /demo  "
            "(video upload → result screen)",
            ha="center", fontsize=8.5, color=GREY, style="italic")

    fig.tight_layout()
    out = os.path.join(FIG_DIR, "fig0_pipeline.png")
    fig.savefig(out, dpi=150, bbox_inches="tight")
    plt.close(fig)
    return out


if __name__ == "__main__":
    print("generated:", os.path.relpath(build(), config.ROOT))
