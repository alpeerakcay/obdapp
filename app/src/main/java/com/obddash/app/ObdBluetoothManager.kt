package com.obddash.app

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlin.concurrent.thread

/**
 * Klasik (SPP) Bluetooth üzerinden ELM327'ye bağlanır, standart OBD2 PID'lerini
 * okur ve sonucu JSON olarak WebView içindeki JS'e aktarır.
 *
 * ÖNEMLİ: ELM327'nin telefonun Bluetooth ayarlarından önceden eşleştirilmiş
 * (paired) olması gerekir. Bu sınıf sadece zaten eşleşmiş cihazlara bağlanır.
 */
class ObdBluetoothManager(private val webView: WebView) {

    companion object {
        // Standart Seri Port Profili (SPP) UUID'si - tüm ELM327 klonlarında aynıdır
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private var socket: BluetoothSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null

    @Volatile private var running = false
    private val mainHandler = Handler(Looper.getMainLooper())

    // ---------- Eşleşmiş cihazları listele ----------
    @SuppressLint("MissingPermission")
    fun getPairedDevicesJson(): String {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        val arr = JSONArray()
        if (adapter == null) return arr.toString()
        try {
            val bonded = adapter.bondedDevices ?: emptySet()
            for (d: BluetoothDevice in bonded) {
                val o = JSONObject()
                o.put("name", d.name ?: "Bilinmeyen cihaz")
                o.put("address", d.address)
                arr.put(o)
            }
        } catch (e: SecurityException) {
            // izin verilmemiş, boş liste dön
        }
        return arr.toString()
    }

    // ---------- Bağlan ----------
    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        disconnect()
        thread(name = "obd-connect") {
            try {
                postStatus(false, "Bağlanıyor...")
                val adapter = BluetoothAdapter.getDefaultAdapter()
                val device = adapter.getRemoteDevice(address)
                adapter.cancelDiscovery()

                val sock = device.createRfcommSocketToServiceRecord(SPP_UUID)
                try {
                    sock.connect()
                } catch (e: Exception) {
                    // Bazı klonlarda standart connect() başarısız olabiliyor,
                    // yansıma (reflection) ile kanal 1 üzerinden yedek bağlantı dene
                    val fallback = fallbackConnect(device)
                    if (fallback == null) throw e
                    socket = fallback
                    input = fallback.inputStream
                    output = fallback.outputStream
                    initAndPoll(device.name ?: address)
                    return@thread
                }
                socket = sock
                input = sock.inputStream
                output = sock.outputStream
                initAndPoll(device.name ?: address)
            } catch (e: Exception) {
                postStatus(false, "Bağlantı hatası: ${e.message}")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun fallbackConnect(device: BluetoothDevice): BluetoothSocket? {
        return try {
            val m = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
            val sock = m.invoke(device, 1) as BluetoothSocket
            sock.connect()
            sock
        } catch (e: Exception) {
            null
        }
    }

    private fun initAndPoll(deviceName: String) {
        try {
            sendCommand("ATZ", 2000)
            sendCommand("ATE0")
            sendCommand("ATL0")
            sendCommand("ATS0")
            sendCommand("ATH0")
            sendCommand("ATSP0")
        } catch (e: Exception) {
            postStatus(false, "Başlatma hatası: ${e.message}")
            return
        }
        postStatus(true, "Canlı: $deviceName")
        running = true
        pollLoop()
    }

    fun disconnect() {
        running = false
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        input = null
        output = null
    }

    // ---------- ELM327 komut gönder / yanıt oku ----------
    private fun sendCommand(cmd: String, timeoutMs: Long = 3000): String {
        val out = output ?: throw IllegalStateException("bağlı değil")
        val inp = input ?: throw IllegalStateException("bağlı değil")
        out.write((cmd + "\r").toByteArray())
        out.flush()

        val sb = StringBuilder()
        val start = System.currentTimeMillis()
        val buf = ByteArray(256)
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (inp.available() > 0) {
                val n = inp.read(buf)
                if (n > 0) sb.append(String(buf, 0, n))
                if (sb.contains(">")) break
            } else {
                Thread.sleep(20)
            }
        }
        return sb.toString()
    }

    private fun hex(b: String) = b.trim().toInt(16)

    private fun extractBytes(resp: String): List<String> {
        return resp.replace("\r", " ").replace("\n", " ").replace(">", " ")
            .trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    }

    private fun queryPid(pid: String, header: String, offset: Int, transform: (Int) -> Double): Double? {
        return try {
            val resp = sendCommand(pid)
            val bytes = extractBytes(resp)
            val i = bytes.indexOf("41")
            if (i < 0 || bytes.size <= i + offset) return null
            transform(hex(bytes[i + offset]))
        } catch (e: Exception) {
            null
        }
    }

    // ---------- Ana okuma döngüsü ----------
    private fun pollLoop() {
        var loop = 0
        while (running) {
            try {
                val rpmResp = sendCommand("01 0C")
                val rpmBytes = extractBytes(rpmResp)
                val ri = rpmBytes.indexOf("41")
                val rpm = if (ri >= 0 && rpmBytes.size > ri + 3)
                    ((hex(rpmBytes[ri + 2]) * 256) + hex(rpmBytes[ri + 3])) / 4.0 else null

                val speed = queryPid("01 0D", "410D", 2) { it.toDouble() }
                val coolant = queryPid("01 05", "4105", 2) { it - 40.0 }
                val throttle = queryPid("01 11", "4111", 2) { it * 100.0 / 255.0 }
                val load = queryPid("01 04", "4104", 2) { it * 100.0 / 255.0 }
                val intake = queryPid("01 0F", "410F", 2) { it - 40.0 }
                val fuel = queryPid("01 2F", "412F", 2) { it * 100.0 / 255.0 }

                val voltResp = sendCommand("ATRV")
                val voltMatch = Regex("([0-9]+\\.[0-9]+)V").find(voltResp)
                val voltage = voltMatch?.groupValues?.get(1)?.toDoubleOrNull()

                val data = JSONObject()
                data.put("rpm", rpm ?: JSONObject.NULL)
                data.put("speed", speed ?: JSONObject.NULL)
                data.put("coolant_temp", coolant ?: JSONObject.NULL)
                data.put("throttle", throttle ?: JSONObject.NULL)
                data.put("engine_load", load ?: JSONObject.NULL)
                data.put("intake_temp", intake ?: JSONObject.NULL)
                data.put("voltage", voltage ?: JSONObject.NULL)
                data.put("fuel_level", fuel ?: JSONObject.NULL)

                if (loop % 10 == 0) {
                    data.put("dtcs", readDtcs())
                }
                loop++

                postData(data.toString())
            } catch (e: Exception) {
                postStatus(false, "Okuma hatası: ${e.message}")
            }
            Thread.sleep(700)
        }
        postStatus(false, "Bağlantı kesildi")
    }

    private fun readDtcs(): JSONArray {
        val arr = JSONArray()
        try {
            val resp = sendCommand("03")
            val bytes = extractBytes(resp)
            val i = bytes.indexOf("43")
            if (i < 0) return arr
            var j = i + 1
            val letters = arrayOf("P", "C", "B", "U")
            while (j + 1 < bytes.size) {
                val b1 = hex(bytes[j]); val b2raw = bytes[j + 1]
                if (b1 != 0 || hex(b2raw) != 0) {
                    val letter = letters[(b1 shr 6) and 0x03]
                    val code = letter + ((b1 shr 4) and 0x03) + (b1 and 0x0F).toString(16) + b2raw
                    val o = JSONObject()
                    o.put("code", code.uppercase())
                    o.put("desc", "Açıklama için kodu OBD veritabanında arayın")
                    arr.put(o)
                }
                j += 2
            }
        } catch (_: Exception) {}
        return arr
    }

    fun clearDtc() {
        thread(name = "obd-clear-dtc") {
            try {
                sendCommand("04")
                postStatus(true, "Arıza kodları temizlendi")
            } catch (e: Exception) {
                postStatus(false, "Temizleme hatası: ${e.message}")
            }
        }
    }

    // ---------- WebView'e veri gönder ----------
    private fun postData(json: String) {
        mainHandler.post {
            webView.evaluateJavascript("window.onData(JSON.parse(${JSONObject.quote(json)}));", null)
        }
    }

    private fun postStatus(live: Boolean, text: String) {
        mainHandler.post {
            val safe = JSONObject.quote(text)
            webView.evaluateJavascript("window.onStatus($live, $safe);", null)
        }
    }
}
