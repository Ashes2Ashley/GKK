# GKK — 25 Real Features. Zero Theatre.

Product: **secure field dispatch** — end-to-end encrypted command and message
delivery to remote devices over SMS (data bearer) and IP, operated from a
real Kali-style console. Every feature below is implementable with real
Android APIs. Nothing simulated, no mock data, no canned output. If a number
is on screen, it came from a sensor, a socket, or a verified peer.

Design laws:
- **Real or removed.** Any feature that can't touch real hardware, real
  network, or a real peer doesn't ship.
- **No manual exhaustion.** Pairing by QR, auto transport selection,
  self-monitoring automation. The operator issues intent; the system handles
  mechanics.
- **Crypto is load-bearing.** Envelope v1 (ECDH P-256 + HKDF-SHA256 +
  AES-256-GCM, Android Keystore identities, anti-replay) underlies every
  remote feature. See `app/src/main/java/com/example/data/secure/`.

## I. Secure channel (the differentiator) — 1–6
1. **E2E encrypted messaging** — Envelope v1 between paired devices. Real
   ECDH key agreement, per-message AES-256-GCM, verified in both directions.
2. **Dispatch over data SMS** — binary SMS (`sendDataMessage`, dedicated
   port), segmented + reassembled, works with zero data connection.
3. **Multi-bearer transport** — same envelope over SMS or WebSocket/IP.
   Auto-selects (IP when available, SMS when dark), manual override.
4. **QR pairing + word verification** — scan to exchange keys, confirm 4
   spoken words. No manual key typing, no trust-on-first-use blindness.
5. **In-band key rotation** — rotate device keys over the encrypted channel
   itself; old keys securely retired.
6. **Verified inbox** — every inbound item shows authenticated sender,
   anti-replay sequence, tamper-evident log. Forgeries are rejected loudly,
   not silently dropped.

## II. Kali console, made real — 7–10
7. **Real command console** — the terminal stays, the theatre goes. Every
   command executes real logic: `sysinfo` (Build.*), `netstat`
   (/proc/net/tcp), `ps` (ActivityManager), `ping`/`dns`/`scan` (real I/O).
   Unknown commands fail honestly instead of printing fiction.
8. **TCP port scanner** — real connect-scan, configurable target/port range,
   service banner grabs. (The `nmap -sv` fantasy, implemented for real.)
9. **Network diagnostics** — ping (RTT stats), DNS + reverse DNS, route trace.
   Real measurements, exportable.
10. **Live device telemetry** — battery, sensors, memory pressure, radio
    state. All from real framework APIs, updating live.

## III. Connectivity, real radios — 11–15
11. **SMS agent mode** — device receives encrypted data SMS, verifies,
    executes whitelisted commands, replies encrypted. This is what makes the
    "field hardware" side real.
12. **SMS↔IP relay** — store-and-forward bridge: a device with both bearers
    relays envelopes between dark-site SMS nodes and IP nodes.
13. **Link monitor** — bearer type, signal strength, latency history with
    graphs. Real TelephonyManager/WifiManager data.
14. **Wi-Fi inspector** — SSID/BSSID/link speed/signal/noise, scan results.
15. **Cellular inspector** — carrier, RSRP/SINR, LTE/NR state, cell identity.

## IV. Fleet command — 16–20
16. **Fleet status board** — encrypted heartbeats from field devices; live
    online / last-seen / battery per device.
17. **Remote command execution** — run whitelisted commands on a field
    device, get encrypted results back in the console.
18. **Group broadcast** — one command, per-device envelopes, one tap.
    (No group key: compromise of one device never leaks the fleet.)
19. **Encrypted file transfer** — chunked over IP bearer, hash-verified,
    resumable. (SMS bearer is for commands, not files — honest limits.)
20. **SOS beacon** — one tap sends encrypted GPS + battery via SMS to paired
    devices. Works where data doesn't.

## V. Local power tools — 21–25
21. **Encrypted vault** — notes/keys/seeds, Keystore-wrapped, biometric gate.
22. **HTTP workbench** — the existing REST client: real, keep, polish.
23. **Edge gateway generator** — the existing Cloudflare Worker generator:
    real output, keep, audit the template.
24. **Chaos lab** — the existing interceptor: real fault injection, keep.
25. **Automation rules** — "if heartbeat missed 10 min → SMS alert", "on SOS
    → beacon back". The system watches itself; the operator isn't a babysitter.

## Build order
1. Crypto foundation (Envelope v1, Keystore identities, registry) — DONE,
   protocol verified against independent Python reference.
