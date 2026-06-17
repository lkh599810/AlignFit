"""Feature engineering for the AlignFit intelligent component (Phase 3).

Two feature forms are produced from each (T, 66) joint-angle sequence:

  (a) Summary vector for the MLP / rule baseline:
        per-dim mean, std, range (max-min), max         -> 4 * 66 = 264
        left/right symmetry score per joint pair (8)     ->  8
        overall symmetry score                           ->  1
      = 273 features.

  (b) Fixed-length sequence tensor for the 1D-CNN:
        each sequence is resampled to SEQ_LEN frames -> (SEQ_LEN, 66).

The summary features are persisted to our own feature DB: db/features.sqlite
(tables: summary_features, sequence_meta, feature_names). The CNN sequence
tensors are loaded on demand from data/processed/sequences/*.npy.

Run:  python -m src.features      # builds db/features.sqlite
"""
from __future__ import annotations

import os
import sqlite3

import numpy as np
import pandas as pd

from src import config
from src.build_dataset import INDEX_CSV, _resample

SEQ_LEN = 100  # fixed length for the CNN sequence form

# ----------------------------------------------------------------------------
# Feature names + group metadata
# ----------------------------------------------------------------------------
STATS = ["mean", "std", "range", "max"]


def _joint_group(joint_idx: int) -> str:
    if joint_idx in config.UPPER_BODY_JOINTS:
        return "upper"
    if joint_idx in config.LOWER_BODY_JOINTS:
        return "lower"
    return "axial"


def build_feature_names():
    """Return list of (name, stat, body_group) for the 273 summary features."""
    names = []
    for stat in STATS:
        for d in range(config.N_ANGLE_DIMS):
            joint = d // config.N_ANGLES_PER_JOINT
            names.append((f"{stat}_d{d:02d}", stat, _joint_group(joint)))
    for (jl, jr) in config.LEFT_RIGHT_PAIRS:
        names.append((f"sym_{config.JOINT_NAMES[jl]}_{config.JOINT_NAMES[jr]}",
                      "symmetry", "symmetry"))
    names.append(("sym_overall", "symmetry", "symmetry"))
    return names


FEATURE_NAMES = build_feature_names()
N_FEATURES = len(FEATURE_NAMES)  # 273


def compute_summary(seq: np.ndarray) -> np.ndarray:
    """Compute the 273-dim summary feature vector for a (T, 66) sequence."""
    feats = [
        seq.mean(axis=0),
        seq.std(axis=0),
        seq.max(axis=0) - seq.min(axis=0),
        seq.max(axis=0),
    ]
    vec = np.concatenate(feats)  # 264

    sym = []
    for (jl, jr) in config.LEFT_RIGHT_PAIRS:
        ld = config.angle_dims_for_joints([jl])
        rd = config.angle_dims_for_joints([jr])
        # mean over time and over the 3 angles of |left - right|
        score = np.mean(np.abs(seq[:, ld] - seq[:, rd]))
        sym.append(score)
    sym = np.array(sym, dtype=np.float32)
    vec = np.concatenate([vec, sym, [sym.mean()]])
    return vec.astype(np.float32)


def load_sequence(rel_path: str) -> np.ndarray:
    return np.load(os.path.join(config.PROCESSED_DIR, rel_path))


def compute_sequence_tensor(seq: np.ndarray) -> np.ndarray:
    """Resample a (T, 66) sequence to (SEQ_LEN, 66) for the CNN."""
    return _resample(seq, SEQ_LEN)


