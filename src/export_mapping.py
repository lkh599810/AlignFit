"""Phase 4 - materialize the classification targets and the
exercise -> body-region -> AlignFit-pattern mapping table.

Targets:
  A) binary correctness  (label_binary:   0=correct, 1=incorrect)
  B) exercise 10-class   (label_exercise: index of m01..m10)

The exercise->region->pattern mapping is an EXPLICIT design choice (documented in
the report limitations) that connects model outputs to the recommendation DB.

Run:  python -m src.export_mapping
"""
from __future__ import annotations

import os

import pandas as pd

from src import config

OUT = os.path.join(config.RESULTS_DIR, "exercise_mapping.csv")


def build_mapping_df() -> pd.DataFrame:
    rows = []
    for mid in config.EXERCISE_IDS:
        rows.append({
            "exercise_id": mid,
            "exercise_index": config.EXERCISE_TO_INDEX[mid],
            "exercise_name": config.EXERCISES[mid],
            "body_region": config.EXERCISE_TO_REGION[mid],
            "alignfit_pattern": config.EXERCISE_TO_ALIGNFIT_PATTERN[mid],
        })
    return pd.DataFrame(rows)


if __name__ == "__main__":
    df = build_mapping_df()
    df.to_csv(OUT, index=False)
    print(f"mapping table -> {OUT}\n")
    print(df.to_string(index=False))
