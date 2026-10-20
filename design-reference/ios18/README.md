# iOS 18 design harvest (persistent copy, 2026-10-14)

Source: iPhone12,1 iOS 18.6.1 (22G90) IPSW, decrypted on Linux with
blacktop/ipsw using published theapplewiki keys (see
design-reference/ios27/online-notes.md Part A for key URLs + tool list).
Original workspace /tmp/opencode/ios-insp/ios18/ (ephemeral) — this is the
curated persistent copy.

Contents:
- harvest/ (~293 MB): full curated tree — Library/Wallpaper (iOS_17, Clownfish,
  Bokeh collections with .ca layers + HEICs), System/Library/Audio/UISounds
  (*.caf), Applications art, System artwork.
- harvest-cryptex/ (~5 MB): cryptex extras.
- wallpapers-jpg/: converted + composited wallpapers ready to ship
  (ios17-hero-light.jpg = Background Light + Screen-blended Floating layers).

NOT stored (regenerable, too big): 8.5 GB IPSW, 11 GB decrypted APFS dump
(dec/), raw .aea files. Re-run the decrypt agent with the same keys to rebuild.

License: Apple assets, reference/inspiration only — do NOT redistribute.
Wallpapers shipped in-device should be replaced or re-licensed before any
public release.
