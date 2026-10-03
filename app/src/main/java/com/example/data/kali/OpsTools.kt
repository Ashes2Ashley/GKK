package com.example.data.kali

import android.Manifest
import android.app.ActivityManager
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.SensorManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.TrafficStats
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Base64
import androidx.core.content.ContextCompat
import com.example.data.dispatch.DispatchManager
import com.example.data.secure.DeviceKeys
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import org.json.JSONObject

/**
 * 50 built-in ops tools for the Kali console. Every tool calls a real
 * Android / Java / Linux API and reports honest errors — no mock data,
 * no canned output, no root required. Tools that need a permission say so
 * instead of failing silently.
 *
 * Wired into the console router: `OpsTools.dispatch(ctx, cmd, args)`
 * returns null when [cmd] is not one of ours.
 */
object OpsTools {

    fun index(): String = """
50 built-in ops tools — real APIs only, zero mock data.
[net]  whois httphead httpget certcheck tlsinfo doh speedtest ifconfig routes arp wifisurvey btlescan nfcinfo sntp tcping
[sys]  battery cpuinfo meminfo storage sensors thermals uptime logcatme packages appinfo perms deviceinfo displayinfo audioinfo flashlight keepawake
[sec]  hash hashfile rand passgen keyinfo trustcheck secscan aesbench
[comms] smsinfo siminfo signal cellinfo datausage phonestate b64e b64d uuid hexdump time
usage: <tool> [args]""".trimIndent()

    private fun granted(ctx: Context, perm: String): Boolean =
        ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED

    private fun fmtDate(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(ms))

