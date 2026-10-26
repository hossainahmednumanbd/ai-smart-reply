#!/usr/bin/env python3
"""Build only with the privately stored permanent key; never create a replacement key."""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile

EXPECTED_SHA1 = "D4DF16159949D42F9A318A91188704E4C2C8657B"

def build(check_only=False):
    encoded = os.environ.get("ANDROID_SIGNING_BUNDLE", "")
    if not encoded:
        raise ValueError("Add repository secret ANDROID_SIGNING_BUNDLE before building the signed APK.")
    try:
        bundle = json.loads(base64.b64decode(encoded, validate=True))
        alias = bundle["alias"]
        password = bundle["password"]
        key_bytes = base64.b64decode(bundle["keystore_base64"], validate=True)
        if alias != "ai-smart-reply" or not isinstance(password, str) or len(password) < 16:
            raise ValueError()
    except Exception:
        raise ValueError("Invalid signing bundle. Restore the original private backup.") from None
    if os.environ.get("GITHUB_ACTIONS") == "true":
        # The combined bundle is already a secret; mask decoded values as well.
        for value in (password, bundle["keystore_base64"]):
            print("::add-mask::" + value, flush=True)
    with tempfile.TemporaryDirectory(prefix="ai-smart-reply-signing-") as temp:
        key_path = Path(temp) / "release.p12"
        key_path.write_bytes(key_bytes)
        key_path.chmod(0o600)
        env = os.environ.copy()
        env.pop("ANDROID_SIGNING_BUNDLE", None)
        env.update(ANDROID_KEYSTORE_PATH=str(key_path), ANDROID_STORE_PASSWORD=password,
                   ANDROID_KEY_PASSWORD=password, ANDROID_KEY_ALIAS=alias)
        result = subprocess.run(
            ["keytool", "-list", "-rfc", "-alias", alias, "-keystore", str(key_path),
             "-storetype", "PKCS12", "-storepass:env", "ANDROID_STORE_PASSWORD"],
            env=env, text=True, capture_output=True)
        match = re.search(r"-----BEGIN CERTIFICATE-----([A-Za-z0-9+/=\s]+)-----END CERTIFICATE-----",
                          result.stdout)
        if result.returncode or not match:
            raise ValueError("Cannot unlock the signing key. Restore the original private backup.")
        cert = base64.b64decode(match.group(1))
        if hashlib.sha1(cert).hexdigest().upper() != EXPECTED_SHA1:
            raise ValueError("Wrong signing certificate. Refusing to produce an incompatible APK.")
        print("Verified permanent signing certificate: " + EXPECTED_SHA1, flush=True)
        if not check_only:
            subprocess.run(["gradle", ":app:assembleRelease", "--no-daemon"], env=env, check=True)

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--check-only", action="store_true")
    args = parser.parse_args()
    try:
        build(args.check_only)
    except (ValueError, subprocess.CalledProcessError) as error:
        # Our own messages contain no key, password, bundle or token.
        print(str(error), file=sys.stderr)
        sys.exit(1)
