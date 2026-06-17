"""Phase 6/7 - training harness + checkpointing + test evaluation.

Trains the MLP (summary features) and 1D-CNN (sequences) for both tasks
(binary correctness, 10-class exercise), saves checkpoints to models/, writes
confusion matrices to results/, and appends rows to results/comparison.csv.

Run:  python -m src.train
"""
from __future__ import annotations

import os

import numpy as np
import torch
import torch.nn as nn

from src import config, evaluate, features
from src.models import CNN1D, MLP, count_params

DEVICE = "cpu"


def set_seed(seed=config.RANDOM_SEED):
    np.random.seed(seed)
    torch.manual_seed(seed)


class Standardizer:
    def fit(self, X):
        self.mu = X.mean(0, keepdims=True)
        self.sd = X.std(0, keepdims=True) + 1e-8
        return self

    def transform(self, X):
        return (X - self.mu) / self.sd


def train_torch(model, Xtr, ytr, Xva, yva, *, lr=1e-3, batch_size=64, epochs=120,
                weight_decay=0.0, patience=15, optimizer="adam", verbose=False):
    set_seed()
    model = model.to(DEVICE)
    Xtr_t = torch.tensor(Xtr, dtype=torch.float32, device=DEVICE)
    ytr_t = torch.tensor(ytr, dtype=torch.long, device=DEVICE)
    Xva_t = torch.tensor(Xva, dtype=torch.float32, device=DEVICE)
    yva_t = torch.tensor(yva, dtype=torch.long, device=DEVICE)

    opt_cls = {"adam": torch.optim.Adam, "adamw": torch.optim.AdamW,
               "sgd": torch.optim.SGD}[optimizer]
    opt = opt_cls(model.parameters(), lr=lr, weight_decay=weight_decay)
    loss_fn = nn.CrossEntropyLoss()

    n = len(Xtr_t)
    best_val, best_state, wait = -1.0, None, 0
    for epoch in range(epochs):
        model.train()
        perm = torch.randperm(n)
        for i in range(0, n, batch_size):
            idx = perm[i:i + batch_size]
            opt.zero_grad()
            loss = loss_fn(model(Xtr_t[idx]), ytr_t[idx])
            loss.backward()
            opt.step()

        model.eval()
        with torch.no_grad():
            va_pred = model(Xva_t).argmax(1)
            val_acc = (va_pred == yva_t).float().mean().item()
        if val_acc > best_val:
            best_val, wait = val_acc, 0
            best_state = {k: v.detach().clone() for k, v in model.state_dict().items()}
        else:
            wait += 1
            if wait >= patience:
                break
        if verbose and epoch % 10 == 0:
            print(f"  epoch {epoch:3d} val_acc {val_acc:.4f}")

    if best_state is not None:
        model.load_state_dict(best_state)
    return model, best_val


@torch.no_grad()
def predict(model, X):
    model.eval()
    Xt = torch.tensor(X, dtype=torch.float32, device=DEVICE)
    return model(Xt).argmax(1).cpu().numpy()


def _label_key(task):
    return "y_binary" if task == "binary_correctness" else "y_exercise"


def _class_labels(task):
    return ["correct", "incorrect"] if task == "binary_correctness" else config.EXERCISE_IDS


def train_mlp(task, hidden_sizes=(128, 64), dropout=0.3, lr=1e-3, batch_size=64,
              weight_decay=1e-4, optimizer="adam", epochs=120, tag="mlp", record=True):
    yk = _label_key(task)
    tr = features.load_summary("train")
    va = features.load_summary("val")
    te = features.load_summary("test")
    std = Standardizer().fit(tr["X"])
    Xtr, Xva, Xte = std.transform(tr["X"]), std.transform(va["X"]), std.transform(te["X"])
    n_classes = 2 if task == "binary_correctness" else len(config.EXERCISE_IDS)

    model = MLP(Xtr.shape[1], n_classes, hidden_sizes=hidden_sizes, dropout=dropout)
    model, best_val = train_torch(model, Xtr, tr[yk], Xva, va[yk], lr=lr,
                                  batch_size=batch_size, weight_decay=weight_decay,
                                  optimizer=optimizer, epochs=epochs)
    pred = predict(model, Xte)
    metrics = evaluate.compute_metrics(te[yk], pred)
    if record:
        ckpt = os.path.join(config.MODELS_DIR, f"{tag}_{task}.pt")
        torch.save({"state_dict": model.state_dict(), "mu": std.mu, "sd": std.sd,
                    "hidden_sizes": hidden_sizes, "dropout": dropout}, ckpt)
        evaluate.save_confusion_matrix(te[yk], pred, _class_labels(task),
                                       f"MLP - {task}", f"cm_{tag}_{task}.png")
        inf = evaluate.measure_inference_ms(lambda X: predict(model, X), Xte)
        evaluate.append_result({"model": tag, "task": task, **metrics,
                                "infer_ms_per_sample": round(inf, 5),
                                "model_size_kb": evaluate.file_size_kb(ckpt),
                                "notes": f"params={count_params(model)}; val_acc={best_val:.3f}"})
    return model, metrics, best_val


def train_cnn(task, channels=(32, 64), kernel_size=5, dropout=0.3, lr=1e-3,
              batch_size=64, weight_decay=1e-4, optimizer="adam", epochs=120,
              tag="cnn1d", record=True):
    yk = _label_key(task)
    tr = features.load_sequences("train")
    va = features.load_sequences("val")
    te = features.load_sequences("test")
    n_classes = 2 if task == "binary_correctness" else len(config.EXERCISE_IDS)

    model = CNN1D(config.N_ANGLE_DIMS, n_classes, channels=channels,
                  kernel_size=kernel_size, dropout=dropout)
    model, best_val = train_torch(model, tr["X"], tr[yk], va["X"], va[yk], lr=lr,
                                  batch_size=batch_size, weight_decay=weight_decay,
                                  optimizer=optimizer, epochs=epochs)
    pred = predict(model, te["X"])
    metrics = evaluate.compute_metrics(te[yk], pred)
    if record:
        ckpt = os.path.join(config.MODELS_DIR, f"{tag}_{task}.pt")
        torch.save({"state_dict": model.state_dict(), "channels": channels,
                    "kernel_size": kernel_size, "dropout": dropout}, ckpt)
        evaluate.save_confusion_matrix(te[yk], pred, _class_labels(task),
                                       f"1D-CNN - {task}", f"cm_{tag}_{task}.png")
        inf = evaluate.measure_inference_ms(lambda X: predict(model, X), te["X"])
        evaluate.append_result({"model": tag, "task": task, **metrics,
                                "infer_ms_per_sample": round(inf, 5),
                                "model_size_kb": evaluate.file_size_kb(ckpt),
                                "notes": f"params={count_params(model)}; val_acc={best_val:.3f}"})
    return model, metrics, best_val


def run():
    for task in ("binary_correctness", "exercise_10class"):
        _, m_mlp, _ = train_mlp(task)
        _, m_cnn, _ = train_cnn(task)
        print(f"[{task}] MLP  f1={m_mlp['f1_macro']:.4f} acc={m_mlp['accuracy']:.4f}")
        print(f"[{task}] CNN  f1={m_cnn['f1_macro']:.4f} acc={m_cnn['accuracy']:.4f}")


if __name__ == "__main__":
    run()
