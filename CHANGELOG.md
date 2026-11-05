# Changelog

## 0.4.0

- Remove device and DSU/ROM allowlists from ATEL readiness and automatic operation.
- Probe each OEM hook with read-only HIDL interfaceChain; work with one available slot.
- Add explicit “attempt anyway” actions for unmatched/unknown conditions, including persistent automatic attempt mode.
- Keep root/HAL/SELinux failures visible; an override attempts the fixed command, not arbitrary commands.
- Separate mount prerequisites from ATEL compatibility, preserve device metadata, and control detected RIL services.
- Support periodic readiness refresh when RIL process names are unknown.
- Expand to 20 compatibility, warning override and polling tests.

## 0.3.0

- Add a foreground management service that reports ATEL UI ready at boot, after app updates, and after Qualcomm RIL process changes.
- Poll process identity every 30 seconds and refresh readiness every five minutes.
- Keep Power Opt enabled in automatic mode. Retain a separately confirmed temporary mount fallback and undo.
- Guard modification by DSU state, device, OEM hook availability, database compatibility and mount namespace.
- Add 11 environment/parser checks and CI/release packaging validation.
- Confirm SMS reception on Xiaomi 12 cupid / Android 13 DSU GSI using ATEL alone.

Full phone reboot and long-term Doze behavior remain to be verified.
