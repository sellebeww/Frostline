#!/usr/bin/env python3
# Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
"""Independent verifier for Frostline report manifests.

It does not share code with the Kotlin app. It re-derives every canonical text and hash from the manifest fields and
checks every signature with the public key embedded in the manifest. It proves "unchanged since the device key signed it".
It does not prove who held the device, and a rooted device could have rewritten the data before export.

Usage:
    python3 verify_report.py MANIFEST.json [--pdf REPORT.pdf] [--readings-csv READINGS.csv]

Also accepts the Phase 2 chain fixture (custody_chain_v1.json), which has events and readings but no manifest signature.
Exit code: 0 all checks passed, 1 at least one check failed, 2 usage or unreadable input.
Requires Python 3 and the `cryptography` package.
"""
import argparse
import base64
import csv
import hashlib
import io
import json
import re
import sys
from datetime import datetime, timezone
from decimal import ROUND_HALF_UP, Decimal

from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec

FORMAT = "frostline-manifest"
FORMAT_VERSION = 1
GENESIS_PREV = "0" * 64
HEX64 = re.compile(r"^[0-9a-f]{64}$")


class Report:
    """Collects check results so one run lists every failure."""

    def __init__(self, out):
        self.out = out
        self.failed = 0

    def ok(self, name):
        print(f"[OK]   {name}", file=self.out)

    def fail(self, name, detail=""):
        self.failed += 1
        print(f"[FAIL] {name}" + (f": {detail}" if detail else ""), file=self.out)

    def skip(self, name, why):
        print(f"[SKIP] {name}: {why}", file=self.out)

    def check(self, name, cond, detail=""):
        (self.ok if cond else lambda n: self.fail(n, detail))(name)
        return bool(cond)


