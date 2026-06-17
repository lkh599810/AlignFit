"""Shared evaluation utilities (Phase 7): metrics, confusion matrices, and the
model-comparison table. Used by the baseline, MLP, CNN, tuning and ablation code.
"""
from __future__ import annotations

import os
import time

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
import seaborn as sns
from sklearn.metrics import (accuracy_score, confusion_matrix, f1_score,
                             precision_score, recall_score)

from src import config


def compute_metrics(y_true, y_pred):
    """Accuracy + macro precision/recall/F1 (zero_division safe)."""
    return {
        "accuracy": float(accuracy_score(y_true, y_pred)),
        "precision_macro": float(precision_score(y_true, y_pred, average="macro", zero_division=0)),
        "recall_macro": float(recall_score(y_true, y_pred, average="macro", zero_division=0)),
        "f1_macro": float(f1_score(y_true, y_pred, average="macro", zero_division=0)),
    }


def save_confusion_matrix(y_true, y_pred, labels, title, fname):
    cm = confusion_matrix(y_true, y_pred, labels=range(len(labels)))
    fig, ax = plt.subplots(figsize=(max(4, len(labels) * 0.7), max(3.5, len(labels) * 0.6)))
    sns.heatmap(cm, annot=True, fmt="d", cmap="Blues", cbar=False,
                xticklabels=labels, yticklabels=labels, ax=ax)
    ax.set_xlabel("Predicted")
    ax.set_ylabel("True")
    ax.set_title(title)
    plt.xticks(rotation=45, ha="right")
    plt.yticks(rotation=0)
    plt.tight_layout()
    path = os.path.join(config.RESULTS_DIR, fname)
    fig.savefig(path, dpi=120)
    plt.close(fig)
    return path


def measure_inference_ms(predict_fn, X, repeats=3):
    """Mean inference time in ms per sample over `repeats` passes."""
    n = max(1, len(X))
    best = None
    for _ in range(repeats):
        t0 = time.perf_counter()
        predict_fn(X)
        dt = (time.perf_counter() - t0) / n * 1000.0
        best = dt if best is None else min(best, dt)
    return float(best)


def file_size_kb(path):
    return round(os.path.getsize(path) / 1024.0, 1) if path and os.path.exists(path) else None


# --- comparison table aggregation -------------------------------------------
RESULTS_CSV = os.path.join(config.RESULTS_DIR, "comparison.csv")
RESULTS_MD = os.path.join(config.RESULTS_DIR, "comparison.md")


def append_result(record: dict):
    """Append one model/task result row to results/comparison.csv (dedup by model+task)."""
    cols = ["model", "task", "accuracy", "precision_macro", "recall_macro",
            "f1_macro", "infer_ms_per_sample", "model_size_kb", "notes"]
    if os.path.exists(RESULTS_CSV):
        df = pd.read_csv(RESULTS_CSV)
    else:
        df = pd.DataFrame(columns=cols)
    for c in cols:
        record.setdefault(c, None)
    df = df[~((df["model"] == record["model"]) & (df["task"] == record["task"]))]
    new_row = pd.DataFrame([record])[cols]
    df = new_row if df.empty else pd.concat([df, new_row], ignore_index=True)
    df.to_csv(RESULTS_CSV, index=False)
    write_markdown(df)
    return df


def df_to_markdown(df: pd.DataFrame) -> str:
    """Minimal markdown-table renderer (avoids the optional `tabulate` dep)."""
    cols = list(df.columns)
    head = "| " + " | ".join(str(c) for c in cols) + " |"
    sep = "| " + " | ".join("---" for _ in cols) + " |"
    rows = []
    for _, r in df.iterrows():
        rows.append("| " + " | ".join("" if pd.isna(v) else str(v) for v in r) + " |")
    return "\n".join([head, sep, *rows])


def write_markdown(df=None):
    if df is None:
        df = pd.read_csv(RESULTS_CSV)
    df = df.sort_values(["task", "f1_macro"], ascending=[True, False])
    lines = ["# Model comparison (test set)\n"]
    for task in df["task"].unique():
        sub = df[df["task"] == task].copy()
        lines.append(f"\n## Task: {task}\n")
        show = sub[["model", "accuracy", "precision_macro", "recall_macro",
                    "f1_macro", "infer_ms_per_sample", "model_size_kb"]].round(4)
        lines.append(df_to_markdown(show))
        lines.append("")
    with open(RESULTS_MD, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))
