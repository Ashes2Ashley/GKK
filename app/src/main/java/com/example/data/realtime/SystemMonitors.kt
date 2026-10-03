package com.example.data.realtime

import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationListener
import android.location.LocationManager
import android.net.TrafficStats
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Looper
import android.os.Process
import android.telephony.TelephonyManager
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer

/** Live battery level / temperature / voltage / charge state. */
object BatteryMonitor : BaseMonitor("battery") {
    override fun start(ctx: Context) = launchLoop(10_000) {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return@launchLoop emit("unknown", "no sticky battery intent", MonitorStatus.UNAVAILABLE)
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val pct = if (scale > 0) level * 100 / scale else -1
        val temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) / 10.0
        val volt = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
        val st = when (i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
            BatteryManager.BATTERY_STATUS_FULL -> "full"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "discharging"
            else -> "unknown"
        }
        emit("$pct%", "temp ${temp}C · ${volt}mV · $st")
    }
}

/** Live accelerometer / light / proximity values. */
object SensorMonitor : BaseMonitor("sensors") {
    private var sm: SensorManager? = null
    private var listener: SensorEventListener? = null
    private val latest = mutableMapOf<String, String>()

    override fun start(ctx: Context) {
        stop()
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            ?: return emit("unavailable", "no SensorManager", MonitorStatus.UNAVAILABLE)
        this.sm = sm
        var attached = 0
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                latest[e.sensor.stringType.substringAfterLast(".")] =
                    e.values.joinToString(",") { "%.2f".format(it) }
                emit(
                    "${latest.size} sensors live",
                    latest.map { (k, v) -> "$k=$v" }.joinToString(" · ")
                )
            }

            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        for (t in listOf(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_LIGHT, Sensor.TYPE_PROXIMITY)) {
            val s = sm.getDefaultSensor(t) ?: continue
            try {
                sm.registerListener(l, s, SensorManager.SENSOR_DELAY_NORMAL, android.os.Handler(Looper.getMainLooper()))
                attached++
            } catch (e: Exception) {
                // keep going with the rest
            }
        }
        listener = l
        if (attached == 0) emit("no sensors", "accel/light/prox absent", MonitorStatus.UNAVAILABLE)
        else emit("listening…", "$attached sensor(s) attached")
    }

    override fun onStop() {
        try {
            sm?.unregisterListener(listener)
        } catch (e: Exception) {
        }
        sm = null
        listener = null
    }
}

/** Live GPS fix. Honest when permission is missing or GPS is off. */
object GpsMonitor : BaseMonitor("gps") {
    private var lm: LocationManager? = null
    private var listener: LocationListener? = null

    override fun start(ctx: Context) {
        stop()
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return emit("unavailable", "no LocationManager", MonitorStatus.UNAVAILABLE)
        this.lm = lm
        try {
            if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                return emit("gps off", "enable GPS for live fixes", MonitorStatus.UNAVAILABLE)
            }
            val l = LocationListener { loc ->
                emit(
                    "%.6f, %.6f".format(loc.latitude, loc.longitude),
                    "speed ${"%.1f".format(loc.speed)} m/s · acc ${"%.0f".format(loc.accuracy)} m"
                )
            }
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000L, 2f, l, Looper.getMainLooper())
            listener = l
            val last = try {
                lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            } catch (e: SecurityException) {
                null
            }
            if (last != null) {
                emit(
                    "%.6f, %.6f (last known)".format(last.latitude, last.longitude),
                    "waiting for live fix…"
                )
            } else {
                emit("waiting…", "for first GPS fix")
            }
        } catch (e: SecurityException) {
            emit("permission needed", "grant LOCATION for live GPS", MonitorStatus.UNAVAILABLE)
        }
    }

    override fun onStop() {
        try {
            listener?.let { lm?.removeUpdates(it) }
        } catch (e: Exception) {
        }
        lm = null
        listener = null
    }
}

/** Live per-app network throughput (this app's UID), via TrafficStats. */
object TrafficMonitor : BaseMonitor("traffic") {
    private var lastTx = -1L
    private var lastRx = -1L
    private var lastT = 0L

    override fun start(ctx: Context) = launchLoop(2000) {
        val uid = Process.myUid()
        val tx = TrafficStats.getUidTxBytes(uid)
        val rx = TrafficStats.getUidRxBytes(uid)
        val now = System.currentTimeMillis()
        if (lastT > 0 && tx >= 0 && rx >= 0) {
            val dt = (now - lastT) / 1000.0
            emit(
                "↑ ${rate(tx - lastTx, dt)} ↓ ${rate(rx - lastRx, dt)}",
                "total tx ${human(tx)} · rx ${human(rx)} (this app)"
            )
        } else {
            emit("sampling…", "collecting first sample")
        }
        lastTx = tx
        lastRx = rx
        lastT = now
    }

    private fun rate(dBytes: Long, dt: Double): String {
        val bps = if (dt > 0) (dBytes / dt).toLong() else 0L
        return human(bps) + "/s"
    }

    private fun human(b: Long): String = when {
        b < 0 -> "n/a"
        b < 1024 -> "$b B"
        b < 1048576 -> "%.1f KB".format(b / 1024.0)
        else -> "%.2f MB".format(b / 1048576.0)
    }
}

/** Live Wi-Fi signal: RSSI, link speed, SSID, BSSID. */
object WifiMonitor : BaseMonitor("wifi") {
    override fun start(ctx: Context) = launchLoop(3000) {
        val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return@launchLoop emit("unavailable", "no WifiManager", MonitorStatus.UNAVAILABLE)
        if (!wm.isWifiEnabled) return@launchLoop emit("wifi off", "", MonitorStatus.UNAVAILABLE)
        val ci = try {
            @Suppress("DEPRECATION")
            wm.connectionInfo
        } catch (e: Exception) {
            return@launchLoop emit("error", e.message ?: "?", MonitorStatus.ERROR)
        }
        if (ci.networkId == -1) return@launchLoop emit("not connected", "", MonitorStatus.OK)
        emit(
            "${ci.rssi} dBm",
            "ssid ${ci.ssid} · ${ci.linkSpeed} Mbps · ${ci.bssid}"
        )
    }
}

