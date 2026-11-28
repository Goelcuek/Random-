package com.goelcuek.irremote

/**
 * Stateful air-conditioner protocols. An AC remote sends the complete state
 * (power, mode, temperature, fan, swing) with every press, so the phone keeps
 * the state and re-encodes it each time.
 *
 * Bit layouts follow the IRremoteESP8266 project (ir_Samsung / ir_LG).
 */

enum class AcMode(val label: String) { AUTO("Auto"), COOL("Cool"), DRY("Dry"), FAN("Fan"), HEAT("Heat") }
enum class AcFan(val label: String) { AUTO("Auto"), LOW("Low"), MED("Medium"), HIGH("High") }

data class AcState(
    val power: Boolean = false,
    val mode: AcMode = AcMode.COOL,
    val temp: Int = 24,
    val fan: AcFan = AcFan.AUTO,
    val swing: Boolean = false,
)

enum class AcBrand(val id: String, val label: String, val hint: String) {
    SAMSUNG("samsung_ac", "Samsung", "Samsung split & WindFree ACs"),
    LG("lg_ac", "LG (newer remote)", "Most LG ACs from ~2015 on — try this first"),
    LG_OLD("lg_ac_old", "LG (older remote)", "Older LG / LG-made units");

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id } ?: SAMSUNG
        const val MIN_TEMP = 16
        const val MAX_TEMP = 30
    }
}

object AcEncoder {
    private const val CARRIER = 38000

    /** Full state frame. [powerChanged] = the power button was pressed. */
    fun state(brand: AcBrand, s: AcState, powerChanged: Boolean): IrSignal = when (brand) {
        AcBrand.SAMSUNG -> IrSignal(CARRIER, samsung(s, extended = powerChanged))
        AcBrand.LG -> IrSignal(CARRIER, lg(lgCode(s), new = true))
        AcBrand.LG_OLD -> IrSignal(CARRIER, lg(lgCode(s), new = false))
    }

    /**
     * Signal for toggling swing. Samsung carries swing in the state; LG uses a
     * separate toggle command.
     */
    fun swing(brand: AcBrand, s: AcState): IrSignal = when (brand) {
        AcBrand.SAMSUNG -> state(brand, s, powerChanged = false)
        AcBrand.LG -> IrSignal(CARRIER, lg(LG_SWING_TOGGLE, new = true))
        AcBrand.LG_OLD -> IrSignal(CARRIER, lg(LG_SWING_TOGGLE, new = false))
    }

    // ------------------------------------------------------------ Samsung
    // 14-byte message in two 7-byte sections, LSB first. Power on/off uses
    // the 21-byte "extended" form with a fixed middle section.

    private val SAMSUNG_BASE = intArrayOf(
        0x02, 0x92, 0x0F, 0x00, 0x00, 0x00, 0xF0,
        0x01, 0x02, 0xAE, 0x71, 0x00, 0x15, 0xF0
    )
    private val SAMSUNG_MIDDLE = intArrayOf(0x01, 0xD2, 0x0F, 0x00, 0x00, 0x00, 0x00)

    fun samsungBytes(s: AcState): IntArray {
        val b = SAMSUNG_BASE.copyOf()
        val pw = if (s.power) 0x30 else 0x00
        b[6] = (b[6] and 0xCF) or pw
        b[13] = (b[13] and 0xCF) or pw
        val t = s.temp.coerceIn(AcBrand.MIN_TEMP, AcBrand.MAX_TEMP) - 16
        b[11] = (b[11] and 0x0F) or (t shl 4)
        val mode = when (s.mode) {
            AcMode.AUTO -> 0; AcMode.COOL -> 1; AcMode.DRY -> 2; AcMode.FAN -> 3; AcMode.HEAT -> 4
        }
        val fan = when {
            s.mode == AcMode.AUTO -> 6          // auto mode requires "auto2" fan
            s.mode == AcMode.DRY -> 0           // dry runs fan on auto
            else -> when (s.fan) { AcFan.AUTO -> 0; AcFan.LOW -> 2; AcFan.MED -> 4; AcFan.HIGH -> 5 }
        }
        b[12] = (b[12] and 0x81) or (mode shl 4) or (fan shl 1)
        val swing = if (s.swing) 0b010 else 0b111
        b[9] = (b[9] and 0x8F) or (swing shl 4)
        samsungChecksum(b, 0)
        samsungChecksum(b, 7)
        return b
    }

    private fun samsungChecksum(b: IntArray, off: Int) {
        var sum = Integer.bitCount(b[off])
        sum += Integer.bitCount(b[off + 1] and 0x0F)
        sum += Integer.bitCount(b[off + 2] shr 4)
        for (i in 3..6) sum += Integer.bitCount(b[off + i])
        val chk = sum.inv() and 0xFF
        b[off + 1] = (b[off + 1] and 0x0F) or ((chk and 0x0F) shl 4)
        b[off + 2] = (b[off + 2] and 0xF0) or (chk shr 4)
    }

    private fun samsung(s: AcState, extended: Boolean): IntArray {
        val st = samsungBytes(s)
        val sections = mutableListOf(st.copyOfRange(0, 7))
        if (extended) sections += SAMSUNG_MIDDLE.copyOf().also { samsungChecksum(it, 0) }
        sections += st.copyOfRange(7, 14)
        val p = ArrayList<Int>()
        p += 690; p += 17844                       // header
        sections.forEachIndexed { i, sec ->
            p += 3086; p += 8864                   // section header
            for (byte in sec) for (bit in 0 until 8) {
                p += 586
                p += if ((byte shr bit) and 1 == 1) 1432 else 436
            }
            p += 586
            p += if (i == sections.lastIndex) 100_000 else 2886
        }
        return p.toIntArray()
    }

    // ------------------------------------------------------------ LG
    // 28-bit word, MSB first: sign 0x88 | power(2) | pad(3) | mode(3) |
    // temp-15 (4) | fan(4) | checksum(4) — checksum = sum of nibbles 1..4.

    const val LG_OFF = 0x88C0051
    const val LG_SWING_TOGGLE = 0x8810001

    fun lgCode(s: AcState): Int {
        if (!s.power) return LG_OFF
        val mode = when (s.mode) {
            AcMode.COOL -> 0; AcMode.DRY -> 1; AcMode.FAN -> 2; AcMode.AUTO -> 3; AcMode.HEAT -> 4
        }
        val fan = when (s.fan) { AcFan.LOW -> 0; AcFan.MED -> 2; AcFan.HIGH -> 4; AcFan.AUTO -> 5 }
        val t = s.temp.coerceIn(AcBrand.MIN_TEMP, AcBrand.MAX_TEMP) - 15
        val body = (0x88 shl 20) or (mode shl 12) or (t shl 8) or (fan shl 4)
        return body or lgChecksum(body)
    }

    private fun lgChecksum(code: Int): Int {
        var sum = 0
        for (n in 1..4) sum += (code shr (4 * n)) and 0xF
        return sum and 0xF
    }

    private fun lg(code: Int, new: Boolean): IntArray {
        val p = ArrayList<Int>()
        if (new) { p += 3200; p += 9900 } else { p += 8500; p += 4250 }
        val mark = if (new) 480 else 550
        for (i in 27 downTo 0) {
            p += mark
            p += if ((code shr i) and 1 == 1) 1600 else 550
        }
        p += mark; p += 50_000
        return p.toIntArray()
    }
}
