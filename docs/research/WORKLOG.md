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

## Phase 4 — targets + mapping
- Targets A (binary correct/incorrect) and B (10-exercise) materialized in data.
- results/exercise_mapping.csv: exercise -> body_region (upper m07-10 / lower m01-06) -> AlignFit pattern.

## Phase 5 — rule-based baseline
- src/baseline.py: rule (asymmetry>thr OR ROM<thr, app logic) for binary; nearest-centroid for exercise.
- Binary baseline ~0.51 (chance — subtle randomized errors defeat single thresholds); exercise 1.0.

## Phases 6-7 — models + evaluation
- src/models.py (MLP, CNN1D), src/train.py (early-stop, checkpoints), src/evaluate.py (metrics, CM plots, comparison table, inference time, model size).
- IMPORTANT: newest torch (2.12) failed to load c10.dll (WinError 1114) -> pinned torch==2.3.1+cpu (loads fine).
- Re-tuned the synthetic difficulty (randomized side/joint subset, smaller error magnitude, larger subject variance) so the binary task is non-trivial.
- Binary correctness (test): baseline 0.51 / MLP 0.70 / CNN 0.71 F1. Exercise: all 1.0.

## Phase 8 — tuning (Optuna, 30 trials)
- Best: 2-layer [64,256], adam, lr~2.5e-3, dropout 0.19. Marginal gain (default already near val ceiling). results/tuning_results.*

## Phase 9 — ablation
- Key finding: mean+std subset (132 feat) F1=0.835 >> full 273-feat (0.695): extra range/max stats overfit subject-specific motion extents. lower-body feats (0.785) > upper. symmetry-only weak (0.50). results/ablation_results.*

## Phase 10 — recommendation DB
- db/recommendation.sqlite (14 PT-knowledge homecare items, NO fake citations, verify_citation='TODO').
- Linking: model output (exercise->region/pattern + correctness) -> DB query -> recommendations. Demo works.
