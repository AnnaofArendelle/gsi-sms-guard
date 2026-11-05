# Contributing

Run `./build.sh` and `./scripts/test.sh` before opening a pull request. Changes to boot handling or vendor commands also require device testing; CI cannot verify modem behavior.

Modification is currently restricted to Xiaomi 12 `cupid` in DSU/GSI. To propose another device, document the vendor interface, Android version, mount namespace and a controlled receive test. Do not broaden the allowlist based only on the chipset name.

Do not submit raw radio logs, SMS text, PDUs, phone numbers, IMEI, IMSI, ICCID, signing keys or tokens. Share redacted event summaries. Do not introduce arbitrary shell/OEM command entry points or SMS permissions.

Preserve the PHH source archive and license notices when changing the native client. Device observations belong in `docs/DEVICE_VALIDATION.md`.
