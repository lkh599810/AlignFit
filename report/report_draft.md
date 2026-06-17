# AlignFit Intelligent Component — Report Draft

*Course: Intelligent Multimedia Systems — Final Project*
*Component: skeleton-feature movement-quality & exercise classifier for the AlignFit posture/homecare app*
*Status: draft generated 2026-06-18. Numbers come from `results/` produced by `python -m src.run_all`.*

---

## 1. Title candidates

1. **AlignFit-ML: Skeleton-Feature Movement-Quality Assessment and Exercise Recognition for a Homecare Posture App**
2. Bringing Intelligence to AlignFit: Subject-Independent Rehabilitation-Movement Classification on UI-PRMD-style Skeleton Data
3. From Rule Thresholds to Learned Features: A Quantitative Study of Movement-Quality Assessment for Homecare Exercise Recommendation

---

## 2. Problem & contribution

AlignFit is an AI posture-analysis and homecare-recommendation app. Its current
backend uses a **rule-based** asymmetry heuristic (shoulder/hip height differences)
to judge posture. This project builds the **"intelligent" component**: a learned
classifier over skeleton joint-angle features that (a) assesses *movement quality*
(normal vs abnormal) and (b) recognizes *which rehabilitation exercise* is being
performed, and then links those outputs to a homecare-exercise **recommendation
database**.

Contributions:

- A reproducible pipeline that takes public-domain rehabilitation skeleton data,
  engineers our own feature set, and stores it in a **custom feature database**
  (`db/features.sqlite`).
- Two prediction targets with a **subject-wise** evaluation protocol (no subject
  appears in more than one split — prevents data leakage).
- A quantitative comparison of a **rule-based baseline** vs an **MLP** (summary
  features) vs a **1D-CNN** (raw sequences), plus **hyperparameter tuning** and a
  **feature-group ablation**.
- A **recommendation database** that maps model outputs → body region → AlignFit
  posture pattern → homecare exercises, with explicit citation-verification flags.

---

## 3. Data

**Source dataset: UI-PRMD** (University of Idaho – Physical Rehabilitation Movement
Data), a public-domain set of 10 rehabilitation exercises performed correctly and
incorrectly by 10 healthy subjects, captured as skeleton joint angles/positions
(Vicon + Kinect).

**Acquisition reality (2026-06-18):** the official UI-PRMD site returned HTTP 404,
and available mirrors provided either *one exercise × many subjects* (avakanski
GitHub: deep-squat reduced set, real, stored in `data/raw/avakanski/`) **or** *ten
exercises × one subject* (per-exercise sample sequences, real, stored in
`data/raw/uiprmd_samples/`). Neither alone supports **both** 10-exercise
classification **and** subject-wise splitting.

**Our dataset (schema-faithful, real-seed-grounded):** for each of the 10
exercises we take the **real** per-exercise sample sequence as the canonical
joint-angle signature, then synthesize 10 subjects × correct/incorrect ×
repetitions by adding (i) consistent per-subject style offsets, (ii) per-frame
noise, and (iii) for incorrect reps, *randomized* range-of-motion reduction and
left/right asymmetry on the region-relevant joints. The result is **2000
variable-length sequences** of 66-dim joint angles (22 joints × 3 Euler angles),
labelled with `exercise_id`, `correctness`, and `subject_id`.

> **⚠️ REAL-DATA REPLACEMENT NEEDED.** The 10-exercise/multi-subject training data
> is grounded on real per-exercise signatures but is **partly synthetic**. When the
> full UI-PRMD segmented set is obtainable, drop it into `data/raw/uiprmd_full/`
> and re-point the loader — the rest of the pipeline is schema-compatible. (See
> `DECISIONS.md`.)

**Split (subject-wise):** train = s01–s06 (1200 seqs), val = s07–s08 (400),
test = s09–s10 (400). Reported metrics are on the **held-out test subjects**.

---

## 4. Method

### 4.1 Feature engineering (`db/features.sqlite`)
- **Summary vector (273-dim)** for the MLP / rule baseline: per-dimension mean,
  std, range (max−min), max (4×66 = 264) + 8 left/right symmetry scores + 1 overall
  symmetry score.
- **Sequence tensor (100×66)** for the 1D-CNN: each sequence linearly resampled to
  100 frames.

### 4.2 Targets
- **A — Binary correctness:** normal (0) vs abnormal (1) movement quality.
- **B — Exercise (10-class):** which of m01–m10.

### 4.3 Models
- **Rule baseline:** flag "incorrect" if asymmetry > τ₁ **or** range-of-motion < τ₂
  (thresholds tuned on train only) — a direct port of AlignFit's app logic. For
  exercise, a nearest-centroid classifier on standardized features.
- **MLP:** fully-connected net over the 273-dim summary vector.
- **1D-CNN:** temporal conv net over the 100×66 sequence (Conv1d→BN→ReLU→MaxPool ×2,
  global average pool, linear head).

### 4.4 Mapping to recommendations
Each exercise maps to a body region (upper/shoulder = m07–m10, lower/trunk =
m01–m06) and an AlignFit posture pattern (`results/exercise_mapping.csv`). The
predicted exercise + correctness query `db/recommendation.sqlite` to return
homecare exercises (`src/recommendation_db.py`). **This mapping is an explicit
design choice** (see Limitations).

---

## 5. Results (Phase 7)

### 5.1 Model comparison (test set)

**Task A — binary correctness**

