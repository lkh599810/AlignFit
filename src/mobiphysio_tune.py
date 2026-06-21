"""Optuna hyperparameter tuning for the MobiPhysio MLP (binary correctness).

Mirrors `src.tune` but on the parallel MobiPhysio 69-d summary features. The
search maximizes subject-wise validation accuracy; the best config is retrained
and evaluated on the held-out test split, with a before/after table written to
results/. Reuses the existing `MLP` + `train_torch` harness unchanged.

Run:  python -m src.mobiphysio_tune  [n_trials]   (default 30)
"""
from __future__ import annotations

import os
import sys

import optuna
import pandas as pd

from src import config, evaluate, mobiphysio_dataset as mobi
from src.models import MLP
from src.train import Standardizer, predict, train_torch

optuna.logging.set_verbosity(optuna.logging.WARNING)

OUT_MD = os.path.join(config.RESULTS_DIR, "mobiphysio_tuning_results.md")
OUT_CSV = os.path.join(config.RESULTS_DIR, "mobiphysio_tuning_results.csv")


def _data():
    tr, va, te = (mobi.load_summary(s) for s in ("train", "val", "test"))
    std = Standardizer().fit(tr["X"])
    return (std.transform(tr["X"]), tr["y_binary"],
            std.transform(va["X"]), va["y_binary"],
            std.transform(te["X"]), te["y_binary"])


def _fit(Xtr, ytr, Xva, yva, *, hidden, dropout, lr, batch_size, weight_decay,
         optimizer, epochs):
    model = MLP(Xtr.shape[1], 2, hidden_sizes=tuple(hidden), dropout=dropout)
    return train_torch(model, Xtr, ytr, Xva, yva, lr=lr, batch_size=batch_size,
                       weight_decay=weight_decay, optimizer=optimizer,
                       epochs=epochs, patience=20)


def run(n_trials=30):
    Xtr, ytr, Xva, yva, Xte, yte = _data()

    # default-config baseline (matches mobiphysio_run's MLP) for before/after.
    base_model, base_val = _fit(Xtr, ytr, Xva, yva, hidden=(128, 64), dropout=0.3,
                                lr=1e-3, batch_size=32, weight_decay=1e-4,
                                optimizer="adam", epochs=200)
    base_metrics = evaluate.compute_metrics(yte, predict(base_model, Xte))

    def objective(trial):
        n_layers = trial.suggest_int("n_layers", 1, 3)
        hidden = [trial.suggest_categorical(f"h{i}", [32, 64, 128, 256]) for i in range(n_layers)]
        dropout = trial.suggest_float("dropout", 0.0, 0.5)
        lr = trial.suggest_float("lr", 1e-4, 5e-3, log=True)
        batch_size = trial.suggest_categorical("batch_size", [16, 32, 64])
        weight_decay = trial.suggest_float("weight_decay", 1e-6, 1e-3, log=True)
        optimizer = trial.suggest_categorical("optimizer", ["adam", "adamw", "sgd"])
        _, best_val = _fit(Xtr, ytr, Xva, yva, hidden=hidden, dropout=dropout, lr=lr,
                           batch_size=batch_size, weight_decay=weight_decay,
                           optimizer=optimizer, epochs=80)
        return best_val

    study = optuna.create_study(direction="maximize",
                                sampler=optuna.samplers.TPESampler(seed=config.RANDOM_SEED))
    study.optimize(objective, n_trials=n_trials)
    best = study.best_params
    n_layers = best["n_layers"]
    hidden = tuple(best[f"h{i}"] for i in range(n_layers))

    tuned_model, tuned_val = _fit(Xtr, ytr, Xva, yva, hidden=hidden,
                                  dropout=best["dropout"], lr=best["lr"],
                                  batch_size=best["batch_size"],
                                  weight_decay=best["weight_decay"],
                                  optimizer=best["optimizer"], epochs=200)
    tuned_metrics = evaluate.compute_metrics(yte, predict(tuned_model, Xte))

    comp = pd.DataFrame([
        {"config": "default", "val_acc": round(base_val, 4),
         **{k: round(v, 4) for k, v in base_metrics.items()}},
        {"config": "tuned", "val_acc": round(tuned_val, 4),
         **{k: round(v, 4) for k, v in tuned_metrics.items()}},
    ])
    comp.to_csv(OUT_CSV, index=False)
    with open(OUT_MD, "w", encoding="utf-8") as f:
        f.write(f"# MobiPhysio hyperparameter tuning (Optuna, {n_trials} trials) "
                f"- task: binary_correctness\n\n")
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
