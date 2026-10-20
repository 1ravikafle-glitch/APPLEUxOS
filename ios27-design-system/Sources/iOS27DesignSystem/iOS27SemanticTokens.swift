import SwiftUI

// MARK: - iOS 27 Semantic Tokens (Layer 2)
// Purpose-aliases that describe WHAT each token is for
// Enables theme switching (light/dark) via environment

// MARK: - Color Semantic Tokens
enum iOS27SemanticColor {
    
    // MARK: Text Colors
    static let onSurface = Color(uiColor: .label)
    static let onSurfaceVariant = Color(uiColor: .secondaryLabel)
    static let onSurfaceTertiary = Color(uiColor: .tertiaryLabel)
    static let onSurfaceQuaternary = Color(uiColor: .quaternaryLabel)
    
    // MARK: Background Colors
    static let background = Color(uiColor: .systemBackground)
    static let backgroundSecondary = Color(uiColor: .secondarySystemBackground)
    static let backgroundTertiary = Color(uiColor: .tertiarySystemBackground)
    static let surface = Color(uiColor: .systemGroupedBackground)
    static let surfaceSecondary = Color(uiColor: .secondarySystemGroupedBackground)
    static let surfaceTertiary = Color(uiColor: .tertiarySystemGroupedBackground)
    
    // MARK: System Colors
    static let systemRed = Color(uiColor: .systemRed)
    static let systemOrange = Color(uiColor: .systemOrange)
    static let systemYellow = Color(uiColor: .systemYellow)
    static let systemGreen = Color(uiColor: .systemGreen)
    static let systemMint = Color(uiColor: .systemMint)
    static let systemTeal = Color(uiColor: .systemTeal)
    static let systemCyan = Color(uiColor: .systemCyan)
    static let systemBlue = Color(uiColor: .systemBlue)
    static let systemIndigo = Color(uiColor: .systemIndigo)
    static let systemPurple = Color(uiColor: .systemPurple)
    static let systemPink = Color(uiColor: .systemPink)
    static let systemBrown = Color(uiColor: .systemBrown)
    
    // MARK: Semantic Action Colors
    static let primary = Color(uiColor: .systemBlue)
    static let primaryHover = Color(uiColor: .systemBlue).opacity(0.8)
    static let primaryActive = Color(uiColor: .systemBlue).opacity(0.6)
    
    static let destructive = Color(uiColor: .systemRed)
    static let destructiveHover = Color(uiColor: .systemRed).opacity(0.8)
    static let destructiveActive = Color(uiColor: .systemRed).opacity(0.6)
    
    static let success = Color(uiColor: .systemGreen)
    static let warning = Color(uiColor: .systemYellow)
    static let info = Color(uiColor: .systemCyan)
    
    // MARK: Outline/Border Colors
    static let outline = Color(uiColor: .separator)
    static let outlineSecondary = Color(uiColor: .opaqueSeparator)
    
    // MARK: Fill Colors
    static let fill = Color(uiColor: .systemFill)
    static let fillSecondary = Color(uiColor: .secondarySystemFill)
    static let fillTertiary = Color(uiColor: .tertiarySystemFill)
    static let fillQuaternary = Color(uiColor: .quaternarySystemFill)
    
    // MARK: Disabled Colors
    static let muted = Color(uiColor: .systemGray5)
    static let mutedForeground = Color(uiColor: .systemGray3)
    static let mutedBorder = Color(uiColor: .systemGray4)
    
    // MARK: Overlay Colors
    static let overlay = Color.black.opacity(0.4)
    static let overlayLight = Color.white.opacity(0.3)
}

// MARK: - Spacing Semantic Tokens
enum iOS27SemanticSpacing {
    static let zero: CGFloat = 0
    static let xxs: CGFloat = 4      // space-1
    static let xs: CGFloat = 8       // space-2
    static let sm: CGFloat = 12      // space-3
    static let md: CGFloat = 16      // space-4
    static let lg: CGFloat = 24      // space-6
    static let xl: CGFloat = 32      // space-8
    static let xxl: CGFloat = 40     // space-10
    static let xxxl: CGFloat = 48    // space-12
    static let huge: CGFloat = 64    // space-16
    static let massive: CGFloat = 80 // space-20
    static let gigantic: CGFloat = 96 // space-24
    
    // Standard layout constants
    static let screenMargin: CGFloat = 16
    static let contentPadding: CGFloat = 16
    static let cardPadding: CGFloat = 16
    static let listRowPadding: CGFloat = 12
    static let toolbarHeight: CGFloat = 54 // iPhone standard
    static let tabBarItemMinWidth: CGFloat = 72
}

