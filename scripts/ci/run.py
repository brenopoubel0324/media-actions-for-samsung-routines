#!/usr/bin/env python3
"""Execute the selected contract. No signing, publication or trusted private key in CI."""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess

from plan import MODULES, tasks_for, valid_sha


def load(path):
    plan = json.loads(Path(path).read_text())
    expected = tasks_for(plan["mode"], plan["modules"])
    if plan["tasks"] != expected or not valid_sha(plan["source_sha"]):
        raise ValueError("Invalid plan or altered task list")
    return plan


def check_source(plan):
    actual = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
    if actual != plan["source_sha"]:
        raise ValueError("Source changed after selection")
    if plan["diff_error"]:
        raise ValueError(plan["diff_error"] + "; nenhuma validação Android declarada")


def patch_format(plan):
    check_source(plan)
    if plan["base"] is None:
        subprocess.run(["git", "show", "--format=", "--check", "HEAD"], check=True)
    else:
        subprocess.run(["git", "diff", "--check", plan["base"], plan["head"], "--"], check=True)


def validate(plan):
    check_source(plan)
    if plan["mode"] == "docs":
        print("Documentação: build, testes e lint Android dispensados; não é passe do app.")
        return
    subprocess.run(["bash", "./gradlew", *plan["tasks"], "--console=plain", "--no-daemon"], check=True)


def one_apk(directory):
    paths = list(Path(directory).glob("*.apk"))
    if len(paths) != 1:
        raise ValueError(f"Expected exactly one APK in {directory}; got {len(paths)}")
    return paths[0]


def badging_contract(text, package, debuggable, permissions):
    found = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", text, re.M)
    if not found or found[1] != package or int(found[2]) <= 0:
        raise ValueError("Unexpected candidate package")
    if ("application-debuggable" in text) != debuggable:
        raise ValueError("Unexpected candidate debuggability")
    if not debuggable and (found[3].endswith(("-dev", "-preview", "-experimental")) or "application-testOnly" in text):
        raise ValueError("Release candidate has a development flag")
    requested = set(re.findall(r"^uses-permission(?:-sdk-\d+)?: name='([^']+)'", text, re.M))
    if requested != set(permissions):
        raise ValueError(f"Unexpected candidate permissions: {sorted(requested)}")
    return {"package": found[1], "version_code": int(found[2]), "version_name": found[3], "debuggable": debuggable}


def manifest_contract(xmltree, debuggable):
    if not debuggable:
        for flag in ("debuggable", "testOnly"):
            match = re.search(r"android:" + flag + r"\([^)]*\)=\(type 0x12\)(0x[0-9a-fA-F]+)", xmltree)
            if match and int(match[1], 16):
                raise ValueError(f"Release manifest enables {flag}")


def inspect(sdk):
    aapt = Path(sdk) / "build-tools/36.0.0/aapt"
    candidates = [
        ("app/build/outputs/apk/release", "dev.local.mediaactions", False, ["android.permission.MODIFY_AUDIO_SETTINGS"]),
        ("app/build/outputs/apk/preview", "dev.local.mediaactions.preview", True, ["android.permission.MODIFY_AUDIO_SETTINGS"]),
        ("wear/build/outputs/apk/debug", "dev.local.mediaactions", True, []),
    ]
    for directory, package, debuggable, permissions in candidates:
        apk = one_apk(directory)
        text = subprocess.check_output([str(aapt), "dump", "badging", str(apk)], text=True)
        result = badging_contract(text, package, debuggable, permissions)
        xmltree = subprocess.check_output([str(aapt), "dump", "xmltree", str(apk), "AndroidManifest.xml"], text=True)
        manifest_contract(xmltree, debuggable)
        print(json.dumps({"candidate": str(apk), **result}))
    print("Candidatos de CI inspecionados. Isto não confirma a assinatura oficial nem publica APKs.")


def summary(plan):
    target = os.environ.get("GITHUB_STEP_SUMMARY")
    if not target:
        return
    with open(target, "a") as stream:
        stream.write("## Validação selecionada\n\n")
        stream.write(f"- Escopo: **{plan['mode']}**; motivo: {plan['reason']}.\n")
        stream.write(f"- SHA do checkout: `{plan['source_sha']}`.\n")
        stream.write(f"- Resultado do job: `{os.environ.get('CI_JOB_STATUS', 'unknown')}`.\n")
        if plan["mode"] == "docs":
            stream.write("- Cadeia Android dispensada; não equivale a build/testes do aplicativo.\n")
        else:
            stream.write(f"- Tarefas previstas: `{ ' '.join(plan['tasks']) }`.\n")
        stream.write("- APKs oficiais exigem assinatura e vínculo com fonte/evidências conforme docs/STABLE_RELEASE.md.\n")
        stream.write("- Duração/custo evitados não foram medidos por este seletor.\n")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=["format", "validate", "inspect", "summary"])
    parser.add_argument("--plan", required=True)
    parser.add_argument("--sdk", default=os.environ.get("ANDROID_HOME"))
    args = parser.parse_args()
    plan = load(args.plan)
    if args.command == "format":
        patch_format(plan)
    elif args.command == "validate":
        validate(plan)
    elif args.command == "inspect":
        check_source(plan)
        if plan["mode"] != "full":
            raise ValueError("Candidate inspection requires full validation")
        inspect(args.sdk)
    else:
        summary(plan)


if __name__ == "__main__":
    main()
