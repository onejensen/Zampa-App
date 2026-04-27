import SwiftUI

struct LanguagePickerView: View {
    @EnvironmentObject var appState: AppState
    @ObservedObject var localization = LocalizationManager.shared
    @Environment(\.dismiss) var dismiss

    var body: some View {
        List {
            ForEach(LocalizationManager.supportedLanguages, id: \.code) { lang in
                Button {
                    localization.setLanguage(lang.code, userId: appState.currentUser?.id)
                    dismiss()
                } label: {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            if lang.code == "auto" {
                                Text(localization.t("language_auto"))
                                    .font(.appBody)
                                    .foregroundColor(.appTextPrimary)
                                let resolved = localization.resolvedLanguage
                                if let resolvedName = LocalizationManager.supportedLanguages.first(where: { $0.code == resolved })?.nativeName {
                                    Text(resolvedName)
                                        .font(.appCaption)
                                        .foregroundColor(.appTextSecondary)
                                }
                            } else {
                                Text(lang.nativeName)
                                    .font(.appBody)
                                    .foregroundColor(.appTextPrimary)
                            }
                        }
                        Spacer()
                        if localization.currentLanguage == lang.code {
                            Image(systemName: "checkmark")
                                .foregroundColor(.appPrimary)
                                .font(.appLabel)
                        }
                    }
                }
            }
        }
        .navigationTitle(localization.t("language_title"))
        .navigationBarTitleDisplayMode(.inline)
    }
}
