"""Frames the raw device captures in store/raw into captioned Play screenshots in store/screenshots."""
import os, subprocess, sys
here = os.path.dirname(os.path.abspath(__file__)); root = os.path.dirname(here)
PHONE = [
    ("01-home", "Ride safe. <em>One-tap SOS.</em>", "Live ride stats, SOS and quick help on one screen"),
    ("04-map", "See your <em>whole group</em> live", "Speed, battery and distance from the captain"),
    ("10-sos-home", "Instant <em>SOS alerts</em>", "Location, blood group and bike number to every rider"),
    ("12-crash", "Automatic <em>crash detection</em>", "30-second countdown, then SOS is sent for you"),
    ("03-chat", "Group chat <em>built for the road</em>", "Regroup, Fuel stop, Road hazard in one tap"),
    ("05-rides", "Plan and <em>share rides</em>", "Public rides or just for your group"),
    ("06-ride-detail", "Every ride, <em>every detail</em>", "Meeting point, distance, difficulty and riders"),
    ("13-admin", "Only <em>verified riders</em>", "Every ID and driving licence is checked"),
]
TABLET = [
    ("home", "Ride safe. <em>One-tap SOS.</em>", "Live ride stats, SOS and quick help"),
    ("map", "See your <em>whole group</em> live", "Speed, battery and distance from the captain"),
    ("chat", "Group chat <em>built for the road</em>", "Quick messages made for riding"),
    ("rides", "Plan and <em>share rides</em>", "Public rides or just for your group"),
    ("admin", "Only <em>verified riders</em>", "Every ID and driving licence is checked"),
]
jobs = [("phone", PHONE, 1080, 1920)] + [(d, TABLET, w, h) for d, w, h in (("tablet-7", 1080, 1920), ("tablet-10", 1440, 2560))]
for folder, items, W, H in jobs:
    for i, (name, title, sub) in enumerate(items, 1):
        raw = os.path.join(root, "raw", folder, name + ".png")
        out = os.path.join(root, "screenshots", folder, f"{i:02d}-{name.split('-', 1)[-1] if folder == 'phone' else name}.png")
        subprocess.run([sys.executable, "-I", os.path.join(here, "frame.py"), raw, out, str(W), str(H), title, sub], check=True)
