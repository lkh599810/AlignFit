import cv2
import numpy as np
import mediapipe as mp

mp_pose = mp.solutions.pose


def extract_landmarks(image_bytes: bytes):
    nparr = np.frombuffer(image_bytes, np.uint8)
    image_bgr = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
    if image_bgr is None:
        return None

    image_rgb = cv2.cvtColor(image_bgr, cv2.COLOR_BGR2RGB)

    with mp_pose.Pose(static_image_mode=True) as pose:
        results = pose.process(image_rgb)

    if not results.pose_landmarks:
        return None

    return results.pose_landmarks.landmark
