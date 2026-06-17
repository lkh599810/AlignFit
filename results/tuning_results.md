# Hyperparameter tuning (Optuna, 30 trials) - task: binary_correctness

## Best config

- hidden_sizes: (64, 256)
- dropout: 0.188
- lr: 0.00248
- batch_size: 64
- weight_decay: 0.000022
- optimizer: adam

## Before vs after (test set)

| config | val_acc | accuracy | precision_macro | recall_macro | f1_macro |
| --- | --- | --- | --- | --- | --- |
| default | 0.8925 | 0.69 | 0.6988 | 0.69 | 0.6865 |
| tuned | 0.87 | 0.6975 | 0.7037 | 0.6975 | 0.6952 |