| model | accuracy | precision_macro | recall_macro | f1_macro | infer ms/sample | size KB |
| --- | --- | --- | --- | --- | --- | --- |
| 1D-CNN | 0.710 | 0.710 | 0.710 | 0.710 | 0.032 | 89.5 |
| MLP | 0.698 | 0.704 | 0.698 | 0.695 | 0.003 | 175.9 |
| MLP (tuned) | 0.698 | 0.704 | 0.698 | 0.695 | 0.003 | 141.8 |
| baseline (rule) | 0.513 | 0.513 | 0.513 | 0.511 | 0.0002 | 0.0 |

**Task B — exercise 10-class:** baseline, MLP, and 1D-CNN all reach **F1 = 1.000**
(the 10 exercises have highly distinct joint-angle signatures).

Confusion matrices: `results/cm_*_binary_correctness.png`,
`results/cm_*_exercise_10class.png`.

**Reading:** On the hard movement-quality task the learned models (≈0.70 F1)
clearly beat the rule baseline (≈0.51 — near chance), demonstrating the value of
the "intelligent" component over the app's current threshold logic. The 1D-CNN
slightly edges the MLP but is ~10× slower to run; the MLP is the better
size/latency trade-off. Exercise recognition is essentially solved by any method.

### 5.2 Hyperparameter tuning (Phase 8, Optuna 30 trials)

Best config: hidden `(64, 256)`, dropout 0.19, lr 2.5e-3, batch 64, optimizer adam.
Gains were marginal (test F1 0.687 → 0.695) because the default model was already
near the validation ceiling for the full 273-dim feature set. The larger
improvement came from **feature selection** (next section), not architecture
search. Full table: `results/tuning_results.md`.

### 5.3 Ablation (Phase 9)

| feature group | n_features | model | f1_macro |
| --- | --- | --- | --- |
| **mean + std** | 132 | MLP | **0.835** |
| lower-body joints | 141 | MLP | 0.785 |
| mean+std+range | 198 | MLP | 0.714 |
| all joints (full 273) | 273 | MLP | 0.695–0.731 |
| raw sequence | 100×66 | 1D-CNN | 0.707 |
| upper-body joints | 141 | MLP | 0.678 |
| mean only | 66 | MLP | 0.622 |
| symmetry only | 9 | MLP | 0.497 |

**Key finding:** a reduced **mean+std** feature set (0.835 F1) substantially
outperforms the full 273-dim set (≈0.70). The `range`/`max` statistics appear to
capture subject-specific motion *extents* that do not transfer to unseen subjects,
i.e. they hurt subject-independent generalization. Lower-body joint features alone
beat upper-body features on this (lower-body-heavy) exercise mix. Symmetry features
alone are weak — consistent with the rule baseline's poor showing. Full table:
`results/ablation_results.md`.

> Note: the spec requested an "angle vs position" ablation, but the UI-PRMD angle
> data has no separate position channel, so we substitute an angle-statistic-group
> ablation; the "summary vs sequence" axis is covered by MLP-vs-1D-CNN above.

---

## 6. Recommendation linking (Phase 10) — example

`recommend_for_prediction("m07", correctness_pred=1)` →
region `upper_shoulder`, pattern `shoulder_asymmetry`, quality `needs_attention`,
recommendations: *Shoulder blade squeeze*, *Wall angel (gentle range)* — each with
`verify_citation = TODO` and a non-diagnostic caution banner.

---

## 7. Meeting the course requirements (mapping)

| Requirement | Where it is satisfied |
| --- | --- |
| Use an existing public model/dataset and adapt it | UI-PRMD skeleton data; real per-exercise seeds adapted into our schema (§3) |
| Build our own database | `db/features.sqlite` (engineered features) + `db/recommendation.sqlite` |
| Feature engineering | 273-dim summary vector + 100×66 sequence tensor (§4.1) |
| ≥1 ML model, trained | MLP + 1D-CNN with checkpoints in `models/` (§4.3) |
| Baseline comparison | rule-based + nearest-centroid baselines (§5.1) |
| Quantitative evaluation | accuracy / macro P-R-F1 / confusion matrices / inference time / model size (§5.1) |
| Hyperparameter tuning | Optuna study, before/after table (§5.2) |
| Ablation study | feature-group ablation (§5.3) |
| Avoid data leakage | strict subject-wise split (§3) |
| Application linkage | model output → region/pattern → recommendation DB (§6) |

---

## 8. Limitations

1. **Partly synthetic training data.** The multi-subject/10-exercise set is grounded
   on real per-exercise signatures but augmented synthetically because the full
   UI-PRMD segmented set was not downloadable. Absolute numbers should be treated as
   *pipeline-validity* evidence, not a clinical benchmark. **Replacing with the full
   real dataset is the #1 next step.**
2. **Static photo vs motion sequence gap.** The AlignFit app analyses a single
   frontal photo, whereas this classifier consumes *motion-capture sequences*.
   Bridging single-image pose → sequence features is unaddressed here.
3. **Healthy subjects only.** UI-PRMD contains healthy participants simulating
   incorrect form; real patients differ.
4. **Mapping is a design choice.** The exercise→region→pattern→recommendation table
   encodes our assumptions, not a validated clinical protocol.
5. **Clinical citations unverified.** Every recommendation row carries
   `verify_citation = TODO`; no real citations are claimed and none were fabricated.
6. **Run-to-run variance** of ≈±0.02–0.03 F1 on the 400-sample test set; trends are
   stable, individual decimals are not.

---

## 9. Reproducibility

```bash
pip install -r requirements.txt          # torch pinned to 2.3.1+cpu (see file)
python -m src.download_data              # fetch real UI-PRMD assets (optional)
python -m src.run_all 30                 # full pipeline -> db/, models/, results/
```
All figures/tables in this report are emitted to `results/`.
