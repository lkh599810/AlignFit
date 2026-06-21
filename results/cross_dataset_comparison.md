# Cross-dataset comparison - binary correct/incorrect (PARALLEL, not merged)

Both datasets evaluated with the **same protocol** (subject-wise split, summary-feature models, our-baselines-only policy). The two datasets are reported side by side and were **never concatenated or co-trained**; each model is trained and tested entirely within one dataset.

| dataset | model | accuracy | f1_macro |
| --- | --- | --- | --- |
| UI-PRMD deep-squat (avakanski, real) | majority | 0.5 | 0.3333 |
| UI-PRMD deep-squat (avakanski, real) | nearest_centroid | 0.9167 | 0.9161 |
| UI-PRMD deep-squat (avakanski, real) | mlp | 1.0 | 1.0 |
| UI-PRMD deep-squat (avakanski, real) | cnn1d | 0.9722 | 0.9722 |
| MobiPhysio (9 exercises) | majority | 0.4839 | 0.3261 |
| MobiPhysio (9 exercises) | nearest_centroid | 0.6129 | 0.6092 |
| MobiPhysio (9 exercises) | mlp | 0.6968 | 0.6967 |

**Reading:** the comparison is across *protocol behaviour*, not raw feature spaces (UI-PRMD = 468-d/1 exercise, MobiPhysio = 69-d/9 exercises). Treat it as 'does the same lightweight summary-MLP recipe generalize to a second, independent dataset?', not a single-leaderboard ranking.