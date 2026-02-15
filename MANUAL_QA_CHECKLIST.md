# Manual QA Checklist (Hotfix Delta)

## DNS + Blocking Regression
1. Enable protection.
2. Visit `https://google.com` and `https://wikipedia.org`.
   - Expected: pages load normally.
   - Expected logs: `DNS decision ALLOW ...`, `DNS forward success ...`, `DNS response write success ... type=upstream`.
3. Visit known blocked domain (e.g., `pornhub.com`).
   - Expected: blocked flow triggers.
   - Expected logs: `DNS decision BLOCK ...`, `DNS response write success ... type=blocked`.
4. Trigger malformed/unknown packet scenarios (normal browsing naturally includes non-DNS packets).
   - Expected logs: `DNS parse miss/unknown packet fail-open ...`.
   - Expected: no global overblocking from parse misses.

## Disable Protection Stability
1. Toggle protection ON/OFF repeatedly (10+ times).
2. Disable via notification action and via main switch.
3. Confirm no crash/ANR; service stops cleanly.

## Notification Spam Guard
1. Hit same blocked domain repeatedly for 30s.
2. Expected: per-domain dedupe suppresses repeats.
3. Hit multiple blocked domains rapidly.
4. Expected: global rate cap limits total notifications per minute.

## UX Checks
1. Blocked screen close button is visible at top-right in dim translucent circular style.
2. BuyMeACoffee centered in layouts.
3. Blocked page BuyMeACoffee image is half-sized vs prior design.
4. Long verses wrap fully (no truncation/cutoff) in app and local HTML block page.

## Domain List + Settings
1. Add extra blocked domain from settings.
2. Re-test that domain gets blocked.
3. Confirm no whitelist controls in settings.
