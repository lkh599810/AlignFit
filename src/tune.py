"""Phase 8 - hyperparameter tuning with Optuna.

Tunes the MLP on the binary correctness task (the harder, more interesting task)
using a subject-wise train/val split. The val accuracy is the objective; the best
config is then retrained and evaluated on the held-out test split, and a
before/after comparison table is written to results/.

Run:  python -m src.tune  [n_trials]   (default 30)
"""
from __future__ import annotations

import os
import sys

import optuna
import pandas as pd

from src import config, evaluate, features, train

optuna.logging.set_verbosity(optuna.logging.WARNING)

TASK = "binary_correctness"
OUT_MD = os.path.join(config.RESULTS_DIR, "tuning_results.md")
OUT_CSV = os.path.join(config.RESULTS_DIR, "tuning_results.csv")


def objective(trial):
    n_layers = trial.suggest_int("n_layers", 1, 3)
    hidden = [trial.suggest_categorical(f"h{i}", [32, 64, 128, 256]) for i in range(n_layers)]
    dropout = trial.suggest_float("dropout", 0.0, 0.5)
    lr = trial.suggest_float("lr", 1e-4, 5e-3, log=True)
    batch_size = trial.suggest_categorical("batch_size", [32, 64, 128])
    weight_decay = trial.suggest_float("weight_decay", 1e-6, 1e-3, log=True)
    optimizer = trial.suggest_categorical("optimizer", ["adam", "adamw", "sgd"])

    _, _, best_val = train.train_mlp(
        TASK, hidden_sizes=tuple(hidden), dropout=dropout, lr=lr,
        batch_size=batch_size, weight_decay=weight_decay, optimizer=optimizer,
        epochs=80, record=False)
    return best_val


def run(n_trials=30):
    # Baseline (default config) test metrics, for before/after comparison.
    _, base_metrics, base_val = train.train_mlp(TASK, tag="mlp_default", record=False)

    study = optuna.create_study(direction="maximize",
                                sampler=optuna.samplers.TPESampler(seed=config.RANDOM_SEED))
    study.optimize(objective, n_trials=n_trials)
    best = study.best_params
    n_layers = best["n_layers"]
    hidden = tuple(best[f"h{i}"] for i in range(n_layers))

    # Retrain best config, record to the main comparison table as the tuned MLP.
    _, tuned_metrics, tuned_val = train.train_mlp(
        TASK, hidden_sizes=hidden, dropout=best["dropout"], lr=best["lr"],
        batch_size=best["batch_size"], weight_decay=best["weight_decay"],
        optimizer=best["optimizer"], epochs=120, tag="mlp_tuned", record=True)

    comp = pd.DataFrame([
        {"config": "default", "val_acc": round(base_val, 4), **{k: round(v, 4) for k, v in base_metrics.items()}},
        {"config": "tuned", "val_acc": round(tuned_val, 4), **{k: round(v, 4) for k, v in tuned_metrics.items()}},
    ])
    comp.to_csv(OUT_CSV, index=False)
    with open(OUT_MD, "w", encoding="utf-8") as f:
        f.write(f"# Hyperparameter tuning (Optuna, {n_trials} trials) - task: {TASK}\n\n")
        f.write("## Best config\n\n")
        f.write(f"- hidden_sizes: {hidden}\n- dropout: {best['dropout']:.3f}\n")
        f.write(f"- lr: {best['lr']:.5f}\n- batch_size: {best['batch_size']}\n")
        f.write(f"- weight_decay: {best['weight_decay']:.6f}\n- optimizer: {best['optimizer']}\n\n")
        f.write("## Before vs after (test set)\n\n")
        f.write(evaluate.df_to_markdown(comp) + "\n")
    print("Best config:", best)
    print("\nBefore/after:\n", comp.to_string(index=False))
    return comp


if __name__ == "__main__":
    n = int(sys.argv[1]) if len(sys.argv) > 1 else 30
    run(n)
