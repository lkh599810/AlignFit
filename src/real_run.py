"""End-to-end REAL-data run: binary correct/incorrect movement-quality
classification on the avakanski UI-PRMD deep-squat data.

No synthetic data, no perturbation, leakage-free subject-wise split (train
s01-s06 / val s07-s08 / test s09-s10). Reuses the model definitions, training
harness and metric/plotting utilities from the existing pipeline WITHOUT
modifying them; all outputs use a `real_`/`*_real` prefix so the synthetic
benchmark artifacts are left untouched.

Models compared on the held-out TEST subjects:
  * majority      - predicts the majority train class (sanity floor)
  * nearest-cent. - nearest-centroid on standardized 468-d summary features
  * MLP           - over the 468-d summary features
  * 1D-CNN        - over the (240, 117) length-aligned sequences

Outputs: models/real_*.pt, results/cm_real_*.png, results/real_comparison.{csv,md},
report/real_data_results.md.

Run:  python -m src.real_run
"""
from __future__ import annotations

import os

import numpy as np
import pandas as pd
import torch
from sklearn.neighbors import NearestCentroid

from src import config, evaluate, real_dataset, real_features
from src.models import CNN1D, MLP, count_params
from src.train import Standardizer, predict, train_torch

CLASS_LABELS = ["correct", "incorrect"]
REAL_CSV = os.path.join(config.RESULTS_DIR, "real_comparison.csv")
REAL_MD = os.path.join(config.RESULTS_DIR, "real_comparison.md")
REAL_REPORT = os.path.join(config.REPORT_DIR, "real_data_results.md")


def _split_masks(split):
    return (split == "train"), (split == "val"), (split == "test")


def run():
    d = real_features.build()
    tr, va, te = _split_masks(d["split"])
    y, yseq = d["y"], d["y"]
    rows = []

    # ---- summary-feature views (standardized on train only) ----------------
    std = Standardizer().fit(d["X_summary"][tr])
    Xs_tr = std.transform(d["X_summary"][tr])
    Xs_va = std.transform(d["X_summary"][va])
    Xs_te = std.transform(d["X_summary"][te])
    ytr, yva, yte = y[tr], y[va], y[te]

    # ---- baseline 1: majority class ----------------------------------------
    maj = int(np.bincount(ytr).argmax())
    pred = np.full_like(yte, maj)
    m = evaluate.compute_metrics(yte, pred)
    evaluate.save_confusion_matrix(yte, pred, CLASS_LABELS,
                                   "Majority baseline - real deep-squat",
                                   "cm_real_majority.png")
    rows.append({"model": "majority", **m, "notes": f"always predicts '{CLASS_LABELS[maj]}'"})

    # ---- baseline 2: nearest centroid --------------------------------------
    nc = NearestCentroid().fit(Xs_tr, ytr)
    pred = nc.predict(Xs_te)
    m = evaluate.compute_metrics(yte, pred)
    evaluate.save_confusion_matrix(yte, pred, CLASS_LABELS,
                                   "Nearest-centroid - real deep-squat",
                                   "cm_real_nearest_centroid.png")
    rows.append({"model": "nearest_centroid", **m, "notes": "468-d summary feats"})

    # ---- MLP over summary features -----------------------------------------
    mlp = MLP(Xs_tr.shape[1], 2, hidden_sizes=(128, 64), dropout=0.3)
    mlp, best_val = train_torch(mlp, Xs_tr, ytr, Xs_va, yva, lr=1e-3,
                                batch_size=32, weight_decay=1e-4, epochs=200,
                                patience=25)
    pred = predict(mlp, Xs_te)
    m = evaluate.compute_metrics(yte, pred)
    ckpt = os.path.join(config.MODELS_DIR, "real_mlp_binary_correctness.pt")
    torch.save({"state_dict": mlp.state_dict(), "mu": std.mu, "sd": std.sd}, ckpt)
    evaluate.save_confusion_matrix(yte, pred, CLASS_LABELS,
                                   "MLP - real deep-squat", "cm_real_mlp.png")
    rows.append({"model": "mlp", **m,
                 "notes": f"468-d feats; params={count_params(mlp)}; val_acc={best_val:.3f}"})

    # ---- 1D-CNN over sequences ---------------------------------------------
    cnn = CNN1D(real_dataset.REAL_DIM, 2, channels=(32, 64), kernel_size=5, dropout=0.3)
    cnn, best_val = train_torch(cnn, d["X_seq"][tr], yseq[tr], d["X_seq"][va], yseq[va],
                                lr=1e-3, batch_size=32, weight_decay=1e-4, epochs=200,
                                patience=25)
    pred = predict(cnn, d["X_seq"][te])
    m = evaluate.compute_metrics(yte, pred)
    ckpt = os.path.join(config.MODELS_DIR, "real_cnn1d_binary_correctness.pt")
    torch.save({"state_dict": cnn.state_dict()}, ckpt)
    evaluate.save_confusion_matrix(yte, pred, CLASS_LABELS,
                                   "1D-CNN - real deep-squat", "cm_real_cnn1d.png")
    rows.append({"model": "cnn1d", **m,
                 "notes": f"(240,117) seq; params={count_params(cnn)}; val_acc={best_val:.3f}"})

    _write_results(rows, ytr, yva, yte)
    print("\n=== REAL deep-squat binary correctness (test subjects s09-s10) ===")
    for r in rows:
        print(f"  {r['model']:17s} F1={r['f1_macro']:.3f}  acc={r['accuracy']:.3f}")
    return rows


