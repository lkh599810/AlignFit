# Enhancing an Existing Model (기존 모델 고도화)

*Report section. Slots into the main report after the Method/Results sections.*
*All citation details below are marked **[verify]** and must be confirmed against
the primary sources before submission — no citation is asserted as final here.*

---

## X.1 Baseline anchor: the Liao & Vakanski (2019) framework

Our work starts from, and is positioned as an enhancement of, the deep-learning
framework for rehabilitation-exercise assessment by **Liao, Vakanski & Xian
(2019)** **[verify: Y. Liao, A. Vakanski, M. Xian, "A Deep Learning Framework for
Assessing Physical Rehabilitation Exercises," arXiv:1901.10435 / IEEE TNSRE —
confirm venue, year, volume/pages]**. This is the *same* codebase and UI-PRMD
deep-squat data we build on (`data/raw/avakanski/`), which makes it the natural
and honest baseline anchor: we are extending a concrete prior system, not an
abstract idea.

**What their framework does (as we use it):**
- Pre-processing of UI-PRMD Vicon skeletal angles (length alignment to 240
  frames, centering) — we reuse their `Prepare_Data_for_NN` output directly.
- Dimensionality reduction (autoencoder) and a Gaussian-Mixture-Model scoring
  function that maps a repetition to a **continuous movement-quality score**.
- Spatio-temporal neural networks (CNN/RNN variants) that **regress** that
  continuous quality score.

**What we change — the "enhancement" for AlignFit:**
1. **Task reframing for a homecare decision.** AlignFit needs a *binary*
   "recommend corrective exercises or not" decision, not a continuous lab score.
   We reframe their quality-regression into a **binary correct/incorrect
   movement-quality classification** with file-derived real labels.
2. **Subject-independent protocol.** We enforce a strict **subject-wise split**
   (train s01–s06 / val s07–s08 / test s09–s10), with subject identity recovered
   deterministically from the source reduction indices, so no subject leaks
   across folds.
3. **Lightweight, deployable models.** Instead of the heavier autoencoder +
   spatio-temporal stack, we use a compact **468-d summary-feature MLP** and a
   small **1D-CNN** that run on CPU in milliseconds — appropriate for a homecare
   app backend.
4. **Application linkage they do not provide.** We add the end-to-end chain
   *feature → classifier → body region / AlignFit posture pattern →
   recommendation-DB rule → homecare exercise list* (`src/real_recommend.py`),
   turning an assessment score into an actionable, non-diagnostic recommendation.

> **No cross-paper metric comparison.** We deliberately do **not** report an F1 (or
> any metric) comparison against Liao & Vakanski or the related work below. Their
> target is continuous-score regression under their own protocol; ours is binary
> classification under a subject-independent split. The numbers are not
> commensurable, and a head-to-head figure would be misleading. We anchor to their
> framework *qualitatively* (as the system we enhance) and evaluate *quantitatively*
> only against our own baselines (§X.3).

---

## X.2 Related work: heavier alternatives considered and **not** adopted (scope)

These directions are stronger in raw capability but were judged out of scope for
the AlignFit homecare MVP (data, compute, dependency, and medical-safety
constraints). They are recorded as deliberate non-choices, not oversights.

- **Skeleton graph models + contrastive pretraining (≈2024).**
  **[verify: confirm exact ST-GCN / contrastive-learning skeleton-assessment
  reference(s), authors, venue, 2024]** Graph-convolutional networks over the
  skeleton joint graph, optionally with contrastive self-supervised pretraining,
  can model joint topology and motion more richly than our per-dimension summary
  features. *Not adopted:* they need substantially more data and compute to train
  well, and our accessible real set is a single exercise with ~10 subjects — too
  small to justify a graph model over a compact MLP/1D-CNN.

- **LLM-generated corrective feedback (≈2025).**
  **[verify: confirm exact LLM-feedback-for-rehabilitation/exercise reference,
  authors, venue, 2025]** Recent work generates natural-language corrective
  feedback ("your left knee caved inward") from movement analysis. *Not adopted:*
  it adds an LLM dependency the MVP intentionally avoids, and free-text clinical
  feedback raises medical-safety/wording risks that conflict with AlignFit's
  non-diagnostic constraint. Our rule-based recommendation DB keeps every claim
  human-verifiable (`verify_citation` flags).

---

## X.3 Quantitative evaluation — against our own baselines only

Per the constraint above, all numbers compare our learned models **only** to our
internal baselines on the **real** UI-PRMD deep-squat data, held-out test
subjects s09–s10 (36 sequences, balanced 18 correct / 18 incorrect). Full table:
`results/real_comparison.md`; confusion matrices: `results/cm_real_*.png`.

