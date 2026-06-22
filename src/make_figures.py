"""Generate presentation-ready PNG figures from the results/ CSV files.

Outputs (under figures/):
  1. fig1_f1_by_dataset.png   - binary movement-quality F1 by dataset & model
  2. fig2_ablation.png         - feature-group ablation F1 (synthetic + MobiPhysio)
  3. fig3_cross_dataset.png    - cross-dataset generalization (same protocol)
  + figures/confusion_matrices/ - existing results/cm_*.png copied for tidiness

Labels are kept in English (matplotlib-safe, ML-standard) with explicit titles.
Run:  python -m src.make_figures
"""
from __future__ import annotations

import os
import shutil

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import pandas as pd

from src import config

FIG_DIR = os.path.join(config.ROOT, "figures")
CM_DIR = os.path.join(FIG_DIR, "confusion_matrices")
RES = config.RESULTS_DIR

# Consistent colours per model tier across all figures.
COLORS = {
    "Majority / rule": "#9aa6b2",
    "Nearest-centroid": "#5b8def",
    "MLP": "#2f8f83",
    "1D-CNN": "#e08a3c",
}


def _bar_labels(ax, bars, fmt="{:.2f}", fontsize=8):
    for b in bars:
        h = b.get_height()
        if h is None:
            continue
        ax.text(b.get_x() + b.get_width() / 2, h + 0.012, fmt.format(h),
                ha="center", va="bottom", fontsize=fontsize)


# ---------------------------------------------------------------------------
# Figure 1: binary movement-quality F1 by dataset and model
# ---------------------------------------------------------------------------
def fig1_f1_by_dataset():
    syn = pd.read_csv(os.path.join(RES, "comparison.csv"))
    syn = syn[syn["task"] == "binary_correctness"].set_index("model")["f1_macro"]
    real = pd.read_csv(os.path.join(RES, "real_comparison.csv")).set_index("model")["f1_macro"]
    mobi = pd.read_csv(os.path.join(RES, "mobiphysio_comparison.csv")).set_index("model")["f1_macro"]

    # dataset -> {tier: f1}; synthetic uses a rule baseline (no nearest-centroid).
    data = {
        "Synthetic UI-PRMD\n(273-d summary)": {
            "Majority / rule": syn.get("baseline_rule"),
            "MLP": syn.get("mlp"),
            "1D-CNN": syn.get("cnn1d"),
        },
        "Real UI-PRMD\n(deep-squat, 468-d)": {
            "Majority / rule": real.get("majority"),
            "Nearest-centroid": real.get("nearest_centroid"),
            "MLP": real.get("mlp"),
            "1D-CNN": real.get("cnn1d"),
        },
        "MobiPhysio\n(9 exercises, 69-d)": {
            "Majority / rule": mobi.get("majority"),
            "Nearest-centroid": mobi.get("nearest_centroid"),
            "MLP": mobi.get("mlp"),
        },
    }

    tiers = ["Majority / rule", "Nearest-centroid", "MLP", "1D-CNN"]
    datasets = list(data.keys())
    n = len(datasets)
    width = 0.2

    fig, ax = plt.subplots(figsize=(9, 5.2))
    for i, tier in enumerate(tiers):
        xs, hs = [], []
        for j, ds in enumerate(datasets):
            v = data[ds].get(tier)
            if v is not None:
                xs.append(j + (i - 1.5) * width)
                hs.append(v)
        bars = ax.bar(xs, hs, width, label=tier, color=COLORS[tier])
        _bar_labels(ax, bars)

    ax.set_xticks(range(n))
    ax.set_xticklabels(datasets, fontsize=9)
    ax.set_ylabel("F1 (macro)")
    ax.set_ylim(0, 1.08)
    ax.set_title("Binary movement-quality: F1 (macro) by dataset and model",
                 fontsize=13, fontweight="bold")
    ax.legend(fontsize=8, ncol=4, loc="upper center",
              bbox_to_anchor=(0.5, -0.09), frameon=False)
    ax.grid(axis="y", alpha=0.3)
    ax.text(0.0, -0.2, "Baseline = rule for synthetic UI-PRMD, majority for the "
            "other datasets. Datasets evaluated in parallel (never co-trained).",
            transform=ax.transAxes, fontsize=7.5, color="#555")
    fig.tight_layout()
    out = os.path.join(FIG_DIR, "fig1_f1_by_dataset.png")
    fig.savefig(out, dpi=150, bbox_inches="tight")
    plt.close(fig)
    return out


