#!/usr/bin/env python3
"""Choose CI work from a complete Git interval. Unknown paths never mean docs-only."""
import argparse
import json
import os
from pathlib import Path, PurePosixPath
import re
import subprocess

MODULES = ("app", "shared", "wear")
DOC_FILES = {"README.md", "TEST_REPORT.md", "THIRD_PARTY_NOTICES.md", "CONTRIBUTING.md"}
SENSITIVE_NAMES = (
    "proguard", "r8", "manifest", "permission", "privilege", "shizuku", "shevery", "gateway", "session",
    "listener", "service", "backup", "store", "preferences", "phonemediaactions",
    "widgetappearance", "widgetconfigureactivity", "widgetplaybackobserver", "mediaactionsapplication",
)
CI_CONTRACTS = {"docs/STABLE_RELEASE.md", "docs/CI.md"}
SHA = re.compile(r"[0-9a-f]{40}(?:[0-9a-f]{24})?\Z")


def tasks_for(mode, modules):
    if mode == "docs":
        return []
    if mode == "full":
        return [":app:assembleDebug", ":app:assemblePreview", ":wear:assembleDebug",
                ":app:assembleRelease", ":app:testReleaseUnitTest", "testDebugUnitTest",
                ":app:lintRelease", "lintDebug", ":app:testPreviewUnitTest",
                "--tests", "*VariantIsolationTest*"]
    if mode != "daily" or not modules or not set(modules) <= set(MODULES):
        raise ValueError("Invalid validation scope")
    return [f":{module}:{task}" for module in MODULES if module in modules
            for task in (("testDebugUnitTest", "lintDebug") if module == "shared"
                         else ("assembleDebug", "testDebugUnitTest", "lintDebug"))]


def documentation(path):
    operational = path in CI_CONTRACTS
    return not operational and (path in DOC_FILES or (path.startswith("docs/") and PurePosixPath(path).suffix.lower() in {".md", ".png", ".jpg", ".jpeg", ".svg"}))


def high_risk(path):
    name = PurePosixPath(path).name.lower()
    return (path in CI_CONTRACTS or path.startswith((".github/", "scripts/ci/", "gradle/")) or
            name in {"gradlew", "gradlew.bat", "gradle.properties", "libs.versions.toml", "cmakelists.txt", "android.mk"} or
            name.endswith((".gradle", ".gradle.kts", ".aidl", ".proto", ".c", ".cc", ".cpp", ".h", ".hpp", ".so", ".a", ".jar", ".aar", ".pom", ".lockfile", ".rs", ".s")) or
            "/src/androidTest/" in path or "/src/preview/" in path or "/src/release/" in path or "/res/xml/" in path or
            any(token in name for token in SENSITIVE_NAMES))


def classify(paths, event_name, ref, requested="full"):
    paths = sorted(set(paths))
    if event_name == "workflow_dispatch":
        if requested not in {"full", "daily"}:
            raise ValueError("Invalid manual validation input")
        return requested, list(MODULES), "Execução manual explícita"
    if not paths:
        return "full", list(MODULES), "Intervalo vazio; não presumir documentação"
    if all(documentation(path) for path in paths):
        return "docs", [], "Somente caminhos documentais permitidos"
    if event_name == "push" and ref == "refs/heads/main":
        return "full", list(MODULES), "Integração na main"
    if any(high_risk(path) for path in paths):
        return "full", list(MODULES), "Build, CI, contrato ou comportamento de maior risco"
    touched = {path.split("/", 1)[0] for path in paths if not documentation(path)}
    if not touched <= set(MODULES):
        return "full", list(MODULES), "Caminho não reconhecido ou distribuição; sem atalho por pasta"
    if "shared" in touched:
        touched.update(MODULES)
    return "daily", [m for m in MODULES if m in touched], "Debug e lint dos módulos afetados"


def git(repo, *args, check=True):
    return subprocess.run(["git", "-C", str(repo), *args], check=check,
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=90)


def valid_sha(value):
    return isinstance(value, str) and SHA.fullmatch(value) is not None and int(value, 16) != 0


