#!/usr/bin/env python3
"""Publish a preview only after the signed Android build and checks succeed."""
import hashlib
import os
from pathlib import Path
import re
import shutil
import subprocess

config = Path("app/build.gradle.kts").read_text()
code = int(re.search(r"versionCode = (\d+)", config)[1])
name = re.search(r'versionName = "([a-zA-Z0-9.-]+)"', config)[1]
if "preview" not in name:
    raise SystemExit("This workflow only publishes preview updates")
source = Path("release-apk/app-release.apk")
if not source.is_file():
    raise SystemExit("Tested signed APK artifact is missing")
asset = source.with_name(f"ai-smart-reply-{code}.apk")
shutil.copyfile(source, asset)
digest = hashlib.sha256(asset.read_bytes()).hexdigest()
notes = Path("release-apk/notes.md")
notes.write_text(
    f"পরীক্ষামূলক আপডেট {name} ({code})।\n\n"
    "হোমের Google অ্যাকাউন্টের তথ্য সেটিংসে নেওয়া হয়েছে। আপডেটের পর্দা সহজ করা হয়েছে। "
    "Drive ব্যাকআপের সরাসরি বাটন, সফল হওয়ার সময় এবং ব্যর্থতার স্পষ্ট বার্তা যোগ হয়েছে। "
    "Google লগইন করলেই ব্যাকআপ হয় না; Settings থেকে Drive অনুমতি দিতে হবে। বাস্তব Drive backup/restore পরীক্ষা বাকি।\n\n"
    "APK ডাউনলোড করে আগের অ্যাপের ওপর Update দিন। আনইনস্টল করবেন না। "
    "এটি preview; ফোনে যাচাই চলছে।\n\n"
    f"SHA-256: `{digest}`\n")
subprocess.run([
    "gh", "release", "create", f"preview-{code}", str(asset),
    "--repo", "hossainahmednumanbd/ai-smart-reply", "--target", os.environ["RELEASE_COMMIT"],
    "--prerelease", "--title", name, "--notes-file", str(notes)
], check=True)
