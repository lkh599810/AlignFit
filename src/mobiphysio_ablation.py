"""Feature-group ablation for MobiPhysio (binary correctness task, MLP).

Mirrors `src.ablation` but on the 69-d MobiPhysio summary features. Compares MLP
test performance across feature subsets defined by the DB's `feature_names`
(group: upper / lower / symmetry; stat: mean / std / range / max / symmetry).

NOTE: the synthetic pipeline's summary-vs-sequence (MLP-vs-1D-CNN) axis is dropped
here because MobiPhysio has no raw sequences (no 1D-CNN). Reuses the existing
`MLP` + `train_torch` harness unchanged.

Run:  python -m src.mobiphysio_ablation
"""
from __future__ import annotations

import os

import pandas as pd

from src import config, evaluate, mobiphysio_dataset as mobi
from src.models import MLP
from src.train import Standardizer, predict, train_torch

OUT_MD = os.path.join(config.RESULTS_DIR, "mobiphysio_ablation_results.md")
OUT_CSV = os.path.join(config.RESULTS_DIR, "mobiphysio_ablation_results.csv")

SYMMETRY_STATS = ["symdiff", "heightdiff"]


def _mlp_on(feature_idx):
    tr = mobi.load_summary("train", feature_idx=feature_idx)
    va = mobi.load_summary("val", feature_idx=feature_idx)
    te = mobi.load_summary("test", feature_idx=feature_idx)
    std = Standardizer().fit(tr["X"])
    Xtr, Xva, Xte = std.transform(tr["X"]), std.transform(va["X"]), std.transform(te["X"])
    model = MLP(Xtr.shape[1], 2, hidden_sizes=(128, 64), dropout=0.3)
    model, _ = train_torch(model, Xtr, tr["y_binary"], Xva, va["y_binary"],
                           lr=1e-3, batch_size=32, weight_decay=1e-4,
                           epochs=200, patience=25)
    return evaluate.compute_metrics(te["y_binary"], predict(model, Xte))


def run():
    groups = {
        "all_features": mobi.feature_indices_by_group(),
        "upper_only": mobi.feature_indices_by_group(group=["upper", "symmetry"]),
        "lower_only": mobi.feature_indices_by_group(group=["lower", "symmetry"]),
        "symmetry_only": mobi.feature_indices_by_group(group=["symmetry"]),
        "mean_only": mobi.feature_indices_by_group(stat=["mean"]),
        "mean_std": mobi.feature_indices_by_group(stat=["mean", "std"]),
        "mean_std_range": mobi.feature_indices_by_group(stat=["mean", "std", "range"]),
        "no_symmetry": mobi.feature_indices_by_group(
            stat=["mean", "std", "range", "max"]),
    }
    rows = []
    for label, idx in groups.items():
        m = _mlp_on(idx)
        rows.append({"feature_group": label, "n_features": len(idx),
                     "model": "MLP", **{k: round(v, 4) for k, v in m.items()}})
        print(f"  {label:16s} n={len(idx):3d}  f1={m['f1_macro']:.4f}")

    df = pd.DataFrame(rows)
    df.to_csv(OUT_CSV, index=False)
    with open(OUT_MD, "w", encoding="utf-8") as f:
        f.write("# MobiPhysio feature-group ablation - task: binary_correctness\n\n")
        f.write("Feature groups from `mobiphysio_features.sqlite.feature_names` "
                "(group: upper/lower/symmetry; stat: mean/std/range/max + "
                "symmetry/height-difference). No 1D-CNN axis (no raw sequences).\n\n")
        f.write(evaluate.df_to_markdown(df) + "\n")
    print("\nAblation table:\n", df.to_string(index=False))
    return df


if __name__ == "__main__":
    run()
