"""MobiPhysio loader for the parallel (NOT merged) movement-quality benchmark.

The MobiPhysio feature DB (`db/mobiphysio_features.sqlite`) ships the same 3-table
layout as the synthetic `db/features.sqlite` (feature_names / sequence_meta /
summary_features), but with a 69-dim summary-feature form and 9 shoulder/wrist/
hip/back exercises across 24 subjects. The subject-wise split (train/val/test) is
already stored in `sequence_meta.split`.

IMPORTANT — kept parallel to UI-PRMD on purpose:
  * This module is fully self-contained and never touches the synthetic
    `features.sqlite` / avakanski pipelines; the two datasets are reported side by
    side, never concatenated.
  * Only the 69-dim `summary_features` exist here — there are NO raw per-frame
    sequences in the DB, so the sequence-based 1D-CNN of the synthetic/real
    pipelines cannot be run on MobiPhysio (documented as a limitation). Baseline /
    nearest-centroid / MLP all consume the 69-dim summary vector.

It mirrors the loader API of `src.features` (load_summary, feature_indices_by_*)
so the existing model/training/eval utilities are reused unchanged.
"""
from __future__ import annotations

import os
import sqlite3

import numpy as np
import pandas as pd

from src import config

MOBI_DB = os.path.join(config.DB_DIR, "mobiphysio_features.sqlite")
N_FEATURES = 69                       # f000..f068 summary features
N_EXERCISE_CLASSES = 9                # E01..E09
SPLITS = ("train", "val", "test")

# MobiPhysio exercise -> body region + AlignFit posture pattern (links the model
# output to the recommendation DB). Regions cover shoulder / wrist / hip / back.
MOBI_EXERCISE_TO_REGION = {
    "E01": config.REGION_UPPER_SHOULDER, "E02": config.REGION_UPPER_SHOULDER,
    "E03": config.REGION_UPPER_SHOULDER, "E04": config.REGION_UPPER_SHOULDER,
    "E05": config.REGION_UPPER_SHOULDER,
    "E06": config.REGION_UPPER_WRIST,
    "E07": config.REGION_LOWER_HIP,
    "E08": config.REGION_LOW_BACK, "E09": config.REGION_LOW_BACK,
}
MOBI_EXERCISE_TO_PATTERN = {
    "E01": "shoulder_asymmetry",   # Abduction
    "E02": "shoulder_mobility",    # Adduction
    "E03": "shoulder_rotation",    # Lateral Rotation
    "E04": "shoulder_rotation",    # Medial Rotation
    "E05": "shoulder_mobility",    # Circumduction
    "E06": "wrist_mobility",       # Wrist Extension
    "E07": "hip_mobility",         # Hip Joint Flexion
    "E08": "low_back_mobility",    # Lumber Flexion
    "E09": "low_back_mobility",    # Back Extension
}


def _fcols():
    return [f"f{i:03d}" for i in range(N_FEATURES)]


def feature_names() -> pd.DataFrame:
    """The feature_names table (idx, feat_col, name, stat, group), idx-ordered."""
    con = sqlite3.connect(MOBI_DB)
    df = pd.read_sql("SELECT * FROM feature_names ORDER BY idx", con)
    con.close()
    return df


def _meta_and_feats() -> pd.DataFrame:
    con = sqlite3.connect(MOBI_DB)
    meta = pd.read_sql("SELECT * FROM sequence_meta", con)
    feats = pd.read_sql("SELECT * FROM summary_features", con)
    con.close()
    return meta.merge(feats, on="seq_id")


def load_summary(split: str = None, feature_idx=None) -> dict:
    """Load 69-dim summary features (optionally one split / a feature subset).

    Mirrors `src.features.load_summary` so the same training/eval code is reused.
    """
    df = _meta_and_feats()
    if split is not None:
        df = df[df["split"] == split].reset_index(drop=True)
    fcols = _fcols()
    if feature_idx is not None:
        fcols = [f"f{i:03d}" for i in feature_idx]
    X = df[fcols].values.astype(np.float32)
    return {
        "X": X,
        "seq_id": df["seq_id"].values,
        "subject_id": df["subject_id"].values,
        "y_binary": df["label_binary"].values.astype(np.int64),
        "y_exercise": df["label_exercise"].values.astype(np.int64),
        "region": df["region"].values,
        "exercise_id": df["exercise_id"].values,
        "df": df,
    }


def feature_indices_by_group(group=None, stat=None):
    """Feature column indices filtered by `group` and/or `stat` (lists)."""
    fn = feature_names()
    idx = []
    for _, row in fn.iterrows():
        if group is not None and row["group"] not in group:
            continue
        if stat is not None and row["stat"] not in stat:
            continue
        idx.append(int(row["idx"]))
    return idx


def exercise_names() -> list[str]:
    """label_exercise index (0..8) -> exercise name, in label order."""
    df = _meta_and_feats()[["label_exercise", "exercise_id", "exercise_name"]]
    df = df.drop_duplicates().sort_values("label_exercise")
    return df["exercise_name"].tolist()


if __name__ == "__main__":
    d = load_summary()
    print(f"MobiPhysio summary features: X={d['X'].shape} (expected (534,{N_FEATURES}))")
    for sp in SPLITS:
        s = load_summary(sp)
        print(f"  {sp:5s}: n={len(s['y_binary']):3d}  "
              f"correct={int((s['y_binary']==0).sum())} incorrect={int((s['y_binary']==1).sum())}  "
              f"subjects={len(set(s['subject_id']))}")
    print("exercise classes:", exercise_names())
    print("upper/lower/symmetry feature counts:",
          len(feature_indices_by_group(group=['upper'])),
          len(feature_indices_by_group(group=['lower'])),
          len(feature_indices_by_group(group=['symmetry'])))
