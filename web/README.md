# GKK/Web — encrypted dispatch console (100 features)

Static single-page web console for the GKK protocol. No build step, no
framework — host `index.html` + `gkk-crypto.js` on any static host
(Cloudflare Pages, GitHub Pages, nginx).

## What is real

- **Envelope v1 is byte-exact with the Android app.** `gkk-crypto.js` is a
  line-for-line port of `EnvelopeCrypto.kt` (same header layout, HKDF salt/
  info, AES-256-GCM AAD, device-ID derivation, replay + freshness checks).
  Proven by `web/interop-test/`: 10/10 cross-implementation checks —
  Python seals → JS opens, JS seals → Python opens, device IDs match,
  tamper/replay/stale/wrong-recipient/sender-mismatch all rejected.
- **Identity**: P-256 via WebCrypto; private key non-extractable in
  IndexedDB, never leaves the device.
- **Transport**: WebRTC data channels (manual SDP copy/paste, or the
  optional `signal-worker.js` Cloudflare Worker). Every chat/file/sos/
  heartbeat payload is a sealed Envelope v1 — same bytes the Android app
  speaks.
- **Voice/video**: real `getUserMedia` + `RTCPeerConnection` calls,
  screen share, MediaRecorder call capture.
- **Ops tools**: real browser APIs throughout (`doh` → dns.google,
  `rdap` → rdap.org, `myip` → STUN srflx, `battery`, `geolocate`,
  `storage`, sensors, `aesbench` via WebCrypto, …).

## Honest limits

- **Web↔Android WebRTC interop is not established.** The envelope layer is
  proven cross-implementation, but the Android app's WebRTC signaling path
  differs from this app's manual-SDP flow — data links today are web↔web.
  `seal`/`openenv` let you hand-verify envelopes across implementations.
- No SMS/telephony in a browser — the Android app remains the SMS bearer.
- `httphead`/`httpget` are CORS-bound: cross-origin targets that don't send
  CORS headers will fail loudly (that's reported, not hidden).
- `qrgen`/`qrscan` lazy-load CDN libraries (qrcode-generator, jsQR); they
  fail loudly offline.
- `myip` needs UDP to a STUN server; symmetric/restrictive NATs may yield
  no srflx candidate.

## The 100 features

Identity & crypto (14): `newid myid exportkey importkey seal openenv
selftest words qrgen qrscan sha256 sha512 hmac rand`

Peers (10): `trust peers untrust trustlevel peerinfo exportpeers
importpeers attest attest-send attest-verify`

WebRTC links (16): `rtcoffer rtcanswer rtcaccept rtclist rtcstate rtcstats
ice-restart candidates myip localips dclog sigset sigsend ping rtt hangup`

Messaging (14): `dispatch inbox inboxsearch inboxclear msginfo broadcast
heartbeat presence watchdog sos exportlog sendfile recvlist getfile`

Voice/video (12): `vcoffer vcanswer vcaccept vchangup mute camoff
screenshare callstats recstart recsave devices setmic`

Ops tools (26): `httphead httpget doh rdap speedtest fetchping battery
deviceinfo displayinfo netinfo storage geolocate sensors audioinfo clipread
clipwrite vibrate wakelock notify time ntpdate uuid b64e b64d hexdump aesbench`

Monitors + app (8): `mon monlist help clear theme alias exportcfg reset`

Run `help` in the console for usage of each.

## Two-browser test

1. Open the page in two browsers. Both: `newid`, then `myid` (note words).
2. A: `rtcoffer bob` → copy the offer. B: `trust alice <A-pubkey>` then
   `rtcanswer alice <offer>` → copy the answer. A: `rtcaccept bob <answer>`.
3. `dispatch bob hello` → B sees it, A sees the ack + RTT.
4. `sendfile bob`, `vcoffer bob --video`, `sos bob`, `ping bob`.
