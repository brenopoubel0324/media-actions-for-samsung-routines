import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

CI = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(CI))
import plan
spec = importlib.util.spec_from_file_location("ci_runner", CI / "run.py")
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)


class SelectionTests(unittest.TestCase):
    def select(self, paths, event="pull_request", ref="refs/pull/12/merge"):
        return plan.classify(paths, event, ref)

    def test_docs_allowlist_and_empty_interval(self):
        self.assertEqual("docs", self.select(["README.md", "docs/testes/guia.md", "docs/imagem.png", "docs/reviews/check.md", "docs/planos/plan.md"])[0])
        self.assertEqual("full", self.select([])[0])
        for path in ["AGENTS.md", "docs/STABLE_RELEASE.md", "docs/CI.md", "docs/config.json", "downloads/v9/README.md", "evidence/test.json", "notes.md"]:
            with self.subTest(path=path):
                self.assertEqual("full", self.select([path])[0])

    def test_assets_are_product_not_documentation(self):
        for path in ["app/src/main/assets/readme.md", "app/src/main/assets/data.json"]:
            self.assertEqual(("daily", ["app"]), self.select([path])[:2])

    def test_daily_modules_and_shared_dependants(self):
        for module in ["app", "wear"]:
            self.assertEqual(("daily", [module]), self.select([f"{module}/src/main/res/layout/example.xml"])[:2])
        self.assertEqual(list(plan.MODULES), self.select(["shared/src/main/java/Protocol.kt"])[1])
        self.assertEqual(["app", "wear"], self.select(["app/src/main/Example.kt", "wear/src/main/Example.kt"])[1])

    def test_risks_promote_full(self):
        for path in [".github/workflows/x.yml", "scripts/ci/plan.py", "gradle/wrapper/gradle-wrapper.jar",
                     "app/build.gradle.kts", "gradle.properties", "app/proguard-rules.pro",
                     "app/libs/dependency.aar", "app/src/preview/res/values/strings.xml", "app/src/release/java/Feature.kt",
                     "app/src/main/AndroidManifest.xml", "app/src/main/res/xml/provider.xml",
                     "app/src/main/native/code.cpp", "app/src/androidTest/Test.kt",
                     "app/src/main/java/AndroidSessionGateway.kt", "app/src/main/java/WidgetAppearance.kt",
                     "app/src/main/java/SheveryBridge.kt", "downloads/v9/phone.apk"]:
            with self.subTest(path=path):
                self.assertEqual("full", self.select([path])[0])

    def test_main_and_manual_contract(self):
        self.assertEqual("full", self.select(["app/src/main/Text.kt"], "push", "refs/heads/main")[0])
        self.assertEqual("docs", self.select(["README.md"], "push", "refs/heads/main")[0])
        self.assertEqual("full", plan.classify([], "workflow_dispatch", "refs/heads/main")[0])
        self.assertEqual("daily", plan.classify([], "workflow_dispatch", "refs/heads/main", "daily")[0])
        with self.assertRaises(ValueError):
            plan.classify([], "workflow_dispatch", "refs/heads/main", "skip")

    def test_distribution_is_never_treated_as_validated_docs(self):
        paths = ["downloads/v9/phone.apk", "downloads/v9/VALIDATION.json", "downloads/v9/README.md"]
        self.assertEqual("full", self.select(paths)[0])

    def test_no_truncation_of_changed_paths(self):
        paths = [f"docs/file-{i}.md" for i in range(1200)] + ["shared/src/main/Contract.kt"]
        self.assertEqual(list(plan.MODULES), self.select(paths)[1])

    def test_daily_omits_preview_release_and_unaffected_wear(self):
        tasks = plan.tasks_for("daily", ["app"])
        self.assertEqual([":app:assembleDebug", ":app:testDebugUnitTest", ":app:lintDebug"], tasks)
        self.assertFalse(any("Preview" in t or "Release" in t or "wear" in t for t in tasks))
        full = plan.tasks_for("full", list(plan.MODULES))
        self.assertIn(":app:testReleaseUnitTest", full)
        self.assertIn(":app:lintRelease", full)
        self.assertEqual(["--tests", "*VariantIsolationTest*"], full[-2:])


class RealGitTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.repo = Path(self.temp.name)
        self.git("init", "-q")
        self.git("config", "user.name", "CI test")
        self.git("config", "user.email", "ci@example.invalid")
        self.write("README.md", "base\n")
        self.base = self.commit()

    def git(self, *args):
        return subprocess.check_output(["git", "-C", str(self.repo), *args], stderr=subprocess.PIPE).decode().strip()

    def write(self, name, text):
        path = self.repo / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)

    def commit(self):
        self.git("add", "-A")
        self.git("commit", "-qm", "fixture")
        return self.git("rev-parse", "HEAD")

    def push_plan(self, base, head):
        return plan.make_plan(self.repo, "push", {"before": base, "after": head}, "refs/heads/work", head)

    def test_entire_push_interval_includes_earlier_code_commit(self):
        self.write("app/src/main/Widget.kt", "code\n")
        self.commit()
        self.write("docs/manual.md", "docs\n")
        head = self.commit()
        result = self.push_plan(self.base, head)
        self.assertEqual("daily", result["mode"])
        self.assertIn("app/src/main/Widget.kt", result["paths"])

    def test_rename_to_docs_preserves_deleted_product_path(self):
        self.write("app/src/main/assets/contract.md", "same\n")
        base = self.commit()
        self.git("mv", "app/src/main/assets/contract.md", "moved.md")
        self.write("docs/contract.md", "same\n")
        (self.repo / "moved.md").unlink()
        head = self.commit()
        result = self.push_plan(base, head)
        self.assertIn("app/src/main/assets/contract.md", result["paths"])
        self.assertEqual("daily", result["mode"])

    def test_deletion_is_not_lost(self):
        self.write("shared/src/main/Protocol.kt", "shared\n")
        base = self.commit()
        (self.repo / "shared/src/main/Protocol.kt").unlink()
        result = self.push_plan(base, self.commit())
        self.assertEqual(list(plan.MODULES), result["modules"])

    def test_pr_uses_merge_base_but_validates_merge_commit(self):
        self.git("branch", "topic")
        self.write("docs/base.md", "base evolved\n")
        base = self.commit()
        self.git("checkout", "-q", "topic")
        self.write("app/src/main/Text.kt", "topic\n")
        head = self.commit()
        self.git("merge", "--no-ff", "-qm", "merge fixture", base)
        merge = self.git("rev-parse", "HEAD")
        result = plan.make_plan(self.repo, "pull_request", {"pull_request": {"base": {"sha": base}, "head": {"sha": head}}}, "refs/pull/1/merge", merge)
        self.assertEqual(self.base, result["base"])
        self.assertEqual(merge, result["source_sha"])
        self.assertNotIn("docs/base.md", result["paths"])
        self.assertEqual(["app"], result["modules"])

    def test_missing_base_and_zero_sha_never_pass_as_documentation(self):
        for base in ["a" * 40, "0" * 40]:
            result = self.push_plan(base, self.base)
            self.assertEqual("full", result["mode"])
            self.assertTrue(result["diff_error"])
            with patch.object(subprocess, "check_output", return_value=self.base + "\n"):
                with self.assertRaises(ValueError):
                    runner.check_source(result)

    def test_malformed_ref_never_reaches_fetch(self):
        with patch.object(plan, "git") as call:
            with self.assertRaises(ValueError):
                plan.ensure_commits(self.repo, ["main; exit 0"], True)
            call.assert_not_called()

    def test_manual_and_cli_outputs(self):
        event = self.repo / "event.json"
        event.write_text('{"inputs":{"validation":"full"}}')
        output = self.repo / "plan.json"
        github_output = self.repo / "github-output"
        env = dict(os.environ, GITHUB_OUTPUT=str(github_output))
        subprocess.run([sys.executable, str(CI / "plan.py"), "--repo", str(self.repo),
                        "--event-name", "workflow_dispatch", "--event-path", str(event),
                        "--ref", "refs/heads/main", "--sha", self.base, "--output", str(output)],
                       check=True, capture_output=True, env=env)
        result = json.loads(output.read_text())
        self.assertEqual("full", result["mode"])
        self.assertEqual("mode=full\n", github_output.read_text())

    def test_daily_with_candidate_upload_fails_before_build(self):
        with self.assertRaisesRegex(ValueError, "validation=full"):
            plan.make_plan(self.repo, "workflow_dispatch", {"inputs": {"validation": "daily", "upload_candidates": "true"}}, "refs/heads/work", self.base)

    def test_shallow_history_recovers_with_one_bounded_fetch(self):
        self.write("app/src/main/Text.kt", "first\n")
        first = self.commit()
        for index in range(4):
            self.write("docs/manual.md", str(index))
            last = self.commit()
        clone = self.repo / "shallow"
        subprocess.run(["git", "clone", "-q", "--depth=1", self.repo.as_uri(), str(clone)], check=True)
        result = plan.make_plan(clone, "push", {"before": first, "after": last}, "refs/heads/work", last, fetch=True)
        self.assertIsNone(result["diff_error"])
        self.assertEqual("docs", result["mode"])


