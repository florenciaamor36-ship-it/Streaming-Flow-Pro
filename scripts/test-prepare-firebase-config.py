import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

SCRIPT = Path(__file__).with_name("prepare-firebase-config.py")
spec = importlib.util.spec_from_file_location("firebase_config", SCRIPT)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

ENV = {
    "FIREBASE_PROJECT_ID": module.EXPECTED_PROJECT,
    "FIREBASE_PROJECT_NUMBER": module.EXPECTED_PROJECT_NUMBER,
    "FIREBASE_ANDROID_APP_ID": module.EXPECTED_ANDROID_APP_ID,
    "FIREBASE_ANDROID_PACKAGE_NAME": module.EXPECTED_PACKAGE,
    "FIREBASE_ANDROID_API_KEY": "AIza" + "x" * 35,
}


class ConfigSynthesisTest(unittest.TestCase):
    def test_synthesizes_registered_app_config_privately(self):
        with tempfile.TemporaryDirectory() as tmp, patch.dict(os.environ, ENV, clear=False):
            destination = Path(tmp) / "app" / "google-services.json"
            old = module.DESTINATION
            module.DESTINATION = destination
            try:
                self.assertEqual(module.main(), 0)
                config = json.loads(destination.read_text())
                self.assertEqual(config["project_info"]["project_id"], module.EXPECTED_PROJECT)
                self.assertEqual(config["project_info"]["project_number"], module.EXPECTED_PROJECT_NUMBER)
                client = config["client"][0]
                self.assertEqual(client["client_info"]["mobilesdk_app_id"], module.EXPECTED_ANDROID_APP_ID)
                self.assertEqual(client["client_info"]["android_client_info"]["package_name"], module.EXPECTED_PACKAGE)
                self.assertEqual(client["api_key"][0]["current_key"], ENV["FIREBASE_ANDROID_API_KEY"])
                self.assertEqual(destination.stat().st_mode & 0o777, 0o600)
            finally:
                module.DESTINATION = old

    def test_rejects_wrong_project(self):
        wrong = dict(ENV, FIREBASE_PROJECT_ID="other-project")
        with patch.dict(os.environ, wrong, clear=False):
            with self.assertRaisesRegex(ValueError, "project_id"):
                module.load_config()

    def test_rejects_wrong_app_id(self):
        wrong = dict(ENV, FIREBASE_ANDROID_APP_ID="1:0:android:wrong")
        with patch.dict(os.environ, wrong, clear=False):
            with self.assertRaisesRegex(ValueError, "app_id"):
                module.load_config()

    def test_rejects_wrong_package(self):
        wrong = dict(ENV, FIREBASE_ANDROID_PACKAGE_NAME="wrong.package")
        with patch.dict(os.environ, wrong, clear=False):
            with self.assertRaisesRegex(ValueError, "package_name"):
                module.load_config()

    def test_rejects_missing_or_malformed_api_key(self):
        for key in ("", "not-a-firebase-key"):
            wrong = dict(ENV, FIREBASE_ANDROID_API_KEY=key)
            with patch.dict(os.environ, wrong, clear=False):
                with self.assertRaisesRegex(ValueError, "API_KEY"):
                    module.load_config()


if __name__ == "__main__":
    unittest.main()
