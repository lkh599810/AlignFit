"""Feature engineering for the real deep-squat pipeline.

Same *methodology* as the synthetic 273-dim pipeline (per-dimension summary
statistics over the whole repetition), adapted to the real data's 117 Vicon-
angle dimensions:

    mean, std, range (max-min), max   per dim   ->  117 * 4 = 468 summary features.

Differences from the synthetic pipeline, documented honestly:
  * 468 dims instead of 273: the real Vicon data has 117 angle dims/frame, vs the
    66 dims of our synthetic 22-joint skeleton.
  * The synthetic pipeline's 9 left/right symmetry features are NOT reproduced:
    the 117-dim Vicon-angle layout has no documented L/R joint pairing here, so a
    symmetry score cannot be computed without guessing the joint map.

The 1D-CNN consumes the length-aligned (240, 117) sequences directly (the source
preprocessing already aligned every repetition to 240 frames).

A real feature DB is written to db/features_real.sqlite (separate from the
synthetic db/features.sqlite, which is preserved).
"""
from __future__ import annotations

import os
import sqlite3

import numpy as np

from src import config, real_dataset

STATS = ("mean", "std", "range", "max")
N_SUMMARY = real_dataset.REAL_DIM * len(STATS)   # 468
FEATURES_REAL_DB = os.path.join(config.DB_DIR, "features_real.sqlite")


def summary_vector(seq: np.ndarray) -> np.ndarray:
    """(T, 117) -> (468,) per-dim [mean, std, range, max] concatenated."""
    mean = seq.mean(axis=0)
    std = seq.std(axis=0)
    rng = seq.max(axis=0) - seq.min(axis=0)
    mx = seq.max(axis=0)
    return np.concatenate([mean, std, rng, mx]).astype(np.float32)


def feature_names():
    names = []
    for stat in STATS:
        for d in range(real_dataset.REAL_DIM):
            names.append((len(names), f"{stat}_d{d:03d}", stat, d))
    return names


def build(write_db: bool = True) -> dict:
    """Compute summary + sequence features for all real sequences.

    Returns a dict of stacked arrays keyed by split-agnostic full arrays plus a
    parallel metadata list; callers slice by `split`.
    """
    recs = real_dataset.load()
    X_summary = np.stack([summary_vector(r["seq"]) for r in recs])          # (N,468)
    X_seq = np.stack([r["seq"] for r in recs]).astype(np.float32)           # (N,240,117)
    y = np.array([r["label_binary"] for r in recs], dtype=np.int64)
    subject = np.array([r["subject"] for r in recs], dtype=np.int64)
    split = np.array([r["split"] for r in recs])
    seq_ids = [r["seq_id"] for r in recs]

    if write_db:
        _write_db(recs, X_summary)

    return {
        "X_summary": X_summary, "X_seq": X_seq, "y": y,
        "subject": subject, "split": split, "seq_ids": seq_ids,
    }


def _write_db(recs, X_summary):
    con = sqlite3.connect(FEATURES_REAL_DB)
    cur = con.cursor()
    cur.executescript(
        """
        DROP TABLE IF EXISTS sequence_meta_real;
        DROP TABLE IF EXISTS summary_features_real;
        DROP TABLE IF EXISTS feature_names_real;
        CREATE TABLE sequence_meta_real (
            seq_id TEXT PRIMARY KEY, subject_id INTEGER, correctness TEXT,
            label_binary INTEGER, split TEXT, n_frames INTEGER, n_dims INTEGER);
        CREATE TABLE feature_names_real (
            idx INTEGER PRIMARY KEY, name TEXT, stat TEXT, dim INTEGER);
        """
    )
    fcols = ", ".join(f"f{i:03d} REAL" for i in range(N_SUMMARY))
    cur.execute(f"CREATE TABLE summary_features_real (seq_id TEXT PRIMARY KEY, {fcols})")

    cur.executemany(
        "INSERT INTO feature_names_real VALUES (?,?,?,?)", feature_names())
    cur.executemany(
        "INSERT INTO sequence_meta_real VALUES (?,?,?,?,?,?,?)",
        [(r["seq_id"], r["subject"], r["correctness"], r["label_binary"],
          r["split"], real_dataset.REAL_LEN, real_dataset.REAL_DIM) for r in recs])
    ph = ",".join("?" * (N_SUMMARY + 1))
    cur.executemany(
        f"INSERT INTO summary_features_real VALUES ({ph})",
        [(r["seq_id"], *[float(v) for v in X_summary[i]])
         for i, r in enumerate(recs)])
    con.commit()
    con.close()


if __name__ == "__main__":
    d = build()
    print(f"summary features: {d['X_summary'].shape}  (expected (180,{N_SUMMARY}))")
    print(f"sequence tensor : {d['X_seq'].shape}")
    print(f"feature DB written -> {FEATURES_REAL_DB}")
