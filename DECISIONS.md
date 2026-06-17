# DECISIONS — assumptions made autonomously (sleep-auto)

Each line: decision + rationale. Review in the morning.

## Phase 0
- Use the existing miniconda Python 3.10.8. Create a project-local venv `.venv` for isolation as requested. If venv creation/torch install fails, fall back to installing into the current interpreter and record it here.
- Root `requirements.txt` is for the ML/intelligent component (separate from `backend/requirements.txt`).

## Phase 1 — Data acquisition (IMPORTANT — read this)
- The official UI-PRMD site (`webpages.uidaho.edu/ui-prmd/`) returns **HTTP 404** (down as of 2026-06-18). web.archive.org is blocked by the fetch tool. The full multi-subject *segmented* dataset (all 10 movements × 10 subjects × 10 reps) could NOT be downloaded from any working mirror.
- **Real UI-PRMD data that WAS obtained and stored in `data/raw/`:**
  1. `data/raw/avakanski/` — the paper's "reduced data set": **deep squat only**, 117/240-dim Vicon-derived angle sequences, correct + incorrect, with continuous quality-score labels. Source: avakanski GitHub (real UI-PRMD subset). Gitignored (46 MB) but downloadable via `src/download_data.py`.
  2. `data/raw/uiprmd_samples/` — **one real sample sequence per exercise** (m01–m10, subject 1, episode 1), 66-dim (22 joints × 3 Euler angles) Kinect-angle format. Source: tejas1904 python-port GitHub. Committed (small).
- **Decision (fallback per sleep-auto rule):** The mirrors give either one exercise × many subjects (avakanski) OR ten exercises × one subject (tejas) — neither alone supports BOTH 10-exercise classification AND subject-wise splitting. So the **primary modeling dataset is a schema-faithful, real-seed-grounded synthetic dataset**: for each of the 10 exercises I take the REAL per-exercise sample sequence (tejas) as the mean joint-angle signature, then synthesize 10 subjects × multiple reps × correct/incorrect by adding subject-level systematic offsets + per-frame noise + exercise-specific "incorrect" deviations (range/asymmetry injection on relevant joints). This makes subject-wise split meaningful and grounds each exercise class in real motion-capture statistics.
- **>>> REAL-DATA REPLACEMENT NEEDED <<<** When the UI-PRMD site is back (or a full mirror is found), drop the full segmented files into `data/raw/uiprmd_full/` and point the loader at it — the rest of the pipeline (features, DB, models, eval) is schema-compatible and needs no change. This limitation is recorded in the report.
- Schema chosen: 66-dim per-frame joint angles, variable-length sequences, labels = (exercise_id m01–m10, correctness ∈ {correct, incorrect}, subject_id s01–s10). This matches the real UI-PRMD Kinect-angle sample format.
