# Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
"""Tests for tools/verify_report.py. Run: pytest tools/tests"""
import base64
import copy
import io
import json
import shutil
import subprocess
import sys
from pathlib import Path

import pytest
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))
import verify_report as vr  # noqa: E402

FIX = ROOT / "tools" / "tests" / "fixtures"
KOTLIN = FIX / "kotlin_v1"
CHAIN_FIXTURE = ROOT / "domain" / "src" / "test" / "resources" / "fixtures" / "custody_chain_v1.json"


def run(manifest, pdf=None, csv=None):
    argv = [str(manifest)]
    if pdf:
        argv += ["--pdf", str(pdf)]
    if csv:
        argv += ["--readings-csv", str(csv)]
    out = io.StringIO()
    return vr.run(argv, out), out.getvalue()


@pytest.fixture
def work(tmp_path):
    """A writable copy of the Kotlin-made manifest, its stand-in PDF and readings CSV."""
    for name in ("report.manifest.json", "report_stub.pdf", "readings.csv"):
        shutil.copy(KOTLIN / name, tmp_path / name)
    return tmp_path


def load(work):
    return json.loads((work / "report.manifest.json").read_text(encoding="utf-8"))


def save(work, m):
    (work / "report.manifest.json").write_bytes(vr.canonical_json(m))


def resign(m, key):
    """Recomputes every hash and signs the manifest with [key], like an attacker holding their own key."""
    m["content_hash"] = vr.sha256_hex(vr.canonical_json(m["content"]))
    body = {k: v for k, v in m.items() if k not in ("manifest_hash", "manifest_signature")}
    m["manifest_hash"] = vr.sha256_hex(vr.canonical_json(body))
    m["manifest_signature"] = key.sign(m["manifest_hash"].encode(), ec.ECDSA(hashes.SHA256())).hex()
    return m


def check(work, **kw):
    return run(work / "report.manifest.json", work / "report_stub.pdf" if kw.get("pdf", True) else None,
               work / "readings.csv" if kw.get("csv", True) else None)


# --- valid inputs pass ---------------------------------------------------------------------------------------------

def test_phase2_chain_fixture_passes():
    code, out = run(CHAIN_FIXTURE)
    assert code == 0, out
    assert "RESULT: PASS" in out


def test_kotlin_manifest_passes_with_pdf_and_csv(work):
    code, out = check(work)
    assert code == 0, out
    assert "[FAIL]" not in out and "[SKIP]" not in out


def test_kotlin_manifest_passes_alone_and_says_what_it_skipped(work):
    code, out = check(work, pdf=False, csv=False)
    assert code == 0, out
    assert out.count("[SKIP]") == 2


def test_python_canonical_json_and_hash_match_the_kotlin_golden_file():
    golden = (FIX / "golden_content_stub.json").read_text(encoding="utf-8")
    expected = (FIX / "golden_content_stub.sha256").read_text().strip()
    assert vr.canonical_json(json.loads(golden)).decode("utf-8") == golden
    assert vr.sha256_hex(golden.encode("utf-8")) == expected


def test_command_line_exit_codes(work):
    script = str(ROOT / "tools" / "verify_report.py")
    ok = subprocess.run([sys.executable, script, str(work / "report.manifest.json")], capture_output=True, text=True)
    assert ok.returncode == 0, ok.stdout
    (work / "report.manifest.json").write_text("{not json")
    bad = subprocess.run([sys.executable, script, str(work / "report.manifest.json")], capture_output=True, text=True)
    assert bad.returncode == 1
    missing = subprocess.run([sys.executable, script, str(work / "nope.json")], capture_output=True, text=True)
    assert missing.returncode == 2


# --- every kind of forgery fails -----------------------------------------------------------------------------------

def test_changing_any_single_character_of_the_manifest_fails(work):
    original = (work / "report.manifest.json").read_text(encoding="utf-8")
    survivors = []
    for i, ch in enumerate(original):
        mutated = original[:i] + ("0" if ch != "0" else "1") + original[i + 1:]
        (work / "report.manifest.json").write_text(mutated, encoding="utf-8")
        code, _ = check(work)
        if code == 0:
            survivors.append(i)
    assert survivors == [], f"single-character edits at {survivors[:5]} still verified"


def test_changed_event_field_without_recomputing_fails(work):
    m = load(work)
    m["content"]["events"][1]["user"] = "mallory"
    save(work, m)
    code, out = check(work)
    assert code == 1 and "content_hash" in out