    fun dispatch(ctx: Context, cmd: String, args: List<String>): String? {
        return try {
            when (cmd) {
                "ops" -> index()
                // net (15)
                "whois" -> whois(args)
                "httphead" -> httphead(args)
                "httpget" -> httpget(args)
                "certcheck" -> certcheck(args)
                "tlsinfo" -> tlsinfo(args)
                "doh" -> doh(args)
                "speedtest" -> speedtest()
                "ifconfig" -> ifconfig()
                "routes" -> routes()
                "arp" -> arp()
                "wifisurvey" -> wifisurvey(ctx)
                "btlescan" -> btlescan(ctx, args)
                "nfcinfo" -> nfcinfo(ctx)
                "sntp" -> sntp(args)
                "tcping" -> tcping(args)
                // sys (16)
                "battery" -> battery(ctx)
                "cpuinfo" -> cpuinfo()
                "meminfo" -> meminfo(ctx)
                "storage" -> storage(ctx)
                "sensors" -> sensors(ctx)
                "thermals" -> thermals(ctx)
                "uptime" -> uptime()
                "logcatme" -> logcatme()
                "packages" -> packages(ctx, args)
                "appinfo" -> appinfo(ctx, args)
                "perms" -> perms(ctx)
                "deviceinfo" -> deviceinfo()
                "displayinfo" -> displayinfo(ctx)
                "audioinfo" -> audioinfo(ctx)
                "flashlight" -> flashlight(ctx, args)
                "keepawake" -> keepawake(ctx, args)
                // sec (8)
                "hash" -> hash(args)
                "hashfile" -> hashfile(args)
                "rand" -> rand(args)
                "passgen" -> passgen(args)
                "keyinfo" -> keyinfo()
                "trustcheck" -> trustcheck()
                "secscan" -> secscan(ctx)
                "aesbench" -> aesbench(args)
                // comms (11)
                "smsinfo" -> smsinfo(ctx)
                "siminfo" -> siminfo(ctx)
                "signal" -> signal(ctx)
                "cellinfo" -> cellinfo(ctx)
                "datausage" -> datausage()
                "phonestate" -> phonestate(ctx)
                "b64e" -> b64e(args)
                "b64d" -> b64d(args)
                "uuid" -> UUID.randomUUID().toString()
                "hexdump" -> hexdump(args)
                "time" -> time()
                else -> null
            }
        } catch (e: Exception) {
            "tool failed: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    // ---------------- net ----------------

    private fun whois(args: List<String>): String {
        val domain = args.getOrNull(0)?.trim()?.lowercase() ?: return "usage: whois <domain>"
        if (!domain.contains(".")) return "not a domain: $domain"
        return try {
            fun query(server: String, q: String): String {
                Socket().use { s ->
                    s.connect(InetSocketAddress(server, 43), 10000)
                    s.soTimeout = 10000
                    s.getOutputStream().write("$q\r\n".toByteArray())
                    return s.getInputStream().readBytes().toString(Charsets.UTF_8)
                }
            }
            val first = query("whois.iana.net", domain).take(6000)
            val referral = first.lines()
                .firstOrNull { it.startsWith("whois:", ignoreCase = true) }
                ?.substringAfter(":")?.trim()
            if (referral.isNullOrBlank()) "== whois.iana.net ==\n$first"
            else "== $referral ==\n" + query(referral, domain).take(8000)
        } catch (e: Exception) {
            "whois failed: ${e.message}"
        }
    }

    private fun httphead(args: List<String>): String {
        val raw = args.getOrNull(0) ?: return "usage: httphead <url>"
        return try {
            val conn = URL(raw).openConnection() as HttpURLConnection
            conn.requestMethod = "HEAD"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.instanceFollowRedirects = true
            val code = conn.responseCode
            val headers = conn.headerFields.entries
                .filter { it.key != null }
                .joinToString("\n") { "${it.key}: ${it.value.joinToString(", ")}" }
            conn.disconnect()
            "HTTP $code\n$headers"
        } catch (e: Exception) {
            "httphead failed: ${e.message}"
        }
    }

    private fun httpget(args: List<String>): String {
        val raw = args.getOrNull(0) ?: return "usage: httpget <url>"
        return try {
            val conn = URL(raw).openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            conn.instanceFollowRedirects = true
            val code = conn.responseCode
            val body = conn.inputStream.bufferedReader().readText().take(65536)
            conn.disconnect()
            "HTTP $code (${body.length} bytes shown of up to 64KB)\n" + body.take(2000)
        } catch (e: Exception) {
            "httpget failed: ${e.message}"
        }
    }

    private fun tlsSocket(host: String, port: Int): SSLSocket {
        val sock = SSLSocketFactory.getDefault().createSocket() as SSLSocket
        sock.connect(InetSocketAddress(host, port), 10000)
        sock.soTimeout = 10000
        sock.startHandshake()
        return sock
    }

    private fun certcheck(args: List<String>): String {
        val hp = args.getOrNull(0) ?: return "usage: certcheck <host[:port]>"
        val host = hp.substringBefore(":")
        val port = hp.substringAfter(":", "443").toIntOrNull() ?: 443
        return try {
            val sock = tlsSocket(host, port)
            val certs = sock.session.peerCertificates.mapNotNull { it as? X509Certificate }
            sock.close()
            if (certs.isEmpty()) return "no certificates presented"
            val md = MessageDigest.getInstance("SHA-256")
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            "chain depth: ${certs.size}\n" + certs.joinToString("\n---\n") { c ->
                val fp = md.digest(c.encoded).joinToString("") { "%02x".format(it) }
                "subject: ${c.subjectX500Principal.name}\n" +
                    "issuer: ${c.issuerX500Principal.name}\n" +
                    "expiry: ${fmt.format(c.notAfter)}\n" +
                    "sha256: $fp"
            }
        } catch (e: Exception) {
            "certcheck failed: ${e.message}"
        }
    }

    private fun tlsinfo(args: List<String>): String {
        val hp = args.getOrNull(0) ?: return "usage: tlsinfo <host[:port]>"
        val host = hp.substringBefore(":")
        val port = hp.substringAfter(":", "443").toIntOrNull() ?: 443
        return try {
            val sock = tlsSocket(host, port)
            val sess = sock.session
            val out = "protocol: ${sess.protocol}\ncipher: ${sess.cipherSuite}\n" +
                "peer: ${sess.peerHost}"
            sock.close()
            out
        } catch (e: Exception) {
            "tlsinfo failed: ${e.message}"
        }
    }

    private fun doh(args: List<String>): String {
        val name = args.getOrNull(0) ?: return "usage: doh <name> [type]"
        val type = args.getOrNull(1) ?: "A"
        return try {
            val conn = URL("https://dns.google/resolve?name=$name&type=$type")
                .openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            val obj = JSONObject(body)
            if (obj.optInt("Status") != 0) return "DNS status ${obj.optInt("Status")} for $name"
            val ans = obj.optJSONArray("Answer") ?: return "no Answer section"
            (0 until ans.length()).joinToString("\n") { i ->
                val a = ans.getJSONObject(i)
                "${a.optString("name")}  type=${a.optInt("type")}  ${a.optString("data")}"
            }
        } catch (e: Exception) {
            "doh failed: ${e.message}"
        }
    }

    private fun speedtest(): String {
        return try {
            val bytes = 10_000_000L
            val conn = URL("https://speed.cloudflare.com/__down?bytes=$bytes")
                .openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 30000
            val t0 = System.nanoTime()
            var total = 0L
            conn.inputStream.use { ins ->
                val buf = ByteArray(65536)
                var n: Int
                while (ins.read(buf).also { n = it } != -1) total += n
            }
            conn.disconnect()
            val sec = (System.nanoTime() - t0) / 1e9
            "downloaded ${total / 1_000_000} MB in ${"%.1f".format(sec)}s\n" +
                "throughput: ${"%.1f".format(total * 8 / sec / 1e6)} Mbps (via speed.cloudflare.com)"
        } catch (e: Exception) {
            "speedtest failed: ${e.message}"
        }
    }

    private fun ifconfig(): String {
        return try {
            val ifs = java.net.NetworkInterface.getNetworkInterfaces() ?: return "no interfaces"
            val out = mutableListOf<String>()
            while (ifs.hasMoreElements()) {
                val ni = ifs.nextElement()
                val addrs = ni.inetAddresses.toList()
                    .filter { !it.isLoopbackAddress }
                    .joinToString(", ") { it.hostAddress ?: "?" }
                val mac = ni.hardwareAddress?.joinToString(":") { "%02x".format(it) } ?: "-"
                val mtu = try { ni.mtu } catch (e: Exception) { -1 }
                out.add("${ni.name}: up=${ni.isUp} mtu=$mtu mac=$mac\n  $addrs")
            }
            if (out.isEmpty()) "no non-loopback interfaces" else out.joinToString("\n")
        } catch (e: Exception) {
            "ifconfig failed: ${e.message}"
        }
    }

    private fun hexIpLe(h: String): String = try {
        val v = h.toLong(16)
        "${v and 0xFF}.${(v shr 8) and 0xFF}.${(v shr 16) and 0xFF}.${(v shr 24) and 0xFF}"
    } catch (e: Exception) {
        h
    }

    private fun routes(): String {
        return try {
            val lines = File("/proc/net/route").readLines()
            if (lines.size < 2) return "no routes"
            lines.drop(1).take(20).joinToString("\n") {
                val p = it.split("\\s+".toRegex())
                if (p.size < 8) it
                else "${p[0]}  dst=${hexIpLe(p[1])}  gw=${hexIpLe(p[2])}  mask=${hexIpLe(p[7])}"
            }
        } catch (e: Exception) {
            "/proc/net/route unreadable: ${e.message}"
        }
    }

    private fun arp(): String {
        return try {
            val lines = File("/proc/net/arp").readLines()
            if (lines.size < 2) return "arp table empty"
            lines.drop(1).take(30).joinToString("\n") {
                val p = it.split("\\s+".toRegex())
                if (p.size < 6) it else "${p[0]}  ${p[3]}  dev=${p[5]}"
            }
        } catch (e: Exception) {
            "/proc/net/arp unreadable: ${e.message}"
        }
    }

    private fun wifisurvey(ctx: Context): String {
        if (!granted(ctx, Manifest.permission.ACCESS_FINE_LOCATION)) {
            return "needs location permission (Android requires it for Wi-Fi scans)"
        }
        return try {
            val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                ?: return "no Wi-Fi service"
            if (!wm.isWifiEnabled) return "Wi-Fi is off"
            val started = try {
                wm.startScan()
            } catch (e: SecurityException) {
                return "scan denied: ${e.message}"
            }
            val list = wm.scanResults.take(40)
            if (list.isEmpty()) return "no results (OS throttles scans — wait ~30s and retry)"
            val body = list.sortedBy { it.level }.joinToString("\n") {
                "${it.SSID.ifBlank { "<hidden>" }}  ${it.BSSID}  ${it.level} dBm  ${it.frequency} MHz"
            }
            body + if (!started) "\n(note: startScan throttled by OS, showing cached results)" else ""
        } catch (e: Exception) {
            "wifisurvey failed: ${e.message}"
        }
    }

    private fun btlescan(ctx: Context, args: List<String>): String {
        if (!granted(ctx, Manifest.permission.BLUETOOTH_SCAN)) {
            return "needs BLUETOOTH_SCAN permission"
        }
        val secs = (args.getOrNull(0)?.toIntOrNull() ?: 8).coerceIn(3, 30)
        return try {
            val bm = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = bm?.adapter ?: return "no Bluetooth adapter"
            if (!adapter.isEnabled) return "Bluetooth is off"
            val scanner = adapter.bluetoothLeScanner ?: return "BLE scanner unavailable"
            val seen = mutableMapOf<String, Int>()
            val cb = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    result.device?.address?.let { seen[it] = result.rssi }
                }
            }
            scanner.startScan(cb)
            CountDownLatch(1).await(secs.toLong(), TimeUnit.SECONDS)
            try {
                scanner.stopScan(cb)
            } catch (e: Exception) {
            }
            if (seen.isEmpty()) "no BLE devices seen in ${secs}s"
            else "seen ${seen.size} device(s):\n" + seen.entries.sortedBy { it.value }
                .joinToString("\n") { "${it.key}  ${it.value} dBm" }
        } catch (e: SecurityException) {
            "BLE scan denied: ${e.message}"
        } catch (e: Exception) {
            "btlescan failed: ${e.message}"
        }
    }

