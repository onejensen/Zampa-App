import SwiftUI
import FirebaseFirestore

final class LocalizationManager: ObservableObject {
    static let shared = LocalizationManager()

    @Published var currentLanguage: String {
        didSet {
            UserDefaults.standard.set(currentLanguage, forKey: "appLanguage")
            loadStrings()
        }
    }

    private var strings: [String: String] = [:]

    static let supportedLanguages: [(code: String, nativeName: String)] = [
        ("auto", "Automático"),
        ("es", "Español"),
        ("ca", "Català"),
        ("eu", "Euskara"),
        ("gl", "Galego"),
        ("en", "English"),
        ("de", "Deutsch"),
        ("fr", "Français"),
        ("it", "Italiano"),
    ]

    private init() {
        self.currentLanguage = UserDefaults.standard.string(forKey: "appLanguage") ?? "auto"
        loadStrings()
    }

    var resolvedLanguage: String {
        if currentLanguage == "auto" {
            let systemLang = Locale.current.language.languageCode?.identifier ?? "es"
            let supported = Self.supportedLanguages.map { $0.code }.filter { $0 != "auto" }
            return supported.contains(systemLang) ? systemLang : "es"
        }
        return currentLanguage
    }

    var resolvedLanguageNativeName: String {
        if currentLanguage == "auto" {
            let resolved = resolvedLanguage
            let systemName = Self.supportedLanguages.first { $0.code == resolved }?.nativeName ?? "Español"
            return "Automático (\(systemName))"
        }
        return Self.supportedLanguages.first { $0.code == currentLanguage }?.nativeName ?? currentLanguage
    }

    private func loadStrings() {
        let lang = resolvedLanguage
        guard let url = Bundle.main.url(forResource: lang, withExtension: "json", subdirectory: nil),
              let data = try? Data(contentsOf: url),
              let dict = try? JSONDecoder().decode([String: String].self, from: data) else {
            // Fallback to Spanish
            if lang != "es",
               let url = Bundle.main.url(forResource: "es", withExtension: "json", subdirectory: nil),
               let data = try? Data(contentsOf: url),
               let dict = try? JSONDecoder().decode([String: String].self, from: data) {
                strings = dict
            }
            return
        }
        strings = dict
    }

    func t(_ key: String) -> String {
        strings[key] ?? key
    }

    func setLanguage(_ code: String, userId: String?) {
        currentLanguage = code
        guard let userId = userId else { return }
        Firestore.firestore().collection("users").document(userId).updateData([
            "languagePreference": code
        ])
    }

    func syncFromFirebase(userId: String) {
        Firestore.firestore().collection("users").document(userId).getDocument { [weak self] snapshot, _ in
            if let lang = snapshot?.data()?["languagePreference"] as? String, !lang.isEmpty {
                DispatchQueue.main.async {
                    self?.currentLanguage = lang
                }
            }
        }
    }
}