def ensure_commits(repo, values, fetch):
    if not all(valid_sha(value) for value in values):
        raise ValueError("Referência ausente ou inválida")
    if all(git(repo, "cat-file", "-e", f"{value}^{{commit}}", check=False).returncode == 0 for value in values):
        return False
    if fetch:
        # Only validated SHAs, no branch names or shell interpolation; one bounded fetch.
        git(repo, "fetch", "--no-tags", "--depth=128", "origin", *dict.fromkeys(values))
    for value in values:
        git(repo, "cat-file", "-e", f"{value}^{{commit}}")
    return fetch


def changed_paths(repo, base, head):
    # Disabling rename detection exposes both the deletion and addition, without rename limits.
    data = git(repo, "diff", "--name-only", "--no-renames", "-z", base, head, "--").stdout
    return [os.fsdecode(path) for path in data.split(b"\0") if path]


def make_plan(repo, event_name, event, ref, checkout_sha, fetch=False):
    if event_name not in {"push", "pull_request", "workflow_dispatch"}:
        raise ValueError("Unsupported event")
    actual = git(repo, "rev-parse", "HEAD").stdout.decode().strip()
    if not valid_sha(checkout_sha) or actual != checkout_sha:
        raise ValueError("Checkout does not match the event commit")
    result = {"source_sha": actual, "event": event_name, "ref": ref,
              "base": None, "head": None, "paths": [], "diff_error": None}
    requested = event.get("inputs", {}).get("validation", "full")
    if event_name == "workflow_dispatch":
        if requested != "full" and event.get("inputs", {}).get("upload_candidates") in (True, "true"):
            raise ValueError("Candidatos exigem validation=full")
        mode, modules, reason = classify([], event_name, ref, requested)
    else:
        try:
            if event_name == "pull_request":
                base = event["pull_request"]["base"]["sha"]
                head = event["pull_request"]["head"]["sha"]
                fetched = ensure_commits(repo, [base, head], fetch)
                common = git(repo, "merge-base", base, head, check=False)
                if common.returncode and fetch and not fetched:
                    git(repo, "fetch", "--no-tags", "--depth=128", "origin", base, head)
                    common = git(repo, "merge-base", base, head)
                if common.returncode:
                    raise ValueError("Base comum do PR indisponível")
                base = common.stdout.decode().strip()
            else:
                base, head = event["before"], event["after"]
                ensure_commits(repo, [base, head], fetch)
                if head != actual:
                    raise ValueError("Push head differs from checkout")
            result.update(base=base, head=head, paths=changed_paths(repo, base, head))
            mode, modules, reason = classify(result["paths"], event_name, ref)
        except (KeyError, ValueError, subprocess.CalledProcessError, subprocess.TimeoutExpired) as error:
            # The runner refuses this plan explicitly; never call this docs-only or passed.
            result["diff_error"] = f"Comparação indisponível ({type(error).__name__})"
            mode, modules, reason = "full", list(MODULES), "Diff não confiável: falhar explicitamente antes do build"
    result.update(mode=mode, modules=modules, reason=reason, tasks=tasks_for(mode, modules))
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", type=Path, default=Path.cwd())
    parser.add_argument("--event-path", type=Path, default=os.environ.get("GITHUB_EVENT_PATH"))
    parser.add_argument("--event-name", default=os.environ.get("GITHUB_EVENT_NAME"))
    parser.add_argument("--ref", default=os.environ.get("GITHUB_REF", ""))
    parser.add_argument("--sha", default=os.environ.get("GITHUB_SHA"))
    parser.add_argument("--fetch", action="store_true")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    with args.event_path.open() as stream:
        event = json.load(stream)
    plan = make_plan(args.repo, args.event_name, event, args.ref, args.sha, args.fetch)
    args.output.write_text(json.dumps(plan, ensure_ascii=True, indent=2) + "\n")
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a") as stream:
            stream.write(f"mode={plan['mode']}\n")
    print(json.dumps({k: plan[k] for k in ("mode", "modules", "reason", "source_sha", "diff_error")}, ensure_ascii=True))


if __name__ == "__main__":
    main()