    private fun nfcinfo(ctx: Context): String {
        return try {
            val nfc = NfcAdapter.getDefaultAdapter(ctx) ?: return "no NFC hardware"
            "NFC present, enabled=${nfc.isEnabled}"
        } catch (e: Exception) {
            "nfcinfo failed: ${e.message}"
        }
    }

    private fun sntp(args: List<String>): String {
        val host = args.getOrNull(0) ?: "time.google.com"
        return try {
            val buf = ByteArray(48)
            buf[0] = 0x1B // LI=0 VN=3 Mode=3 (client)
            val addr = InetAddress.getByName(host)
            DatagramSocket().use { sock ->
                sock.soTimeout = 5000
                val t0 = System.currentTimeMillis()
                sock.send(DatagramPacket(buf, buf.size, addr, 123))
                val resp = ByteArray(48)
                sock.receive(DatagramPacket(resp, resp.size))
                val t3 = System.currentTimeMillis()
                fun u32(off: Int): Long =
                    ((resp[off].toLong() and 0xFF) shl 24) or
                        ((resp[off + 1].toLong() and 0xFF) shl 16) or
                        ((resp[off + 2].toLong() and 0xFF) shl 8) or
                        (resp[off + 3].toLong() and 0xFF)
                val txSec = u32(40)
                val txFrac = u32(44)
                val serverMs = (txSec - 2208988800L) * 1000 + txFrac * 1000 / 0x100000000L
                val offset = serverMs - (t0 + t3) / 2
                "server: $host\nserver time: ${fmtDate(serverMs)}\n" +
                    "offset: ${offset}ms (clock ${if (offset >= 0) "behind" else "ahead"} by ${kotlin.math.abs(offset)}ms)\n" +
                    "rtt: ${t3 - t0}ms"
            }
        } catch (e: Exception) {
            "sntp failed: ${e.message}"
        }
    }

