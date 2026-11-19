import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest

SCRIPT = Path(__file__).with_name("prepare-firebase-config.py")
spec = importlib.util.spec_from_file_location("firebase_config", SCRIPT)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def valid_config():
    return {
        "project_info": {"project_id": "stream-flow-pro"},
        "client": [{
            "client_info": {
                "mobilesdk_app_id": "1:837367317965:android:privatefixture",
                "android_client_info": {"package_name": "com.aistudio.controlstreaming.sales"},
            },
            "api_key": [{"current_key": "fixture-not-real"}],
        }],
    }


class ConfigValidationTest(unittest.TestCase):
    def test_accepts_matching_config(self):
        with tempfile.TemporaryDirectory() as tmp:
            old = module.DESTINATION
            module.DESTINATION = Path(tmp) / "app" / "google-services.json"
            try:
                raw = json.dumps(valid_config())
                os.environ["FIREBASE_ANDROID_JSON"] = raw
                self.assertEqual(module.main(), 0)
                self.assertEqual(json.loads(module.DESTINATION.read_text()), valid_config())
                self.assertEqual(module.DESTINATION.stat().st_mode & 0o777, 0o600)
            finally:
                module.DESTINATION = old
                os.environ.pop("FIREBASE_ANDROID_JSON", None)

    def test_rejects_wrong_project(self):
        config = valid_config()
        config["project_info"]["project_id"] = "other-project"
        os.environ["FIREBASE_ANDROID_JSON"] = json.dumps(config)
        with self.assertRaisesRegex(ValueError, "unexpected project"):
            module.load_config()
        os.environ.pop("FIREBASE_ANDROID_JSON", None)

    def test_rejects_wrong_package(self):
        config = valid_config()
        config["client"][0]["client_info"]["android_client_info"]["package_name"] = "wrong.package"
        os.environ["FIREBASE_ANDROID_JSON"] = json.dumps(config)
        with self.assertRaisesRegex(ValueError, "registered Android app"):
            module.load_config()
        os.environ.pop("FIREBASE_ANDROID_JSON", None)

    def test_rejects_malformed_json(self):
        os.environ["FIREBASE_ANDROID_JSON"] = "not-json"
        with self.assertRaisesRegex(ValueError, "not valid JSON"):
            module.load_config()
        os.environ.pop("FIREBASE_ANDROID_JSON", None)


if __name__ == "__main__":
    unittest.main()
