import SwiftUI

// MARK: - Colors (aligned with design-system/tokens.json)
extension Color {
    // Brand
    static let appPrimary = Color(red: 0.980, green: 0.686, blue: 0.196)            // #FAAF32
    static let appPrimaryDark = Color(red: 0.820, green: 0.545, blue: 0.086)       // #D18B16
    static let appPrimaryLight = Color(red: 1.000, green: 0.820, blue: 0.510)      // #FFD182
    static let appPrimarySurface = Color(red: 1.000, green: 0.969, blue: 0.878)    // #FFF7E0
    static let appSecondary = Color(red: 76/255, green: 175/255, blue: 80/255)     // #4CAF50
    static let appSecondaryLight = Color(red: 232/255, green: 245/255, blue: 233/255) // #E8F5E9
    static let appAccent = Color(red: 0.980, green: 0.686, blue: 0.196)            // #FAAF32
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

// MARK: - Fonts (aligned with design-system/tokens.json)
extension Font {
    static let appLargeTitle = Font.system(size: 34, weight: .bold, design: .default)
    static let appHeadline = Font.system(size: 28, weight: .bold, design: .default)
    static let appSubheadline = Font.system(size: 18, weight: .semibold, design: .default)
    static let appBody = Font.system(size: 14, weight: .regular, design: .default)
    static let appBodyLarge = Font.system(size: 16, weight: .regular, design: .default)
    static let appButton = Font.system(size: 16, weight: .bold, design: .default)
    static let appCaption = Font.system(size: 12, weight: .regular, design: .default)
    static let appLabel = Font.system(size: 14, weight: .semibold, design: .default)
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
