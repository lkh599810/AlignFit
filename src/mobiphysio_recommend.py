"""End-to-end linking for the MobiPhysio model -> homecare recommendation.

    69-d summary feature + exercise id
        -> trained MobiPhysio MLP (binary correct/incorrect)
        -> body region + AlignFit posture pattern (shoulder / wrist / hip / back)
        -> recommendation.sqlite rule lookup
        -> homecare exercise list + non-diagnostic caution.

This extends the recommendation linkage to the four MobiPhysio regions
(shoulder, wrist, low-back, hip) WITHOUT changing the UI-PRMD linker
(`src.recommendation_db` / `src.real_recommend`); it reuses the shared
`recommend_by_pattern` helper. The MobiPhysio and UI-PRMD recommenders run in
parallel over the same recommendation DB.

Run:  python -m src.mobiphysio_recommend     # demo on real MobiPhysio test seqs
"""
from __future__ import annotations

import os

import numpy as np
import torch

from src import config, mobiphysio_dataset as mobi, recommendation_db
from src.models import MLP

MOBI_MLP_PATH = os.path.join(config.MODELS_DIR, "mobi_mlp_binary_correctness.pt")

CAUTION_BANNER = recommendation_db.CAUTION_BANNER


def load_classifier():
    """Reconstruct + load the trained MobiPhysio binary MLP and its standardizer."""
    ckpt = torch.load(MOBI_MLP_PATH, map_location="cpu")
    model = MLP(mobi.N_FEATURES, 2, hidden_sizes=tuple(ckpt["hidden_sizes"]),
                dropout=ckpt["dropout"])
    model.load_state_dict(ckpt["state_dict"])
    model.eval()
    return model, ckpt["mu"], ckpt["sd"]


@torch.no_grad()
def classify(feat: np.ndarray, model, mu, sd) -> dict:
    """69-d summary feature -> binary movement-quality prediction."""
    x = (feat[None, :] - mu) / sd
    prob = torch.softmax(model(torch.tensor(x, dtype=torch.float32)), dim=1)[0].numpy()
    pred = int(prob.argmax())
    return {"correctness_pred": pred,
            "predicted_label": "incorrect" if pred == 1 else "correct",
            "confidence": float(prob[pred])}


def recommend(feat: np.ndarray, exercise_id: str, model, mu, sd) -> dict:
    """Full chain for one MobiPhysio sequence (summary feature + its exercise id)."""
    cls = classify(feat, model, mu, sd)
    region = mobi.MOBI_EXERCISE_TO_REGION[exercise_id]
    pattern = mobi.MOBI_EXERCISE_TO_PATTERN[exercise_id]
    recs = recommendation_db.recommend_by_pattern(pattern, region=region)
    quality = "needs_attention" if cls["correctness_pred"] == 1 else "looks_ok"
    if cls["correctness_pred"] == 1:
        note = ("Movement showed asymmetry/limited range in this analysis; the "
                "items below may help. This is not a diagnosis.")
    else:
        note = ("Movement looked broadly even; items below are for general "
                "maintenance.")
    return {
        "model": "mobi_mlp (MobiPhysio, subject-independent)",
        "exercise_id": exercise_id, "target_region": region,
        "posture_pattern": pattern, "predicted_label": cls["predicted_label"],
        "confidence": round(cls["confidence"], 3), "movement_quality": quality,
        "note": note, "recommendations": recs, "caution_message": CAUTION_BANNER,
    }


def _print(title, p):
    print(f"\n--- {title} ---")
    print(f"  model            : {p['model']}")
    print(f"  predicted quality: {p['predicted_label']} (conf {p['confidence']}) "
          f"-> {p['movement_quality']}")
    print(f"  exercise/region  : {p['exercise_id']} -> {p['target_region']} / {p['posture_pattern']}")
    print(f"  note             : {p['note']}")
    print("  recommended homecare exercises:")
    for r in p["recommendations"]:
        print(f"    - {r['exercise_name']} ({r['related_body_part']}) "
              f"[verify_citation={r['verify_citation']}]")
    print(f"  caution          : {p['caution_message']}")


def demo():
    if not os.path.exists(config.RECOMMENDATION_DB):
        recommendation_db.build_db()
    model, mu, sd = load_classifier()
    te = mobi.load_summary("test")

    # one test sequence per region (shoulder / wrist / hip / back) to show coverage.
    print("=== MobiPhysio end-to-end demo (held-out test subjects) ===")
    shown = set()
    for i in range(len(te["exercise_id"])):
        ex = te["exercise_id"][i]
        region = mobi.MOBI_EXERCISE_TO_REGION[ex]
        if region in shown:
            continue
        shown.add(region)
        p = recommend(te["X"][i], ex, model, mu, sd)
        _print(f"{te['seq_id'][i]}  (exercise {ex}, region {region})", p)
        if len(shown) == 4:
            break


if __name__ == "__main__":
    demo()
