# DMXorcist

*Casts the demons out of your rig.*

A portable DMX512 tester for Android, driving a USB-DMX dongle over OTG or an
Art-Net node over Wi-Fi.

Built for the field: plug in, push a colour to the whole patch, and watch which
fixture does not follow.

## What it does

| Mode | What it tells you |
|---|---|
| **Colour on every fixture** | Red everywhere means every RGB fixture should be red. One that stays blue has its colour channels swapped; one that stays dark is mis-addressed. |
| **Chase** | One fixture at a time, in patch order, with a live readout: `Fixture 3/8 - address 13`. Tells you which machine holds which address. |
| **R / G / B cycle** | Red, green, blue on everything. Catches swapped colour wiring. |
| **Strobe** | 1 to 15 Hz on the selected colour. |
| **All channels at 100%** | Forces all 512 channels to 255, ignoring the patch. The sledgehammer: if nothing lights, the fault is in the cable, the dongle or the power, not the addressing. |
| **Universe sweep** | All channels at 255, stepping through Art-Net universes. Finds which universe a rig actually listens on. |
| **Ramp the whole universe** | All 512 channels fade together. Anything that stays static is not receiving. |
| **Block sweep** | Walks the universe in blocks the size of the chosen profile, ignoring the patch. Finds every fixture whatever its address. |
| **Channel walk** | Raises one channel at a time, 1 to 512, to recover an unknown address. |
| **Inverse walk** | All channels at 255 except one pulled to zero. The fixture stays lit and you watch what drops out, which works even when channel 1 is a master dimmer. |
| **Manual channel** | One channel, one value. |
| **Blackout** | Everything at zero, frame still transmitted. |

A **Transmitted universe** view shows all 512 channels live as a grid, so you can
confirm what the app is actually sending independently of what the rig does.

The patch is three fields: fixture type (RGB 3ch, RGBW 4ch, Dimmer+RGB 4ch,
LED PAR 6/8ch, single dimmer…), start address, fixture count. Successive
addresses are worked out for you.

## Hardware

- An Android phone that acts as a USB host (any Pixel does)
- A USB-C to USB-A **OTG** adapter
- A USB-DMX dongle

**The dongle type matters.** Two families are supported, selectable in the app:

- **Open DMX (raw FTDI)** — Enttec Open DMX USB and nearly every cheap FT232RL
  stick. The dongle is dumb: the phone generates the BREAK and the frame itself.
  This is the common case.
- **Enttec DMX USB Pro** — a microcontroller inside the stick handles the
  timing. Considerably more stable, but only works with genuine Pro hardware or
  a truly compatible clone.

A Sunlite / FreeStyler / other proprietary stick will not work: they expose no
serial port. The app displays the detected VID/PID so you can identify what you
plugged in.

For Art-Net, no dongle is needed at all — just the phone on the same network as
the node.

## Install

Grab the APK from the [Releases](../../releases) page and open it on the phone.
Android will ask you to allow installation from that source.

Then plug the dongle in: Android offers to open DMXorcist and grants the USB
permission at the same time.

## Build from source

1. Install Android Studio.
2. `File → Open` and select this directory.
3. Wait for the Gradle sync (it downloads the toolchain the first time).
4. Plug in the phone with USB debugging enabled, then **Run ▶**.

From the command line:

```bash
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`.

The bottom of the screen shows the version and the exact build timestamp, so you
can tell at a glance whether an install actually took.

## Releasing

Push a tag and CI builds the APK and publishes it:

```bash
git tag v1.0 && git push origin v1.0
```

The workflow in `.github/workflows/android.yml` builds on every push, and on a
`v*` tag it attaches `dmxorcist-<tag>.apk` to a GitHub release with generated
notes.

The published APK is signed with the standard Android debug key. It installs
fine from a phone, but is not suitable for the Play Store — add a real keystore
through repository secrets if you ever need that.

## Notes

- One dongle equals **one universe**. Universe numbering is an Art-Net/sACN
  concept, not a DMX512 one: an XLR line carries a single universe. That is why
  the universe sweep only does anything over Art-Net.
- **Art-Net numbers universes from 0**, while consoles and nodes almost always
  display them from 1. A desk's universe "1" is `0` on the wire. This is the
  most common mismatch there is, and the sweep mode exists to settle it in
  seconds.
- Art-Net is sent as UDP broadcast on port 6454. The default destination is
  `255.255.255.255`; set the node's IP if broadcast is filtered on the network.
- Art-Net needs a local network, not internet access. Note that when a phone
  joins a Wi-Fi with no internet, Android may keep routing traffic over mobile
  data — answer "stay connected" to the prompt, and turn mobile data off.
- The frame is retransmitted continuously at roughly 30 Hz. Without that,
  fixtures time out after a few seconds.
- In Open DMX mode the BREAK uses the native FTDI command, which costs a USB
  round trip (~500 µs). The resulting BREAK is longer than the typical 176 µs
  but perfectly legal, since DMX512-A allows up to 1 s. If the driver does not
  expose that command, the app falls back to the baud-rate trick (0x00 at
  56 000 baud gives about 160 µs of low line).
- Android is not a real-time system, so frame timing has jitter. Harmless for
  testing; not the tool for running a show.

## Layout

```
app/src/main/java/net/seb/dmxorcist/
├── MainActivity.kt          entry point, keeps the screen awake
├── Patch.kt                 fixture profiles, patch, test modes
├── TesterViewModel.kt       app state and universe rendering
├── dmx/
│   ├── DmxDriver.kt         OpenDmxDriver (raw FTDI) and EnttecProDriver
│   ├── ArtNetOutput.kt      Art-Net UDP output
│   └── DmxEngine.kt         USB link, permissions, refresh loop
└── ui/
    └── TesterScreen.kt      Compose interface
```

## Licence

MIT, (c) 2026 Sebastien Lebon. See [LICENSE](LICENSE).
