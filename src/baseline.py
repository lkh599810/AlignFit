"""Phase 5 - non-deep baselines, to compare against the MLP/CNN.

  * Binary (correct vs incorrect): a RULE-BASED classifier that ports AlignFit's
    app logic - flag a rep as 'incorrect' if its left/right asymmetry is high OR
    its range of motion is low, with thresholds tuned on the training split only.
  * Exercise (10-class): a nearest-centroid classifier on standardized summary
    features (a simple, non-deep reference baseline).

Run:  python -m src.baseline
"""
from __future__ import annotations

import numpy as np

from src import config, evaluate, features


def _asymmetry_and_rom(data):
    """Per-sample asymmetry score (sym_overall) and mean range-of-motion."""
    X = data["X"]
    asym = X[:, features.N_FEATURES - 1]                      # sym_overall
    rom_idx = features.feature_indices_by_group(stat=["range"])
    rom = X[:, rom_idx].mean(axis=1)
    return asym, rom


def fit_rule_binary(train):
    """Tune asymmetry/ROM thresholds on train to maximize F1 for 'incorrect'."""
    asym, rom = _asymmetry_and_rom(train)
    y = train["y_binary"]
    a_grid = np.quantile(asym, np.linspace(0.3, 0.9, 13))
    r_grid = np.quantile(rom, np.linspace(0.1, 0.7, 13))
    best, best_thr = -1.0, (a_grid[0], r_grid[0])
    for a in a_grid:
        for r in r_grid:
            pred = ((asym > a) | (rom < r)).astype(int)
            m = evaluate.compute_metrics(y, pred)
            if m["f1_macro"] > best:
                best, best_thr = m["f1_macro"], (a, r)
    return best_thr


def predict_rule_binary(data, thr):
    asym, rom = _asymmetry_and_rom(data)
    return ((asym > thr[0]) | (rom < thr[1])).astype(int)


class NearestCentroid:
    def fit(self, X, y):
        self.mu = X.mean(0)
        self.sd = X.std(0) + 1e-8
        Xs = (X - self.mu) / self.sd
        self.classes = np.unique(y)
        self.centroids = np.stack([Xs[y == c].mean(0) for c in self.classes])
        return self

    def predict(self, X):
        Xs = (X - self.mu) / self.sd
        d = ((Xs[:, None, :] - self.centroids[None, :, :]) ** 2).sum(-1)
        return self.classes[d.argmin(1)]


def run():
    train = features.load_summary("train")
    test = features.load_summary("test")

    # --- binary rule-based ---
    thr = fit_rule_binary(train)
    pred_b = predict_rule_binary(test, thr)
    m_b = evaluate.compute_metrics(test["y_binary"], pred_b)
    evaluate.save_confusion_matrix(test["y_binary"], pred_b, ["correct", "incorrect"],
                                   "Baseline (rule) - correctness", "cm_baseline_binary.png")
    inf_b = evaluate.measure_inference_ms(lambda X: predict_rule_binary({"X": X}, thr), test["X"])
    evaluate.append_result({"model": "baseline_rule", "task": "binary_correctness",
                            **m_b, "infer_ms_per_sample": round(inf_b, 5),
                            "model_size_kb": 0.0,
                            "notes": f"asym>{thr[0]:.2f} or rom<{thr[1]:.2f}"})

    # --- exercise nearest-centroid ---
    nc = NearestCentroid().fit(train["X"], train["y_exercise"])
    pred_e = nc.predict(test["X"])
    m_e = evaluate.compute_metrics(test["y_exercise"], pred_e)
    evaluate.save_confusion_matrix(test["y_exercise"], pred_e, config.EXERCISE_IDS,
                                   "Baseline (nearest-centroid) - exercise", "cm_baseline_exercise.png")
    inf_e = evaluate.measure_inference_ms(nc.predict, test["X"])
    evaluate.append_result({"model": "baseline_centroid", "task": "exercise_10class",
                            **m_e, "infer_ms_per_sample": round(inf_e, 5),
                            "model_size_kb": 0.0, "notes": "nearest centroid"})

    print("Baseline binary :", {k: round(v, 4) for k, v in m_b.items()}, "thr=", thr)
    print("Baseline 10class:", {k: round(v, 4) for k, v in m_e.items()})
    return m_b, m_e


if __name__ == "__main__":
    run()