class RunnerTests(unittest.TestCase):
    def test_test_failure_is_propagated(self):
        sample = {"mode": "daily", "modules": ["app"], "source_sha": "a" * 40,
                  "diff_error": None, "tasks": plan.tasks_for("daily", ["app"])}
        with patch.object(runner, "check_source"), patch.object(subprocess, "run", side_effect=subprocess.CalledProcessError(1, "gradle")):
            with self.assertRaises(subprocess.CalledProcessError):
                runner.validate(sample)

    def test_docs_do_not_invoke_gradle(self):
        with patch.object(runner, "check_source"), patch.object(subprocess, "run") as call:
            runner.validate({"mode": "docs"})
            call.assert_not_called()

    def test_tampered_plan_tasks_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "plan.json"
            path.write_text(json.dumps({"mode": "daily", "modules": ["app"], "source_sha": "a" * 40, "tasks": ["help"]}))
            with self.assertRaises(ValueError):
                runner.load(path)

    def test_artifact_package_permission_and_debug_contracts(self):
        text = "package: name='dev.local.mediaactions' versionCode='34' versionName='0.34'\nuses-permission: name='android.permission.MODIFY_AUDIO_SETTINGS'\n"
        self.assertEqual("0.34", runner.badging_contract(text, "dev.local.mediaactions", False, ["android.permission.MODIFY_AUDIO_SETTINGS"])["version_name"])
        for broken in [text.replace("mediaactions'", "mediaactions.preview'"), text + "application-debuggable\n", text.replace("0.34'", "0.34-preview'"), text + "uses-permission: name='android.permission.WAKE_LOCK'\n", text.replace("versionCode='34'", "versionCode='0'"), text.replace("0.34'", "0.34-experimental'")]:
            with self.assertRaises(ValueError):
                runner.badging_contract(broken, "dev.local.mediaactions", False, ["android.permission.MODIFY_AUDIO_SETTINGS"])

    def test_release_test_only_and_debuggable_flags_are_checked_in_binary_manifest(self):
        runner.manifest_contract("A: android:testOnly(0x1)=(type 0x12)0x0", False)
        for flag in ("testOnly", "debuggable"):
            with self.assertRaises(ValueError):
                runner.manifest_contract(f"A: android:{flag}(0x1)=(type 0x12)0xffffffff", False)
        runner.manifest_contract("A: android:debuggable(0x1)=(type 0x12)0xffffffff", True)

    def test_missing_or_multiple_candidates_fail(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(ValueError):
                runner.one_apk(directory)
            Path(directory, "one.apk").touch()
            self.assertEqual("one.apk", runner.one_apk(directory).name)
            Path(directory, "two.apk").touch()
            with self.assertRaises(ValueError):
                runner.one_apk(directory)


if __name__ == "__main__":
    unittest.main()