def sha256_hex(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def canonical_json(obj) -> bytes:
    return json.dumps(obj, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode("utf-8")


def _no_duplicates(pairs):
    keys = [k for k, _ in pairs]
    if len(keys) != len(set(keys)):
        raise ValueError("duplicate key in JSON object")
    return dict(pairs)


def _reject_float(text):
    raise ValueError(f"floating point number {text} is not allowed in a manifest")


def load_json(text: str, allow_decimal: bool):
    kwargs = {"object_pairs_hook": _no_duplicates}
    if allow_decimal:
        kwargs["parse_float"] = Decimal
    else:
        kwargs["parse_float"] = _reject_float
    kwargs["parse_constant"] = lambda c: (_ for _ in ()).throw(ValueError(f"constant {c} not allowed"))
    return json.loads(text, **kwargs)


def _text(v: str) -> str:
    if v == "-":
        return "%2D"
    return v.replace("%", "%25").replace("\n", "%0A").replace("\r", "%0D")


def _decimal(v, places: int, strict: bool) -> str:
    if v is None:
        return "-"
    if strict and not isinstance(v, str):
        raise ValueError("coordinates must be strings in a manifest")
    d = Decimal(v) if not isinstance(v, Decimal) else v
    q = d.quantize(Decimal(1).scaleb(-places), rounding=ROUND_HALF_UP)
    if q == 0:
        q = abs(q)
    text = format(q, "f")
    if strict and text != v:
        raise ValueError(f"value {v!r} is not in canonical form {text!r}")
    return text


def event_canonical(e: dict, strict: bool) -> str:
    """Rebuilds the signed text from the event fields (SPEC.md 5.3), never from the stored `canonical` string."""
    def opt(v):
        return "-" if v is None else _text(v)

    if not isinstance(e["excursion_open"], bool):
        raise ValueError("excursion_open must be a boolean")
    temp = e["temp_centi"]
    if temp is not None and (isinstance(temp, bool) or not isinstance(temp, int)):
        raise ValueError("temp_centi must be an integer or null")
    lines = [
        ("v", "1"),
        ("trip", _text(e["trip"])),
        ("seq", str(e["seq"])),
        ("type", e["type"]),
        ("tag", opt(e["tag"])),
        ("tag_label", opt(e["tag_label"])),
        ("ts", str(e["ts"])),
        ("lat", _decimal(e["lat"], 7, strict)),
        ("lon", _decimal(e["lon"], 7, strict)),
        ("acc", _decimal(e["acc"], 1, strict)),
        ("user", _text(e["user"])),
        ("temp_centi", "-" if temp is None else str(temp)),
        ("excursion_open", "1" if e["excursion_open"] else "0"),
        ("readings_digest", e["readings_digest"]),
        ("prev", e["prev"]),
    ]
    return "\n".join(f"{k}={v}" for k, v in lines)


def readings_digest(readings) -> str:
    """readings: iterable of (ts_ms, temp_centi). Sorted by ts, stable."""
    body = "".join(f"{ts}|{c}\n" for ts, c in sorted(readings, key=lambda r: r[0]))
    return sha256_hex(body.encode("utf-8"))


def verify_sig(pub, sig_hex: str, data: bytes) -> bool:
    try:
        sig = bytes.fromhex(sig_hex)
        pub.verify(sig, data, ec.ECDSA(hashes.SHA256()))
        return True
    except (ValueError, InvalidSignature):
        return False


def load_public_key(der: bytes):
    try:
        key = serialization.load_der_public_key(der)
    except Exception as ex:  # cryptography raises several unrelated types for malformed or unsupported keys
        raise ValueError(f"cannot parse public key ({type(ex).__name__})") from ex
    if not isinstance(key, ec.EllipticCurvePublicKey) or not isinstance(key.curve, ec.SECP256R1):
        raise ValueError("public key is not ECDSA P-256")
    return key


def verify_events(rep: Report, events, pub, strict: bool, trip_id=None, readings=None, require_delivery=False):
    if not rep.check("custody chain is not empty", len(events) > 0, "no events"):
        return
    prev_hash = GENESIS_PREV
    all_ok = True
    for i, e in enumerate(events):
        label = f"event {i + 1}"
        problems = []
        try:
            if e["seq"] != i + 1:
                problems.append(f"seq is {e['seq']}, expected {i + 1}")
            if e["type"] not in ("PICKUP", "HANDOFF", "DELIVERY"):
                problems.append(f"unknown type {e['type']!r}")
            if i == 0 and e["type"] != "PICKUP":
                problems.append("first event must be PICKUP")
            if i > 0 and e["type"] == "PICKUP":
                problems.append("PICKUP is only allowed first")
            if i > 0 and events[i - 1]["type"] == "DELIVERY":
                problems.append("event after DELIVERY")
            if trip_id is not None and e["trip"] != trip_id:
                problems.append("trip id differs from the manifest trip")
            if e["trip"] != events[0]["trip"]:
                problems.append("trip id differs from the first event")
            if e["prev"] != prev_hash:
                problems.append("prev does not match the previous hash")
            canonical = event_canonical(e, strict)
            if "canonical" in e and e["canonical"] != canonical:
                problems.append("stored canonical text differs from the one rebuilt from the fields")
            digest = sha256_hex(canonical.encode("utf-8"))
            if e["hash"] != digest:
                problems.append("hash does not match the canonical text")
            if not verify_sig(pub, e["signature"], canonical.encode("utf-8")):
                problems.append("signature is invalid")
            if readings is not None:
                expected = readings_digest([r for r in readings if r[0] <= e["ts"]])
                if e["readings_digest"] != expected:
                    problems.append("readings_digest does not match the readings up to this event")
        except (KeyError, ValueError, TypeError, AttributeError) as ex:
            problems.append(f"malformed event ({type(ex).__name__}: {ex})")
        if problems:
            all_ok = False
            rep.fail(label, "; ".join(problems))
        prev_hash = e.get("hash", "") if isinstance(e, dict) else ""
    if all_ok:
        rep.ok(f"{len(events)} custody events: order, hash chain, canonical text, hashes, signatures")
    if require_delivery:
        rep.check("trip is closed by DELIVERY", events[-1].get("type") == "DELIVERY", "last event is not DELIVERY")


def parse_readings_csv(text: str, trip_id: str):
    reader = csv.DictReader(io.StringIO(text, newline=""))
    if reader.fieldnames != ["ts_utc_iso", "temp_c", "humidity", "trip_id"]:
        raise ValueError(f"unexpected CSV header {reader.fieldnames}")
    out = []
    for row in reader:
        if row["trip_id"] != trip_id:
            raise ValueError(f"row for another trip: {row['trip_id']!r}")
        t = datetime.strptime(row["ts_utc_iso"], "%Y-%m-%dT%H:%M:%S.%fZ").replace(tzinfo=timezone.utc)
        ms = int(t.timestamp()) * 1000 + t.microsecond // 1000
        centi = Decimal(row["temp_c"]) * 100
        if centi != centi.to_integral_value():
            raise ValueError(f"temperature {row['temp_c']} has more than two decimals")
        out.append((ms, int(centi)))
    return out


def verify_manifest(m, rep: Report, pdf_bytes=None, csv_text=None):
    required = ["format", "format_version", "content", "content_hash", "pdf", "generated_at_utc_ms",
                "manifest_hash", "manifest_signature"]
    missing = [k for k in required if k not in m]
    if not rep.check("manifest has all required fields", not missing, f"missing {missing}"):
        return
    rep.check("format and version", m["format"] == FORMAT and m["format_version"] == FORMAT_VERSION,
              f"got {m['format']!r} v{m['format_version']!r}")
    content = m["content"]

    rep.check("content_hash matches the canonical content", m["content_hash"] == sha256_hex(canonical_json(content)))

    try:
        der = base64.b64decode(content["public_key_x509_base64"], validate=True)
        pub = load_public_key(der)
    except (KeyError, ValueError, TypeError) as ex:
        rep.fail("public key is a valid ECDSA P-256 key", str(ex))
        return
    rep.ok("public key is a valid ECDSA P-256 key")
    rep.check("key fingerprint matches the public key", content.get("key_fingerprint") == sha256_hex(der))
    print(f"       signer key fingerprint: {sha256_hex(der)} (compare it with the one you trust for this device)", file=rep.out)

    trip_id = content.get("trip", {}).get("id")
    readings = None
    if csv_text is None:
        rep.skip("readings digest", "no --readings-csv given")
    else:
        try:
            readings = parse_readings_csv(csv_text, trip_id)
        except (ValueError, KeyError) as ex:
            rep.fail("readings CSV is readable", str(ex))
        else:
            rep.check("readings CSV row count matches the manifest", len(readings) == content.get("reading_count"),
                      f"{len(readings)} rows vs {content.get('reading_count')}")
            rep.check("readings CSV digest matches the manifest readings_digest",
                      readings_digest(readings) == content.get("readings_digest"))
    verify_events(rep, content.get("events", []), pub, True, trip_id, readings, require_delivery=True)

    body = {k: v for k, v in m.items() if k not in ("manifest_hash", "manifest_signature")}
    rep.check("manifest_hash matches the canonical manifest", m["manifest_hash"] == sha256_hex(canonical_json(body)))
    rep.check("manifest signature is valid for the embedded key",
              verify_sig(pub, m["manifest_signature"], m["manifest_hash"].encode("ascii", "replace")))

    if pdf_bytes is None:
        rep.skip("PDF hash", "no --pdf given")
    else:
        pdf = m["pdf"]
        rep.check("PDF SHA-256 matches the manifest", sha256_hex(pdf_bytes) == pdf.get("sha256"))
        rep.check("PDF size matches the manifest", len(pdf_bytes) == pdf.get("size_bytes"))


def verify_fixture(doc, rep: Report):
    pub = load_public_key(bytes.fromhex(doc["public_key_x509_hex"]))
    readings = [(r["ts"], r["temp_centi"]) for r in doc["readings"]]
    rep.ok("fixture mode (chain fixture, no manifest signature)")
    verify_events(rep, doc["events"], pub, False, None, readings)


def run(argv, out=sys.stdout) -> int:
    ap = argparse.ArgumentParser(description="Verify a Frostline report manifest.")
    ap.add_argument("manifest")
    ap.add_argument("--pdf")
    ap.add_argument("--readings-csv")
    try:
        args = ap.parse_args(argv)
    except SystemExit as ex:
        return 2 if ex.code else 0
    rep = Report(out)
    try:
        with open(args.manifest, "r", encoding="utf-8") as f:
            text = f.read()
        pdf = open(args.pdf, "rb").read() if args.pdf else None
        csv_text = open(args.readings_csv, "r", encoding="utf-8", newline="").read() if args.readings_csv else None
    except OSError as ex:
        print(f"cannot read input: {ex}", file=sys.stderr)
        return 2
    try:
        probe = json.loads(text)
        fixture = isinstance(probe, dict) and "format" not in probe and "events" in probe and "version" in probe
        doc = load_json(text, allow_decimal=fixture)
        if fixture:
            verify_fixture(doc, rep)
        else:
            verify_manifest(doc, rep, pdf, csv_text)
    except (ValueError, KeyError, TypeError) as ex:
        rep.fail("manifest is readable", f"{type(ex).__name__}: {ex}")
    print("RESULT: PASS" if rep.failed == 0 else f"RESULT: FAIL ({rep.failed} check(s) failed)", file=out)
    return 0 if rep.failed == 0 else 1


if __name__ == "__main__":
    sys.exit(run(sys.argv[1:]))
