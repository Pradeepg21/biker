"""Seeds the LOCAL Firebase emulators (never production) with demo riders for store screenshots.

Usage: python -I store/src/seed_demo.py [--sos | --clear-sos | --pending]
Emulators: auth on 127.0.0.1:9099, firestore on 127.0.0.1:8080 (see firebase.json).
"""
import json, sys, urllib.request
from datetime import datetime, timedelta, timezone

PROJECT = "satya-digital-34053"
AUTH = "http://127.0.0.1:9099"
FS = f"http://127.0.0.1:8080/v1/projects/{PROJECT}/databases/(default)/documents"
PASSWORD = "Demo@1234"
NOW = datetime.now(timezone.utc)


def call(method, url, body=None):
    req = urllib.request.Request(url, method=method, data=json.dumps(body).encode() if body is not None else None,
                                 headers={"Content-Type": "application/json", "Authorization": "Bearer owner"})
    with urllib.request.urlopen(req) as r:
        return json.loads(r.read() or b"{}")


def val(v):
    if isinstance(v, bool): return {"booleanValue": v}
    if isinstance(v, int): return {"integerValue": str(v)}
    if isinstance(v, float): return {"doubleValue": v}
    if isinstance(v, datetime): return {"timestampValue": v.strftime("%Y-%m-%dT%H:%M:%S.%fZ")}
    if isinstance(v, dict): return {"mapValue": {"fields": {k: val(x) for k, x in v.items()}}}
    if isinstance(v, list): return {"arrayValue": {"values": [val(x) for x in v]}}
    if v is None: return {"nullValue": None}
    return {"stringValue": str(v)}


def put(path, data):
    call("PATCH", f"{FS}/{path}", {"fields": {k: val(x) for k, x in data.items()}})


def user(email):
    r = call("POST", f"{AUTH}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo",
             {"email": email, "password": PASSWORD, "returnSecureToken": True})
    return r["localId"]


def ago(**kw): return NOW - timedelta(**kw)


RIDERS = [
    # key, name, email, phone, blood, make, model, reg, city, lat, lng, speed, battery
    ("arjun", "Arjun Mehta", "arjun@demo.bikersthrill.app", "+91 98450 11234", "B+", "Royal Enfield", "Himalayan 450", "KA 01 AB 4521", "Bengaluru", 13.3702, 77.6835, 62.0, 78),
    ("priya", "Priya Nair", "priya@demo.bikersthrill.app", "+91 99001 22345", "O+", "KTM", "390 Adventure", "KA 03 MK 7788", "Bengaluru", 13.3651, 77.6812, 58.0, 64),
    ("rohan", "Rohan Shetty", "rohan@demo.bikersthrill.app", "+91 97412 33456", "A+", "Royal Enfield", "Interceptor 650", "KA 05 RS 6502", "Bengaluru", 13.3755, 77.6859, 66.0, 91),
    ("kabir", "Kabir Singh", "kabir@demo.bikersthrill.app", "+91 98861 44567", "AB+", "Triumph", "Speed 400", "KA 51 TS 4004", "Bengaluru", 13.3598, 77.6791, 54.0, 42),
    ("sneha", "Sneha Rao", "sneha@demo.bikersthrill.app", "+91 96320 55678", "B-", "Bajaj", "Dominar 400", "KA 02 SR 4000", "Bengaluru", 13.3800, 77.6880, 60.0, 85),
]
PENDING = [
    ("aditya", "Aditya Kulkarni", "aditya@demo.bikersthrill.app", "+91 90080 66789", "O-", "Honda", "CB350", "MH 12 AK 3501", "Pune"),
    ("meera", "Meera Iyer", "meera@demo.bikersthrill.app", "+91 94440 77890", "A-", "Yamaha", "R15 V4", "TN 09 MI 1504", "Chennai"),
    ("farhan", "Farhan Qureshi", "farhan@demo.bikersthrill.app", "+91 98110 88901", "B+", "Hero", "XPulse 200", "DL 08 FQ 2004", "New Delhi"),
]


