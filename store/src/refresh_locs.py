"""Refreshes demo riders' live positions (fresh timestamps, clustered behind the captain) in the local emulator."""
import json, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import seed_demo as s
uids = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "demo_uids.json")))
pos = {"priya": (13.3742, 77.6861, 58.0, 64), "rohan": (13.3786, 77.6884, 66.0, 91),
       "kabir": (13.3711, 77.6849, 54.0, 42), "sneha": (13.3768, 77.6873, 61.0, 85)}
names = {r[0]: (r[1], f"{r[5]} {r[6]}") for r in s.RIDERS}
for k, (lat, lng, sp, b) in pos.items():
    s.put(f"locations/{uids[k]}", dict(uid=uids[k], name=names[k][0], bike=names[k][1], lat=lat, lng=lng,
                                      speedKmh=sp, battery=b, sharing=True, updatedAt=s.ago(seconds=8)))
print("ok")
