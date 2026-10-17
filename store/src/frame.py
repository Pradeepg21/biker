"""frame.py <raw.png> <out.png> <W> <H> "<title html>" "<subtitle>" — wraps a raw device screenshot in a captioned store frame."""
import os, subprocess, sys, urllib.parse
from PIL import Image
raw, out, W, H, title, sub = sys.argv[1:7]
here = os.path.dirname(os.path.abspath(__file__))
w, h = Image.open(raw).size
q = urllib.parse.urlencode({"i": "file:///" + os.path.abspath(raw).replace("\\", "/"), "t": title, "s": sub, "ar": h / w,
                            "dh": "0.70" if h / w > 1.5 else "0.74"})
page = "file:///" + os.path.join(here, "frame.html").replace("\\", "/") + "?" + q
chrome = r"C:\Program Files\Google\Chrome\Application\chrome.exe"
subprocess.run([chrome, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--force-device-scale-factor=1",
                "--virtual-time-budget=8000", "--allow-file-access-from-files", f"--user-data-dir={os.path.join(os.environ['TEMP'], 'bt-chrome')}",
                f"--screenshot={os.path.abspath(out)}", f"--window-size={W},{H}", page],
               stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=90)
Image.open(out).convert("RGB").save(out)
print(out, Image.open(out).size)
