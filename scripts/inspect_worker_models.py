#!/usr/bin/env python3
"""Read deployed model names; never print credentials or other binding values."""
import json
import os
import re
import urllib.error
import urllib.request
account = "403f3c447ee5ba906794a7d1ee3852eb"
worker = "wandering-waterfall-20ai-smart-reply-backend"
request = urllib.request.Request(
    f"https://api.cloudflare.com/client/v4/accounts/{account}/workers/scripts/{worker}/settings",
    headers={"Authorization": "Bearer " + os.environ["CLOUDFLARE_API_TOKEN"]})
try:
    with urllib.request.urlopen(request, timeout=30) as response:
        data = json.load(response)
except urllib.error.HTTPError as error:
    raise SystemExit(f"Cannot read Worker model settings: HTTP {error.code}") from None
if not data.get("success"):
    raise SystemExit("Worker settings query failed")
bindings = data.get("result", {}).get("bindings", [])
for name in ("GEMINI_MODEL", "VOICE_GEMINI_MODEL"):
    binding = next((b for b in bindings if b.get("name") == name), {})
    value = binding.get("text", "")
    if value and re.fullmatch(r"(?:models/)?gemini-[a-zA-Z0-9.-]{1,100}", value):
        print(f"Deployed {name}: {value}")
    else:
        print(f"Deployed {name}: absent or not a readable model name")
print("Gemini key binding present:", any(b.get("name") == "GEMINI_API_KEY" for b in bindings))
