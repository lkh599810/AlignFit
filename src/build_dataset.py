"""Build the modeling dataset for the AlignFit intelligent component.

Strategy (see DECISIONS.md): the official UI-PRMD site is down, and the available
mirrors give either one exercise x many subjects OR ten exercises x one subject.
We therefore build a *schema-faithful, real-seed-grounded* dataset:

  - Each of the 10 exercises uses its REAL sample sequence (data/raw/uiprmd_samples)
    as the canonical mean joint-angle trajectory (resampled to a fixed length).
  - We synthesize N_SUBJECTS subjects, each with a consistent per-dimension "style"
    offset, so that a SUBJECT-WISE train/val/test split is meaningful (no leakage).
  - For each subject we generate `correct` and `incorrect` repetitions. Incorrect
    reps inject exercise-region-aware errors (left/right asymmetry + reduced range
    of motion on the relevant joints) plus larger noise -> a learnable, non-trivial
    correct-vs-incorrect signal that also matches AlignFit's asymmetry concept.
  - Sequence length is randomized per rep (variable-length sequences).

Outputs:
  data/processed/sequences/<seq_id>.npy   one (n_frames, 66) float32 array per rep
  data/processed/index.csv                metadata + subject-wise split assignment

Run:  python -m src.build_dataset
"""
from __future__ import annotations

import os

import numpy as np
import pandas as pd

from src import config

SEQ_DIR = os.path.join(config.PROCESSED_DIR, "sequences")
INDEX_CSV = os.path.join(config.PROCESSED_DIR, "index.csv")

CANONICAL_LEN = 100          # length the real seed is resampled to (mean trajectory)
LEN_RANGE = (60, 140)        # generated reps get a random length in this range

# Subject-wise split: which subject ids go to each split (no subject in two splits).
TRAIN_SUBJECTS = [f"s{ i:02d}" for i in range(1, 7)]   # s01..s06
VAL_SUBJECTS = ["s07", "s08"]
TEST_SUBJECTS = ["s09", "s10"]


def _resample(seq: np.ndarray, n: int) -> np.ndarray:
    """Linearly resample a (T, D) sequence to length n along time."""
    t_old = np.linspace(0.0, 1.0, num=seq.shape[0])
    t_new = np.linspace(0.0, 1.0, num=n)
    out = np.empty((n, seq.shape[1]), dtype=np.float32)
    for d in range(seq.shape[1]):
        out[:, d] = np.interp(t_new, t_old, seq[:, d])
    return out


def load_seed(mid: str) -> np.ndarray:
    """Load a real per-exercise sample, return (CANONICAL_LEN, 66) mean trajectory."""
    path = os.path.join(config.SAMPLES_DIR, f"{mid}_s01_e01_angles.txt")
    raw = pd.read_csv(path, header=None).values.astype(np.float32)
    # The tejas samples are 66-dim; guard against trailing/short columns.
    if raw.shape[1] >= config.N_ANGLE_DIMS:
        raw = raw[:, : config.N_ANGLE_DIMS]
    else:  # pad if a file is unexpectedly narrow
        pad = np.zeros((raw.shape[0], config.N_ANGLE_DIMS - raw.shape[1]), np.float32)
        raw = np.hstack([raw, pad])
    return _resample(raw, CANONICAL_LEN)


def error_joint_dims(mid: str):
    """Joint angle-dims where 'incorrect' errors are injected, by body region."""
    region = config.EXERCISE_TO_REGION[mid]
    if region == config.REGION_UPPER_SHOULDER:
        joints = [4, 5, 6, 8, 9, 10]          # shoulders / elbows / wrists
        pairs = [(4, 8), (5, 9), (6, 10)]
    else:
        joints = [0, 1, 12, 13, 14, 16, 17, 18]  # spine / hips / knees / ankles
        pairs = [(12, 16), (13, 17), (14, 18)]
    return config.angle_dims_for_joints(joints), pairs


def split_for_subject(sid: str) -> str:
    if sid in TRAIN_SUBJECTS:
        return "train"
    if sid in VAL_SUBJECTS:
        return "val"
    return "test"


