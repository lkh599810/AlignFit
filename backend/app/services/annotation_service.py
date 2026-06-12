import base64

import cv2
import numpy as np

_SHOULDER_COLOR = (0, 200, 0)       # BGR green
_HIP_COLOR = (0, 140, 255)           # BGR orange
_TRUNK_COLOR = (255, 255, 255)       # BGR white
_HEAD_COLOR = (0, 230, 230)          # BGR yellow
_FOOT_COLOR = (220, 80, 0)           # BGR blue
_LINE_THICKNESS = 2
_POINT_RADIUS = 8
_FONT = cv2.FONT_HERSHEY_SIMPLEX
_FONT_SCALE = 0.5
_FONT_THICKNESS = 1
_VISIBILITY_THRESHOLD = 0.5


def _visible(landmark) -> bool:
    return getattr(landmark, "visibility", 1.0) >= _VISIBILITY_THRESHOLD


def _pt(landmark, w: int, h: int) -> tuple[int, int]:
    return (int(landmark.x * w), int(landmark.y * h))


def annotate_image(image_bytes: bytes, landmarks, analysis: dict) -> str:
    nparr = np.frombuffer(image_bytes, np.uint8)
    image_bgr = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
    if image_bgr is None:
        return ""

    h, w = image_bgr.shape[:2]

    ls = landmarks[11]
    rs = landmarks[12]
    lh = landmarks[23]
    rh = landmarks[24]

    ls_pt = _pt(ls, w, h)
    rs_pt = _pt(rs, w, h)
    lh_pt = _pt(lh, w, h)
    rh_pt = _pt(rh, w, h)

    shoulder_diff = analysis["shoulder_height_difference"]
    hip_diff = analysis["hip_height_difference"]

    # shoulder line + points
    cv2.line(image_bgr, ls_pt, rs_pt, _SHOULDER_COLOR, _LINE_THICKNESS)
    cv2.circle(image_bgr, ls_pt, _POINT_RADIUS, _SHOULDER_COLOR, -1)
    cv2.circle(image_bgr, rs_pt, _POINT_RADIUS, _SHOULDER_COLOR, -1)
    cv2.putText(image_bgr, "L.Shoulder", (ls_pt[0] + 6, ls_pt[1] - 6),
                _FONT, _FONT_SCALE, _SHOULDER_COLOR, _FONT_THICKNESS)
    cv2.putText(image_bgr, "R.Shoulder", (rs_pt[0] + 6, rs_pt[1] - 6),
                _FONT, _FONT_SCALE, _SHOULDER_COLOR, _FONT_THICKNESS)

    s_mid = ((ls_pt[0] + rs_pt[0]) // 2, min(ls_pt[1], rs_pt[1]) - 14)
    cv2.putText(image_bgr, f"Shoulder diff: {shoulder_diff:.4f}",
                s_mid, _FONT, _FONT_SCALE, _SHOULDER_COLOR, _FONT_THICKNESS)

    # hip line + points
    cv2.line(image_bgr, lh_pt, rh_pt, _HIP_COLOR, _LINE_THICKNESS)
    cv2.circle(image_bgr, lh_pt, _POINT_RADIUS, _HIP_COLOR, -1)
    cv2.circle(image_bgr, rh_pt, _POINT_RADIUS, _HIP_COLOR, -1)
    cv2.putText(image_bgr, "L.Hip", (lh_pt[0] + 6, lh_pt[1] - 6),
                _FONT, _FONT_SCALE, _HIP_COLOR, _FONT_THICKNESS)
    cv2.putText(image_bgr, "R.Hip", (rh_pt[0] + 6, rh_pt[1] - 6),
                _FONT, _FONT_SCALE, _HIP_COLOR, _FONT_THICKNESS)

    h_mid = ((lh_pt[0] + rh_pt[0]) // 2, min(lh_pt[1], rh_pt[1]) - 14)
    cv2.putText(image_bgr, f"Hip diff: {hip_diff:.4f}",
                h_mid, _FONT, _FONT_SCALE, _HIP_COLOR, _FONT_THICKNESS)

    # trunk center line from shoulder center to hip center
    s_cx = int(analysis["shoulder_center_x"] * w)
    s_cy = int(analysis["shoulder_center_y"] * h)
    hip_cx = int(analysis["hip_center_x"] * w)
    hip_cy = int(analysis["hip_center_y"] * h)
    cv2.line(image_bgr, (s_cx, s_cy), (hip_cx, hip_cy), _TRUNK_COLOR, _LINE_THICKNESS)
    cv2.putText(image_bgr, "Trunk line", (s_cx + 6, s_cy - 6),
                _FONT, _FONT_SCALE, _TRUNK_COLOR, _FONT_THICKNESS)

    # nose / head center point
    nose = landmarks[0]
    if _visible(nose):
        nose_pt = _pt(nose, w, h)
        cv2.circle(image_bgr, nose_pt, 6, _HEAD_COLOR, -1)
        cv2.putText(image_bgr, "Head", (nose_pt[0] + 6, nose_pt[1] - 6),
                    _FONT, _FONT_SCALE, _HEAD_COLOR, _FONT_THICKNESS)

    # foot direction arrows (heel → foot_index) when landmarks are visible
    left_heel = landmarks[29]
    left_fi = landmarks[31]
    if _visible(left_heel) and _visible(left_fi):
        lheel_pt = _pt(left_heel, w, h)
        lfi_pt = _pt(left_fi, w, h)
        cv2.arrowedLine(image_bgr, lheel_pt, lfi_pt, _FOOT_COLOR, _LINE_THICKNESS, tipLength=0.3)
        cv2.putText(image_bgr, "L.Foot dir", (lheel_pt[0] + 6, lheel_pt[1] - 6),
                    _FONT, _FONT_SCALE, _FOOT_COLOR, _FONT_THICKNESS)

    right_heel = landmarks[30]
    right_fi = landmarks[32]
    if _visible(right_heel) and _visible(right_fi):
        rheel_pt = _pt(right_heel, w, h)
        rfi_pt = _pt(right_fi, w, h)
        cv2.arrowedLine(image_bgr, rheel_pt, rfi_pt, _FOOT_COLOR, _LINE_THICKNESS, tipLength=0.3)
        cv2.putText(image_bgr, "R.Foot dir", (rheel_pt[0] + 6, rheel_pt[1] - 6),
                    _FONT, _FONT_SCALE, _FOOT_COLOR, _FONT_THICKNESS)

    _, buffer = cv2.imencode(".jpg", image_bgr)
    return base64.b64encode(buffer).decode("utf-8")
