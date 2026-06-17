# Model comparison (test set)


## Task: binary_correctness

| model | accuracy | precision_macro | recall_macro | f1_macro | infer_ms_per_sample | model_size_kb |
| --- | --- | --- | --- | --- | --- | --- |
| cnn1d | 0.71 | 0.71 | 0.71 | 0.71 | 0.0324 | 89.5 |
| mlp | 0.6975 | 0.7037 | 0.6975 | 0.6952 | 0.0027 | 175.9 |
| mlp_tuned | 0.6975 | 0.7037 | 0.6975 | 0.6952 | 0.0028 | 141.8 |
| baseline_rule | 0.5125 | 0.5127 | 0.5125 | 0.5106 | 0.0002 | 0.0 |


## Task: exercise_10class

| model | accuracy | precision_macro | recall_macro | f1_macro | infer_ms_per_sample | model_size_kb |
| --- | --- | --- | --- | --- | --- | --- |
| baseline_centroid | 1.0 | 1.0 | 1.0 | 1.0 | 0.0061 | 0.0 |
| mlp | 1.0 | 1.0 | 1.0 | 1.0 | 0.0026 | 177.8 |
| cnn1d | 1.0 | 1.0 | 1.0 | 1.0 | 0.0314 | 91.5 |
