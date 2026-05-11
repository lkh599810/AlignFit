CAUTION_MESSAGE = (
    "This result is a simple posture analysis for educational purposes. "
    "It is not a medical diagnosis. "
    "If pain continues or worsens, consult a medical professional."
)

SHOULDER_THRESHOLD = 0.03
HIP_THRESHOLD = 0.03


def generate_recommendations(shoulder_diff: float, hip_diff: float) -> dict:
    exercises = []

    if shoulder_diff > SHOULDER_THRESHOLD:
        exercises.append(
            "Shoulder mobility exercise: perform shoulder rolls and cross-body arm stretches."
        )
        exercises.append(
            "Gentle upper back stretching: try a doorway chest stretch and thoracic spine rotation."
        )

    if hip_diff > HIP_THRESHOLD:
        exercises.append(
            "Hip flexor stretch: perform a kneeling lunge stretch on each side."
        )
        exercises.append(
            "Glute bridge: lie on your back with knees bent and lift your hips."
        )

    if not exercises:
        exercises.append(
            "Maintain your current posture habits and continue regular movement throughout the day."
        )

    return {
        "exercises": exercises,
        "caution_message": CAUTION_MESSAGE,
    }
