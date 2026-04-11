import AuthenticationServices
import CryptoKit
import FirebaseAuth
import FirebaseCore
import UIKit

#if canImport(GoogleSignIn)
import GoogleSignIn
#endif

@MainActor
final class SocialAuthService: NSObject {

    static let shared = SocialAuthService()
    private override init() {}

    private var currentNonce: String?
    private var appleSignInContinuation: CheckedContinuation<ASAuthorization, Error>?

    // MARK: - Sign in with Apple

    func signInWithApple() async throws -> SocialCredentialResult {
        let nonce = generateNonce()
        currentNonce = nonce

        let provider = ASAuthorizationAppleIDProvider()
        let request  = provider.createRequest()
        request.requestedScopes = [.fullName, .email]
        request.nonce = sha256(nonce)

        let controller = ASAuthorizationController(authorizationRequests: [request])
        controller.delegate = self
        controller.presentationContextProvider = self

        let authorization = try await withCheckedThrowingContinuation { continuation in
            self.appleSignInContinuation = continuation
            controller.performRequests()
        }

        guard
            let appleID   = authorization.credential as? ASAuthorizationAppleIDCredential,
            let tokenData = appleID.identityToken,
            let idToken   = String(data: tokenData, encoding: .utf8),
            let rawNonce  = currentNonce
        else { throw SocialAuthError.invalidCredential }

        let credential = OAuthProvider.appleCredential(
            withIDToken: idToken,
            rawNonce: rawNonce,
            fullName: appleID.fullName
        )

        let name: String? = appleID.fullName.flatMap {
            let s = PersonNameComponentsFormatter().string(from: $0)
            return s.isEmpty ? nil : s
        }

        return SocialCredentialResult(credential: credential, name: name, email: appleID.email)
    }

    // MARK: - Sign in with Google

    #if canImport(GoogleSignIn)
    // Prevents concurrent sign-in attempts from crashing GIDSignIn
    private var isSigningInWithGoogle = false

    func signInWithGoogle() async throws -> SocialCredentialResult {
        guard !isSigningInWithGoogle else { throw SocialAuthError.cancelled }
        isSigningInWithGoogle = true
        defer { isSigningInWithGoogle = false }

        guard let clientID = FirebaseApp.app()?.options.clientID else {
            throw SocialAuthError.missingConfig
        }

        GIDSignIn.sharedInstance.configuration = GIDConfiguration(
            clientID: clientID,
            serverClientID: "840515033444-v88gh7mrsiu6vkc931rjp04urrbn40gj.apps.googleusercontent.com"
        )

        guard let windowScene = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene })
            .first(where: { $0.activationState == .foregroundActive })
        else { throw SocialAuthError.noPresentingViewController }

        // Use a dedicated UIWindow so we never conflict with SwiftUI's
        // presentation stack. SwiftUI manages its own UIKit presentations
        // internally and traversing the existing VC hierarchy always risks
        // hitting a VC mid-transition. A fresh blank window/VC has no
        // presentations, no transitions, and no SwiftUI overhead.
        let signInWindow = UIWindow(windowScene: windowScene)
        let signInVC = UIViewController()
        signInVC.view.backgroundColor = .clear
        signInWindow.rootViewController = signInVC
        signInWindow.windowLevel = .alert
        signInWindow.makeKeyAndVisible()

        defer {
            signInWindow.isHidden = true
            signInWindow.resignKey()
        }

        let result = try await GIDSignIn.sharedInstance.signIn(withPresenting: signInVC)

        guard let idToken = result.user.idToken?.tokenString else {
            throw SocialAuthError.invalidCredential
        }

        let credential = GoogleAuthProvider.credential(
            withIDToken: idToken,
            accessToken: result.user.accessToken.tokenString
        )

        return SocialCredentialResult(
            credential: credential,
            name: result.user.profile?.name,
            email: result.user.profile?.email
        )
    }
    #endif

    // MARK: - Nonce helpers

    private func generateNonce(length: Int = 32) -> String {
        var bytes = [UInt8](repeating: 0, count: length)
        _ = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        let charset = Array("0123456789ABCDEFGHIJKLMNOPQRSTUVXYZabcdefghijklmnopqrstuvwxyz-._")
        return String(bytes.map { charset[Int($0) % charset.count] })
    }

    private func sha256(_ input: String) -> String {
        SHA256.hash(data: Data(input.utf8))
            .map { String(format: "%02x", $0) }
            .joined()
    }
}

// MARK: - ASAuthorizationControllerDelegate

extension SocialAuthService: ASAuthorizationControllerDelegate {
    nonisolated func authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithAuthorization authorization: ASAuthorization
    ) {
        Task { @MainActor in
            appleSignInContinuation?.resume(returning: authorization)
            appleSignInContinuation = nil
        }
    }

    nonisolated func authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithError error: Error
    ) {
        Task { @MainActor in
            let code = (error as? ASAuthorizationError)?.code
            appleSignInContinuation?.resume(throwing: code == .canceled
                ? SocialAuthError.cancelled
                : error)
            appleSignInContinuation = nil
        }
    }
}

// MARK: - ASAuthorizationControllerPresentationContextProviding

extension SocialAuthService: ASAuthorizationControllerPresentationContextProviding {
    // Apple always calls this on the main thread, so assumeIsolated is safe.
    nonisolated func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        MainActor.assumeIsolated {
            UIApplication.shared.connectedScenes
                .compactMap { $0 as? UIWindowScene }
                .flatMap { $0.windows }
                .first { $0.isKeyWindow } ?? UIWindow()
        }
    }
}

// MARK: - Supporting types

struct SocialCredentialResult {
    let credential: AuthCredential
    let name: String?
    let email: String?
}

enum SocialAuthError: LocalizedError {
    case invalidCredential
    case cancelled
    case missingConfig
    case noPresentingViewController

    var errorDescription: String? {
        switch self {
        case .invalidCredential:          return "No se pudieron obtener las credenciales."
        case .cancelled:                  return "Inicio de sesión cancelado."
        case .missingConfig:              return "Configuración de Google Sign-In no encontrada."
        case .noPresentingViewController: return "No se puede mostrar el diálogo de inicio de sesión."
        }
    }
}
