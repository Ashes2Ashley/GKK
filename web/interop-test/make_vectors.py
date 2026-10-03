#!/usr/bin/env python3
"""Generate interop vectors: EC keypairs + a Python-sealed Envelope v1.
Writes /tmp/interop/vectors.json for the Node.js side to consume/verify."""
import base64, json, os, struct, time
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives import hashes, hmac, serialization
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.exceptions import InvalidTag

ID_LEN, NONCE_LEN, HEADER_LEN = 8, 12, 45

def hkdf_sha256(ikm, salt, info, length):
    h = hmac.HMAC(salt, hashes.SHA256()); h.update(ikm); prk = h.finalize()
    okm, t, c = b"", b"", 1
    while len(okm) < length:
        h = hmac.HMAC(prk, hashes.SHA256()); h.update(t + info + bytes([c])); t = h.finalize()
        okm += t; c += 1
    return okm[:length]

def device_id(pub):
    from cryptography.hazmat.primitives import hashes as hh
    d = hh.Hash(hh.SHA256()); d.update(pub.public_bytes(
        serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo))
    return d.finalize()[:ID_LEN]

def derive_key(own_priv, peer_pub, sender_id, recipient_id):
    shared = own_priv.exchange(ec.ECDH(), peer_pub)
    return hkdf_sha256(shared, b"GKK-v1" + sender_id + recipient_id, b"envelope-aes-256-gcm", 32)

def seal(sender_priv, sender_id, recip_pub, recip_id, seq, plaintext, ts=None):
    ts = int(time.time() * 1000) if ts is None else ts
    nonce = os.urandom(NONCE_LEN)
    key = derive_key(sender_priv, recip_pub, sender_id, recip_id)
    header = struct.pack(">B", 1) + sender_id + recip_id + struct.pack(">Q", seq) + struct.pack(">Q", ts) + nonce
    ct = AESGCM(key).encrypt(nonce, plaintext, header)
    return header + struct.pack(">H", len(ct)) + ct

def open_envelope(recip_priv, recip_id, sender_pub, expected_sender_id, env, last_seen=0):
    assert env[0] == 1 and len(env) >= HEADER_LEN + 2
    sender_id, r_id = env[1:9], env[9:17]
    seq, ts = struct.unpack(">Q", env[17:25])[0], struct.unpack(">Q", env[25:33])[0]
    nonce, ct_len = env[33:45], struct.unpack(">H", env[45:47])[0]
    assert sender_id == expected_sender_id and r_id == recip_id and seq > last_seen
    assert abs(int(time.time()*1000) - ts) <= 24*3600*1000 and len(env) == HEADER_LEN + 2 + ct_len
    key = derive_key(recip_priv, sender_pub, sender_id, r_id)
    pt = AESGCM(key).decrypt(nonce, env[47:47+ct_len], env[:HEADER_LEN])
    return pt, seq

a_priv = ec.generate_private_key(ec.SECP256R1())
b_priv = ec.generate_private_key(ec.SECP256R1())
a_pub, b_pub = a_priv.public_key(), b_priv.public_key()
a_id, b_id = device_id(a_pub), device_id(b_pub)

def b64(b): return base64.b64encode(b).decode()
def pub_b64(p): return b64(p.public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo))
def priv_b64(p): return b64(p.private_bytes(serialization.Encoding.DER, serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))

body = b'{"kind":"msg","tag":"py1","body":{"text":"hello from python"}}'
env = seal(a_priv, a_id, b_pub, b_id, 7, body)
# tampered copy (flip a ciphertext byte)
tam = bytearray(env); tam[60] ^= 0x01
# stale copy (timestamp 25h ago)
stale = seal(a_priv, a_id, b_pub, b_id, 8, body, int(time.time()*1000) - 25*3600*1000)

os.makedirs("/tmp/interop", exist_ok=True)
json.dump({
    "a_pub_spki_b64": pub_b64(a_pub), "b_pub_spki_b64": pub_b64(b_pub),
    "a_priv_pkcs8_b64": priv_b64(a_priv), "b_priv_pkcs8_b64": priv_b64(b_priv),
    "a_id_hex": a_id.hex(), "b_id_hex": b_id.hex(),
    "py_envelope_b64": b64(env), "py_plaintext": body.decode(),
    "tampered_b64": b64(bytes(tam)), "stale_b64": b64(stale),
}, open("/tmp/interop/vectors.json", "w"))
print("vectors written; a_id=%s b_id=%s" % (a_id.hex(), b_id.hex()))
