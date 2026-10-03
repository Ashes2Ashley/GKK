#!/usr/bin/env node
/* Consume /tmp/interop/vectors.json: prove JS<->Python Envelope v1 interop.
 * 1. JS opens Python-sealed envelope (round trip cross-impl)
 * 2. JS seals B->A, Python will open it (written to js_envelope_b64)
 * 3. Tamper / replay / stale / wrong-recipient all rejected by JS
 */
const fs = require("fs");
const G = require("../gkk-crypto.js");

const b64d = (s) => new Uint8Array(Buffer.from(s, "base64"));
const b64e = (b) => Buffer.from(b).toString("base64");
const eq = (a, b) => a.length === b.length && a.every((v, i) => v === b[i]);
let pass = 0;
function ok(name, cond) {
  console.log((cond ? "PASS" : "FAIL") + " " + name);
  if (cond) pass++;
  else process.exitCode = 1;
}

(async () => {
  const v = JSON.parse(fs.readFileSync("/tmp/interop/vectors.json", "utf8"));
  const aPub = await G.importPeerPublicKey(b64d(v.a_pub_spki_b64));
  const bPub = await G.importPeerPublicKey(b64d(v.b_pub_spki_b64));
  const aPriv = await crypto.subtle.importKey("pkcs8", b64d(v.a_priv_pkcs8_b64),
    { name: "ECDH", namedCurve: "P-256" }, true, ["deriveBits"]);
  const bPriv = await crypto.subtle.importKey("pkcs8", b64d(v.b_priv_pkcs8_b64),
    { name: "ECDH", namedCurve: "P-256" }, true, ["deriveBits"]);

  // device IDs must match Python's
  const aSpki = b64d(v.a_pub_spki_b64), bSpki = b64d(v.b_pub_spki_b64);
  const aId = await G.deviceId(aSpki), bId = await G.deviceId(bSpki);
  ok("deviceId(A) matches python", G.deviceIdHex(aId) === v.a_id_hex);
  ok("deviceId(B) matches python", G.deviceIdHex(bId) === v.b_id_hex);

  // 1. JS opens Python's envelope
  const opened = await G.open(bPriv, bId, aPub, aId, b64d(v.py_envelope_b64), 0);
  ok("js opens python envelope",
    Buffer.from(opened.plaintext).toString() === v.py_plaintext && opened.seq === 7);

  // 2. JS seals B->A for Python to open
  const pt = G.utf8('{"kind":"msg","tag":"js1","body":{"text":"hello from js"}}');
  const envJs = await G.seal(bPriv, bId, aPub, aId, 3, pt);
  fs.writeFileSync("/tmp/interop/js_envelope_b64.txt", b64e(envJs));
  ok("js sealed envelope for python", envJs.length > 47);

  // 3a. tampered envelope rejected
  let threw = false;
  try { await G.open(bPriv, bId, aPub, aId, b64d(v.tampered_b64), 0); }
  catch (e) { threw = /authentication failed/.test(e.message); }
  ok("tamper rejected", threw);

  // 3b. replay rejected (seq 7 <= lastSeen 7)
  threw = false;
  try { await G.open(bPriv, bId, aPub, aId, b64d(v.py_envelope_b64), 7); }
  catch (e) { threw = /replay/.test(e.message); }
  ok("replay rejected", threw);

  // 3c. stale rejected
  threw = false;
  try { await G.open(bPriv, bId, aPub, aId, b64d(v.stale_b64), 0); }
  catch (e) { threw = /stale/.test(e.message); }
  ok("stale rejected", threw);

  // 3d. wrong recipient rejected (A opens, claims to be the recipient)
  threw = false;
  try { await G.open(aPriv, aId, aPub, aId, b64d(v.py_envelope_b64), 0); }
  catch (e) { threw = /not addressed/.test(e.message); }
  ok("wrong recipient rejected", threw);

  // 3e. sender mismatch rejected (envelope says A, caller expects B)
  threw = false;
  try { await G.open(bPriv, bId, aPub, bId, b64d(v.py_envelope_b64), 0); }
  catch (e) { threw = /sender identity mismatch/.test(e.message); }
  ok("sender mismatch rejected", threw);

  console.log(pass + "/9 js-side checks passed");
})().catch((e) => { console.error("FATAL", e); process.exitCode = 1; });
