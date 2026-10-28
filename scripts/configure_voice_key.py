#!/usr/bin/env python3
"""Validate the dedicated voice credential, then set only that Worker secret."""
import json
import os
import urllib.error
import urllib.request


def send(url, method, headers, body):
    request = urllib.request.Request(url, method=method, headers=headers,
                                     data=json.dumps(body).encode())
    try:
        with urllib.request.urlopen(request, timeout=90) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        try:
            data = json.load(error)
        except ValueError:
            data = {}
        return error.code, data


def main():
    key = os.environ.get("VOICE_GEMINI_API_KEY", "").strip()
    if not key:
        raise SystemExit("VOICE_GEMINI_API_KEY repository secret is missing; existing Worker credentials were not changed.")
    code, result = send(
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent",
        "POST", {"Content-Type": "application/json", "x-goog-api-key": key},
        {"contents": [{"parts": [{"text": "Correct the spelling and return only this Bengali sentence: আমি বাংলায় কথা বলি।"}]}],
         "generationConfig": {"maxOutputTokens": 128, "thinkingConfig": {"thinkingBudget": 0}}})
    print(f"Dedicated voice key Gemini test: HTTP {code}", flush=True)
    if code != 200:
        # Do not print provider messages, URLs, project identifiers or key values.
        for detail in result.get("error", {}).get("details", []):
            for violation in detail.get("violations", []):
                item = {name: violation[name] for name in ("quotaMetric", "quotaId", "quotaValue") if name in violation}
                print("Provider quota detail:", json.dumps(item), flush=True)
        raise SystemExit("Gemini test failed; Worker voice and Auto Reply credentials were not changed.")
    candidates = result.get("candidates", [])
    if not candidates or candidates[0].get("finishReason") != "STOP" or not any(
            p.get("text", "").strip() for p in candidates[0].get("content", {}).get("parts", []) if not p.get("thought")):
        raise SystemExit("Gemini test returned no complete text; Worker credentials were not changed.")
    account = "403f3c447ee5ba906794a7d1ee3852eb"
    worker = "wandering-waterfall-20ai-smart-reply-backend"
    code, result = send(
        f"https://api.cloudflare.com/client/v4/accounts/{account}/workers/scripts/{worker}/secrets",
        "PUT", {"Content-Type": "application/json", "Authorization": "Bearer " + os.environ["CLOUDFLARE_API_TOKEN"]},
        {"name": "VOICE_GEMINI_API_KEY", "text": key, "type": "secret_text"})
    if code not in (200, 201) or not result.get("success"):
        raise SystemExit(f"Cannot save dedicated Worker voice key: HTTP {code}")
    print("Dedicated Worker voice key saved after successful Gemini test. Auto Reply key unchanged.", flush=True)


if __name__ == "__main__":
    main()
