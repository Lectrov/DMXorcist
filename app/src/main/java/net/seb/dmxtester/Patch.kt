package net.seb.dmxtester

/**
 * Describes a fixture type: how many channels it occupies, and where its
 * components sit as offsets from its start address.
 */
data class FixtureProfile(
    val label: String,
    val footprint: Int,
    val dimmer: Int? = null,
    val red: Int? = null,
    val green: Int? = null,
    val blue: Int? = null,
    val white: Int? = null,
) {
    companion object {
        val PRESETS = listOf(
            FixtureProfile("RGB, 3 channels", 3, red = 0, green = 1, blue = 2),
            FixtureProfile("RGBW, 4 channels", 4, red = 0, green = 1, blue = 2, white = 3),
            FixtureProfile("Dimmer + RGB, 4 channels", 4, dimmer = 0, red = 1, green = 2, blue = 3),
            FixtureProfile("Dimmer + RGBW, 5 channels", 5, dimmer = 0, red = 1, green = 2, blue = 3, white = 4),
            FixtureProfile("RGB + Dimmer, 4 channels", 4, red = 0, green = 1, blue = 2, dimmer = 3),
            FixtureProfile("LED PAR, 6 channels", 6, dimmer = 0, red = 1, green = 2, blue = 3, white = 4),
            FixtureProfile("LED PAR, 8 channels", 8, dimmer = 0, red = 1, green = 2, blue = 3, white = 4),
            FixtureProfile("Single dimmer, 1 channel", 1, dimmer = 0),
        )
    }
}

data class Patch(
    val profile: FixtureProfile = FixtureProfile.PRESETS.first(),
    val startAddress: Int = 1,
    val count: Int = 4,
) {
    fun addressOf(index: Int): Int = startAddress + index * profile.footprint

    val lastAddress: Int
        get() = addressOf(count - 1) + profile.footprint - 1

    val overflows: Boolean
        get() = lastAddress > 512

    val summary: String
        get() = "$count fixture(s), channels $startAddress to $lastAddress"
}

data class TestColor(
    val red: Int = 255,
    val green: Int = 0,
    val blue: Int = 0,
    val white: Int = 0,
    val dimmer: Int = 255,
) {
    companion object {
        val PRESETS = listOf(
            "Red" to TestColor(255, 0, 0, 0),
            "Green" to TestColor(0, 255, 0, 0),
            "Blue" to TestColor(0, 0, 255, 0),
            "Yellow" to TestColor(255, 255, 0, 0),
            "Cyan" to TestColor(0, 255, 255, 0),
            "Magenta" to TestColor(255, 0, 255, 0),
            "RGB white" to TestColor(255, 255, 255, 0),
            "Dedicated white" to TestColor(0, 0, 0, 255),
        )
    }
}

enum class TestMode(val label: String, val hint: String) {
    COLOR(
        "Colour on every fixture",
        "Sends the same colour to the whole patch. A fixture that does not follow is either " +
            "mis-addressed or wired to a different channel order."
    ),
    CHASE(
        "Chase",
        "Lights one fixture at a time, in patch order. Use it to work out which machine holds which address."
    ),
    RGB_CYCLE(
        "R / G / B cycle",
        "Red, then green, then blue on everything. Reveals swapped colour channels."
    ),
    STROBE(
        "Strobe",
        "Flashes the selected colour."
    ),
    ALL_ON(
        "All channels at 100%",
        "Forces all 512 channels to 255, ignoring the patch. The sledgehammer: if nothing lights up, " +
            "the fault is upstream of the addressing."
    ),
    UNIVERSE_SWEEP(
        "Universe sweep (Art-Net)",
        "All channels at 255, stepping through universes. Finds which universe a rig actually listens on: " +
            "the protocol counts from 0 while consoles usually display from 1."
    ),
    UNIVERSE_RAMP(
        "Ramp the whole universe",
        "All 512 channels fade up and down together. Everything connected should breathe in sync."
    ),
    BLOCK_SWEEP(
        "Block sweep",
        "Walks the entire universe in blocks the size of the selected profile, ignoring the patch. " +
            "Finds every fixture whatever its address."
    ),
    CHANNEL_WALK(
        "Channel walk",
        "Raises one channel at a time, from 1 to 512. Use it to recover the address of an unknown fixture."
    ),
    INVERSE_WALK(
        "Inverse walk",
        "All channels at 255 except one, pulled to zero. The fixture stays lit and you watch what drops out: " +
            "when red disappears, the readout gives you the red channel number."
    ),
    MANUAL(
        "Manual channel",
        "One channel, one value."
    ),
    BLACKOUT(
        "Blackout",
        "Everything at zero, but the frame keeps being transmitted."
    ),
}