# ----------------------------------------------------------------------------
# Build the feature DB
# ----------------------------------------------------------------------------
def build_feature_db():
    df = pd.read_csv(INDEX_CSV)
    X = np.stack([compute_summary(load_sequence(p)) for p in df["path"]])

    conn = sqlite3.connect(config.FEATURES_DB)
    cur = conn.cursor()
    cur.execute("DROP TABLE IF EXISTS summary_features")
    cur.execute("DROP TABLE IF EXISTS sequence_meta")
    cur.execute("DROP TABLE IF EXISTS feature_names")

    cur.execute("CREATE TABLE feature_names (idx INTEGER PRIMARY KEY, name TEXT, stat TEXT, body_group TEXT)")
    cur.executemany("INSERT INTO feature_names VALUES (?,?,?,?)",
                    [(i, n, s, g) for i, (n, s, g) in enumerate(FEATURE_NAMES)])

    cur.execute("""CREATE TABLE sequence_meta (
        seq_id TEXT PRIMARY KEY, exercise_id TEXT, exercise_name TEXT,
        subject_id TEXT, correctness TEXT, label_binary INTEGER,
        label_exercise INTEGER, region TEXT, n_frames INTEGER, split TEXT)""")
    meta_cols = ["seq_id", "exercise_id", "exercise_name", "subject_id", "correctness",
                 "label_binary", "label_exercise", "region", "n_frames", "split"]
    cur.executemany(
        f"INSERT INTO sequence_meta VALUES ({','.join('?' * len(meta_cols))})",
        df[meta_cols].itertuples(index=False, name=None))

    fcols = ", ".join(f"f{i:03d} REAL" for i in range(N_FEATURES))
    cur.execute(f"CREATE TABLE summary_features (seq_id TEXT PRIMARY KEY, {fcols})")
    placeholders = ",".join(["?"] * (N_FEATURES + 1))
    rows = [(sid, *map(float, x)) for sid, x in zip(df["seq_id"], X)]
    cur.executemany(f"INSERT INTO summary_features VALUES ({placeholders})", rows)

    conn.commit()
    conn.close()
    return df, X


# ----------------------------------------------------------------------------
# Loaders used by models / baseline / ablation
# ----------------------------------------------------------------------------
def load_summary(split: str = None, feature_idx=None):
    """Load summary features from the DB.

    Returns dict with X (n, k), seq_id, subject_id, y_binary, y_exercise, region.
    If `split` is given, filter to that split. If `feature_idx` is given (list of
    column indices), select only those feature columns (used by ablation).
    """
    conn = sqlite3.connect(config.FEATURES_DB)
    meta = pd.read_sql("SELECT * FROM sequence_meta", conn)
    feats = pd.read_sql("SELECT * FROM summary_features", conn)
    conn.close()
    df = meta.merge(feats, on="seq_id")
    if split is not None:
        df = df[df["split"] == split].reset_index(drop=True)

    fcols = [f"f{i:03d}" for i in range(N_FEATURES)]
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


def load_sequences(split: str = None):
    """Load CNN sequence tensors. Returns X (n, SEQ_LEN, 66) and labels."""
    df = pd.read_csv(INDEX_CSV)
    if split is not None:
        df = df[df["split"] == split].reset_index(drop=True)
    X = np.stack([compute_sequence_tensor(load_sequence(p)) for p in df["path"]])
    return {
        "X": X.astype(np.float32),
        "seq_id": df["seq_id"].values,
        "subject_id": df["subject_id"].values,
        "y_binary": df["label_binary"].values.astype(np.int64),
        "y_exercise": df["label_exercise"].values.astype(np.int64),
        "region": df["region"].values,
        "exercise_id": df["exercise_id"].values,
    }


def feature_indices_by_group(body_group=None, stat=None):
    """Return feature column indices filtered by body_group and/or stat."""
    idx = []
    for i, (_, s, g) in enumerate(FEATURE_NAMES):
        if body_group is not None and g not in body_group:
            continue
        if stat is not None and s not in stat:
            continue
        idx.append(i)
    return idx


if __name__ == "__main__":
    df, X = build_feature_db()
    print(f"feature DB built -> {config.FEATURES_DB}")
    print(f"summary features: {X.shape} (n_sequences, n_features={N_FEATURES})")
    print(f"feature groups: upper={len(feature_indices_by_group(body_group=['upper']))}, "
          f"lower={len(feature_indices_by_group(body_group=['lower']))}, "
          f"symmetry={len(feature_indices_by_group(body_group=['symmetry']))}")