def seed():
    uids, profiles = {}, {}
    for (k, name, email, phone, blood, make, model, reg, city, *_rest) in RIDERS:
        uid = user(email); uids[k] = uid
        profiles[k] = dict(uid=uid, email=email, role="admin" if k == "arjun" and "--admin" in sys.argv else "rider",
                           status="APPROVED", fullName=name, phone=phone, dateOfBirth="14/06/1992", gender="Male" if k in ("arjun", "rohan", "kabir") else "Female",
                           bloodGroup=blood, address="12, 4th Cross, Indiranagar", city=city, state="Karnataka", pincode="560038",
                           idType="Aadhaar", idNumber="XXXX XXXX 4321", licenseNumber="KA0120190012345", licenseExpiry="12/08/2039",
                           bikeMake=make, bikeModel=model, bikeRegNumber=reg, ridingExperienceYears="8",
                           emergencyContacts=[{"name": "Neha Mehta", "relation": "Spouse", "phone": "+91 98450 99887"},
                                              {"name": "Raj Mehta", "relation": "Brother", "phone": "+91 99860 11223"}],
                           medicalNotes="No allergies", createdAt=ago(days=40), submittedAt=ago(days=39), reviewedAt=ago(days=38), reviewedBy="admin")
        put(f"users/{uid}", profiles[k])
    for (k, name, email, phone, blood, make, model, reg, city) in PENDING:
        uid = user(email); uids[k] = uid
        put(f"users/{uid}", dict(uid=uid, email=email, role="rider", status="PENDING", fullName=name, phone=phone, bloodGroup=blood,
                                 city=city, bikeMake=make, bikeModel=model, bikeRegNumber=reg, idType="Aadhaar",
                                 createdAt=ago(hours=5), submittedAt=ago(hours=4 if k == "aditya" else 2)))
    admin = user("admin@demo.bikersthrill.app")
    put(f"users/{admin}", dict(uid=admin, email="admin@demo.bikersthrill.app", role="admin", status="APPROVED", fullName="Ops Admin", createdAt=ago(days=60)))

    def member(k, role):
        p = profiles[k]
        return {"uid": p["uid"], "name": p["fullName"], "phone": p["phone"], "bike": f'{p["bikeMake"]} {p["bikeModel"]}', "bloodGroup": p["bloodGroup"], "role": role}

    g1 = ["arjun", "priya", "rohan", "kabir", "sneha"]
    roles = {"arjun": "Captain", "priya": "Sweep"}
    put("groups/blr-weekend", dict(name="Bangalore Weekend Riders", description="Sunday sunrise rides around Bengaluru. Helmets & jackets mandatory.",
                                   code="482915", ownerId=uids["arjun"], memberIds=[uids[k] for k in g1],
                                   members={uids[k]: member(k, roles.get(k, "Member")) for k in g1}, createdAt=ago(days=30),
                                   lastMessage="Priya: Fuel stop at Devanahalli in 10 min", lastMessageAt=ago(minutes=2)))
    put("groups/coorg-crew", dict(name="Coorg Coffee Trail 2026", description="2-day ride to Coorg via Mysuru.", code="731406",
                                  ownerId=uids["rohan"], memberIds=[uids[k] for k in ("rohan", "arjun", "sneha")],
                                  members={uids[k]: member(k, "Captain" if k == "rohan" else "Member") for k in ("rohan", "arjun", "sneha")},
                                  createdAt=ago(days=12), lastMessage="Rohan: Homestay booked for 6 riders ✅", lastMessageAt=ago(hours=3)))
    put("groups/ladakh-26", dict(name="Ladakh Expedition – June", description="Manali → Leh → Khardung La.", code="905218",
                                 ownerId=uids["kabir"], memberIds=[uids[k] for k in ("kabir", "arjun", "priya")],
                                 members={uids[k]: member(k, "Captain" if k == "kabir" else "Member") for k in ("kabir", "arjun", "priya")},
                                 createdAt=ago(days=50), lastMessage="Kabir: Permits done for everyone", lastMessageAt=ago(days=1)))

    msgs = [
        ("arjun", "Arjun Mehta created the group", "SYSTEM", 600),
        ("arjun", "Morning riders! Meeting at Hebbal flyover, 5:15 AM sharp 🌄", "TEXT", 95),
        ("priya", "I'll be the sweep today. Riding at the back 👍", "TEXT", 90),
        ("rohan", "Regroup", "QUICK", 40),
        ("kabir", "Shared live location", "LOCATION", 30),
        ("sneha", "Road hazard", "QUICK", 12),
        ("sneha", "Big pothole after the toll, left lane. Go slow!", "TEXT", 11),
        ("arjun", "Thanks Sneha. Keep 2-second gap everyone", "TEXT", 8),
        ("priya", "Fuel stop at Devanahalli in 10 min", "TEXT", 2),
    ]
    for i, (k, text, typ, mins) in enumerate(msgs):
        m = dict(senderId=uids[k], senderName=profiles[k]["fullName"], text=text, type=typ, createdAt=ago(minutes=mins))
        if typ == "LOCATION":
            m.update(lat=13.3598, lng=77.6791)
        put(f"groups/blr-weekend/messages/m{i:02d}", m)

    for (k, name, *_x, lat, lng, speed, battery) in RIDERS:
        p = profiles[k]
        put(f"locations/{uids[k]}", dict(uid=uids[k], name=name, bike=f'{p["bikeMake"]} {p["bikeModel"]}', lat=lat, lng=lng,
                                        speedKmh=speed, battery=battery, sharing=True, updatedAt=ago(seconds=20)))

    tomorrow5 = (NOW + timedelta(days=1)).replace(hour=23, minute=30, second=0, microsecond=0)  # 5:00 AM IST
    rides = [
        ("nandi", "Sunrise ride to Nandi Hills", "Hebbal Flyover, Bengaluru", "Nandi Hills", "Indian Oil, Hebbal", tomorrow5, 62.0, "Easy", 12, "GROUP", "blr-weekend", "Bangalore Weekend Riders", "arjun", ["arjun", "priya", "rohan", "sneha"]),
        ("coorg", "Coorg Coffee Trail – 2 days", "Bengaluru", "Madikeri, Coorg", "Kengeri Metro", tomorrow5 + timedelta(days=6), 265.0, "Moderate", 8, "PUBLIC", "", "", "rohan", ["rohan", "arjun", "sneha"]),
        ("chikmag", "Chikmagalur Monsoon Ride", "Bengaluru", "Mullayanagiri", "Nelamangala Toll", tomorrow5 + timedelta(days=13), 245.0, "Hard", 10, "PUBLIC", "", "", "priya", ["priya", "kabir"]),
        ("skandagiri", "Skandagiri Night Trek & Ride", "Yelahanka", "Skandagiri", "Yelahanka New Town", tomorrow5 + timedelta(days=3), 70.0, "Moderate", 15, "PUBLIC", "", "", "kabir", ["kabir", "priya", "rohan", "arjun", "sneha"]),
    ]
    for (rid, title, start, dest, meet, when, km, diff, maxr, vis, gid, gname, by, parts) in rides:
        put(f"rides/{rid}", dict(title=title, description="Moderate pace, regroup every 50 km. Full riding gear required.",
                                 startPoint=start, destination=dest, meetingPoint=meet, startTime=when, distanceKm=km,
                                 difficulty=diff, maxRiders=maxr, visibility=vis, groupId=gid, groupName=gname,
                                 createdBy=uids[by], creatorName=profiles[by]["fullName"], participantIds=[uids[k] for k in parts],
                                 participantNames={uids[k]: profiles[k]["fullName"] for k in parts}, createdAt=ago(days=2)))
    json.dump(uids, open(__file__.replace("seed_demo.py", "demo_uids.json"), "w"))
    print("seeded", len(uids), "riders; sign in as arjun@demo.bikersthrill.app /", PASSWORD)


def sos(active=True):
    uids = json.load(open(__file__.replace("seed_demo.py", "demo_uids.json")))
    if active:
        put("sos/demo-kabir", dict(uid=uids["kabir"], name="Kabir Singh", phone="+91 98861 44567", bloodGroup="AB+", bikeRegNumber="KA 51 TS 4004",
                                   lat=13.3598, lng=77.6791, reason="Crash detected – rider not responding", groupIds=["blr-weekend", "ladakh-26"],
                                   active=True, createdAt=ago(seconds=40)))
    else:
        call("DELETE", f"{FS}/sos/demo-kabir")


if __name__ == "__main__":
    if "--sos" in sys.argv: sos(True)
    elif "--clear-sos" in sys.argv: sos(False)
    else: seed()