| model | what it is | accuracy | f1_macro |
| --- | --- | --- | --- |
| majority | predicts the majority train class (sanity floor) | 0.500 | 0.333 |
| nearest-centroid | simple distance baseline on 468-d summary feats | 0.917 | 0.916 |
| **MLP (ours)** | 468-d summary-feature classifier | **1.000** | **1.000** |
| **1D-CNN (ours)** | conv net over (240,117) sequences | **0.972** | **0.972** |

**Reading:** both learned models clearly beat the majority floor (0.33 F1) and
improve on the nearest-centroid baseline, confirming the enhancement adds value
over a naive rule. **Caveat (do not over-read):** the test fold is only 2 subjects
/ 36 sequences, so F1 is high-variance; the MLP's 1.000 reflects that real
correct-vs-deliberately-incorrect deep squats are highly separable in these
features on this tiny fold, *not* a claim of perfect generalization. See the
real-data Limitations in `report/real_data_results.md`.

> **Relationship to the synthetic benchmark.** The synthetic 273-d pipeline
> (`build_dataset.py`, `db/features.sqlite`, etc.) is preserved unchanged and is
> reported separately as a *controlled synthetic benchmark*; this section's numbers
> are the *real-data* enhancement results.

---

## X.4 MobiPhysio comparison — a second, independent dataset (parallel, not merged)

To test whether the lightweight summary-feature recipe generalizes beyond the
single avakanski deep-squat set, we ran the **same protocol** on a second,
independent dataset, **MobiPhysio** **[verify: confirm MobiPhysio reference —
authors, venue, year, license]** — 9 shoulder/wrist/hip/back exercises (E01–E09),
24 subjects, 534 sequences, with the subject-wise train/val/test split shipped in
the dataset. The two datasets are reported **side by side and were never
concatenated or co-trained**; each model is trained and tested entirely within one
dataset (`db/mobiphysio_features.sqlite`).

**Same evaluation policy as §X.3:** our learned model is compared **only** to our
own internal baselines (majority floor, nearest-centroid), never head-to-head
against another paper's metric.

**Setup difference (honest):** MobiPhysio ships only a **69-d summary-feature**
table — there are **no raw per-frame sequences** — so the **1D-CNN cannot be run
here** (it needs temporal frames). Only summary-feature models are reported. Full
tables: `results/mobiphysio_comparison.md`; confusion matrices:
`results/cm_mobi_*.png`.

**Binary correct/incorrect (held-out test subjects):**

| model | what it is | accuracy | f1_macro |
| --- | --- | --- | --- |
| majority | predicts the majority train class (sanity floor) | 0.484 | 0.326 |
| nearest-centroid | distance baseline on 69-d summary feats | 0.613 | 0.609 |
| **MLP (ours)** | 69-d summary-feature classifier | **0.697** | **0.697** |

**9-class exercise recognition (held-out test subjects):**

| model | accuracy | f1_macro |
| --- | --- | --- |
| majority | 0.148 | 0.029 |
| nearest-centroid | 0.774 | 0.745 |
| **MLP (ours)** | **0.903** | **0.875** |

**Reading:** on this larger, multi-exercise dataset the MLP again clearly beats
both baselines on both tasks. The binary movement-quality task is genuinely
**harder** here than on the tiny avakanski fold (0.70 vs 1.00 F1) — a more honest,
less saturated number, because MobiPhysio's test set spans 6 subjects / 155
sequences across 9 exercises rather than one exercise on 2 subjects. Exercise
*recognition* is easy (0.875 F1); movement-*quality* judgement is the hard part,
as expected.

**Tuning / ablation (binary task).** Optuna (30 trials) raised validation accuracy
(0.750 → 0.762) but the retrained config did **not** improve held-out test F1
(0.703 → 0.644) — a textbook small-validation overfit, reported as-is rather than
cherry-picked (`results/mobiphysio_tuning_results.md`). The feature-group ablation
(`results/mobiphysio_ablation_results.md`) shows upper-body features alone (0.642
F1) beat lower-body-only (0.598) and symmetry-only (0.587), consistent with the
dataset being shoulder-heavy (5 of 9 exercises).

> **Cross-dataset table:** `results/cross_dataset_comparison.md` pairs the
> avakanski and MobiPhysio binary numbers under the identical protocol. It is a
> *parallel* view of "does the same recipe transfer to an independent dataset?",
> **not** a single leaderboard — the feature spaces differ (468-d/1-exercise vs
> 69-d/9-exercise).

**Recommendation linkage extended.** The homecare linker now covers all four
MobiPhysio regions — **shoulder, wrist, low-back, hip** (`src/mobiphysio_recommend.py`)
— reusing the shared `recommend_by_pattern` lookup over the same
`recommendation.sqlite` (every row carries an anchored `verify_citation` and the
non-diagnostic caution banner).

---

*Citation checklist before submission:* resolve every **[verify]** marker above —
Liao & Vakanski (2019) full reference, the 2024 skeleton-graph/contrastive
reference(s), the 2025 LLM-feedback reference, and MobiPhysio. No reference in
this section is to be treated as final until verified.
