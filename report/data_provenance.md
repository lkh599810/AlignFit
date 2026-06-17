# Data provenance & license

## Primary dataset: UI-PRMD
- **Name:** University of Idaho – Physical Rehabilitation Movement Data (UI-PRMD)
- **Official page:** http://webpages.uidaho.edu/ui-prmd/  — **returned HTTP 404 on 2026-06-18** (site unavailable; web.archive.org was blocked by our fetch tool).
- **Reference paper:** Vakanski, Jun, Paul, Baker, "A Data Set of Human Body Movements for Physical Rehabilitation Exercises," *Data*, 2018. (PMC5773117)
- **License/terms:** UI-PRMD is published as a **public-domain** research dataset (freely available for research). Verify the exact terms on the official page once it is back online before any redistribution. **`verify_citation: TODO`**

## Real assets actually downloaded (mirrors)
1. **avakanski reduced deep-squat set** — `data/raw/avakanski/` (gitignored, ~46 MB)
   - URL base: `https://raw.githubusercontent.com/avakanski/A-Deep-Learning-Framework-for-Assessing-Physical-Rehabilitation-Exercises/master/Data/`
   - Files: `Data_Correct.csv`, `Data_Incorrect.csv`, `Labels_Correct.csv`, `Labels_Incorrect.csv`
   - Content: real UI-PRMD subset, **deep squat only**, 117/240-dim angle sequences + continuous quality scores.
2. **Per-exercise sample sequences** — `data/raw/uiprmd_samples/` (committed, small)
   - URL base: `https://raw.githubusercontent.com/tejas1904/UI-PRMD-Visualize-python-port/master/data/`
   - Files: `m01..m10_s01_e01_angles.txt` — real UI-PRMD per-exercise samples, 66-dim (22 joints × 3 Euler angles). Used as the canonical per-exercise signatures that ground our generated dataset.

Both mirrors are third-party GitHub repositories re-hosting UI-PRMD-derived data;
their own repo licenses should be checked before redistribution. We use them only
as research inputs and do not redistribute the large files (gitignored).

## Generated/derived data (ours)
- `data/processed/` — the schema-faithful, real-seed-grounded dataset (regenerable; gitignored).
- `db/features.sqlite` — our engineered feature database (committed).
- `db/recommendation.sqlite` — our homecare recommendation database, authored from
  **general** physical-therapy knowledge; **no fabricated citations**; clinical rows
  flagged `verify_citation = TODO`.

Re-download with: `python -m src.download_data`