def test_changed_event_field_with_content_hash_and_manifest_hash_recomputed_still_fails(work):
    m = load(work)
    m["content"]["events"][1]["user"] = "mallory"
    m["content_hash"] = vr.sha256_hex(vr.canonical_json(m["content"]))
    body = {k: v for k, v in m.items() if k not in ("manifest_hash", "manifest_signature")}
    m["manifest_hash"] = vr.sha256_hex(vr.canonical_json(body))
    save(work, m)
    code, out = check(work)
    assert code == 1
    assert "signature is invalid" in out or "stored canonical text differs" in out
    assert "manifest signature is valid" not in out.replace("[FAIL] manifest signature is valid", "")


def test_attacker_who_re_signs_the_manifest_but_not_the_events_fails(work):
    attacker = ec.generate_private_key(ec.SECP256R1())
    m = load(work)
    m["content"]["stats"]["excursion_count"] = 0
    der = attacker.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)
    m["content"]["public_key_x509_base64"] = base64.b64encode(der).decode()
    m["content"]["key_fingerprint"] = vr.sha256_hex(der)
    save(work, resign(m, attacker))
    code, out = check(work)
    assert code == 1
    assert "signature is invalid" in out  # the events were signed by the device key, not the attacker's


def test_swapped_event_order_fails(work):
    m = load(work)
    ev = m["content"]["events"]
    ev[1], ev[2] = ev[2], ev[1]
    save(work, resign(m, ec.generate_private_key(ec.SECP256R1())))
    code, out = check(work)
    assert code == 1


def test_deleted_middle_event_fails(work):
    m = load(work)
    del m["content"]["events"][1]
    save(work, resign(m, ec.generate_private_key(ec.SECP256R1())))
    code, out = check(work)
    assert code == 1 and ("seq is" in out or "prev does not match" in out)


def test_event_signature_taken_from_another_event_fails(work):
    m = load(work)
    ev = m["content"]["events"]
    ev[1]["signature"] = ev[0]["signature"]
    save(work, resign(m, ec.generate_private_key(ec.SECP256R1())))
    code, out = check(work)
    assert code == 1 and "signature is invalid" in out


def test_modified_pdf_fails(work):
    (work / "report_stub.pdf").write_bytes((work / "report_stub.pdf").read_bytes() + b" ")
    code, out = check(work)
    assert code == 1 and "PDF SHA-256" in out


def test_modified_readings_csv_fails(work):
    lines = (work / "readings.csv").read_text().splitlines(keepends=True)
    lines[3] = lines[3].replace(",5.", ",6.", 1)
    (work / "readings.csv").write_text("".join(lines))
    code, out = check(work)
    assert code == 1 and "digest" in out


def test_deleted_readings_csv_row_fails(work):
    lines = (work / "readings.csv").read_text().splitlines(keepends=True)
    del lines[5]
    (work / "readings.csv").write_text("".join(lines))
    code, out = check(work)
    assert code == 1 and "row count" in out


def test_float_number_in_manifest_is_rejected(work):
    text = (work / "report.manifest.json").read_text()
    (work / "report.manifest.json").write_text(text.replace('"format_version":1', '"format_version":1.0'))
    code, out = check(work)
    assert code == 1 and "floating point" in out


def test_duplicate_keys_are_rejected(work):
    text = (work / "report.manifest.json").read_text()
    (work / "report.manifest.json").write_text('{"format":"x",' + text[1:])
    code, out = check(work)
    assert code == 1 and "duplicate" in out


def test_trip_not_closed_by_delivery_fails(work):
    m = load(work)
    m["content"]["events"].pop()  # chain still valid, but the trip was never delivered
    save(work, resign(m, ec.generate_private_key(ec.SECP256R1())))
    code, out = check(work)
    assert code == 1 and "DELIVERY" in out


def test_phase2_fixture_with_one_changed_character_fails(tmp_path):
    text = CHAIN_FIXTURE.read_text(encoding="utf-8")
    i = text.index('"user": "petugas-02"') + len('"user": "petugas-0')
    bad = tmp_path / "chain.json"
    bad.write_text(text[:i] + "3" + text[i + 1:], encoding="utf-8")
    code, out = run(bad)
    assert code == 1


def test_phase2_fixture_with_a_forged_reading_fails(tmp_path):
    doc = json.loads(CHAIN_FIXTURE.read_text(encoding="utf-8"))
    doc["readings"][12]["temp_centi"] = 500  # hides the excursion
    bad = tmp_path / "chain.json"
    bad.write_text(json.dumps(doc), encoding="utf-8")
    code, out = run(bad)
    assert code == 1 and "readings_digest" in out
