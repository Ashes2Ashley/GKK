#!/usr/bin/env python3
"""Verify the JS-sealed envelope (B->A) with the Python implementation."""
import base64, json
from cryptography.hazmat.primitives import serialization

# Reuse the helpers (hkdf_sha256, derive_key, open_envelope) without re-running keygen.
src = open("make_vectors.py").read().split("a_priv = ec.generate_private_key")[0]
exec(src)

v = json.load(open("/tmp/interop/vectors.json"))
a_priv = serialization.load_der_private_key(base64.b64decode(v["a_priv_pkcs8_b64"]), password=None)
b_pub = serialization.load_der_public_key(base64.b64decode(v["b_pub_spki_b64"]))
a_id = bytes.fromhex(v["a_id_hex"])
b_id = bytes.fromhex(v["b_id_hex"])
env = base64.b64decode(open("/tmp/interop/js_envelope_b64.txt").read().strip())
pt, seq = open_envelope(a_priv, a_id, b_pub, b_id, env)
assert seq == 3, seq
assert pt == b'{"kind":"msg","tag":"js1","body":{"text":"hello from js"}}', pt
print("PASS python opens js envelope (seq=3, cross-impl B->A)")
