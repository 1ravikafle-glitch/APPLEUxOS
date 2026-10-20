# In-Call Takeover — custom dialer + iOS call screen (2026-10-16)

## What was done
- **AppleuxInCallService** (`appleux/launcher/.../apps/AppleuxInCallService.kt`):
  Telecom-bound service with `IN_CALL_SERVICE_UI` meta. `onCallAdded` opens
  our call screen; `onCallRemoved` broadcasts call-end. Shared `Calls` holder
  tracks the live call + second timer. Static `service` ref + `setMuted` /
  `setRoute` helpers (audio control must run on the bound service, not an
  activity — calling them from the activity does not compile).
- **AppleuxCallScreenActivity**: iOS incoming (contact + "mobile" caption,
  Remind Me / Message row, round red Decline + green Accept) and ongoing UI
  (large name + live mm:ss, symmetric 6-button glass grid with SF glyphs:
  mute / keypad + DTMF pad / speaker / hold / add-call / record, red End).
- **Unannounced recording**: shared `AppleuxCallRecorder` — direct
  `VOICE_CALL` capture (priv-app `CAPTURE_AUDIO_OUTPUT`, allowlisted in
  `osbuild/privapp-permissions-com.appleux.xml`), mic fallback. No
  announcement on any path (announcements are stock-dialer behavior).
  Auto on/off toggle in Phone → Record tab; manual red-dot button in-call.
- **System takeover (no file deletion)**: manifest claims `DIAL`,
  `CALL_BUTTON`, contact `VIEW`/`EDIT`/`INSERT`, `SENDTO` (`smsto:`/`sms:`/
  `mms:`); in-app one-tap requests for `ROLE_DIALER` (Phone) and `ROLE_SMS`
  (Messages). Holding the roles makes stock dialer/SMS UI never surface.

## Purpose
Replace stock Dialer/InCallUI/Messages/Contacts entry points with iOS 27 UI
while keeping every AOSP backend (Telecom, CallLog, ContactsContract,
SmsManager). Deleting stock packages would brick fallback paths (emergency
calls, role loss) — takeover by role + intent is the ROM-correct mechanism.

## How
- Pattern follows AOSP `InCallServiceImpl` (Apache 2.0, in-tree at
  `platform/packages/apps/InCallUI`); no third-party in-call code — the web
  search confirmed the only references are AOSP docs + AOSP itself.
- Calls placed via `TelecomManager.placeCall` when holding the dialer role
  (emergency-safe), else `ACTION_CALL`. Mute/speaker/hold/DTMF through live
  `Call` + bound-service audio methods.
- Apple design skill applied: press-down feedback, critically-damped motion
  tokens, grouped cards, large titles, direct labels, back chevrons.

## Remaining
1. Grant `ROLE_DIALER` / `ROLE_SMS` on device (one tap each) + simulated
   GSM incoming call proof (`adb emu gsm call`) with screenshots.
2. Rebake image (new `CAPTURE_AUDIO_OUTPUT` allowlist activates at boot).
3. `ROLE_DIALER`/`ROLE_SMS` persistence across reboot (verify, re-grant flow).
4. Camera2 viewfinder (intent-only for 1–11), per-contact message history,
   multi-timer list model, notification-count badge pipeline.
5. True stock-package removal is a ROM-tree step (Soong path, OOM-blocked) —
   not attempted from here by design.
