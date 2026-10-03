/* GKK Envelope v1 — byte-exact port of Android EnvelopeCrypto.kt
 * Works in browsers and Node 18+ (globalThis.crypto.subtle).
 * Layout (big-endian): [0]=0x01 ver, [1..8]=senderId, [9..16]=recipientId,
 * [17..24]=seq u64, [25..32]=timestampMs u64, [33..44]=nonce12,
 * [45..46]=ctLen u16, [47..]=ct||tag16. AAD = header bytes [0..44].
 * Key: ECDH(P-256) -> HKDF-SHA256(salt="GKK-v1"||senderId||recipientId,
 *      info="envelope-aes-256-gcm", L=32) -> AES-256-GCM.
 * Device ID = first 8 bytes of SHA-256 over the X.509 SPKI public key.
 */
(function (root, factory) {
  if (typeof module !== "undefined" && module.exports) module.exports = factory();
  else root.GkkCrypto = factory();
})(typeof self !== "undefined" ? self : this, function () {
  "use strict";
  const subtle = () => globalThis.crypto.subtle;
  const VERSION = 0x01;
  const FRESHNESS_MS = 24 * 3600 * 1000;
  const ID_LEN = 8, NONCE_LEN = 12, HEADER_LEN = 45, TAG_BITS = 128;

  const utf8 = (s) => new TextEncoder().encode(s);
  function concat(...arrs) {
    const out = new Uint8Array(arrs.reduce((n, a) => n + a.length, 0));
    let o = 0;
    for (const a of arrs) { out.set(a, o); o += a.length; }
    return out;
  }
  const hex = (b) => [...new Uint8Array(b)].map((x) => x.toString(16).padStart(2, "0")).join("");
  function b64encode(bytes) {
    const bin = String.fromCharCode(...new Uint8Array(bytes));
    if (typeof btoa !== "undefined") return btoa(bin);
    return Buffer.from(bin, "binary").toString("base64");
  }
  function b64decode(s) {
    if (typeof atob !== "undefined") {
      const bin = atob(s);
      const out = new Uint8Array(bin.length);
      for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
      return out;
    }
    return new Uint8Array(Buffer.from(s, "base64"));
  }

  async function sha256(data) {
    return new Uint8Array(await subtle().digest("SHA-256", data));
  }
  /** Device ID: first 8 bytes of SHA-256(SPKI). Matches Android deviceId(). */
  async function deviceId(spkiBytes) {
    return (await sha256(spkiBytes)).slice(0, ID_LEN);
  }
  const deviceIdHex = (id) => hex(id);

  async function deriveMessageKey(ownPriv, peerPub, senderId, recipientId) {
    if (senderId.length !== ID_LEN || recipientId.length !== ID_LEN)
      throw new Error("bad device id length");
    const bits = await subtle().deriveBits({ name: "ECDH", public: peerPub }, ownPriv, 256);
    const salt = concat(utf8("GKK-v1"), senderId, recipientId);
    const info = utf8("envelope-aes-256-gcm");
    const ikm = await subtle().importKey("raw", bits, "HKDF", false, ["deriveKey"]);
    return subtle().deriveKey(
      { name: "HKDF", hash: "SHA-256", salt, info },
      ikm, { name: "AES-GCM", length: 256 }, false, ["encrypt", "decrypt"]
    );
  }

  async function seal(senderPriv, senderId, recipientPub, recipientId, seq, plaintext, timestampMs) {
    if (!(seq > 0)) throw new Error("seq must be positive");
    if (plaintext.length < 1 || plaintext.length > 4096) throw new Error("bad plaintext length");
    const ts = timestampMs === undefined ? Date.now() : timestampMs;
    const nonce = globalThis.crypto.getRandomValues(new Uint8Array(NONCE_LEN));
    const key = await deriveMessageKey(senderPriv, recipientPub, senderId, recipientId);
    const header = new Uint8Array(HEADER_LEN);
    const dv = new DataView(header.buffer);
    dv.setUint8(0, VERSION);
    header.set(senderId, 1);
    header.set(recipientId, 9);
    dv.setBigUint64(17, BigInt(seq));
    dv.setBigUint64(25, BigInt(ts));
    header.set(nonce, 33);
    const ctBuf = await subtle().encrypt(
      { name: "AES-GCM", iv: nonce, additionalData: header, tagLength: TAG_BITS },
      key, plaintext
    );
    const ct = new Uint8Array(ctBuf);
    if (ct.length > 0xffff) throw new Error("ciphertext too large");
    const out = new Uint8Array(HEADER_LEN + 2 + ct.length);
    out.set(header, 0);
    new DataView(out.buffer).setUint16(HEADER_LEN, ct.length);
    out.set(ct, HEADER_LEN + 2);
    return out;
  }

  async function open(recipientPriv, recipientId, senderPub, expectedSenderId, envelope, lastSeenSeq, nowMs) {
    const now = nowMs === undefined ? Date.now() : nowMs;
    if (envelope.length < HEADER_LEN + 2) throw new Error("envelope too short");
    const dv = new DataView(envelope.buffer, envelope.byteOffset, envelope.byteLength);
    if (dv.getUint8(0) !== VERSION) throw new Error("unsupported envelope version");
    const senderId = envelope.slice(1, 9);
    const recipId = envelope.slice(9, 17);
    const seq = dv.getBigUint64(17);
    const timestamp = dv.getBigUint64(25);
    const nonce = envelope.slice(33, 45);
    const ctLen = dv.getUint16(45);
    const eq = (a, b) => a.length === b.length && a.every((v, i) => v === b[i]);
    if (!eq(senderId, expectedSenderId)) throw new Error("sender identity mismatch");
    if (!eq(recipId, recipientId)) throw new Error("envelope not addressed to this device");
    if (seq <= BigInt(lastSeenSeq)) throw new Error("replay rejected (seq=" + seq + " <= " + lastSeenSeq + ")");
    const age = now > Number(timestamp) ? BigInt(now) - timestamp : timestamp - BigInt(now);
    if (age > BigInt(FRESHNESS_MS)) throw new Error("stale envelope");
    if (envelope.length !== HEADER_LEN + 2 + ctLen) throw new Error("truncated envelope");
    const header = envelope.slice(0, HEADER_LEN);
    const ct = envelope.slice(HEADER_LEN + 2, HEADER_LEN + 2 + ctLen);
    const key = await deriveMessageKey(recipientPriv, senderPub, senderId, recipId);
    let pt;
    try {
      pt = await subtle().decrypt(
        { name: "AES-GCM", iv: nonce, additionalData: header, tagLength: TAG_BITS },
        key, ct
      );
    } catch (e) {
      throw new Error("envelope authentication failed");
    }
    return { plaintext: new Uint8Array(pt), seq: Number(seq) };
  }

  async function generateIdentity() {
    const kp = await subtle().generateKey({ name: "ECDH", namedCurve: "P-256" }, true, ["deriveBits"]);
    const spki = new Uint8Array(await subtle().exportKey("spki", kp.publicKey));
    return { privateKey: kp.privateKey, publicKey: kp.publicKey, spki };
  }
  async function importPeerPublicKey(spkiBytes) {
    return subtle().importKey("spki", spkiBytes, { name: "ECDH", namedCurve: "P-256" }, true, []);
  }

  // 256-word pairing verification list — identical to Android DeviceKeys.
  const WORDS = ("amber arc ash atlas aurora axiom azimuth badger banyan basalt beacon birch bison blade bluff bolt " +
    "boreal bravo bridge brisk bronze brook bundle bunker cactus cadence camel canyon carbon cargo cascade cedar " +
    "cerule chasm cherry cinder cipher citadel cliff cobalt comet compass copper coral cougar crane crater crescent " +
    "crest cricket crimson cross crown crystal curlew cypress dagger dahlia dawn delta desert dew dial dingle " +
    "dodo drift dune eagle ebony echo eclipse eddy ember emerald engine enigma envoy epoch elm estuary " +
    "falcon fern fiber finch fjord flint flora flux forest forge fossil foxtrot frost fuchsia garnet gecko " +
    "glacier glint glyph gneiss goblin gorge granite grove gull gypsum harbor hawk hazel helix heron hickory " +
    "horizon hornet hotel hyena ibex ice igloo iguana indigo inlet iridium iron island ivory jackal jasper " +
    "juniper kestrel kiln koala krill lagoon lance lark laser lattice laurel ledge lemur lichen lilac linen " +
    "lizard llama locket lotus lumen lynx magma magnet mammoth mango maple marble marsh mason meadow mercury " +
    "meridian mesa meteor mica micro mimosa minnow mint mirage monsoon moose moss moth mount mulberry murmur " +
    "nadir nebula needle nimbus nomad north nova nugget oasis obsidian ochre octane omega onyx opal orbit " +
    "orchid osprey otter oxide oyster palm panda parrot pebble pelican pepper peridot petal phantom pinnacle pioneer " +
    "plaza prism puma quartz quasar quill radar raven reef relay ridge river robin rocket rotor ruby " +
    "sable saffron sage salmon sand sapphire saturn savanna scarlet sentry sequoia sierra signal silver siren slate " +
    "sleet sodium solar sonar sparrow sphinx spice spire spruce squid stable stanza steppe stone storm summit").split(" ");
  if (WORDS.length !== 256) throw new Error("wordlist must be 256 words");
  /** 4-word code from device id bytes 0,2,4,6 — matches Android exactly. */
  function verificationWords(id) {
    return [0, 2, 4, 6].map((i) => WORDS[id[i]]).join(" ");
  }

  return {
    VERSION, FRESHNESS_MS, ID_LEN, NONCE_LEN, HEADER_LEN,
    utf8, concat, hex, b64encode, b64decode,
    sha256, deviceId, deviceIdHex,
    deriveMessageKey, seal, open,
    generateIdentity, importPeerPublicKey,
    verificationWords, WORDS
  };
});