def _write_results(rows, ytr, yva, yte):
    cols = ["model", "accuracy", "precision_macro", "recall_macro", "f1_macro", "notes"]
    df = pd.DataFrame(rows)[cols].round(4)
    df.to_csv(REAL_CSV, index=False)

    counts = (f"train={len(ytr)} (cor {int((ytr == 0).sum())}/inc {int((ytr == 1).sum())}), "
              f"val={len(yva)}, test={len(yte)} (cor {int((yte == 0).sum())}/inc {int((yte == 1).sum())})")
    md = [
        "# Real-data results - UI-PRMD deep squat (binary correct/incorrect)\n",
        "**Data:** real Vicon-captured deep-squat repetitions (avakanski reduced "
        "UI-PRMD set). No synthetic generation, no perturbation.",
        "**Label:** file-derived - correct file = `correct`, incorrect file = "
        "`incorrect` (real execution quality).",
        "**Split:** leakage-free subject-wise - train s01-s06 / val s07-s08 / "
        "test s09-s10. Subject ids recovered deterministically from the official "
        "`Prepare_Data_for_NN.m` reduction indices.",
        f"**Sample counts:** {counts}.",
        "**Features:** 468-d per-dim summary stats (mean/std/range/max over 117 "
        "Vicon angle dims) for MLP; length-aligned (240,117) sequences for 1D-CNN.\n",
        "## Test-set comparison\n",
        evaluate.df_to_markdown(df),
        "\n## Confusion matrices",
        "- `results/cm_real_majority.png`",
        "- `results/cm_real_nearest_centroid.png`",
        "- `results/cm_real_mlp.png`",
        "- `results/cm_real_cnn1d.png`\n",
        "## Limitations (honest)",
        "- **Small data:** only 10 subjects; the test fold is 2 subjects (36 "
        "sequences). F1 is therefore high-variance - treat differences of a few "
        "points as noise, not a ranking.",
        "- **Single exercise:** deep squat only (the only movement available in "
        "the accessible real reduced set), so there is no 10-class exercise task "
        "here - unlike the synthetic benchmark.",
        "- **Feature parity:** 468-d (117 dims x 4 stats) instead of the "
        "synthetic 273-d; left/right symmetry features are omitted (no documented "
        "L/R joint pairing in this 117-dim Vicon layout).",
        "- **Subject recovery assumption:** subjects are reconstructed from the "
        "source MATLAB reduction order; it is deterministic but relies on that "
        "script being the true provenance of `Data_*.csv`.\n",
        "## Relationship to the synthetic benchmark",
        "The synthetic 273-d pipeline (`build_dataset.py`, `db/features.sqlite`, "
        "etc.) is preserved unchanged and should be reported as a *controlled "
        "synthetic benchmark*. This file is the *real-data* result.",
    ]
    with open(REAL_MD, "w", encoding="utf-8") as f:
        f.write("\n".join(md))
    with open(REAL_REPORT, "w", encoding="utf-8") as f:
        f.write("\n".join(md))


if __name__ == "__main__":
    run()