# ---------------------------------------------------------------------------
# Figure 2: feature-group ablation (two datasets, horizontal bars)
# ---------------------------------------------------------------------------
def fig2_ablation():
    syn = pd.read_csv(os.path.join(RES, "ablation_results.csv"))
    mobi = pd.read_csv(os.path.join(RES, "mobiphysio_ablation_results.csv"))

    fig, axes = plt.subplots(1, 2, figsize=(12, 5.4))

    for ax, df, title in (
        (axes[0], syn, "Synthetic UI-PRMD (binary, 273-d)"),
        (axes[1], mobi, "MobiPhysio (binary, 69-d)"),
    ):
        labels = [f"{g}  (n={n})" for g, n in zip(df["feature_group"], df["n_features"])]
        f1 = df["f1_macro"].values
        ypos = range(len(df))
        best = f1.argmax()
        colors = ["#2f8f83" if k == best else "#9cc3bd" for k in range(len(df))]
        bars = ax.barh(list(ypos), f1, color=colors)
        ax.set_yticks(list(ypos))
        ax.set_yticklabels(labels, fontsize=8)
        ax.invert_yaxis()
        ax.set_xlim(0, 1.0)
        ax.set_xlabel("F1 (macro)")
        ax.set_title(title, fontsize=11, fontweight="bold")
        ax.grid(axis="x", alpha=0.3)
        for b, v in zip(bars, f1):
            ax.text(v + 0.012, b.get_y() + b.get_height() / 2, f"{v:.3f}",
                    va="center", fontsize=7.5)

    fig.suptitle("Feature-group ablation - F1 (macro)  (best group highlighted)",
                 fontsize=13, fontweight="bold")
    fig.tight_layout(rect=(0, 0, 1, 0.95))
    out = os.path.join(FIG_DIR, "fig2_ablation.png")
    fig.savefig(out, dpi=150, bbox_inches="tight")
    plt.close(fig)
    return out


# ---------------------------------------------------------------------------
# Figure 3: cross-dataset generalization (same protocol, parallel benchmark)
# ---------------------------------------------------------------------------
def fig3_cross_dataset():
    real = pd.read_csv(os.path.join(RES, "real_comparison.csv")).set_index("model")["f1_macro"]
    mobi = pd.read_csv(os.path.join(RES, "mobiphysio_comparison.csv")).set_index("model")["f1_macro"]

    data = {
        "Real UI-PRMD\n(deep-squat)": {
            "Majority / rule": real.get("majority"),
            "Nearest-centroid": real.get("nearest_centroid"),
            "MLP": real.get("mlp"),
            "1D-CNN": real.get("cnn1d"),
        },
        "MobiPhysio\n(9 exercises)": {
            "Majority / rule": mobi.get("majority"),
            "Nearest-centroid": mobi.get("nearest_centroid"),
            "MLP": mobi.get("mlp"),
        },
    }
    tiers = ["Majority / rule", "Nearest-centroid", "MLP", "1D-CNN"]
    datasets = list(data.keys())
    width = 0.18

    fig, ax = plt.subplots(figsize=(8, 5))
    for i, tier in enumerate(tiers):
        xs, hs = [], []
        for j, ds in enumerate(datasets):
            v = data[ds].get(tier)
            if v is not None:
                xs.append(j + (i - 1.5) * width)
                hs.append(v)
        bars = ax.bar(xs, hs, width, label=tier, color=COLORS[tier])
        _bar_labels(ax, bars)

    ax.set_xticks(range(len(datasets)))
    ax.set_xticklabels(datasets, fontsize=10)
    ax.set_ylabel("F1 (macro)")
    ax.set_ylim(0, 1.08)
    ax.set_title("Cross-dataset generalization (binary correctness)\n"
                 "same lightweight summary-MLP recipe, two independent datasets",
                 fontsize=12, fontweight="bold")
    ax.legend(fontsize=8, ncol=4, loc="upper center",
              bbox_to_anchor=(0.5, -0.1), frameon=False)
    ax.grid(axis="y", alpha=0.3)
    ax.text(0.0, -0.21, "Parallel benchmark: each model trained & tested within one "
            "dataset; never concatenated or co-trained.",
            transform=ax.transAxes, fontsize=7.5, color="#555")
    fig.tight_layout()
    out = os.path.join(FIG_DIR, "fig3_cross_dataset.png")
    fig.savefig(out, dpi=150, bbox_inches="tight")
    plt.close(fig)
    return out


def collect_confusion_matrices():
    os.makedirs(CM_DIR, exist_ok=True)
    copied = []
    for fn in sorted(os.listdir(RES)):
        if fn.startswith("cm_") and fn.endswith(".png"):
            shutil.copy2(os.path.join(RES, fn), os.path.join(CM_DIR, fn))
            copied.append(fn)
    return copied


if __name__ == "__main__":
    os.makedirs(FIG_DIR, exist_ok=True)
    outs = [fig1_f1_by_dataset(), fig2_ablation(), fig3_cross_dataset()]
    cms = collect_confusion_matrices()
    print("generated figures:")
    for o in outs:
        print(" -", os.path.relpath(o, config.ROOT))
    print(f"copied {len(cms)} confusion-matrix PNGs -> "
          f"{os.path.relpath(CM_DIR, config.ROOT)}")
