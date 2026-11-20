#!/usr/bin/env python3
"""Synthesize the registered Android Firebase config from public Actions variables."""
import json
import os
from pathlib import Path
import sys

EXPECTED_PROJECT = "stream-flow-pro"
EXPECTED_PROJECT_NUMBER = "837367317965"
EXPECTED_ANDROID_APP_ID = "1:837367317965:android:f68b87a0496d1c14d87084"
EXPECTED_PACKAGE = "com.aistudio.controlstreaming.sales"
DESTINATION = Path("app/google-services.json")


def load_config():
    values = {
        "project_id": os.environ.get("FIREBASE_PROJECT_ID", ""),
        "project_number": os.environ.get("FIREBASE_PROJECT_NUMBER", ""),
        "app_id": os.environ.get("FIREBASE_ANDROID_APP_ID", ""),
        "package_name": os.environ.get("FIREBASE_ANDROID_PACKAGE_NAME", ""),
        "api_key": os.environ.get("FIREBASE_ANDROID_API_KEY", ""),
    }
    expected = {
        "project_id": EXPECTED_PROJECT,
        "project_number": EXPECTED_PROJECT_NUMBER,
        "app_id": EXPECTED_ANDROID_APP_ID,
        "package_name": EXPECTED_PACKAGE,
    }
    for field, expected_value in expected.items():
        if values[field] != expected_value:
            raise ValueError(f"{field} is missing or does not match the registered Android app")
    if not values["api_key"].startswith("AIza") or len(values["api_key"]) < 30:
        raise ValueError("FIREBASE_ANDROID_API_KEY is missing or malformed")

    config = {
        "project_info": {
            "project_number": values["project_number"],
            "project_id": values["project_id"],
        },
        "client": [{
            "client_info": {
                "mobilesdk_app_id": values["app_id"],
                "android_client_info": {"package_name": values["package_name"]},
            },
            "api_key": [{"current_key": values["api_key"]}],
            "services": {"appinvite_service": {"other_platform_oauth_client": []}},
        }],
        "configuration_version": "1",
    }
    return json.dumps(config, separators=(",", ":"))


def main():
    try:
        raw = load_config()
        DESTINATION.parent.mkdir(parents=True, exist_ok=True)
        fd = os.open(DESTINATION, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
        with os.fdopen(fd, "w", encoding="utf-8") as output:
            output.write(raw)
        os.chmod(DESTINATION, 0o600)
        print("Firebase Android config synthesized and validated for the registered app.")
        return 0
    except (ValueError, OSError) as error:
        print(f"Firebase config setup failed: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
