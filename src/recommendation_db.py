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
     "shoulders / upper back", "shoulder blade squeeze exercise",
     "[B] Ekstrom RA, Donatelli RA, Soderberg GL. J Orthop Sports Phys Ther. 2003;33(5):247-258."),
    ("Wall angel (gentle range)", config.REGION_UPPER_SHOULDER, "shoulder_asymmetry",
     "A controlled mobility drill commonly used for upper-back and shoulder awareness.",
     "Move only within a comfortable range; avoid arching the lower back.",
     "shoulders / thoracic spine", "wall angel exercise beginner",
     "[B] Ekstrom RA, et al. J Orthop Sports Phys Ther. 2003;33(5):247-258. [verify]"),
    ("Pendulum shoulder swing", config.REGION_UPPER_SHOULDER, "shoulder_mobility",
     "Gentle motion often used to encourage relaxed shoulder mobility.",
     "Keep it light and pain-free; not a substitute for clinical care.",
     "shoulder joint", "pendulum exercise shoulder",
     "Codman pendulum (classic shoulder rehabilitation). [verify - Codman original / shoulder rehab text]"),
    ("Doorway pec stretch", config.REGION_UPPER_SHOULDER, "shoulder_mobility",
     "A common stretch to counter a forward-rounded shoulder posture.",
     "Avoid overstretching; ease off if you feel tingling in the arm.",
     "chest / front shoulder", "doorway chest stretch",
     "[E] Kisner C, Colby LA. Therapeutic Exercise: Foundations and Techniques. F.A. Davis. [verify ed.]"),
    ("External rotation with light band", config.REGION_UPPER_SHOULDER, "shoulder_rotation",
     "Often used to build awareness/control of shoulder rotation.",
     "Use very light resistance; stop with any sharp pain.",
     "rotator cuff region", "shoulder external rotation band beginner",
     "[B] Ekstrom RA, et al. J Orthop Sports Phys Ther. 2003;33(5):247-258. [verify - add rotator-cuff band study]"),
    ("Scapular wall slide", config.REGION_UPPER_SHOULDER, "shoulder_rotation",
     "Encourages coordinated shoulder-blade movement during arm motion.",
     "Keep the lower back neutral; move slowly.",
     "shoulder blades", "scapular wall slide",
     "[B] Ekstrom RA, et al. J Orthop Sports Phys Ther. 2003;33(5):247-258. [verify exercise name]"),

    ("Glute bridge", config.REGION_LOWER_TRUNK, "trunk_hip_strength",
     "Commonly used to engage the hips and trunk in a supported position.",
     "Avoid over-arching the lower back; stop if it hurts.",
     "glutes / hips / core", "glute bridge exercise",
     "[A] Boren K, et al. Int J Sports Phys Ther. 2011;6(3):206-223."),
    ("Bird-dog", config.REGION_LOWER_TRUNK, "trunk_hip_strength",
     "Often used for trunk control and balanced left/right activation.",
     "Keep the spine neutral; move in a slow, controlled way.",
     "core / lower back", "bird dog exercise beginner",
     "[C] McGill SM. Low Back Disorders: Evidence-Based Prevention and Rehabilitation. Human Kinetics. [verify ed./year]"),
    ("Standing hip flexor stretch", config.REGION_LOWER_TRUNK, "hip_mobility",
     "A general stretch for the front-of-hip area.",
     "Do not force the range; keep it comfortable.",
     "hip flexors", "standing hip flexor stretch",
     "[E] Kisner C, Colby LA. Therapeutic Exercise: Foundations and Techniques. F.A. Davis. [verify ed.]"),
    ("Half-kneeling hip mobility drill", config.REGION_LOWER_TRUNK, "hip_mobility",
     "Gentle mobility work for the hips.",
     "Pad the knee; avoid pain.",
     "hips", "half kneeling hip mobility",
     "[E] Kisner C, Colby LA. Therapeutic Exercise: Foundations and Techniques. F.A. Davis. [verify ed.]"),
    ("Mini squat (comfortable depth)", config.REGION_LOWER_TRUNK, "lower_body_alignment",
     "Helps practise even, controlled lower-body movement.",
     "Keep knees tracking over toes; reduce depth if uncomfortable.",
     "knees / hips / thighs", "mini squat proper form",
     "[A] Boren K, et al. Int J Sports Phys Ther. 2011;6(3):206-223. [verify - mini vs single-leg squat]"),
    ("Step-up (low step)", config.REGION_LOWER_TRUNK, "lower_body_alignment",
     "Practises balanced single-leg loading on a low step.",
     "Use a low, stable step and support if needed.",
     "legs / hips", "low step up exercise",
     "[A] Boren K, et al. Int J Sports Phys Ther. 2011;6(3):206-223. [verify exercise]"),
    ("Single-leg stand near support", config.REGION_LOWER_TRUNK, "single_leg_balance",
     "A simple balance drill done next to a stable surface.",
     "Stay close to support; stop if dizzy.",
     "ankles / hips / balance", "single leg balance exercise beginner",
     "[A] Boren K, et al. Int J Sports Phys Ther. 2011;6(3):206-223; [D] Distefano LJ, et al. J Orthop Sports Phys Ther. 2009;39(7):532-540."),
    ("Side-lying leg lift", config.REGION_LOWER_TRUNK, "lateral_pelvis_stability",
     "Often used to encourage lateral hip/pelvis stability.",
     "Keep movements small and controlled.",
     "lateral hip / glute medius", "side lying leg raise",
     "[A] Boren K, et al. Int J Sports Phys Ther. 2011;6(3):206-223."),

    # ---- MobiPhysio-region extension (wrist + low-back patterns) -------------
    # Shoulder and hip_mobility patterns above are reused for MobiPhysio; only the
    # wrist and low-back patterns are new. Citations follow the same anchored,
    # [verify]-flagged convention as report/recommendation_citations.md (general
    # therapeutic-exercise / spine-mobility anchors; no fabricated per-exercise RCT).
    ("Wrist flexor stretch", config.REGION_UPPER_WRIST, "wrist_mobility",
     "A gentle stretch for the underside of the forearm/wrist.",
     "Ease off if you feel numbness or tingling in the hand.",
     "wrist / forearm flexors", "wrist flexor stretch",
     "[E] Kisner C, Colby LA. Therapeutic Exercise: Foundations and Techniques. F.A. Davis. [verify ed.]"),
    ("Wrist extensor stretch", config.REGION_UPPER_WRIST, "wrist_mobility",
     "A gentle stretch for the top of the forearm/wrist.",
     "Keep it comfortable; do not force the range.",
     "wrist / forearm extensors", "wrist extensor stretch",
     "[E] Kisner C, Colby LA. Therapeutic Exercise: Foundations and Techniques. F.A. Davis. [verify ed.]"),
    ("Wrist circles (active range)", config.REGION_UPPER_WRIST, "wrist_mobility",
     "Slow active circles to encourage relaxed wrist mobility.",
     "Move slowly and stay pain-free.",
     "wrist joint", "wrist mobility circles exercise",
     "[E] Kisner C, Colby LA. Therapeutic Exercise: Foundations and Techniques. F.A. Davis. [verify ed.]"),
    ("Cat-camel mobility", config.REGION_LOW_BACK, "low_back_mobility",
     "A slow spinal flexion/extension drill commonly used to warm up the low back.",
     "Move within a comfortable range; avoid forcing the ends of motion.",
     "lumbar spine / low back", "cat camel exercise",
     "[C] McGill SM. Low Back Disorders: Evidence-Based Prevention and Rehabilitation. Human Kinetics. [verify ed./year]"),
    ("Gentle back extension (prone press-up)", config.REGION_LOW_BACK, "low_back_mobility",
     "A low-range extension movement sometimes used for low-back comfort.",
     "Stop if it increases or centralizes pain; not a substitute for clinical care.",
     "low back / lumbar spine", "prone press up gentle back extension",
     "[E] Kisner C, Colby LA. Therapeutic Exercise: Foundations and Techniques. F.A. Davis. [verify ed.]"),
    ("Pelvic tilt", config.REGION_LOW_BACK, "low_back_mobility",
     "A small controlled tilt to practise gentle lumbar/pelvic movement.",
     "Keep movements small; avoid breath-holding.",
     "pelvis / lower back", "pelvic tilt exercise beginner",
     "[E] Kisner C, Colby LA. Therapeutic Exercise: Foundations and Techniques. F.A. Davis. [verify ed.]"),
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


def recommend_by_pattern(pattern: str, region: str = None, limit: int = 3) -> list[dict]:
    """Look up homecare items by AlignFit posture pattern (region as fallback).

    Dataset-agnostic helper used by both the UI-PRMD and MobiPhysio linkers so
    neither has to embed SQL. Matches `posture_pattern` first; if nothing matches
    and a `region` is given, falls back to a `target_region` match.
    """
    conn = sqlite3.connect(config.RECOMMENDATION_DB)
    conn.row_factory = sqlite3.Row
    cur = conn.cursor()
    cols = ("exercise_name, related_body_part, reason, cautions, youtube_query, "
            "verify_citation")
    rows = cur.execute(
        f"SELECT {cols} FROM recommendation WHERE posture_pattern = ? LIMIT ?",
        (pattern, limit)).fetchall()
    if not rows and region is not None:
        rows = cur.execute(
            f"SELECT {cols} FROM recommendation WHERE target_region = ? LIMIT ?",
            (region, limit)).fetchall()
    conn.close()
    return [dict(r) for r in rows]


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
