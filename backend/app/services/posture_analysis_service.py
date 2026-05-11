def analyze_posture(landmarks) -> dict:
    left_shoulder = landmarks[11]
    right_shoulder = landmarks[12]
    left_hip = landmarks[23]
    right_hip = landmarks[24]

    shoulder_diff = abs(left_shoulder.y - right_shoulder.y)
    hip_diff = abs(left_hip.y - right_hip.y)

    issues = []
    if shoulder_diff > 0.03:
        issues.append("shoulder asymmetry detected")
    if hip_diff > 0.03:
        issues.append("hip asymmetry detected")

    if issues:
        summary = "Posture analysis complete. " + " and ".join(issues).capitalize() + "."
    else:
        summary = "Posture analysis complete. No significant asymmetry detected."

    return {
        "shoulder_height_difference": round(shoulder_diff, 4),
        "hip_height_difference": round(hip_diff, 4),
        "simple_summary": summary,
    }
