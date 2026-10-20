import SwiftUI

// MARK: - iOS 27 Design System Tokens
// Extracted from Apple's official iOS 27 Figma Community Kit (1651309003795292092)
// Following three-layer token architecture: Primitive → Semantic → Component

// MARK: - Primitive Tokens (Layer 1)
enum iOS27Primitive {
    
    // MARK: Color Primitives
    enum Color {
        // Primary Text
        static let primaryTextLight = SwiftUI.Color(hex: 0x000000, opacity: 1.0)
        static let primaryTextDark = SwiftUI.Color(hex: 0xFFFFFF, opacity: 1.0)
        
        // Secondary Text
        static let secondaryTextLight = SwiftUI.Color(hex: 0x3C3C43, opacity: 0.6)
        static let secondaryTextDark = SwiftUI.Color(hex: 0xEBEBF5, opacity: 0.7)
        
        // Tertiary Text
        static let tertiaryTextLight = SwiftUI.Color(hex: 0x3C3C43, opacity: 0.3)
        static let tertiaryTextDark = SwiftUI.Color(hex: 0xEBEBF5, opacity: 0.3)
        
        // Quaternary Text
        static let quaternaryTextLight = SwiftUI.Color(hex: 0x3C3C43, opacity: 0.18)
        static let quaternaryTextDark = SwiftUI.Color(hex: 0xEBEBF5, opacity: 0.16)
        
        // System Colors
        static let systemRedLight = SwiftUI.Color(hex: 0xFF383C)
        static let systemRedDark = SwiftUI.Color(hex: 0xFF4245)
        
        static let systemOrangeLight = SwiftUI.Color(hex: 0xFF8D28)
        static let systemOrangeDark = SwiftUI.Color(hex: 0xFF9230)
        
        static let systemYellowLight = SwiftUI.Color(hex: 0xFFCC00)
        static let systemYellowDark = SwiftUI.Color(hex: 0xFFD600)
        
        static let systemGreenLight = SwiftUI.Color(hex: 0x34C759)
        static let systemGreenDark = SwiftUI.Color(hex: 0x30D158)
        
        static let systemMintLight = SwiftUI.Color(hex: 0x00C8B3)
        static let systemMintDark = SwiftUI.Color(hex: 0x00DAC3)
        
        static let systemTealLight = SwiftUI.Color(hex: 0x00C3D0)
        static let systemTealDark = SwiftUI.Color(hex: 0x00D2E0)
        
        static let systemCyanLight = SwiftUI.Color(hex: 0x00C0E8)
        static let systemCyanDark = SwiftUI.Color(hex: 0x3CD3FE)
        
        static let systemBlueLight = SwiftUI.Color(hex: 0x0088FF)
        static let systemBlueDark = SwiftUI.Color(hex: 0x0091FF)
        
        static let systemIndigoLight = SwiftUI.Color(hex: 0x6155F5)
        static let systemIndigoDark = SwiftUI.Color(hex: 0x6D7CFF)
        
        static let systemPurpleLight = SwiftUI.Color(hex: 0xCB30E0)
        static let systemPurpleDark = SwiftUI.Color(hex: 0xDB34F2)
        
        static let systemPinkLight = SwiftUI.Color(hex: 0xFF2D55)
        static let systemPinkDark = SwiftUI.Color(hex: 0xFF375F)
        
        static let systemBrownLight = SwiftUI.Color(hex: 0xAC7F5E)
        static let systemBrownDark = SwiftUI.Color(hex: 0xB78A66)
    }
    
    // MARK: Spacing Primitives
    enum Spacing {
        static let _0: CGFloat = 0
        static let _4: CGFloat = 4
        static let _8: CGFloat = 8
        static let _12: CGFloat = 12
        static let _16: CGFloat = 16
        static let _20: CGFloat = 20
        static let _24: CGFloat = 24
        static let _32: CGFloat = 32
        static let _40: CGFloat = 40
        static let _48: CGFloat = 48
        static let _64: CGFloat = 64
        static let _80: CGFloat = 80
        static let _96: CGFloat = 96
    }
    
