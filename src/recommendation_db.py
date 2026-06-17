"""Phase 10 - AlignFit homecare recommendation database + linking function.

Builds db/recommendation.sqlite from a curated, rule-based draft grounded in
*general* physical-therapy knowledge. IMPORTANT (per project rules):
  - This is NOT a medical diagnosis and contains NO invented paper citations.
  - Every clinically-flavoured claim carries `verify_citation = 'TODO'` so a human
    must verify it against a real source before any real-world use.

Linking: a model prediction (exercise_id -> region + AlignFit posture pattern)
is mapped to recommended homecare exercises by querying this DB.

Run:  python -m src.recommendation_db        # build + demo lookup
"""
from __future__ import annotations

import sqlite3

from src import config

# (recommended_exercise, target_region, posture_pattern, reason, cautions,
#  related_body_part, youtube_query, verify_citation)
RECOMMENDATIONS = [
    ("Shoulder blade squeeze", config.REGION_UPPER_SHOULDER, "shoulder_asymmetry",
     "May help raise awareness of left/right shoulder evenness during daily posture.",
     "Stop if it causes shoulder or neck pain; keep movements gentle.",
     "shoulders / upper back", "shoulder blade squeeze exercise", "TODO"),
    ("Wall angel (gentle range)", config.REGION_UPPER_SHOULDER, "shoulder_asymmetry",
     "A controlled mobility drill commonly used for upper-back and shoulder awareness.",
     "Move only within a comfortable range; avoid arching the lower back.",
     "shoulders / thoracic spine", "wall angel exercise beginner", "TODO"),
    ("Pendulum shoulder swing", config.REGION_UPPER_SHOULDER, "shoulder_mobility",
     "Gentle motion often used to encourage relaxed shoulder mobility.",
     "Keep it light and pain-free; not a substitute for clinical care.",
     "shoulder joint", "pendulum exercise shoulder", "TODO"),
    ("Doorway pec stretch", config.REGION_UPPER_SHOULDER, "shoulder_mobility",
     "A common stretch to counter a forward-rounded shoulder posture.",
     "Avoid overstretching; ease off if you feel tingling in the arm.",
     "chest / front shoulder", "doorway chest stretch", "TODO"),
    ("External rotation with light band", config.REGION_UPPER_SHOULDER, "shoulder_rotation",
     "Often used to build awareness/control of shoulder rotation.",
     "Use very light resistance; stop with any sharp pain.",
     "rotator cuff region", "shoulder external rotation band beginner", "TODO"),
    ("Scapular wall slide", config.REGION_UPPER_SHOULDER, "shoulder_rotation",
     "Encourages coordinated shoulder-blade movement during arm motion.",
     "Keep the lower back neutral; move slowly.",
     "shoulder blades", "scapular wall slide", "TODO"),

    ("Glute bridge", config.REGION_LOWER_TRUNK, "trunk_hip_strength",
     "Commonly used to engage the hips and trunk in a supported position.",
     "Avoid over-arching the lower back; stop if it hurts.",
     "glutes / hips / core", "glute bridge exercise", "TODO"),
    ("Bird-dog", config.REGION_LOWER_TRUNK, "trunk_hip_strength",
     "Often used for trunk control and balanced left/right activation.",
     "Keep the spine neutral; move in a slow, controlled way.",
     "core / lower back", "bird dog exercise beginner", "TODO"),
    ("Standing hip flexor stretch", config.REGION_LOWER_TRUNK, "hip_mobility",
     "A general stretch for the front-of-hip area.",
     "Do not force the range; keep it comfortable.",
     "hip flexors", "standing hip flexor stretch", "TODO"),
    ("Half-kneeling hip mobility drill", config.REGION_LOWER_TRUNK, "hip_mobility",
     "Gentle mobility work for the hips.",
     "Pad the knee; avoid pain.",
     "hips", "half kneeling hip mobility", "TODO"),
    ("Mini squat (comfortable depth)", config.REGION_LOWER_TRUNK, "lower_body_alignment",
     "Helps practise even, controlled lower-body movement.",
     "Keep knees tracking over toes; reduce depth if uncomfortable.",
     "knees / hips / thighs", "mini squat proper form", "TODO"),
    ("Step-up (low step)", config.REGION_LOWER_TRUNK, "lower_body_alignment",
     "Practises balanced single-leg loading on a low step.",
     "Use a low, stable step and support if needed.",
     "legs / hips", "low step up exercise", "TODO"),
    ("Single-leg stand near support", config.REGION_LOWER_TRUNK, "single_leg_balance",
     "A simple balance drill done next to a stable surface.",
     "Stay close to support; stop if dizzy.",
     "ankles / hips / balance", "single leg balance exercise beginner", "TODO"),
    ("Side-lying leg lift", config.REGION_LOWER_TRUNK, "lateral_pelvis_stability",
     "Often used to encourage lateral hip/pelvis stability.",
     "Keep movements small and controlled.",
     "lateral hip / glute medius", "side lying leg raise", "TODO"),
]

