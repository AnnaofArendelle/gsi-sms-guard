# Contributing

Run `./build.sh` and `./scripts/test.sh` before opening a pull request. Changes to boot handling or vendor commands also require device testing; CI cannot verify modem behavior.

ATEL modification is selected by detected conditions, with an explicit warning override. Device/ROM identity is diagnostic metadata, not an allowlist. To report compatibility, document the vendor interface, Android version and a controlled receive test. A successful interface probe or user override alone does not establish SMS recovery. Mount prerequisites must remain separate from ATEL.

Do not submit raw radio logs, SMS text, PDUs, phone numbers, IMEI, IMSI, ICCID, signing keys or tokens. Share redacted event summaries. Do not introduce arbitrary shell/OEM command entry points or SMS permissions.

Preserve the PHH source archive and license notices when changing the native client. Device observations belong in `docs/DEVICE_VALIDATION.md`.