/** Live cellular signal: dBm, operator, radio type. */
object CellMonitor : BaseMonitor("cell") {
    override fun start(ctx: Context) = launchLoop(3000) {
        val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return@launchLoop emit("unavailable", "no TelephonyManager", MonitorStatus.UNAVAILABLE)
        try {
            val ss = tm.signalStrength
                ?: return@launchLoop emit("no signal info", "", MonitorStatus.UNAVAILABLE)
            val css = ss.cellSignalStrengths.firstOrNull()
            val dbm = css?.dbm
            val netType = try {
                tm.dataNetworkType
            } catch (e: Exception) {
                -1
            }
            emit(
                if (dbm != null) "$dbm dBm" else "level ${ss.level}/4",
                "operator ${tm.networkOperatorName.ifEmpty { "?" }} · ${netName(netType)}"
            )
        } catch (e: SecurityException) {
            emit("permission needed", "grant PHONE_STATE for live cell data", MonitorStatus.UNAVAILABLE)
        }
    }

    private fun netName(t: Int): String = when (t) {
        TelephonyManager.NETWORK_TYPE_NR -> "5G NR"
        TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
        TelephonyManager.NETWORK_TYPE_UMTS,
        TelephonyManager.NETWORK_TYPE_HSDPA,
        TelephonyManager.NETWORK_TYPE_HSPA,
        TelephonyManager.NETWORK_TYPE_HSPAP -> "3G"
        TelephonyManager.NETWORK_TYPE_EDGE,
        TelephonyManager.NETWORK_TYPE_GPRS -> "2G"
        else -> "?"
    }
}

/** Live Bluetooth LE scan: nearby devices as they appear. */
object BleMonitor : BaseMonitor("ble") {
    private var scanner: android.bluetooth.le.BluetoothLeScanner? = null
    private var callback: ScanCallback? = null
    private val seen = mutableMapOf<String, Int>()

    override fun start(ctx: Context) {
        stop()
        try {
            val bm = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = bm?.adapter
            if (adapter == null || !adapter.isEnabled) {
                return emit("bluetooth off", "enable Bluetooth to scan", MonitorStatus.UNAVAILABLE)
            }
            val scanner = adapter.bluetoothLeScanner
                ?: return emit("no LE scanner", "", MonitorStatus.UNAVAILABLE)
            this.scanner = scanner
            seen.clear()
            val cb = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, r: ScanResult) {
                    val addr = try {
                        r.device.address
                    } catch (e: SecurityException) {
                        return
                    } ?: return
                    seen[addr] = r.rssi
                    val best = seen.maxByOrNull { it.value }
                    emit(
                        "${seen.size} device(s)",
                        "strongest: ${best?.key} @ ${best?.value}dBm"
                    )
                }

                override fun onScanFailed(errorCode: Int) {
                    emit("scan failed", "code $errorCode", MonitorStatus.ERROR)
                }
            }
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            scanner.startScan(null, settings, cb)
            callback = cb
            emit("scanning…", "BLE scan active")
        } catch (e: SecurityException) {
            emit("permission needed", "grant BLUETOOTH_SCAN (+ location)", MonitorStatus.UNAVAILABLE)
        } catch (e: Exception) {
            emit("error", e.message ?: "?", MonitorStatus.ERROR)
        }
    }

    override fun onStop() {
        try {
            callback?.let { scanner?.stopScan(it) }
        } catch (e: Exception) {
        }
        scanner = null
        callback = null
    }
}

/** Live clock offset vs pool.ntp.org (real SNTP over UDP). */
object NtpMonitor : BaseMonitor("ntp") {
    override fun start(ctx: Context) = launchLoop(60_000) {
        val offset = sntpOffset("pool.ntp.org")
        if (offset == null) {
            emit("unreachable", "pool.ntp.org:123 timeout", MonitorStatus.ERROR)
        } else {
            emit(
                (if (offset >= 0) "+" else "") + "${offset}ms",
                "clock offset vs pool.ntp.org"
            )
        }
    }

    fun sntpOffset(host: String, timeoutMs: Int = 5000): Long? {
        return try {
            val buf = ByteArray(48)
            buf[0] = 0x1B.toByte() // LI=0, VN=4, Mode=3 (client)
            val t1 = System.currentTimeMillis()
            ByteBuffer.wrap(buf, 40, 8).putLong(toNtp(t1))
            DatagramSocket().use { sock ->
                sock.soTimeout = timeoutMs
                val addr = InetAddress.getByName(host)
                sock.send(DatagramPacket(buf, buf.size, addr, 123))
                val resp = ByteArray(48)
                sock.receive(DatagramPacket(resp, resp.size))
                val t4 = System.currentTimeMillis()
                val b = ByteBuffer.wrap(resp)
                val rx = b.getLong(32)
                val tx = b.getLong(40)
                val t2 = fromNtp(rx)
                val t3 = fromNtp(tx)
                ((t2 - t1) + (t3 - t4)) / 2
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun toNtp(ms: Long): Long =
        ((ms / 1000L + 2208988800L) shl 32) or ((ms % 1000L) * 4294967296L / 1000L)

    private fun fromNtp(ntp: Long): Long {
        val secs = (ntp ushr 32) - 2208988800L
        val frac = (ntp and 0xFFFFFFFFL) * 1000L / 4294967296L
        return secs * 1000L + frac
    }
}
