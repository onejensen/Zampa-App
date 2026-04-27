import SwiftUI

// MARK: - Colors (aligned with design-system/tokens.json)
extension Color {
    // Brand — pure 100% saturated
    static let appPrimary = Color(red: 1.0, green: 0.667, blue: 0.110)             // #FFAA1C  RGB(255,170,28)
    static let appPrimaryDark = Color(red: 0.878, green: 0.549, blue: 0.0)        // #E08C00
    static let appPrimaryLight = Color(red: 1.0, green: 0.8, blue: 0.333)         // #FFCC55
    static let appPrimarySurface = Color(red: 1.0, green: 0.949, blue: 0.851)     // #FFF2D9
    static let appSecondary = Color(red: 0.0, green: 0.8, blue: 0.0)              // #00CC00  HSL(120°,100%,40%)
    static let appSecondaryLight = Color(red: 0.8, green: 1.0, blue: 0.8)         // #CCFFCC  HSL(120°,100%,90%)
    static let appAccent = Color(red: 1.0, green: 0.667, blue: 0.110)             // #FFAA1C
}

// MARK: - Spacing (aligned with design-system/tokens.json)
struct AppSpacing {
    static let xxs: CGFloat = 4
    static let xs: CGFloat = 8
    static let sm: CGFloat = 12
    static let md: CGFloat = 16
    static let lg: CGFloat = 24
    static let xl: CGFloat = 32
    static let xxl: CGFloat = 48
}

// MARK: - Radius (aligned with design-system/tokens.json)
struct AppRadius {
    static let sm: CGFloat = 8
    static let md: CGFloat = 12
    static let lg: CGFloat = 16
    static let xl: CGFloat = 20
}

// MARK: - Fonts (Sora)
extension Font {
    static let appLargeTitle = Font.custom("Sora-Bold", size: 34)
    static let appHeadline = Font.custom("Sora-Bold", size: 28)
    static let appSubheadline = Font.custom("Sora-SemiBold", size: 18)
    static let appBody = Font.custom("Sora-Regular", size: 14)
    static let appBodyLarge = Font.custom("Sora-Regular", size: 16)
    static let appButton = Font.custom("Sora-Bold", size: 16)
    static let appCaption = Font.custom("Sora-Regular", size: 12)
    static let appLabel = Font.custom("Sora-SemiBold", size: 14)
}

// MARK: - Design Constants & Styles
struct AppDesign {
    static let cornerRadius: CGFloat = AppRadius.lg
    static let padding: CGFloat = AppSpacing.md

    struct ButtonStyle: SwiftUI.ButtonStyle {
        var isPrimary: Bool = true
        var isDisabled: Bool = false

        func makeBody(configuration: Configuration) -> some View {
            configuration.label
                .font(.appButton)
                .frame(maxWidth: .infinity)
                .padding()
                .background(
                    RoundedRectangle(cornerRadius: AppRadius.md)
                        .fill(isDisabled ? Color.gray.opacity(0.3) : (isPrimary ? Color.appPrimary : Color.clear))
                )
                .overlay(
                    RoundedRectangle(cornerRadius: AppRadius.md)
                        .stroke(isPrimary ? Color.clear : Color.appTextSecondary.opacity(0.3), lineWidth: 1)
                )
                .foregroundColor(isDisabled ? .gray : (isPrimary ? .white : .appTextPrimary))
                .scaleEffect(configuration.isPressed ? 0.98 : 1.0)
                .animation(.easeOut(duration: 0.2), value: configuration.isPressed)
        }
    }

    struct CardModifier: ViewModifier {
        func body(content: Content) -> some View {
            content
                .background(Color.appSurface)
                .cornerRadius(cornerRadius)
                .shadow(color: Color.appCardShadow, radius: 10, x: 0, y: 4)
        }
    }
}

extension View {
    func appCardStyle() -> some View {
        self.modifier(AppDesign.CardModifier())
    }
}

// MARK: - Reusable Components

struct CategoryPill: View {
    let title: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.appButton)
                .lineLimit(1)
                .fixedSize(horizontal: true, vertical: false)
                .padding(.horizontal, AppSpacing.md)
                .padding(.vertical, AppSpacing.xs)
                .background(isSelected ? Color.appPrimary : Color.appInputBackground)
                .foregroundColor(isSelected ? .white : .appTextPrimary)
                .cornerRadius(AppRadius.xl)
        }
    }
}

struct CustomTextField: View {
    let title: String
    @Binding var text: String
    let icon: String

    var body: some View {
        HStack(spacing: AppSpacing.sm) {
            Image(systemName: icon)
                .foregroundColor(.appTextSecondary)
                .frame(width: 20)
            TextField("", text: $text, prompt: Text(title).foregroundColor(.appTextSecondary))
                .font(.appBody)
                .foregroundColor(.appTextPrimary)
        }
        .padding()
        .background(RoundedRectangle(cornerRadius: AppRadius.md).fill(Color.appInputBackground))
    }
}

struct CustomSecureField: View {
    let title: String
    @Binding var text: String
    let icon: String

    var body: some View {
        HStack(spacing: AppSpacing.sm) {
            Image(systemName: icon)
                .foregroundColor(.appTextSecondary)
                .frame(width: 20)
            SecureField("", text: $text, prompt: Text(title).foregroundColor(.appTextSecondary))
                .font(.appBody)
                .foregroundColor(.appTextPrimary)
        }
        .padding()
        .background(RoundedRectangle(cornerRadius: AppRadius.md).fill(Color.appInputBackground))
    }
}

// MARK: - Filter Toggle

struct FilterToggle: View {
    let title: String
    let icon: String
    @Binding var isOn: Bool

    var body: some View {
        HStack(spacing: AppSpacing.sm) {
            Image(systemName: icon)
                .foregroundColor(.appPrimary)
                .frame(width: 24)
            Text(title)
                .font(.appBody)
                .foregroundColor(.appTextPrimary)
            Spacer()
            Toggle("", isOn: $isOn)
                .labelsHidden()
                .tint(.appPrimary)
        }
        .padding(AppSpacing.sm)
        .background(RoundedRectangle(cornerRadius: AppRadius.sm).fill(Color.appInputBackground))
    }
}
