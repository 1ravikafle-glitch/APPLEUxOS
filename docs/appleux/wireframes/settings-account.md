# WIREFRAME — Settings Account Card + Account Page (iOS 27 structure)

**Purpose:** the complete iOS 27 account tree as the target, with an
explicit wiring point per row: what binds NOW (honest, real backends)
and what wires LATER when the Google-account tally lands. This is the
build spec A codes from; C refines visuals; nothing here ships until its
row's data source is real (no-fake-rows law).

**Binding contract (the tally):** the account identity comes from
`AccountManager.get(context).getAccountsByType("com.google")` — primary
account = first entry. That is the LATER wire point. Until GMS exists on
the image, the card binds the honest placeholder (device name) it shows
today. NO other identity source may be used — no invented "AppleUX ID".

---

## 1. Account CARD (Settings root, first row) — SHIPPED (v1)

```
┌────────────────────────────────────────────────┐
│  (avatar 52dp   Name (bold 17sp)               > │   ← card: 14dp pad, capsule bg
│   squircle/     Google Account, AppleUX, and…   │     same margins as row groups
│   circle)                                             chevron: registry, tertiaryLabel
└────────────────────────────────────────────────┘
```

| Element | NOW (v1, live) | LATER (Google tally) |
|---|---|---|
| Avatar | iOS-blue circle + monogram of the bound name (C's polish) | Google profile photo: `AccountManager.getUserData(acc, ...)` / `People`-API URI |
| Name | `Settings.Global.DEVICE_NAME` ("AppleUX" fallback) | primary Google account name |
| Subtitle | "Device, AppleUX, and more" | "Google Account, AppleUX, and more" |
| Tap | opens Account page (§2) | same — page rebinds |

## 2. ACCOUNT PAGE (tap destination) — WIREFRAME ONLY, not yet built

```
┌──────────────────────────────────────────────┐
│  ‹ Back            Apple Account  (large title)│
├──────────────────────────────────────────────┤
│  ┌────────────────────────────────────────┐  │
│  │ (avatar 72)  Name                       │  │  identity card: email under name,
│  │              name@example.com          │  │  subtitle row "Google Account"
│  └────────────────────────────────────────┘  │
│                                              │
│  Personal Info                    >  │ row group 1
│    Name, email, phone — AccountManager data  │
│  Sign-In & Security               >  │
│    NOW: device lock state (real)             │
│    LATER: Google sign-in preferences         │
│  Devices                          >  │
│    NOW: this device (model, build)           │
├──────────────────────────────────────────────┤
│  iCloud → "Cloud & Backup"        >  │ row group 2 (rename: no Apple claim)
│    LATER: Google One / Drive / backup (GMS)  │
│  Subscriptions                    >  │
│    LATER: Play billing (GMS)                 │
│  Wallet                           >  │
│    LATER: Google Wallet (GMS)                │
├──────────────────────────────────────────────┤
│  Sign Out (red text)                │ bottom destructive row
│    NOW: absent (no account to sign out)       │
│    LATER: AccountManager.removeAccount       │
└──────────────────────────────────────────────┘
```

## 3. Row wiring table (the tally, row by row)

| iOS 27 row | Our page row | Data source NOW | Google wire LATER | Wire point |
|---|---|---|---|---|
| Apple Account header | Identity card | DEVICE_NAME + "no account" | account name/email | `AccountManager.getAccountsByType("com.google")` |
| Personal Info | Personal Info | device lock + owner none | Google profile fields | `AccountManager.getUserData` + People API |
| Sign-In & Security | Sign-In & Security | `KeyguardManager.isDeviceSecure` (REAL today) | Google security settings | GMS `AccountSettings` intent |
| Devices | Devices | `Build.MODEL` + fingerprint (REAL) | Google device list | GMS myaccount |
| iCloud | Cloud & Backup | **hidden until real** | Google One/Backup | `AccountManager` + GMS backup intent |
| Subscriptions | Subscriptions | **hidden until real** | Play Billing | Play Store deep link |
| Wallet | Wallet | **hidden until real** | Google Wallet | GMS Wallet intent |
| Sign Out | Sign Out | hidden (no account) | real sign-out | `AccountManager.removeAccount` (needs user grant) |

**Honesty rule per row:** a LATER row renders ONLY when its wire is live
(GMS present + account bound). Never a dead row, never a fake value.

## 4. Root "Apple services" group (iOS: Game Center/iCloud/Wallet/Apps)

Per the catalog law these were OMITTED, not faked. With the account tally
they become the account page's LATER rows (see §3) — they belong INSIDE
the account page, not on the Settings root. No change to the root catalog.

## 5. Build order

1. ✅ Card (v1 shipped — live DEVICE_NAME binding)
2. Account page skeleton: identity card + NOW-real rows only
   (Sign-In & Security via KeyguardManager, Devices via Build) — **A codes**
3. C: visual pass on card + page vs ground-truth (`Settings-root.png`,
   account page references)
4. LATER (GMS phase): bind the Google wires; LATER rows un-hide per the
   honesty rule. Requires the rock image with GMS — tracked in WORK_QUEUE
   as part of A8 (device integration).
