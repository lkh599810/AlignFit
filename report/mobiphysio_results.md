# MobiPhysio results - parallel movement-quality benchmark

**Data:** MobiPhysio summary-feature DB (`db/mobiphysio_features.sqlite`), 9 shoulder/wrist/hip/back exercises (E01-E09), 24 subjects, 534 sequences.
**Split:** subject-wise train/val/test as stored in `sequence_meta.split` (no subject crosses folds).
**Features:** 69-d per-region summary stats (mean/std/range/max + L/R symmetry + height-difference features).
**Models:** reuse the existing `MLP` + training/eval code unchanged (same harness as the synthetic and avakanski pipelines).

> **Kept parallel, not merged:** MobiPhysio is reported side by side with the UI-PRMD results; the datasets are never concatenated.

> **No 1D-CNN here (honest limitation):** the MobiPhysio DB provides only the 69-d `summary_features` table - there are NO raw per-frame sequences - so the sequence-based 1D-CNN used on the synthetic/avakanski data cannot be run. Only summary-feature models (majority / nearest-centroid / MLP) are reported.

## Task: binary_correctness

Sample counts: train=295, val=84, test=155; 2 classes.

| model | accuracy | precision_macro | recall_macro | f1_macro | notes |
| --- | --- | --- | --- | --- | --- |
| majority | 0.4839 | 0.2419 | 0.5 | 0.3261 | always predicts class 1 |
| nearest_centroid | 0.6129 | 0.6228 | 0.6167 | 0.6092 | 69-d summary feats |
| mlp | 0.6968 | 0.6984 | 0.6979 | 0.6967 | 69-d feats; params=17346; val_acc=0.762 |

Confusion matrices: `results/cm_mobi_*_binary_correctness.png`

## Task: exercise_9class

Sample counts: train=295, val=84, test=155; 9 classes.

| model | accuracy | precision_macro | recall_macro | f1_macro | notes |
| --- | --- | --- | --- | --- | --- |
| majority | 0.1484 | 0.0165 | 0.1111 | 0.0287 | always predicts class 1 |
| nearest_centroid | 0.7742 | 0.7673 | 0.7688 | 0.7449 | 69-d summary feats |
| mlp | 0.9032 | 0.8854 | 0.8728 | 0.8754 | 69-d feats; params=17801; val_acc=0.929 |

Confusion matrices: `results/cm_mobi_*_exercise_9class.png`

## Limitations (honest)
- **Summary-feature only:** no raw frames in the DB, so no temporal 1D-CNN; the MLP sees only per-region summary statistics.
- **Imbalanced 9-class task:** exercise classes range from 40 to 80 sequences, so macro-F1 is the fair headline metric (not accuracy).
- **Distinct schema from UI-PRMD:** 69-d features and 9 exercises here vs the 273-d/10-exercise synthetic and 468-d/1-exercise avakanski sets - the datasets are comparable in *protocol* (subject-wise split, summary-MLP), not in raw feature dimensionality. See the cross-dataset table.