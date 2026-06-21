"""Real-data loader (avakanski UI-PRMD deep-squat) for the binary
correct/incorrect movement-quality task.

This module is SEPARATE from the synthetic 273-dim pipeline (build_dataset.py),
which is intentionally preserved as a controlled synthetic benchmark. Here there
is NO synthetic generation and NO perturbation: every sequence is real Vicon-
captured deep-squat motion, and the binary label is file-derived
(correct file -> 0, incorrect file -> 1).

Source layout (data/raw/avakanski/, real UI-PRMD reduced set):
  Data_Correct.csv    (90*117, 240)  -> 90 correct repetitions
  Data_Incorrect.csv  (90*117, 240)  -> 90 incorrect repetitions
Each repetition is stored as a 117 (Vicon skeletal-angle dims) x 240 (frames,
length-aligned by the source preprocessing) block, stacked vertically.

Subject identity (needed for a leakage-free subject-wise split) is recovered
deterministically from the official Prepare_Data_for_NN.m reduction. The source
reads 10 subjects x 10 episodes in subject-major order (k=1..100, subject =
ceil(k/10)) and then keeps a fixed index subset, dropping noisy episodes. We
replicate that exact index list, so every kept sequence's subject is known.
"""
from __future__ import annotations

import os

import numpy as np
import pandas as pd

from src import config

REAL_DIM = 117          # Vicon skeletal-angle dimensions per frame (deep squat)
REAL_LEN = 240          # frames per repetition (length-aligned in source preprocessing)
N_SUBJECTS = 10

# Subject-wise split. The dataset is small (~9 reps/subject/class), so each fold
# holds only a few subjects; this is documented as a limitation in the report.
TRAIN_SUBJECTS = [1, 2, 3, 4, 5, 6]
VAL_SUBJECTS = [7, 8]
TEST_SUBJECTS = [9, 10]


def _keep_indices_1based():
    """The exact 1-based index list kept by Prepare_Data_for_NN.m (90 of 100)."""
    def r(a, b):
        return list(range(a, b + 1))
    return (r(2, 10) + r(12, 20) + r(22, 30) + r(32, 40) + r(42, 50) +
            r(51, 60) + r(62, 63) + r(65, 70) + r(72, 80) + r(82, 84) +
            r(86, 90) + r(91, 100))


def subject_ids():
    """Recovered subject id (1..10) for each of the 90 kept sequences, in order."""
    # original k=1..100 was filled subject-major with 10 episodes per subject.
    return [(k - 1) // 10 + 1 for k in _keep_indices_1based()]


def _load_file(path):
    """Return a list of (REAL_LEN, REAL_DIM) sequences from one stacked CSV."""
    mat = pd.read_csv(path, header=None).values.astype(np.float32)
    if mat.shape[0] % REAL_DIM != 0:
        raise ValueError(f"{path}: {mat.shape[0]} rows not divisible by {REAL_DIM}")
    n_seq = mat.shape[0] // REAL_DIM
    seqs = []
    for i in range(n_seq):
        block = mat[i * REAL_DIM:(i + 1) * REAL_DIM, :]   # (117 dims, 240 frames)
        seqs.append(block.T.copy())                       # (240 frames, 117 dims)
    return seqs


def split_of(subject: int) -> str:
    if subject in TRAIN_SUBJECTS:
        return "train"
    if subject in VAL_SUBJECTS:
        return "val"
    return "test"


def load():
    """Load all 180 real sequences with labels, subjects and split assignment.

    Returns a list of dicts: {seq_id, seq (T,117), subject, label_binary,
    correctness, split}.
    """
    subj = subject_ids()
    cor = _load_file(os.path.join(config.AVAKANSKI_DIR, "Data_Correct.csv"))
    inc = _load_file(os.path.join(config.AVAKANSKI_DIR, "Data_Incorrect.csv"))
    if not (len(cor) == len(inc) == len(subj) == 90):
        raise ValueError(f"expected 90/90/90, got {len(cor)}/{len(inc)}/{len(subj)}")

    records = []
    for i, s in enumerate(subj):
        records.append({
            "seq_id": f"deepsquat_s{s:02d}_correct_{i:03d}",
            "seq": cor[i], "subject": s, "label_binary": 0,
            "correctness": "correct", "split": split_of(s),
        })
    for i, s in enumerate(subj):
        records.append({
            "seq_id": f"deepsquat_s{s:02d}_incorrect_{i:03d}",
            "seq": inc[i], "subject": s, "label_binary": 1,
            "correctness": "incorrect", "split": split_of(s),
        })
    return records


if __name__ == "__main__":
    import collections
    recs = load()
    print(f"loaded {len(recs)} real sequences (dim={REAL_DIM}, len={REAL_LEN})")
    for sp in ("train", "val", "test"):
        sub = [r for r in recs if r["split"] == sp]
        subs = sorted({r["subject"] for r in sub})
        n_cor = sum(r["label_binary"] == 0 for r in sub)
        print(f"  {sp:5s}: {len(sub):3d} seqs  subjects={subs}  "
              f"correct={n_cor} incorrect={len(sub) - n_cor}")
    print("per-subject:", dict(sorted(collections.Counter(
        r["subject"] for r in recs).items())))
