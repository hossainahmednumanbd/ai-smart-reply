#!/usr/bin/env python3
"""Check existing CI credential availability without printing any secret values."""
import os
from pathlib import Path

expected_account = "403f3c447ee5ba906794a7d1ee3852eb"
token_present = bool(os.environ.get("CLOUDFLARE_API_TOKEN", "").strip())
account = os.environ.get("CLOUDFLARE_ACCOUNT_ID", "").strip()
if account and account != expected_account:
    raise SystemExit("Configured Cloudflare account does not match this project's existing Worker; refusing deployment.")
print("Existing Cloudflare API token configured:", str(token_present).lower())
print("Existing Cloudflare account secret configured:", str(bool(account)).lower())
output = os.environ.get("GITHUB_OUTPUT")
if output:
    with open(output, "a") as file:
        file.write("configured=" + str(token_present).lower() + "\n")
summary = os.environ.get("GITHUB_STEP_SUMMARY")
if summary:
    with open(summary, "a") as file:
        file.write("## Worker deploy access\n\n")
        file.write("Cloudflare API token available to GitHub Actions: **" + ("yes" if token_present else "no") + "**.\n\n")
        if not token_present:
            file.write("**No production deployment was attempted.** The connected Cloudflare Git app uses its own credentials; they are not automatically available to GitHub Actions. Backend tests and dry-run results do not mean the live Worker was updated.\n")
