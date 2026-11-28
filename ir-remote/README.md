# Remote (IR) — TV + air conditioner

Android IR remote for phones with an IR blaster (`ConsumerIrManager`).

**TV:** 117 brands / ~380 code sets from [Flipper-IRDB](https://github.com/Lucaslhm/Flipper-IRDB) (CC0), pre-encoded by `tools/build_codes.py`. Setup wizard steps through codes until the TV reacts.

**Air conditioner:** stateful Samsung (14/21-byte) and LG (28-bit, new + old timing) protocols in `AcProtocols.kt`, after IRremoteESP8266's layouts. Power, mode, 16–30 °C, fan, swing.

APK: `.github/workflows/ir-remote.yml` → release `ir-remote-latest`. Signed with the committed `app/remote.keystore` so new builds install over old ones.
