#!/usr/bin/env python3
"""Verify public route contracts after a deployment, without using private credentials."""
import json
import time
import urllib.error
import urllib.request

root = "https://wandering-waterfall-20ai-smart-reply-backend.hossainahmednumanbd.workers.dev"

def probe(path, method="GET"):
    request = urllib.request.Request(root + path, method=method,
        data=b"{}" if method == "POST" else None,
        headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(request, timeout=15) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        return error.code, {}

for attempt in range(6):
    try:
        health, body = probe("/health")
        voice, _ = probe("/voice", "POST")
        reply, _ = probe("/reply", "POST")
        print(f"Live route check: health={health}, voice={voice}, reply={reply}")
        if health == 200 and body.get("status") == "ok" and body.get("voice_writer") is True and voice == 401 and reply == 401:
            print("Worker is live; voice and reply require Google authentication. Real signed-in Gemini/device testing is still required.")
            break
    except (OSError, ValueError):
        print("Live Worker check temporarily unavailable")
    if attempt == 5:
        raise SystemExit("Live Worker routes did not meet the expected contract; do not mark voice rollout complete.")
    time.sleep(5)
