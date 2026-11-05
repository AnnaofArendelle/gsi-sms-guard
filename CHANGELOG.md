# Changelog

## 0.3.0

- Add a foreground management service that reports ATEL UI ready at boot, after app updates, and after Qualcomm RIL process changes.
- Poll process identity every 30 seconds and refresh readiness every five minutes.
- Keep Power Opt enabled in automatic mode. Retain a separately confirmed temporary mount fallback and undo.
- Guard modification by DSU state, device, OEM hook availability, database compatibility and mount namespace.
- Add 11 environment/parser checks and CI/release packaging validation.
- Confirm SMS reception on Xiaomi 12 cupid / Android 13 DSU GSI using ATEL alone.

Full phone reboot and long-term Doze behavior remain to be verified.
