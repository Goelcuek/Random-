package com.goelcuek.irremote

import android.content.Context
import android.hardware.ConsumerIrManager
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

/** One IR signal: carrier frequency + alternating on/off durations in µs. */
class IrSignal(val freq: Int, val pattern: IntArray)

/** A full code set (one physical remote model) for a brand. */
class CodeSet(val model: String, val keys: Map<String, IrSignal>)

/** A device the user has set up: a TV (code-set based) or an AC (stateful). */
data class SavedRemote(
    val name: String,
    val brand: String,
    val setIndex: Int = 0,
    val kind: String = KIND_TV,
    val ac: AcState = AcState(),
) {
    val isAc get() = kind == KIND_AC

    companion object {
        const val KIND_TV = "tv"
        const val KIND_AC = "ac"
    }
}

/** Loads the bundled TV code database (Flipper-IRDB, CC0). */
class CodeDb(private val root: JSONObject) {
    val brands: List<String> = root.keys().asSequence().toList()
        .sortedBy { it.lowercase() }

    fun setCount(brand: String): Int = root.optJSONArray(brand)?.length() ?: 0

    fun codeSet(brand: String, index: Int): CodeSet? {
        val arr = root.optJSONArray(brand) ?: return null
        if (arr.length() == 0) return null
        val obj = arr.getJSONObject(index.coerceIn(0, arr.length() - 1))
        val keysObj = obj.getJSONObject("keys")
        val keys = HashMap<String, IrSignal>()
        val it = keysObj.keys()
        while (it.hasNext()) {
            val k = it.next()
            val a: JSONArray = keysObj.getJSONArray(k)
            val pat = IntArray(a.length() - 1) { i -> a.getInt(i + 1) }
            keys[k] = IrSignal(a.getInt(0), pat)
        }
        return CodeSet(obj.getString("model"), keys)
    }

    companion object {
        fun load(ctx: Context): CodeDb {
            val text = ctx.assets.open("tvcodes.json").bufferedReader().use { it.readText() }
            return CodeDb(JSONObject(text).getJSONObject("brands"))
        }
    }
}

/** Sends IR on a background thread so the UI never blocks. */
class IrTransmitter(ctx: Context) {
    private val ir = ctx.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
    private val exec = Executors.newSingleThreadExecutor()

    val available: Boolean = try { ir?.hasIrEmitter() == true } catch (_: Exception) { false }

    fun send(signal: IrSignal, onError: (String) -> Unit = {}) {
        val mgr = ir ?: return onError("No IR blaster found")
        exec.execute {
            try {
                mgr.transmit(bestFreq(mgr, signal.freq), signal.pattern)
            } catch (e: Exception) {
                onError(e.message ?: "IR transmit failed")
            }
        }
    }

    /** Some blasters only support specific carrier ranges; snap into range. */
    private fun bestFreq(mgr: ConsumerIrManager, f: Int): Int {
        val ranges = try { mgr.carrierFrequencies } catch (_: Exception) { null }
        if (ranges.isNullOrEmpty()) return f
        if (ranges.any { f in it.minFrequency..it.maxFrequency }) return f
        return ranges.minBy { r ->
            minOf(kotlin.math.abs(f - r.minFrequency), kotlin.math.abs(f - r.maxFrequency))
        }.let { r -> f.coerceIn(r.minFrequency, r.maxFrequency) }
    }
}

/** Saved devices + the active one, stored in SharedPreferences. */
class RemoteStore(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("remotes", Context.MODE_PRIVATE)

    fun load(): List<SavedRemote> {
        val arr = try { JSONArray(prefs.getString("list", "[]")) } catch (_: Exception) { JSONArray() }
        return (0 until arr.length()).mapNotNull {
            try {
                val o = arr.getJSONObject(it)
                val a = o.optJSONObject("ac")
                val ac = if (a == null) AcState() else AcState(
                    power = a.optBoolean("power", false),
                    mode = AcMode.entries.getOrElse(a.optInt("mode", 1)) { AcMode.COOL },
                    temp = a.optInt("temp", 24),
                    fan = AcFan.entries.getOrElse(a.optInt("fan", 0)) { AcFan.AUTO },
                    swing = a.optBoolean("swing", false),
                )
                SavedRemote(
                    o.getString("name"), o.getString("brand"), o.optInt("set", 0),
                    o.optString("kind", SavedRemote.KIND_TV), ac
                )
            } catch (_: Exception) { null }
        }
    }

    fun save(list: List<SavedRemote>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject().put("name", it.name).put("brand", it.brand).put("set", it.setIndex)
                    .put("kind", it.kind)
                    .put(
                        "ac", JSONObject().put("power", it.ac.power).put("mode", it.ac.mode.ordinal)
                            .put("temp", it.ac.temp).put("fan", it.ac.fan.ordinal).put("swing", it.ac.swing)
                    )
            )
        }
        prefs.edit().putString("list", arr.toString()).apply()
    }

    var active: Int
        get() = prefs.getInt("active", 0)
        set(v) { prefs.edit().putInt("active", v).apply() }
}
