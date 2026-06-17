# QUESTIONS — for the user to answer in the morning (priority ordered)

### P1 — Real data replacement (most important)
The official UI-PRMD site was **down (404)** overnight, so the multi-subject /
10-exercise training data is **real-seed-grounded but partly synthetic** (see
DECISIONS.md, report §3). I built the whole pipeline to be schema-compatible so
real data can be dropped in later.
- **My temporary choice:** generate a faithful synthetic dataset from the 10 real
  per-exercise sample sequences so the pipeline runs end-to-end.
- **Need from you:** Do you have access to the full UI-PRMD segmented dataset (or a
  working mirror)? If yes, point me at it and I'll re-run `run_all` on real data —
  no code changes needed beyond the loader path.

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