// MARK: - Typography Semantic Tokens
enum iOS27SemanticTypography {
    
    // MARK: Large Title
    static let largeTitle = Font.system(size: 34, weight: .regular, design: .default)
    static let largeTitleBold = Font.system(size: 34, weight: .bold, design: .default)
    
    // MARK: Title 1
    static let title1 = Font.system(size: 28, weight: .regular, design: .default)
    static let title1Bold = Font.system(size: 28, weight: .bold, design: .default)
    
    // MARK: Title 2
    static let title2 = Font.system(size: 22, weight: .regular, design: .default)
    static let title2Bold = Font.system(size: 22, weight: .bold, design: .default)
    
    // MARK: Title 3
    static let title3 = Font.system(size: 20, weight: .semibold, design: .default)
    
    // MARK: Headline
    static let headline = Font.system(size: 17, weight: .semibold, design: .default)
    
    // MARK: Body
    static let body = Font.system(size: 17, weight: .regular, design: .default)
    static let bodySemibold = Font.system(size: 17, weight: .semibold, design: .default)
    
    // MARK: Callout
    static let callout = Font.system(size: 16, weight: .regular, design: .default)
    static let calloutSemibold = Font.system(size: 16, weight: .semibold, design: .default)
    
    // MARK: Subheadline
    static let subheadline = Font.system(size: 15, weight: .regular, design: .default)
    static let subheadlineSemibold = Font.system(size: 15, weight: .semibold, design: .default)
    
    // MARK: Footnote
    static let footnote = Font.system(size: 13, weight: .regular, design: .default)
    static let footnoteSemibold = Font.system(size: 13, weight: .semibold, design: .default)
    
    // MARK: Caption
    static let caption1 = Font.system(size: 12, weight: .regular, design: .default)
    static let caption1Semibold = Font.system(size: 12, weight: .semibold, design: .default)
    
    static let caption2 = Font.system(size: 11, weight: .regular, design: .default)
    static let caption2Semibold = Font.system(size: 11, weight: .semibold, design: .default)
    
    // MARK: Dynamic Type Support (HIG-compliant)
    static func dynamicBody(relativeTo textStyle: Font.TextStyle = .body) -> Font {
        .system(textStyle, design: .default)
    }
    
    static func dynamicHeadline(relativeTo textStyle: Font.TextStyle = .headline) -> Font {
        .system(textStyle, design: .default)
    }
    
    static func dynamicCaption(relativeTo textStyle: Font.TextStyle = .caption) -> Font {
        .system(textStyle, design: .default)
    }
}

// MARK: - Radius Semantic Tokens
enum iOS27SemanticRadius {
    static let sm: CGFloat = 4     // Small controls, pills
    static let md: CGFloat = 8     // Standard cards, containers
    static let lg: CGFloat = 12    // Large containers
    static let xl: CGFloat = 16    // Extra large containers
    static let full: CGFloat = 9999 // Pill shapes, avatars (use Capsule())
    
    // Component-specific radii
    static let button: CGFloat = 10      // iOS standard button radius
    static let textField: CGFloat = 10   // iOS standard text field radius
    static let card: CGFloat = 12        // Card radius
    static let sheet: CGFloat = 16       // Sheet/modal radius
    static let alert: CGFloat = 14       // Alert radius
}

// MARK: - Shadow Semantic Tokens
enum iOS27SemanticShadow {
    static let sm = iOS27Primitive.Shadow.sm
    static let md = iOS27Primitive.Shadow.md
    static let lg = iOS27Primitive.Shadow.lg
    
    // Component-specific shadows
    static let card = iOS27Primitive.Shadow.md
    static let floating = iOS27Primitive.Shadow.lg
    static let subtle = iOS27Primitive.Shadow.sm
}

// MARK: - View Modifier for iOS 27 Shadows
struct IOS27ShadowModifier: ViewModifier {
    let shadow: iOS27Primitive.Shadow.ShadowStyle
    
    func body(content: Content) -> some View {
        content.shadow(
            color: shadow.color,
            radius: shadow.radius,
            x: shadow.x,
            y: shadow.y
        )
    }
}

extension View {
    func ios27Shadow(_ shadow: iOS27Primitive.Shadow.ShadowStyle = iOS27SemanticShadow.md) -> some View {
        modifier(IOS27ShadowModifier(shadow: shadow))
    }
    
    func ios27CardShadow() -> some View {
        ios27Shadow(iOS27SemanticShadow.card)
    }
    
    func ios27FloatingShadow() -> some View {
        ios27Shadow(iOS27SemanticShadow.floating)
    }
}
