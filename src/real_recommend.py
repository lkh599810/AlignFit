"""End-to-end linking for the REAL deep-squat model:

    raw sequence -> 468-d summary feature -> trained MLP classifier
    -> (deep squat = m01) body region + AlignFit posture pattern
    -> recommendation.sqlite rule lookup -> homecare exercise list.

This wires the real-data movement-quality classifier (models/real_mlp_*.pt) into
the existing recommendation database (db/recommendation.sqlite) WITHOUT changing
either. The synthetic-model linker in `recommendation_db.py` is preserved.

Deep squat is exercise m01 in our schema, so it maps to region `lower_trunk` and
AlignFit pattern `lower_body_alignment` (see src/config.py). The classifier's
binary output (correct/incorrect) selects the messaging; the DB rule selects the
recommended homecare exercises.

Run:  python -m src.real_recommend     # build DB if needed + demo on real test seqs
"""
from __future__ import annotations

import os

import numpy as np
import torch

from src import config, real_dataset, real_features, recommendation_db
from src.models import MLP

REAL_MLP_PATH = os.path.join(config.MODELS_DIR, "real_mlp_binary_correctness.pt")
DEEP_SQUAT_EXERCISE_ID = "m01"   # avakanski real data is the deep squat exercise


def load_real_classifier():
    """Reconstruct + load the trained real-data MLP and its standardizer stats."""
    ckpt = torch.load(REAL_MLP_PATH, map_location="cpu")
    model = MLP(real_features.N_SUMMARY, 2, hidden_sizes=(128, 64), dropout=0.3)
    model.load_state_dict(ckpt["state_dict"])
    model.eval()
    return model, ckpt["mu"], ckpt["sd"]


@torch.no_grad()
def classify_sequence(seq: np.ndarray, model, mu, sd) -> dict:
    """(T,117) real deep-squat sequence -> binary movement-quality prediction."""
    feat = real_features.summary_vector(seq)[None, :]      # (1, 468)
    x = (feat - mu) / sd
    logits = model(torch.tensor(x, dtype=torch.float32))
    prob = torch.softmax(logits, dim=1)[0].numpy()
    pred = int(prob.argmax())
    return {"correctness_pred": pred,
            "predicted_label": "incorrect" if pred == 1 else "correct",
            "confidence": float(prob[pred])}


def recommend_for_sequence(seq: np.ndarray, model, mu, sd) -> dict:
    """Full chain: feature -> classifier -> region/pattern -> DB rule -> exercises."""
    cls = classify_sequence(seq, model, mu, sd)
    # exercise id is fixed (deep squat); reuse the existing DB linker for the
    # region/pattern -> recommendation rule lookup.
    payload = recommendation_db.recommend_for_prediction(
        DEEP_SQUAT_EXERCISE_ID, cls["correctness_pred"])
    payload["model"] = "real_mlp (UI-PRMD deep-squat, subject-independent)"
    payload["predicted_label"] = cls["predicted_label"]
    payload["confidence"] = round(cls["confidence"], 3)
    return payload


def _print_payload(title, p):
    print(f"\n--- {title} ---")
    print(f"  model            : {p['model']}")
    print(f"  predicted quality: {p['predicted_label']} "
          f"(conf {p['confidence']}) -> {p['movement_quality']}")
    print(f"  exercise/region  : {p['exercise_name']} -> {p['target_region']} / {p['posture_pattern']}")
    print(f"  note             : {p['note']}")
    print("  recommended homecare exercises:")
    for r in p["recommendations"]:
        print(f"    - {r['exercise_name']} ({r['related_body_part']}) "
              f"[verify_citation={r['verify_citation']}]")
    print(f"  caution          : {p['caution_message']}")


def demo():
    if not os.path.exists(config.RECOMMENDATION_DB):
        recommendation_db.build_db()
    model, mu, sd = load_real_classifier()

    recs = real_dataset.load()
    test = [r for r in recs if r["split"] == "test"]
    # pick one real correct and one real incorrect test repetition
    sample_correct = next(r for r in test if r["label_binary"] == 0)
    sample_incorrect = next(r for r in test if r["label_binary"] == 1)

    print("=== REAL end-to-end demo (held-out test subjects s09-s10) ===")
    for label, rec in (("true=correct", sample_correct),
                       ("true=incorrect", sample_incorrect)):
        p = recommend_for_sequence(rec["seq"], model, mu, sd)
        _print_payload(f"{rec['seq_id']}  ({label})", p)


if __name__ == "__main__":
    demo()
