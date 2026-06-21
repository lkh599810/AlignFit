"""End-to-end MobiPhysio run — PARALLEL to the UI-PRMD pipelines (never merged).

Reuses the existing model definitions (`src.models.MLP`), training harness
(`src.train.train_torch` / `Standardizer` / `predict`) and metric/plot utilities
(`src.evaluate`) unchanged — exactly the pattern `src.real_run` uses for the
avakanski real data. All outputs carry a `mobi_`/`mobiphysio_` prefix so the
synthetic and avakanski artifacts are left untouched.

Two tasks, on the held-out TEST subjects:
  * binary_correctness  — correct vs incorrect movement quality
  * exercise_9class     — which of the 9 MobiPhysio exercises (E01..E09)

Models per task:
  * majority         — predicts the majority train class (sanity floor)
  * nearest_centroid — nearest-centroid on standardized 69-d summary features
  * MLP              — over the 69-d summary features

NOTE: MobiPhysio ships only the 69-d summary features (no raw per-frame
sequences), so the sequence-based 1D-CNN of the other pipelines is intentionally
omitted here — documented as a limitation in the report.

Outputs: models/mobi_*.pt, results/cm_mobi_*.png,
results/mobiphysio_comparison.{csv,md}, results/cross_dataset_comparison.md,
report/mobiphysio_results.md.

Run:  python -m src.mobiphysio_run
"""
from __future__ import annotations

import os

import numpy as np
import pandas as pd
import torch
from sklearn.neighbors import NearestCentroid

from src import config, evaluate, mobiphysio_dataset as mobi
from src.models import MLP, count_params
from src.train import Standardizer, predict, train_torch

MOBI_CSV = os.path.join(config.RESULTS_DIR, "mobiphysio_comparison.csv")
MOBI_MD = os.path.join(config.RESULTS_DIR, "mobiphysio_comparison.md")
MOBI_REPORT = os.path.join(config.REPORT_DIR, "mobiphysio_results.md")
CROSS_MD = os.path.join(config.RESULTS_DIR, "cross_dataset_comparison.md")

TASKS = {
    "binary_correctness": {"ykey": "y_binary", "labels": ["correct", "incorrect"]},
    "exercise_9class": {"ykey": "y_exercise", "labels": None},  # filled at runtime
}


def _run_task(task: str, cfg: dict):
    tr = mobi.load_summary("train")
    va = mobi.load_summary("val")
    te = mobi.load_summary("test")
    yk = cfg["ykey"]
    labels = cfg["labels"] or mobi.exercise_names()
    n_classes = len(labels)

    std = Standardizer().fit(tr["X"])
    Xtr, Xva, Xte = std.transform(tr["X"]), std.transform(va["X"]), std.transform(te["X"])
    ytr, yva, yte = tr[yk], va[yk], te[yk]
    rows = []

    # ---- baseline 1: majority class ----------------------------------------
    maj = int(np.bincount(ytr).argmax())
    pred = np.full_like(yte, maj)
    m = evaluate.compute_metrics(yte, pred)
    evaluate.save_confusion_matrix(yte, pred, labels,
                                   f"Majority baseline - MobiPhysio {task}",
                                   f"cm_mobi_majority_{task}.png")
    rows.append({"model": "majority", **m,
                 "notes": f"always predicts class {maj}"})

    # ---- baseline 2: nearest centroid --------------------------------------
    nc = NearestCentroid().fit(Xtr, ytr)
    pred = nc.predict(Xte)
    m = evaluate.compute_metrics(yte, pred)
    evaluate.save_confusion_matrix(yte, pred, labels,
                                   f"Nearest-centroid - MobiPhysio {task}",
                                   f"cm_mobi_nearest_centroid_{task}.png")
    rows.append({"model": "nearest_centroid", **m, "notes": "69-d summary feats"})

    # ---- MLP over summary features -----------------------------------------
    mlp = MLP(Xtr.shape[1], n_classes, hidden_sizes=(128, 64), dropout=0.3)
    mlp, best_val = train_torch(mlp, Xtr, ytr, Xva, yva, lr=1e-3, batch_size=32,
                                weight_decay=1e-4, epochs=200, patience=25)
    pred = predict(mlp, Xte)
    m = evaluate.compute_metrics(yte, pred)
    ckpt = os.path.join(config.MODELS_DIR, f"mobi_mlp_{task}.pt")
    torch.save({"state_dict": mlp.state_dict(), "mu": std.mu, "sd": std.sd,
                "hidden_sizes": (128, 64), "dropout": 0.3}, ckpt)
    evaluate.save_confusion_matrix(yte, pred, labels, f"MLP - MobiPhysio {task}",
                                   f"cm_mobi_mlp_{task}.png")
    rows.append({"model": "mlp", **m,
                 "notes": f"69-d feats; params={count_params(mlp)}; val_acc={best_val:.3f}"})

    counts = (f"train={len(ytr)}, val={len(yva)}, test={len(yte)}; "
              f"{n_classes} classes")
    return rows, counts


