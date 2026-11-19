#!/usr/bin/env python3
"""Materialize a private Firebase Android config without printing its contents."""
import json
import os
from pathlib import Path
import sys

EXPECTED_PROJECT = "stream-flow-pro"
EXPECTED_PACKAGE = "com.aistudio.controlstreaming.sales"
DESTINATION = Path("app/google-services.json")


def load_config():
    raw = os.environ.get("FIREBASE_ANDROID_JSON")
    if not raw:
        raise ValueError("FIREBASE_ANDROID_JSON is not set")
    try:
        value = json.loads(raw)
    except (json.JSONDecodeError, UnicodeDecodeError):
        raise ValueError("FIREBASE_ANDROID_JSON is not valid JSON") from None
    if not isinstance(value, dict):
        raise ValueError("Firebase config must be a JSON object")
    if value.get("project_info", {}).get("project_id") != EXPECTED_PROJECT:
        raise ValueError("Firebase config belongs to an unexpected project")
    apps = value.get("client")
    if not isinstance(apps, list) or not any(
        isinstance(c, dict)
        and c.get("client_info", {}).get("android_client_info", {}).get("package_name") == EXPECTED_PACKAGE
        and c.get("client_info", {}).get("mobilesdk_app_id")
        and isinstance(c.get("api_key"), list)
        and any(isinstance(k, dict) and k.get("current_key") for k in c["api_key"])
        for c in apps
    ):
        raise ValueError("Firebase config lacks the registered Android app and API key")
    return raw


def main():
    try:
        raw = load_config()
        DESTINATION.parent.mkdir(parents=True, exist_ok=True)
        fd = os.open(DESTINATION, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
        with os.fdopen(fd, "w", encoding="utf-8") as output:
            output.write(raw)
        print("Firebase Android config validated and written privately.")
        return 0
    except (ValueError, OSError) as error:
        print(f"Firebase config setup failed: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
