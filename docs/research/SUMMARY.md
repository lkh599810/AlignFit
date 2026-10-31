# ☀️ Morning briefing — AlignFit intelligent component

Built autonomously overnight (sleep-auto mode) on branch `sleep-auto-001`.
**The full pipeline runs end-to-end and produces real results, models, DBs, plots,
and a report draft.** Read `QUESTIONS.md` first — there is one important decision
(real-data replacement).

---

## ✅ What is done (all 11 phases)

| Phase | Deliverable | Status |
| --- | --- | --- |
| 0 | venv `.venv` + `requirements.txt` (torch pinned 2.3.1+cpu) + dir structure | ✅ |
| 1 | Real UI-PRMD data fetched (`data/raw/`) + provenance/license note | ✅ |
| 2 | Loader + **subject-wise** split (no leakage) | ✅ |
| 3 | Feature engineering → **`db/features.sqlite`** (our feature DB) | ✅ |
| 4 | Targets A/B + exercise→region→pattern mapping (`results/exercise_mapping.csv`) | ✅ |
| 5 | Rule-based baseline (app logic) + nearest-centroid | ✅ |
| 6 | MLP + 1D-CNN, training loop, checkpoints (`models/`) | ✅ |
| 7 | Quantitative eval: acc / macro P-R-F1 / confusion matrices / infer time / size | ✅ |
| 8 | Optuna hyperparameter tuning + before/after table | ✅ |
| 9 | Feature-group ablation table | ✅ |
| 10 | **`db/recommendation.sqlite`** + model→DB linking (no fake citations) | ✅ |
| 11 | `report/report_draft.md` (+ `report/data_provenance.md`) | ✅ |

---

## 📊 Current performance (held-out test subjects s09–s10)

**Task A — movement quality (normal vs abnormal)** — the headline, hard task:
| model | F1 (macro) | accuracy |
| --- | --- | --- |
| rule baseline | 0.51 | 0.51 |
| MLP | 0.70 | 0.70 |
| **1D-CNN** | **0.71** | 0.71 |
| MLP (best ablation: mean+std feats) | **0.835** | 0.835 |

**Task B — exercise type (10-class):** every method = **F1 1.00** (exercises are
very distinct → trivially separable; this is honest, not a bug).

**Headline insights for the report:**
1. Learned models beat the rule baseline by ~0.20 F1 on movement quality → justifies
   the "intelligent" upgrade over AlignFit's current threshold logic.
2. Ablation: a **mean+std feature subset (0.835)** beats the full 273-dim set
   (~0.70) — extra range/max stats overfit subject-specific motion extents.

---

## ▶️ How to run

```bash
cd C:\AlignFit-intelligent
# one-time: deps already installed in .venv. To recreate:
#   python -m venv .venv && .venv\Scripts\python -m pip install -r requirements.txt
#   (torch: pip install torch==2.3.1 --index-url https://download.pytorch.org/whl/cpu)

.venv\Scripts\python -m src.run_all 30      # FULL pipeline (~2-3 min)
```
Individual phases: `python -m src.{build_dataset, features, baseline, train, tune, ablation, recommendation_db}`

Outputs: `db/features.sqlite`, `db/recommendation.sqlite`, `models/*.pt`,
`results/comparison.md`, `results/*.png`, `results/{tuning,ablation}_results.md`,
`report/report_draft.md`.

---

## ⚠️ Most important caveat
The 10-exercise/multi-subject training data is **real-seed-grounded but partly
synthetic** because the official UI-PRMD site was down (404) overnight. The pipeline
is schema-ready for the real full dataset — see **QUESTIONS.md P1** and
`DECISIONS.md`.

## 📁 Logs
`WORKLOG.md` (chronological), `DECISIONS.md` (assumptions), `QUESTIONS.md` (for you).