def build() -> pd.DataFrame:
    rng = np.random.default_rng(config.RANDOM_SEED)
    os.makedirs(SEQ_DIR, exist_ok=True)

    # Per-exercise canonical mean + per-dim scale (range) from the real seed.
    seeds, scales = {}, {}
    for mid in config.EXERCISE_IDS:
        mean_traj = load_seed(mid)
        seeds[mid] = mean_traj
        rng_dim = mean_traj.max(axis=0) - mean_traj.min(axis=0)
        scales[mid] = np.clip(rng_dim, 1.0, None)  # avoid zero-scale dims

    rows = []
    for mid in config.EXERCISE_IDS:
        mean_traj = seeds[mid]
        scale = scales[mid]
        err_dims, err_pairs = error_joint_dims(mid)
        err_dims = np.array(err_dims, dtype=int)

        for s in range(1, config.N_SUBJECTS + 1):
            sid = f"s{s:02d}"
            # Consistent per-subject style offset (same for all this subject's reps).
            # Larger spread => test subjects (s09,s10) genuinely differ from train,
            # so the correct/incorrect signal must generalise across subjects.
            subj_rng = np.random.default_rng(config.RANDOM_SEED + 1000 * s)
            subject_offset = subj_rng.normal(0.0, 0.22 * scale, size=config.N_ANGLE_DIMS)

            for correctness in ("correct", "incorrect"):
                for r in range(1, config.N_REPS_PER_CLASS + 1):
                    n = int(rng.integers(LEN_RANGE[0], LEN_RANGE[1] + 1))
                    traj = _resample(mean_traj, n) + subject_offset[None, :]
                    traj = traj + rng.normal(0.0, 0.35 * scale, size=(n, config.N_ANGLE_DIMS))

                    if correctness == "incorrect":
                        # Modest, *randomised* errors so the abnormal pattern is not
                        # identical across reps (harder, more realistic than a fixed rule).
                        side = rng.integers(0, 2)  # 0=left side affected, 1=right side
                        for jl, jr in err_pairs:
                            if rng.random() > 0.6:        # only some joints affected per rep
                                continue
                            jdim = config.angle_dims_for_joints([jl if side == 0 else jr])
                            jdim = np.array(jdim, dtype=int)
                            # mild range-of-motion reduction on this joint
                            center = traj[:, jdim].mean(axis=0, keepdims=True)
                            traj[:, jdim] = center + (traj[:, jdim] - center) * rng.uniform(0.82, 0.97)
                            # mild left/right asymmetry offset on this joint
                            traj[:, jdim] += (rng.uniform(0.20, 0.40) * scale[jdim])[None, :]
                        # slightly noisier execution overall
                        traj = traj + rng.normal(0.0, 0.12 * scale, size=(n, config.N_ANGLE_DIMS))

                    seq_id = f"{mid}_{sid}_{correctness}_r{r:02d}"
                    np.save(os.path.join(SEQ_DIR, seq_id + ".npy"), traj.astype(np.float32))
                    rows.append({
                        "seq_id": seq_id,
                        "exercise_id": mid,
                        "exercise_name": config.EXERCISES[mid],
                        "subject_id": sid,
                        "correctness": correctness,
                        "label_binary": 0 if correctness == "correct" else 1,
                        "label_exercise": config.EXERCISE_TO_INDEX[mid],
                        "region": config.EXERCISE_TO_REGION[mid],
                        "n_frames": n,
                        "split": split_for_subject(sid),
                        "path": os.path.join("sequences", seq_id + ".npy"),
                    })

    df = pd.DataFrame(rows)
    df.to_csv(INDEX_CSV, index=False)
    return df


if __name__ == "__main__":
    df = build()
    print(f"built {len(df)} sequences -> {INDEX_CSV}")
    print("\nby split:")
    print(df.groupby("split").size())
    print("\nby correctness:")
    print(df.groupby("correctness").size())
    print("\nby exercise (first 3):")
    print(df.groupby("exercise_name").size().head(3))
    print("\nsubject-wise split (subjects per split):")
    print(df.groupby("split")["subject_id"].unique())
