import SwiftUI

/// Vista pública con Política de Privacidad y Términos & Condiciones.
/// Contenido local (no carga la landing), para garantizar acceso offline
/// y cumplir con las guidelines de Apple/Google.
struct LegalView: View {
    @Environment(\.presentationMode) var presentationMode
    @ObservedObject var localization = LocalizationManager.shared

    enum Section: Hashable { case privacy, terms }
    @State private var section: Section = .privacy

    private let privacyCount = 7
    private let termsCount = 9

    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                Picker("", selection: $section) {
                    Text(localization.t("legal_privacy_title")).tag(Section.privacy)
                    Text(localization.t("legal_terms_title")).tag(Section.terms)
                }
                .pickerStyle(.segmented)
                .padding(.horizontal, 20)
                .padding(.vertical, 12)

                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        let range = section == .privacy ? 1...privacyCount : 1...termsCount
                        let prefix = section == .privacy ? "legal_privacy" : "legal_terms"
                        ForEach(Array(range), id: \.self) { i in
                            Text(localization.t("\(prefix)_\(i)_title"))
                                .font(.custom("Sora-Bold", size: 16))
                                .foregroundColor(.appTextPrimary)
                            Text(localization.t("\(prefix)_\(i)_body"))
                                .font(.appBody)
                                .foregroundColor(.appTextPrimary)
                                .lineSpacing(4)
                        }
                    }
                    .padding(.horizontal, 24)
                    .padding(.vertical, 16)
                }
            }
            .background(Color.appBackground.ignoresSafeArea())
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    Text(section == .privacy
                         ? localization.t("legal_privacy_title")
                         : localization.t("legal_terms_title"))
                        .font(.custom("Sora-Bold", size: 16))
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: { presentationMode.wrappedValue.dismiss() }) {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundColor(.appTextSecondary)
                    }
                }
            }
        }
    }
}