def run():
    all_rows = {}
    counts = {}
    for task, cfg in TASKS.items():
        rows, cnt = _run_task(task, cfg)
        all_rows[task] = rows
        counts[task] = cnt
        print(f"\n=== MobiPhysio {task} (held-out test subjects) ===")
        for r in rows:
            print(f"  {r['model']:17s} F1={r['f1_macro']:.3f}  acc={r['accuracy']:.3f}")
    _write_results(all_rows, counts)
    _write_cross_dataset(all_rows)
    return all_rows


def _df_for(rows):
    cols = ["model", "accuracy", "precision_macro", "recall_macro", "f1_macro", "notes"]
    return pd.DataFrame(rows)[cols].round(4)


def _write_results(all_rows, counts):
    md = [
        "# MobiPhysio results - parallel movement-quality benchmark\n",
        "**Data:** MobiPhysio summary-feature DB (`db/mobiphysio_features.sqlite`), "
        "9 shoulder/wrist/hip/back exercises (E01-E09), 24 subjects, 534 sequences.",
        "**Split:** subject-wise train/val/test as stored in `sequence_meta.split` "
        "(no subject crosses folds).",
        "**Features:** 69-d per-region summary stats (mean/std/range/max + L/R "
        "symmetry + height-difference features).",
        "**Models:** reuse the existing `MLP` + training/eval code unchanged "
        "(same harness as the synthetic and avakanski pipelines).\n",
        "> **Kept parallel, not merged:** MobiPhysio is reported side by side with "
        "the UI-PRMD results; the datasets are never concatenated.\n",
        "> **No 1D-CNN here (honest limitation):** the MobiPhysio DB provides only "
        "the 69-d `summary_features` table - there are NO raw per-frame sequences - "
        "so the sequence-based 1D-CNN used on the synthetic/avakanski data cannot "
        "be run. Only summary-feature models (majority / nearest-centroid / MLP) "
        "are reported.\n",
    ]
    for task in TASKS:
        md += [f"## Task: {task}\n",
               f"Sample counts: {counts[task]}.\n",
               evaluate.df_to_markdown(_df_for(all_rows[task])),
               f"\nConfusion matrices: `results/cm_mobi_*_{task}.png`\n"]
    md += [
        "## Limitations (honest)",
        "- **Summary-feature only:** no raw frames in the DB, so no temporal "
        "1D-CNN; the MLP sees only per-region summary statistics.",
        "- **Imbalanced 9-class task:** exercise classes range from 40 to 80 "
        "sequences, so macro-F1 is the fair headline metric (not accuracy).",
        "- **Distinct schema from UI-PRMD:** 69-d features and 9 exercises here vs "
        "the 273-d/10-exercise synthetic and 468-d/1-exercise avakanski sets - the "
        "datasets are comparable in *protocol* (subject-wise split, summary-MLP), "
        "not in raw feature dimensionality. See the cross-dataset table.",
    ]
    df_bin = _df_for(all_rows["binary_correctness"])
    df_bin.to_csv(MOBI_CSV, index=False)
    text = "\n".join(md)
    for path in (MOBI_MD, MOBI_REPORT):
        with open(path, "w", encoding="utf-8") as f:
            f.write(text)


def _write_cross_dataset(all_rows):
    """Cross-dataset comparison on the SHARED binary correct/incorrect task.

    Pulls the avakanski (UI-PRMD real) binary numbers from results/real_comparison.csv
    if present, and pairs them with the MobiPhysio binary numbers. Parallel view -
    NOT a merged training set.
    """
    rows = []
    real_csv = os.path.join(config.RESULTS_DIR, "real_comparison.csv")
    if os.path.exists(real_csv):
        rdf = pd.read_csv(real_csv)
        for _, r in rdf.iterrows():
            rows.append({"dataset": "UI-PRMD deep-squat (avakanski, real)",
                         "model": r["model"], "accuracy": round(r["accuracy"], 4),
                         "f1_macro": round(r["f1_macro"], 4)})
    for r in all_rows["binary_correctness"]:
        rows.append({"dataset": "MobiPhysio (9 exercises)", "model": r["model"],
                     "accuracy": round(r["accuracy"], 4),
                     "f1_macro": round(r["f1_macro"], 4)})
    df = pd.DataFrame(rows)
    md = [
        "# Cross-dataset comparison - binary correct/incorrect (PARALLEL, not merged)\n",
        "Both datasets evaluated with the **same protocol** (subject-wise split, "
        "summary-feature models, our-baselines-only policy). The two datasets are "
        "reported side by side and were **never concatenated or co-trained**; each "
        "model is trained and tested entirely within one dataset.\n",
        evaluate.df_to_markdown(df),
        "\n**Reading:** the comparison is across *protocol behaviour*, not raw "
        "feature spaces (UI-PRMD = 468-d/1 exercise, MobiPhysio = 69-d/9 exercises). "
        "Treat it as 'does the same lightweight summary-MLP recipe generalize to a "
        "second, independent dataset?', not a single-leaderboard ranking.",
    ]
    with open(CROSS_MD, "w", encoding="utf-8") as f:
        f.write("\n".join(md))


if __name__ == "__main__":
    run()
