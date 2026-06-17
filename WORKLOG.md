# WORKLOG — sleep-auto session (2026-06-18)

Chronological log of the autonomous build of the AlignFit "intelligent" component.

## Phase 0 — Environment
- Branch confirmed: `sleep-auto-001` (intelligent-multimedia work branch).
- Python 3.10.8 (miniconda base).
- Pre-installed: numpy 1.26.4, pandas 2.2.2, matplotlib 3.10.7, requests, sqlite3.
- Missing -> to install: scikit-learn, seaborn, optuna, torch, scipy.
- Created directory structure: data/{raw,processed} src models results db report logs.
- Wrote requirements.txt.
- Started venv + dependency install (background).
- Installed all deps incl. torch 2.12.1+cpu, sklearn 1.7.2, optuna 4.9.0, seaborn. All import OK.

## Phase 1 — Data acquisition
- Official UI-PRMD site 404; archive.org blocked. See DECISIONS.md.
- Downloaded REAL data: avakanski deep-squat reduced set (data/raw/avakanski, gitignored, 46MB) + 10 real per-exercise sample sequences (data/raw/uiprmd_samples, m01-m10, 66-dim, committed).
- Wrote src/download_data.py (reproducible re-download).

## Phase 2 — Dataset build + subject-wise split
- src/build_dataset.py: real-seed-grounded synthetic generation. 2000 sequences = 10 exercises x 10 subjects x 2 classes x 10 reps. Variable length [60,140].
- Subject-wise split (no leakage): train s01-s06 (1200), val s07-s08 (400), test s09-s10 (400).

## Phase 3 — Feature engineering + feature DB
- src/features.py: (a) 273-dim summary vector (mean/std/range/max per dim + 8 L/R symmetry scores + overall), (b) fixed-length (100,66) sequence tensor for CNN.
- Built db/features.sqlite (tables: summary_features 2000x273, sequence_meta, feature_names with body_group for ablation).
