# Real-data results - UI-PRMD deep squat (binary correct/incorrect)

**Data:** real Vicon-captured deep-squat repetitions (avakanski reduced UI-PRMD set). No synthetic generation, no perturbation.
**Label:** file-derived - correct file = `correct`, incorrect file = `incorrect` (real execution quality).
**Split:** leakage-free subject-wise - train s01-s06 / val s07-s08 / test s09-s10. Subject ids recovered deterministically from the official `Prepare_Data_for_NN.m` reduction indices.
**Sample counts:** train=110 (cor 55/inc 55), val=34, test=36 (cor 18/inc 18).
**Features:** 468-d per-dim summary stats (mean/std/range/max over 117 Vicon angle dims) for MLP; length-aligned (240,117) sequences for 1D-CNN.

## Test-set comparison

| model | accuracy | precision_macro | recall_macro | f1_macro | notes |
| --- | --- | --- | --- | --- | --- |
| majority | 0.5 | 0.25 | 0.5 | 0.3333 | always predicts 'correct' |
| nearest_centroid | 0.9167 | 0.9286 | 0.9167 | 0.9161 | 468-d summary feats |
| mlp | 1.0 | 1.0 | 1.0 | 1.0 | 468-d feats; params=68418; val_acc=1.000 |
| cnn1d | 0.9722 | 0.9737 | 0.9722 | 0.9722 | (240,117) seq; params=29378; val_acc=1.000 |

## Confusion matrices
- `results/cm_real_majority.png`
- `results/cm_real_nearest_centroid.png`
- `results/cm_real_mlp.png`
- `results/cm_real_cnn1d.png`

## Limitations (honest)
- **Small data:** only 10 subjects; the test fold is 2 subjects (36 sequences). F1 is therefore high-variance - treat differences of a few points as noise, not a ranking.
- **Single exercise:** deep squat only (the only movement available in the accessible real reduced set), so there is no 10-class exercise task here - unlike the synthetic benchmark.
- **Feature parity:** 468-d (117 dims x 4 stats) instead of the synthetic 273-d; left/right symmetry features are omitted (no documented L/R joint pairing in this 117-dim Vicon layout).
- **Subject recovery assumption:** subjects are reconstructed from the source MATLAB reduction order; it is deterministic but relies on that script being the true provenance of `Data_*.csv`.

## Relationship to the synthetic benchmark
The synthetic 273-d pipeline (`build_dataset.py`, `db/features.sqlite`, etc.) is preserved unchanged and should be reported as a *controlled synthetic benchmark*. This file is the *real-data* result.