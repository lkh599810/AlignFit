"""End-to-end pipeline driver for the AlignFit intelligent component.

Runs every phase in order and regenerates all artifacts (feature DB, models,
results tables, confusion-matrix figures, recommendation DB):

  1. build dataset        (real-seed-grounded, subject-wise split)
  2. feature DB           (db/features.sqlite)
  3. target mapping       (results/exercise_mapping.csv)
  4. baselines            (rule-based binary + nearest-centroid exercise)
  5. train MLP + 1D-CNN   (both tasks; -> results/comparison.*)
  6. hyperparameter tuning (Optuna; -> results/tuning_results.*)
  7. ablation             (feature groups; -> results/ablation_results.*)
  8. recommendation DB    (db/recommendation.sqlite) + demo lookup

Run:  python -m src.run_all  [n_tune_trials]   (default 30)
"""
from __future__ import annotations

import os
import sys
import warnings

warnings.filterwarnings("ignore")

from src import (ablation, baseline, build_dataset, evaluate, export_mapping,
                 features, recommendation_db, train, tune)


def main(n_trials=30):
    # Start the comparison table fresh.
    for f in (evaluate.RESULTS_CSV, evaluate.RESULTS_MD):
        if os.path.exists(f):
            os.remove(f)

    print("=" * 60, "\n[1/8] build dataset")
    df = build_dataset.build()
    print(f"  {len(df)} sequences; splits: {df.groupby('split').size().to_dict()}")

    print("=" * 60, "\n[2/8] feature DB")
    _, X = features.build_feature_db()
    print(f"  summary features {X.shape}")

    print("=" * 60, "\n[3/8] target mapping")
    export_mapping.build_mapping_df().to_csv(export_mapping.OUT, index=False)
    print(f"  -> {export_mapping.OUT}")

    print("=" * 60, "\n[4/8] baselines")
    baseline.run()

    print("=" * 60, "\n[5/8] train MLP + 1D-CNN")
    train.run()

    print("=" * 60, f"\n[6/8] hyperparameter tuning ({n_trials} trials)")
    tune.run(n_trials)

    print("=" * 60, "\n[7/8] ablation")
    ablation.run()

    print("=" * 60, "\n[8/8] recommendation DB + demo")
    recommendation_db.build_db()
    demo = recommendation_db.recommend_for_prediction("m07", correctness_pred=1)
    print(f"  demo: {demo['exercise_id']} -> pattern={demo['posture_pattern']}; "
          f"{len(demo['recommendations'])} recs; quality={demo['movement_quality']}")

    print("=" * 60, "\nDONE. See results/comparison.md and results/*.png")


if __name__ == "__main__":
    main(int(sys.argv[1]) if len(sys.argv) > 1 else 30)
