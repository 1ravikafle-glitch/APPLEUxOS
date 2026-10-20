import SwiftUI

// Measurement target. Each vertical band is a known colour; the glass panel
// crosses the middle of every band, so one screenshot gives each colour both
// direct and through-glass. Solving O = a*T + (1-a)*B across two bands yields
// the veil alpha and tint without guessing.
let bands: [(String, Color)] = [
  ("K",  Color(red: 0,    green: 0,    blue: 0)),
  ("W",  Color(red: 1,    green: 1,    blue: 1)),
  ("G5", Color(red: 0.5,  green: 0.5,  blue: 0.5)),
  ("R",  Color(red: 1,    green: 0,    blue: 0)),
  ("G",  Color(red: 0,    green: 1,    blue: 0)),
  ("B",  Color(red: 0,    green: 0,    blue: 1)),
  ("C",  Color(red: 0,    green: 0.8,  blue: 0.9)),
  ("M",  Color(red: 0.9,  green: 0.1,  blue: 0.6)),
]

struct ContentView: View {
  var body: some View {
    ZStack {
      HStack(spacing: 0) {
        ForEach(Array(bands.enumerated()), id: \.offset) { _, b in
          b.1.frame(maxWidth: .infinity, maxHeight: .infinity)
        }
      }
      .ignoresSafeArea()

      VStack(spacing: 26) {
        // regular — the default Liquid Glass
        Color.clear.frame(height: 120)
          .glassEffect(.regular, in: .rect(cornerRadius: 0))
        // clear — the see-through variant
        Color.clear.frame(height: 120)
          .glassEffect(.clear, in: .rect(cornerRadius: 0))
      }
      .padding(.horizontal, 0)
    }
    .background(Color.black)
    .statusBarHidden(true)
  }
}

@main struct GlassProbeApp: App {
  var body: some Scene { WindowGroup { ContentView() } }
}