    // MARK: Typography Primitives
    enum Typography {
        struct TextStyle {
            let size: CGFloat
            let lineHeight: CGFloat
            let letterSpacing: CGFloat
            let weight: Font.Weight
        }
        
        static let largeTitle = TextStyle(size: 34, lineHeight: 41, letterSpacing: 0.40, weight: .regular)
        static let largeTitleBold = TextStyle(size: 34, lineHeight: 41, letterSpacing: 0.40, weight: .bold)
        
        static let title1 = TextStyle(size: 28, lineHeight: 34, letterSpacing: 0.38, weight: .regular)
        static let title1Bold = TextStyle(size: 28, lineHeight: 34, letterSpacing: 0.38, weight: .bold)
        
        static let title2 = TextStyle(size: 22, lineHeight: 28, letterSpacing: -0.26, weight: .regular)
        static let title2Bold = TextStyle(size: 22, lineHeight: 28, letterSpacing: -0.26, weight: .bold)
        
        static let title3 = TextStyle(size: 20, lineHeight: 25, letterSpacing: -0.45, weight: .semibold)
        
        static let headline = TextStyle(size: 17, lineHeight: 22, letterSpacing: -0.43, weight: .semibold)
        
        static let body = TextStyle(size: 17, lineHeight: 22, letterSpacing: -0.43, weight: .regular)
        static let bodySemibold = TextStyle(size: 17, lineHeight: 22, letterSpacing: -0.43, weight: .semibold)
        
        static let callout = TextStyle(size: 16, lineHeight: 21, letterSpacing: -0.31, weight: .regular)
        static let calloutSemibold = TextStyle(size: 16, lineHeight: 21, letterSpacing: -0.31, weight: .semibold)
        
        static let subheadline = TextStyle(size: 15, lineHeight: 20, letterSpacing: -0.23, weight: .regular)
        static let subheadlineSemibold = TextStyle(size: 15, lineHeight: 20, letterSpacing: -0.23, weight: .semibold)
        
        static let footnote = TextStyle(size: 13, lineHeight: 18, letterSpacing: -0.08, weight: .regular)
        static let footnoteSemibold = TextStyle(size: 13, lineHeight: 18, letterSpacing: -0.08, weight: .semibold)
        
        static let caption1 = TextStyle(size: 12, lineHeight: 16, letterSpacing: 0, weight: .regular)
        static let caption1Semibold = TextStyle(size: 12, lineHeight: 16, letterSpacing: 0, weight: .semibold)
        
        static let caption2 = TextStyle(size: 11, lineHeight: 13, letterSpacing: 0.06, weight: .regular)
        static let caption2Semibold = TextStyle(size: 11, lineHeight: 13, letterSpacing: 0.06, weight: .semibold)
    }
    
    // MARK: Radius Primitives
    enum Radius {
        static let sm: CGFloat = 4
        static let md: CGFloat = 8
        static let lg: CGFloat = 12
        static let xl: CGFloat = 16
        static let full: CGFloat = 9999 // Use Capsule()
    }
    
    // MARK: Shadow Primitives
    enum Shadow {
        struct ShadowStyle {
            // Qualified: the nested `iOS27Primitive.Color` namespace shadows
            // `SwiftUI.Color` for the whole `iOS27Primitive` scope, so an
            // unqualified `Color` here resolves to the token enum and the
            // `.black` literals below stop compiling.
            let color: SwiftUI.Color
            let radius: CGFloat
            let x: CGFloat
            let y: CGFloat
        }
        
        static let sm = ShadowStyle(color: .black.opacity(0.05), radius: 2, x: 0, y: 1)
        static let md = ShadowStyle(color: .black.opacity(0.1), radius: 8, x: 0, y: 4)
        static let lg = ShadowStyle(color: .black.opacity(0.15), radius: 16, x: 0, y: 8)
    }
}

// MARK: - Color Hex Extension
extension Color {
    init(hex: UInt, opacity: Double = 1.0) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255.0,
            green: Double((hex >> 8) & 0xFF) / 255.0,
            blue: Double(hex & 0xFF) / 255.0,
            opacity: opacity
        )
    }
}
