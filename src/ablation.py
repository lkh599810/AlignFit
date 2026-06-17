"""Phase 9 - ablation study on feature groups (binary correctness task, MLP).

Compares MLP test performance when trained on different feature subsets:
  - upper-body joints only      vs  lower-body joints only  vs  all joints
  - summary stat groups:        mean-only / +std / +range / full (mean+std+range+max)
  - symmetry features only
  - summary features (MLP)       vs  raw sequence (1D-CNN)        [feature-form ablation]

Note (documented in report): UI-PRMD angle data has no separate "position" channel,
so the angle-vs-position ablation requested in the spec is replaced by an
angle-statistic-group ablation; the summary-vs-sequence comparison covers the
"summary vs sequence" axis.

Run:  python -m src.ablation
"""
from __future__ import annotations

import os

import pandas as pd

from src import config, evaluate, features, train

TASK = "binary_correctness"
OUT_MD = os.path.join(config.RESULTS_DIR, "ablation_results.md")
OUT_CSV = os.path.join(config.RESULTS_DIR, "ablation_results.csv")


def _mlp_on_features(feature_idx, label):
    """Train MLP on a feature subset by temporarily monkeypatching load_summary."""
    orig = features.load_summary

    def patched(split=None, feature_idx=feature_idx):
        return orig(split=split, feature_idx=feature_idx)

    features.load_summary = patched
    try:
        _, metrics, val = train.train_mlp(TASK, tag=f"ablate_{label}", record=False)
    finally:
        features.load_summary = orig
    return metrics, val


def run():
    rows = []

    groups = {
        "all_joints": features.feature_indices_by_group(),
        "upper_only": features.feature_indices_by_group(body_group=["upper", "symmetry"]),
        "lower_only": features.feature_indices_by_group(body_group=["lower", "symmetry"]),
        "symmetry_only": features.feature_indices_by_group(body_group=["symmetry"]),
        "mean_only": features.feature_indices_by_group(stat=["mean"]),
        "mean_std": features.feature_indices_by_group(stat=["mean", "std"]),
        "mean_std_range": features.feature_indices_by_group(stat=["mean", "std", "range"]),
    }
    for label, idx in groups.items():
        metrics, val = _mlp_on_features(idx, label)
        rows.append({"feature_group": label, "n_features": len(idx),
                     "model": "MLP", **{k: round(v, 4) for k, v in metrics.items()}})
        print(f"  {label:16s} n={len(idx):3d}  f1={metrics['f1_macro']:.4f}")

    # feature-form ablation: full summary MLP vs sequence 1D-CNN
    _, m_mlp, _ = train.train_mlp(TASK, tag="ablate_full_summary", record=False)
    rows.append({"feature_group": "full_summary (MLP)",
                 "n_features": features.N_FEATURES, "model": "MLP",
                 **{k: round(v, 4) for k, v in m_mlp.items()}})
    _, m_cnn, _ = train.train_cnn(TASK, tag="ablate_sequence", record=False)
    rows.append({"feature_group": "raw_sequence (1D-CNN)",
                 "n_features": f"{features.SEQ_LEN}x{config.N_ANGLE_DIMS}", "model": "CNN1D",
                 **{k: round(v, 4) for k, v in m_cnn.items()}})

    df = pd.DataFrame(rows)
    df.to_csv(OUT_CSV, index=False)
    with open(OUT_MD, "w", encoding="utf-8") as f:
        f.write(f"# Feature-group ablation - task: {TASK}\n\n")
        f.write(evaluate.df_to_markdown(df) + "\n")
    print("\nAblation table:\n", df.to_string(index=False))
    return df


if __name__ == "__main__":
    run()
