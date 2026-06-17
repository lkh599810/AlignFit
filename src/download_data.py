"""Download the real UI-PRMD data assets used to seed/ground this project.

Two real sources are fetched (the official UI-PRMD site returned HTTP 404 as of
2026-06-18, see DECISIONS.md):

1. avakanski reduced data set  -> data/raw/avakanski/   (deep squat, real, large)
2. tejas1904 per-exercise samples -> data/raw/uiprmd_samples/ (m01..m10, real)

Run:  python -m src.download_data
"""
from __future__ import annotations

import os
import urllib.request

from src import config

AVAKANSKI_BASE = (
    "https://raw.githubusercontent.com/avakanski/"
    "A-Deep-Learning-Framework-for-Assessing-Physical-Rehabilitation-Exercises/"
    "master/Data"
)
AVAKANSKI_FILES = [
    "Data_Correct.csv", "Data_Incorrect.csv",
    "Labels_Correct.csv", "Labels_Incorrect.csv",
]

TEJAS_BASE = (
    "https://raw.githubusercontent.com/tejas1904/"
    "UI-PRMD-Visualize-python-port/master/data"
)


def _fetch(url: str, dest: str) -> None:
    if os.path.exists(dest) and os.path.getsize(dest) > 0:
        print(f"  [skip] {os.path.basename(dest)} already present")
        return
    print(f"  [get ] {url}")
    urllib.request.urlretrieve(url, dest)


def download_avakanski() -> None:
    os.makedirs(config.AVAKANSKI_DIR, exist_ok=True)
    print("avakanski reduced deep-squat data:")
    for f in AVAKANSKI_FILES:
        _fetch(f"{AVAKANSKI_BASE}/{f}", os.path.join(config.AVAKANSKI_DIR, f))


def download_samples() -> None:
    os.makedirs(config.SAMPLES_DIR, exist_ok=True)
    print("UI-PRMD per-exercise real sample sequences (m01..m10):")
    for mid in config.EXERCISE_IDS:
        name = f"{mid}_s01_e01_angles.txt"
        _fetch(f"{TEJAS_BASE}/{name}", os.path.join(config.SAMPLES_DIR, name))


if __name__ == "__main__":
    download_samples()
    download_avakanski()
    print("done.")
