# QUESTIONS — for the user to answer in the morning (priority ordered)

### P1 — Real data replacement  ✅ RESOLVED (2026-06-21)
The official UI-PRMD site was **down (404)** overnight, so the multi-subject /
10-exercise training data is **real-seed-grounded but partly synthetic** (see
DECISIONS.md, report §3). I built the whole pipeline to be schema-compatible so
real data can be dropped in later.

**Resolution (2026-06-21):**
- The named Kaggle mirror `liza5757/uiprmd` is **NOT** the raw dataset — it is a
  single preprocessed sequence (`input.csv` 1423×100) + one regression label
  (`48.333`); no subject/movement/correctness structure. Unsuitable. (Downloaded
  to `data/raw/uiprmd_kaggle/`, kept as evidence.)
- The official uidaho site is now **403 hard-blocked** (full raw 2000-file set
  still inaccessible without manual/authenticated download).
- **Decision (user):** proceed with the **real avakanski UI-PRMD deep-squat**
  reduced set already on disk (`data/raw/avakanski/`) — real Vicon motion, real
  file-derived correct/incorrect labels, **no synthetic generation**.
- **Built (new modules, synthetic pipeline preserved):** `src/real_dataset.py`,
  `src/real_features.py`, `src/real_run.py` → `db/features_real.sqlite`,
  `models/real_*.pt`, `results/cm_real_*.png`, `results/real_comparison.md`,
  `report/real_data_results.md`. Subject ids **recovered** from the official
  `Prepare_Data_for_NN.m` reduction → leakage-free subject-wise split
  (train s01-s06 / val s07-s08 / test s09-s10).
- **Real-data test F1 (s09-s10):** majority 0.33 · nearest-centroid 0.92 · MLP
  1.00 · 1D-CNN 0.97. Caveat: tiny test fold (36 seq, 2 subjects) → high variance.

**Still open (optional):** if you want the full **10-exercise** real set, it
requires a manual/authenticated download (browser login to the official site or
a real Kaggle mirror); drop it in and I'll extend the real loader to all m01-m10.

### P2 — Is "movement quality" (normal/abnormal) the right headline task?
Exercise-type classification turned out **trivially easy (F1=1.0)** — the 10
exercises are very distinct. The interesting, hard task is **correct vs incorrect
movement** (ML ≈0.70 vs rule baseline ≈0.51).
- **My choice:** treat binary movement-quality as the headline result.
- **Need from you:** confirm this framing matches what the professor expects, or say
  if exercise classification must be the centerpiece.

### P3 — Recommendation DB clinical content
`db/recommendation.sqlite` is authored from general PT knowledge with **no
fabricated citations**; every clinical row is flagged `verify_citation = TODO`.
- **Need from you:** have a physio / verified source confirm the exercise→pattern
  recommendations before any real use, and fill in real citations.

### P4 — Body-region / pattern mapping
The exercise→region→AlignFit-pattern table (`results/exercise_mapping.csv`) is **my
design choice** (upper/shoulder = m07–m10, lower/trunk = m01–m06). Confirm it
matches AlignFit's intended posture patterns, or give me the official mapping.

### P5 — Scope/“intelligent” depth
Current models: rule baseline, MLP, 1D-CNN, + tuning + ablation. If you want more
(e.g., LSTM/Transformer over sequences, or real-data quality-score regression using
the avakanski continuous labels), tell me and I'll add it.