    private fun tcping(args: List<String>): String {
        val host = args.getOrNull(0) ?: return "usage: tcping <host> <port> [tries]"
        val port = args.getOrNull(1)?.toIntOrNull() ?: return "usage: tcping <host> <port> [tries]"
        val tries = (args.getOrNull(2)?.toIntOrNull() ?: 3).coerceIn(1, 10)
        return try {
            val times = mutableListOf<Long>()
            var fails = 0
            repeat(tries) {
                try {
                    val t0 = System.nanoTime()
                    Socket().use { s ->
                        s.connect(InetSocketAddress(host, port), 5000)
                    }
                    times.add((System.nanoTime() - t0) / 1_000_000)
                } catch (e: Exception) {
                    fails++
                }
            }
            if (times.isEmpty()) "$host:$port — all $tries tries failed (refused/timeout)"
            else "$host:$port — min=${times.min()}ms avg=${times.average().toInt()}ms " +
                "max=${times.max()}ms fails=$fails/$tries"
        } catch (e: Exception) {
            "tcping failed: ${e.message}"
        }
    }

    // ---------------- sys ----------------

    private fun battery(ctx: Context): String {
        return try {
            val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                ?: return "battery info unavailable"
            val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val pct = if (level >= 0) level * 100 / scale else -1
            val temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) / 10.0
            val volt = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
            val health = when (i.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "good"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "OVERHEAT"
                BatteryManager.BATTERY_HEALTH_COLD -> "cold"
                BatteryManager.BATTERY_HEALTH_DEAD -> "dead"
                else -> "unknown"
            }
            val plugged = when (i.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)) {
                BatteryManager.BATTERY_PLUGGED_AC -> "AC"
                BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
                else -> "unplugged"
            }
            val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val cap = try {
                bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            } catch (e: Exception) { null }
            "level: $pct%${if (cap != null && cap >= 0) " (capacity api: $cap%)" else ""}\n" +
                "temp: ${temp}C  voltage: ${volt}mV\nhealth: $health  plugged: $plugged"
        } catch (e: Exception) {
            "battery failed: ${e.message}"
        }
    }

    private fun cpuinfo(): String {
        return try {
            val lines = File("/proc/cpuinfo").readLines()
            val model = lines.firstOrNull { it.startsWith("model name") }
                ?.substringAfter(":")?.trim()
                ?: lines.firstOrNull { it.startsWith("Hardware") }
                    ?.substringAfter(":")?.trim()
                ?: "unknown"
            val cores = lines.count { it.startsWith("processor") }
            val freq = try {
                File("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq")
                    .readText().trim().toLongOrNull()?.div(1000)
                    ?.let { "${it} MHz" } ?: "n/a"
            } catch (e: Exception) { "n/a" }
            "model: $model\ncores: $cores  cur freq: $freq"
        } catch (e: Exception) {
            "/proc/cpuinfo unreadable: ${e.message}"
        }
    }

    private fun meminfo(ctx: Context): String {
        return try {
            val map = File("/proc/meminfo").readLines()
                .mapNotNull {
                    val p = it.split("\\s+".toRegex())
                    if (p.size >= 2) p[0].trimEnd(':') to p[1].toLongOrNull() else null
                }.toMap()
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            "total: ${(map["MemTotal"] ?: 0) / 1024} MB\n" +
                "available: ${(map["MemAvailable"] ?: 0) / 1024} MB\n" +
                "app heap limit: ${am?.memoryClass ?: "?"} MB (large: ${am?.largeMemoryClass ?: "?"} MB)"
        } catch (e: Exception) {
            "meminfo failed: ${e.message}"
        }
    }

    private fun storage(ctx: Context): String {
        return try {
            fun df(label: String, f: File?): String {
                if (f == null) return "$label: n/a"
                val sf = StatFs(f.absolutePath)
                val freeGb = sf.availableBytes / 1073741824.0
                val totalGb = sf.totalBytes / 1073741824.0
                return "$label: ${"%.1f".format(freeGb)} / ${"%.1f".format(totalGb)} GB free"
            }
            df("internal", ctx.filesDir) + "\n" +
                df("cache", ctx.cacheDir) + "\n" +
                df("external", ctx.getExternalFilesDir(null))
        } catch (e: Exception) {
            "storage failed: ${e.message}"
        }
    }

    private fun sensors(ctx: Context): String {
        return try {
            val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
                ?: return "no sensor service"
            val list = sm.getSensorList(android.hardware.Sensor.TYPE_ALL)
            if (list.isEmpty()) return "no sensors reported"
            "sensors: ${list.size}\n" + list.take(50).joinToString("\n") {
                "${it.name} (${it.vendor}, max=${it.maximumRange})"
            }
        } catch (e: Exception) {
            "sensors failed: ${e.message}"
        }
    }

    private fun thermals(ctx: Context): String {
        return try {
            val sb = StringBuilder()
            if (Build.VERSION.SDK_INT >= 29) {
                val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
                val st = pm?.currentThermalStatus ?: -1
                val name = when (st) {
                    PowerManager.THERMAL_STATUS_NONE -> "none"
                    PowerManager.THERMAL_STATUS_LIGHT -> "light"
                    PowerManager.THERMAL_STATUS_MODERATE -> "moderate"
                    PowerManager.THERMAL_STATUS_SEVERE -> "severe"
                    PowerManager.THERMAL_STATUS_CRITICAL -> "CRITICAL"
                    PowerManager.THERMAL_STATUS_EMERGENCY -> "EMERGENCY"
                    PowerManager.THERMAL_STATUS_SHUTDOWN -> "SHUTDOWN"
                    else -> "unknown($st)"
                }
                sb.appendLine("thermal status: $name")
            } else {
                sb.appendLine("thermal status: API < 29, unavailable")
            }
            val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val temp = i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)?.div(10.0)
            sb.append("battery temp: ${temp ?: "?"}C")
            sb.toString()
        } catch (e: Exception) {
            "thermals failed: ${e.message}"
        }
    }

    private fun uptime(): String {
        val ms = SystemClock.elapsedRealtime()
        val s = ms / 1000
        return "uptime: ${s / 86400}d ${s % 86400 / 3600}h ${s % 3600 / 60}m ${s % 60}s\n" +
            "boot: ${fmtDate(System.currentTimeMillis() - ms)}"
    }

    private fun logcatme(): String {
        return try {
            val p = Runtime.getRuntime().exec(
                arrayOf("logcat", "-d", "--pid=${android.os.Process.myPid()}", "-t", "80")
            )
            val out = p.inputStream.bufferedReader().readText().take(4000)
            p.waitFor(5, TimeUnit.SECONDS)
            if (out.isBlank()) "(no log lines for this process)" else out
        } catch (e: Exception) {
            "logcat failed: ${e.message}"
        }
    }

    private fun packages(ctx: Context, args: List<String>): String {
        val filter = args.getOrNull(0) ?: ""
        return try {
            val pm = ctx.packageManager
            val list = pm.getInstalledPackages(0)
                .filter { filter.isBlank() || it.packageName.contains(filter, ignoreCase = true) }
                .sortedBy { it.packageName }
            val shown = list.take(200)
            "installed: ${list.size} (showing ${shown.size})\n" + shown.joinToString("\n") {
                val vn = try { it.versionName } catch (e: Exception) { "?" }
                "${it.packageName}  v$vn"
            }
        } catch (e: Exception) {
            "packages failed: ${e.message}"
        }
    }

    private fun appinfo(ctx: Context, args: List<String>): String {
        val pkg = args.getOrNull(0) ?: return "usage: appinfo <package>"
        return try {
            val pm = ctx.packageManager
            val pi = pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
            val apkLen = try {
                File(pi.applicationInfo?.sourceDir ?: "").length()
            } catch (e: Exception) { -1 }
            "package: ${pi.packageName}\n" +
                "version: ${try { pi.versionName } catch (e: Exception) { "?" }} " +
                "(${try { pi.versionCode } catch (e: Exception) { "?" }})\n" +
                "installed: ${fmtDate(pi.firstInstallTime)}\n" +
                "updated: ${fmtDate(pi.lastUpdateTime)}\n" +
                "apk: ${if (apkLen >= 0) "%.1f MB".format(apkLen / 1048576.0) else "?"}\n" +
                "declared permissions: ${pi.requestedPermissions?.size ?: 0}"
        } catch (e: PackageManager.NameNotFoundException) {
            "no such package: $pkg"
        } catch (e: Exception) {
            "appinfo failed: ${e.message}"
        }
    }

    private fun perms(ctx: Context): String {
        val list = listOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.ANSWER_PHONE_CALLS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.CAMERA,
            Manifest.permission.SYSTEM_ALERT_WINDOW
        )
        return list.joinToString("\n") { p ->
            val state = if (p == Manifest.permission.SYSTEM_ALERT_WINDOW) {
                if (Settings.canDrawOverlays(ctx)) "GRANTED" else "DENIED"
            } else if (granted(ctx, p)) "GRANTED" else "DENIED"
            "${p.substringAfterLast(".")}: $state"
        }
    }

    private fun deviceinfo(): String {
        return "manufacturer: ${Build.MANUFACTURER}\n" +
            "model: ${Build.MODEL}\n" +
            "device: ${Build.DEVICE}  board: ${Build.BOARD}\n" +
            "android: ${Build.VERSION.RELEASE} (sdk ${Build.VERSION.SDK_INT})\n" +
            "fingerprint: ${Build.FINGERPRINT}\n" +
            "abis: ${Build.SUPPORTED_ABIS.joinToString(", ")}"
    }

    private fun displayinfo(ctx: Context): String {
        return try {
            val dm = ctx.resources.displayMetrics
            val rr = try {
                val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager
                @Suppress("DEPRECATION")
                wm?.defaultDisplay?.refreshRate?.let { "${it}Hz" }
            } catch (e: Exception) { null }
            "${dm.widthPixels}x${dm.heightPixels} px  density=${dm.density}  dpi=${dm.densityDpi}" +
                (if (rr != null) "  refresh=$rr" else "")
        } catch (e: Exception) {
            "displayinfo failed: ${e.message}"
        }
    }

    private fun audioinfo(ctx: Context): String {
        return try {
            if (Build.VERSION.SDK_INT < 23) return "needs API 23+"
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                ?: return "no audio service"
            val outs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val ins = am.getDevices(AudioManager.GET_DEVICES_INPUTS)
            "outputs:\n" + outs.joinToString("\n") { "  ${it.productName} (type=${it.type})" } +
                "\ninputs:\n" + ins.joinToString("\n") { "  ${it.productName} (type=${it.type})" }
        } catch (e: Exception) {
            "audioinfo failed: ${e.message}"
        }
    }

    private fun flashlight(ctx: Context, args: List<String>): String {
        val on = when (args.getOrNull(0)) {
            "on" -> true
            "off" -> false
            else -> return "usage: flashlight on|off"
        }
        if (!granted(ctx, Manifest.permission.CAMERA)) {
            return "needs CAMERA permission — grant it in system settings first"
        }
        return try {
            val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                ?: return "no camera service"
            val id = cm.cameraIdList.firstOrNull() ?: return "no camera"
            cm.setTorchMode(id, on)
            "flashlight ${if (on) "ON" else "OFF"}"
        } catch (e: SecurityException) {
            "flashlight denied: ${e.message}"
        } catch (e: Exception) {
            "flashlight failed: ${e.message}"
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null

    private fun keepawake(ctx: Context, args: List<String>): String {
        return when (args.getOrNull(0)) {
            "on" -> try {
                if (wakeLock?.isHeld == true) return "wake lock already held"
                val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
                    ?: return "no power service"
                @Suppress("DEPRECATION")
                val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GKK:ops")
                wl.acquire(10 * 60 * 1000L) // 10 min safety cap
                wakeLock = wl
                "wake lock acquired (partial, auto-releases in 10 min)"
            } catch (e: Exception) {
                "keepawake failed: ${e.message}"
            }
            "off" -> try {
                wakeLock?.release()
                wakeLock = null
                "wake lock released"
            } catch (e: Exception) {
                "keepawake failed: ${e.message}"
            }
            else -> "usage: keepawake on|off"
        }
    }

    // ---------------- sec ----------------

    private fun sha256Hex(data: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(data)
            .joinToString("") { "%02x".format(it) }

    private fun hash(args: List<String>): String {
        val text = args.joinToString(" ")
        if (text.isBlank()) return "usage: hash <text>"
        return "sha256: " + sha256Hex(text.toByteArray(Charsets.UTF_8))
    }

    private fun hashfile(args: List<String>): String {
        val path = args.getOrNull(0) ?: return "usage: hashfile <path>"
        return try {
            val f = File(path)
            if (!f.isFile) return "not a file: $path"
            if (f.length() > 32 * 1024 * 1024) return "file too large (max 32MB)"
            val md = MessageDigest.getInstance("SHA-256")
            f.inputStream().use { ins ->
                val buf = ByteArray(65536)
                var n: Int
                while (ins.read(buf).also { n = it } != -1) md.update(buf, 0, n)
            }
            "sha256(${f.name}): " + md.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            "hashfile failed: ${e.message}"
        }
    }

    private fun rand(args: List<String>): String {
        val n = (args.getOrNull(0)?.toIntOrNull() ?: 32).coerceIn(1, 1024)
        val b = ByteArray(n).also { SecureRandom().nextBytes(it) }
        return b.joinToString("") { "%02x".format(it) }
    }

    private fun passgen(args: List<String>): String {
        val len = (args.getOrNull(0)?.toIntOrNull() ?: 20).coerceIn(8, 128)
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#%^&*"
        val r = SecureRandom()
        return (1..len).map { chars[r.nextInt(chars.length)] }.joinToString("")
    }

    private fun keyinfo(): String {
        return try {
            val kp = DeviceKeys.getOrCreateIdentity()
            "algorithm: EC P-256\n" +
                "keystore-backed: ${DeviceKeys.isKeystoreBacked}\n" +
                "fingerprint: ${EnvelopeCrypto.deviceIdHex(kp.public)}\n" +
                "(private key never leaves the keystore / memory — not shown)"
        } catch (e: Exception) {
            "keyinfo failed: ${e.message}"
        }
    }

    private fun trustcheck(): String {
        return try {
            val devs = DispatchManager.registry().all()
            if (devs.isEmpty()) return "no paired devices"
            devs.joinToString("\n") { d ->
                val valid = try {
                    DeviceKeys.isValidPeerKey(d.publicKeyB64)
                } catch (e: Exception) { false }
                "${d.name} [${d.idHex.take(8)}…] trust=${d.trustLevel} " +
                    if (valid) "key OK" else "KEY INVALID"
            }
        } catch (e: Exception) {
            "trustcheck failed: ${e.message}"
        }
    }

    private fun secscan(ctx: Context): String {
        val sb = StringBuilder()
        try {
            val km = ctx.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            sb.appendLine("screen lock: ${if (km?.isDeviceSecure == true) "ON" else "OFF / unknown"}")
        } catch (e: Exception) {
            sb.appendLine("screen lock: unknown")
        }
        fun glob(k: String): String = try {
            Settings.Global.getInt(ctx.contentResolver, k).toString()
        } catch (e: Exception) {
            "?"
        }
        sb.appendLine("adb enabled: ${glob(Settings.Global.ADB_ENABLED)} (1=yes)")
        sb.appendLine("dev options: ${glob(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED)} (1=yes)")
        try {
            val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            val enc = when (dpm?.storageEncryptionStatus) {
                DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE -> "active"
                DevicePolicyManager.ENCRYPTION_STATUS_INACTIVE -> "INACTIVE"
                else -> "unknown"
            }
            sb.appendLine("storage encryption: $enc")
        } catch (e: Exception) {
            sb.appendLine("storage encryption: unknown")
        }
        return sb.toString().trim()
    }

    private fun aesbench(args: List<String>): String {
        val mb = (args.getOrNull(0)?.toIntOrNull() ?: 16).coerceIn(1, 64)
        return try {
            val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val data = ByteArray(mb * 1024 * 1024).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val ks = SecretKeySpec(key, "AES")
            val t0 = System.nanoTime()
            var off = 0
            val chunk = 1024 * 1024
            while (off < data.size) {
                val len = minOf(chunk, data.size - off)
                val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
                cipher.init(Cipher.ENCRYPT_MODE, ks, GCMParameterSpec(128, iv))
                cipher.doFinal(data, off, len)
                off += len
            }
            val ms = (System.nanoTime() - t0) / 1_000_000
            "encrypted $mb MB in ${ms}ms\nthroughput: ${mb * 1000 / maxOf(ms, 1)} MB/s (AES-256-GCM)"
        } catch (e: Exception) {
            "aesbench failed: ${e.message}"
        }
    }

    // ---------------- comms ----------------

    private fun smsinfo(ctx: Context): String {
        return try {
            val sb = StringBuilder()
            try {
                val sm = ctx.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                    as? SubscriptionManager
                val subs = sm?.activeSubscriptionInfoList
                sb.appendLine("active subscriptions: ${subs?.size ?: "?"}")
                subs?.forEach {
                    sb.appendLine("  sub=${it.subscriptionId} carrier=${it.carrierName}")
                }
            } catch (e: SecurityException) {
                sb.appendLine("subscriptions: needs READ_PHONE_STATE")
            }
            sb.append("data-SMS port: 19841 (built-in)")
            sb.toString()
        } catch (e: Exception) {
            "smsinfo failed: ${e.message}"
        }
    }

    private fun siminfo(ctx: Context): String {
        return try {
            val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                ?: return "no telephony service"
            val state = when (val s = try { tm.simState } catch (e: SecurityException) { -1 }) {
                TelephonyManager.SIM_STATE_READY -> "READY"
                TelephonyManager.SIM_STATE_ABSENT -> "ABSENT"
                TelephonyManager.SIM_STATE_PIN_REQUIRED -> "PIN_REQUIRED"
                TelephonyManager.SIM_STATE_PUK_REQUIRED -> "PUK_REQUIRED"
                TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "NETWORK_LOCKED"
                TelephonyManager.SIM_STATE_UNKNOWN -> "UNKNOWN"
                else -> "state=$s"
            }
            val carrier = try {
                if (Build.VERSION.SDK_INT >= 28) tm.simCarrierIdName ?: tm.simOperatorName
                else tm.simOperatorName
            } catch (e: SecurityException) { "?" }
            val mccmnc = try { tm.simOperator } catch (e: SecurityException) { "?" }
            val iso = try { tm.simCountryIso } catch (e: SecurityException) { "?" }
            "sim: $state\ncarrier: $carrier\nmcc/mnc: ${mccmnc ?: "?"}\ncountry: ${iso ?: "?"}"
        } catch (e: Exception) {
            "siminfo failed: ${e.message}"
        }
    }

    private fun signal(ctx: Context): String {
        return try {
            if (Build.VERSION.SDK_INT < 29) return "needs API 29+"
            val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                ?: return "no telephony service"
            val ss = try {
                tm.signalStrength
            } catch (e: SecurityException) {
                return "needs READ_PHONE_STATE permission"
            } ?: return "signal strength unavailable"
            "level: ${ss.level}/4\n" +
                ss.cellSignalStrengths.joinToString("\n") { "  ${it.dbm} dBm" }
        } catch (e: Exception) {
            "signal failed: ${e.message}"
        }
    }

    private fun cellinfo(ctx: Context): String {
        if (!granted(ctx, Manifest.permission.ACCESS_FINE_LOCATION)) {
            return "needs location permission (Android requires it for cell info)"
        }
        return try {
            val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                ?: return "no telephony service"
            val cells = try {
                tm.allCellInfo
            } catch (e: SecurityException) {
                return "cell info denied: ${e.message}"
            } ?: return "no cell info (radio off?)"
            "cells: ${cells.size}\n" + cells.take(10).joinToString("\n") {
                it.toString().take(200)
            }
        } catch (e: Exception) {
            "cellinfo failed: ${e.message}"
        }
    }

    private fun datausage(): String {
        fun mb(b: Long): String =
            if (b == TrafficStats.UNSUPPORTED.toLong()) "unsupported"
            else "%.1f MB".format(b / 1048576.0)
        return "mobile rx=${mb(TrafficStats.getMobileRxBytes())} " +
            "tx=${mb(TrafficStats.getMobileTxBytes())}\n" +
            "total  rx=${mb(TrafficStats.getTotalRxBytes())} " +
            "tx=${mb(TrafficStats.getTotalTxBytes())}"
    }

    private fun phonestate(ctx: Context): String {
        return try {
            val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                ?: return "no telephony service"
            val call = when (try { tm.callState } catch (e: SecurityException) { -1 }) {
                TelephonyManager.CALL_STATE_IDLE -> "idle"
                TelephonyManager.CALL_STATE_RINGING -> "ringing"
                TelephonyManager.CALL_STATE_OFFHOOK -> "offhook"
                else -> "?"
            }
            val dataState = when (try { tm.dataState } catch (e: SecurityException) { -1 }) {
                TelephonyManager.DATA_CONNECTED -> "connected"
                TelephonyManager.DATA_CONNECTING -> "connecting"
                TelephonyManager.DATA_DISCONNECTED -> "disconnected"
                TelephonyManager.DATA_SUSPENDED -> "suspended"
                else -> "?"
            }
            "call: $call\ndata: $dataState"
        } catch (e: Exception) {
            "phonestate failed: ${e.message}"
        }
    }

    private fun b64e(args: List<String>): String {
        val text = args.joinToString(" ")
        if (text.isBlank()) return "usage: b64e <text>"
        return Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    private fun b64d(args: List<String>): String {
        val b64 = args.getOrNull(0) ?: return "usage: b64d <base64>"
        return try {
            Base64.decode(b64, Base64.DEFAULT).toString(Charsets.UTF_8)
        } catch (e: IllegalArgumentException) {
            "invalid base64"
        }
    }

    private fun hexdump(args: List<String>): String {
        val text = args.joinToString(" ")
        if (text.isBlank()) return "usage: hexdump <text>"
        val bytes = text.toByteArray(Charsets.UTF_8)
        val sb = StringBuilder()
        var off = 0
        while (off < bytes.size) {
            val row = bytes.copyOfRange(off, minOf(off + 16, bytes.size))
            sb.append("%04x  ".format(off))
            sb.append(row.joinToString(" ") { "%02x".format(it) })
            sb.append(" ".repeat((16 - row.size) * 3))
            sb.append(" |")
            sb.append(row.map { if (it.toInt() in 32..126) it.toInt().toChar() else '.' }.joinToString(""))
            sb.appendLine("|")
            off += 16
        }
        return sb.toString().trim()
    }

    private fun time(): String {
        val now = System.currentTimeMillis()
        val utc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(now))
        return "epoch ms: $now\nutc: $utc\ntz: ${TimeZone.getDefault().id}\nuptime ms: ${SystemClock.elapsedRealtime()}"
    }
}
