# MobiPhysio hyperparameter tuning (Optuna, 30 trials) - task: binary_correctness

## Best config

- hidden_sizes: (256,)
- dropout: 0.430
- lr: 0.00010
- batch_size: 16
- weight_decay: 0.000002
- optimizer: adamw

## Before vs after (test set)

| config | val_acc | accuracy | precision_macro | recall_macro | f1_macro |
| --- | --- | --- | --- | --- | --- |
| default | 0.75 | 0.7032 | 0.7036 | 0.7037 | 0.7032 |
| tuned | 0.7619 | 0.6452 | 0.6501 | 0.6475 | 0.6442 |