2. Transport + console + 15 real-time monitors — DONE (source; build/device
   proof still required before any "working" claim).
3. Voice calling + floating overlay — DONE (source): real carrier calls via
   ACTION_CALL, live call-state tracking, mute/speaker/hangup, real call log,
   floating overlay on RINGING/OFFHOOK. Console: call/hangup/mute/speaker/calls.
4. Hardening — DONE (source): SIM-ready fail-fast + phone validation on send,
   per-sender inbound rate limit (20/min, SMS-spam DoS guard), 2000-char
   command cap, 4000-byte dispatch cap.
5. `comms` self-check: prefilled SMS+voice configuration is probed live and
   reported CONFIRMED/MISSING — zero manual setup.
2. Data-SMS transport (segmenter, receiver, reassembly) — DONE, segmenter
   fuzz-verified (300 trials) + JVM tests.
3. Dispatch layer (seal→send, verified inbox, heartbeats, automation
   watchdog, agent mode) — DONE.
4. Kali console rewired to real commands — DONE (22 real commands, fiction
   deleted).
5. 15 real-time monitors (7 comms + 8 system) + Live screen — DONE.
6. Pairing UI (QR) + inbox UI polish.
7. Group broadcast, encrypted file transfer, vault.
8. Harden: audit, on-device testing, Play policy review (SMS permissions).

## The 15 real-time features (all live, zero mock data)
Comms: chat (verified inbox stream) · sms (data-SMS traffic log) ·
link (wifi+cell composite ticker) · console (live console tail) ·
alerts (automation event feed) · rtt (dispatch→reply latency) ·
fleet (heartbeat board).
System: battery (level/temp/voltage/state) · sensors (accel/light/prox live) ·
gps (live fix, permission-honest) · traffic (per-app throughput) ·
wifi (RSSI/link speed/SSID) · cell (dBm/operator/radio type) ·
ble (live nearby-device scan) · ntp (real SNTP offset vs pool.ntp.org).
Each monitor reports UNAVAILABLE with the honest reason when a permission
is missing or hardware is absent — never an invented reading.

## Voice calling — what "real" means here
- Outbound calls go through the real carrier (ACTION_CALL). No VoIP, no SIP
  server, no fake dialer — the phone actually rings the number.
- Call state comes from the real TelephonyCallback/PhoneStateListener.
- mute/speaker use the real AudioManager; hangup uses TelecomManager.endCall
  (needs ANSWER_PHONE_CALLS, granted by the user — otherwise it says so).
- The overlay is a real SYSTEM_ALERT_WINDOW floating view with live duration;
  if the permission isn't granted it stays silent instead of pretending.
- Every permission gate fails with the honest reason, never a fake "calling…".

## Bearers: SMS advanced + WebRTC fallback
- Bearer flags (persisted, prefilled, console `bearer`): advanced SMS send
  ON/OFF, WebRTC fallback ON/OFF, bearer priority order.
- Advanced SMS: walks every active SIM subscription, per-SIM retry with
  exponential backoff (2s/4s), per-SIM stats. Main-thread safe.
- WebRTC fallback: real org.webrtc PeerConnection (io.getstream
  StreamVideo WebRTC build from Maven Central — the official artifact only
  ever shipped to jCenter, which is dead), single ordered DataChannel
  carrying the same Envelope v1 bytes as SMS. Signaling (offer/answer/ICE)
  as encrypted "signal" envelopes over UDP to the mDNS-discovered peer —
  no server. STUN for NAT traversal. `rtc discover|connect|status|close`.
- BearerManager tries bearers in priority order; every failure names every
  bearer's real cause.

## IP + IMEI intelligence (admin)
- `netintel`: real interface enumeration + public IP and NAT type via a
  hand-rolled RFC 5389 STUN client (pure UDP, no HTTP). NAT class from two
  STUN servers: open / cone / symmetric. Codec unit-tested on JVM.
- Device attestation: real IMEI (honest UNAVAILABLE on Android 10+ for
  non-privileged apps), ANDROID_ID, build fingerprint. `attest` /
  `attest send` / `attest verify`; inbound claims are change-detected
  (mismatch raises a CRIT alert). Remote `attest` agent command answers
  ONLY admin peers (trustLevel >= 100, set via `trustlevel <name> <n>`).

## Explicitly cut
- Fake Kali command output (`nmap` fiction), fake WebRTC/SIP calls, fake
  SIM hardware state, fake 5G telemetry, fake "dispatch" status flips.
  Where the old code was theatre, it is deleted, not decorated.
