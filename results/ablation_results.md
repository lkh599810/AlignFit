# Feature-group ablation - task: binary_correctness

| feature_group | n_features | model | accuracy | precision_macro | recall_macro | f1_macro |
| --- | --- | --- | --- | --- | --- | --- |
| all_joints | 273 | MLP | 0.7 | 0.7133 | 0.7 | 0.6952 |
| upper_only | 141 | MLP | 0.68 | 0.6847 | 0.68 | 0.6779 |
| lower_only | 141 | MLP | 0.7925 | 0.8375 | 0.7925 | 0.7854 |
| symmetry_only | 9 | MLP | 0.5375 | 0.5551 | 0.5375 | 0.4974 |
| mean_only | 66 | MLP | 0.6225 | 0.6232 | 0.6225 | 0.622 |
| mean_std | 132 | MLP | 0.835 | 0.835 | 0.835 | 0.835 |
| mean_std_range | 198 | MLP | 0.715 | 0.7168 | 0.715 | 0.7144 |
| full_summary (MLP) | 273 | MLP | 0.735 | 0.7494 | 0.735 | 0.7311 |
| raw_sequence (1D-CNN) | 100x66 | CNN1D | 0.71 | 0.7188 | 0.71 | 0.7071 |