CAUTION_BANNER = (
    "This is a simple posture/movement analysis for educational purposes, not a "
    "medical diagnosis. If pain continues or worsens, consult a medical professional."
)


def build_db():
    conn = sqlite3.connect(config.RECOMMENDATION_DB)
    cur = conn.cursor()
    cur.execute("DROP TABLE IF EXISTS recommendation")
    cur.execute("""CREATE TABLE recommendation (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        exercise_name TEXT, target_region TEXT, posture_pattern TEXT,
        reason TEXT, cautions TEXT, related_body_part TEXT,
        youtube_query TEXT, verify_citation TEXT)""")
    cur.executemany(
        "INSERT INTO recommendation (exercise_name, target_region, posture_pattern, "
        "reason, cautions, related_body_part, youtube_query, verify_citation) "
        "VALUES (?,?,?,?,?,?,?,?)", RECOMMENDATIONS)
    conn.commit()
    conn.close()


def recommend_for_exercise(exercise_id: str, limit: int = 3) -> dict:
    """Model output (exercise id) -> region + pattern -> recommended homecare items."""
    region = config.EXERCISE_TO_REGION[exercise_id]
    pattern = config.EXERCISE_TO_ALIGNFIT_PATTERN[exercise_id]
    conn = sqlite3.connect(config.RECOMMENDATION_DB)
    conn.row_factory = sqlite3.Row
    cur = conn.cursor()
    rows = cur.execute(
        "SELECT exercise_name, related_body_part, reason, cautions, youtube_query, "
        "verify_citation FROM recommendation WHERE posture_pattern = ? LIMIT ?",
        (pattern, limit)).fetchall()
    if not rows:  # fall back to region match
        rows = cur.execute(
            "SELECT exercise_name, related_body_part, reason, cautions, youtube_query, "
            "verify_citation FROM recommendation WHERE target_region = ? LIMIT ?",
            (region, limit)).fetchall()
    conn.close()
    return {
        "exercise_id": exercise_id,
        "exercise_name": config.EXERCISES[exercise_id],
        "target_region": region,
        "posture_pattern": pattern,
        "caution_message": CAUTION_BANNER,
        "recommendations": [dict(r) for r in rows],
    }


def recommend_for_prediction(exercise_id: str, correctness_pred: int) -> dict:
    """Full link: 10-class + binary model outputs -> recommendation payload."""
    out = recommend_for_exercise(exercise_id)
    out["movement_quality"] = "needs_attention" if correctness_pred == 1 else "looks_ok"
    if correctness_pred == 0:
        out["note"] = ("Movement looked broadly even; recommendations below are for "
                       "general maintenance.")
    else:
        out["note"] = ("Movement showed asymmetry/limited range in this analysis; the "
                       "items below may help. This is not a diagnosis.")
    return out


if __name__ == "__main__":
    build_db()
    print(f"recommendation DB built -> {config.RECOMMENDATION_DB}\n")
    demo = recommend_for_prediction("m07", correctness_pred=1)  # shoulder, needs attention
    print(f"Predicted exercise : {demo['exercise_id']} ({demo['exercise_name']})")
    print(f"Region / pattern   : {demo['target_region']} / {demo['posture_pattern']}")
    print(f"Movement quality   : {demo['movement_quality']}")
    print(f"Note               : {demo['note']}")
    print("Recommendations:")
    for r in demo["recommendations"]:
        print(f"  - {r['exercise_name']} ({r['related_body_part']}) "
              f"[verify_citation={r['verify_citation']}]")
    print(f"\nCaution: {demo['caution_message']}")
