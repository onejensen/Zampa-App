import SwiftUI
import AuthenticationServices

#if canImport(GoogleSignIn)
import GoogleSignIn
#endif

struct AuthView: View {
    @EnvironmentObject var appState: AppState
    @State private var email:        String = ""
    @State private var password:     String = ""
    @State private var name:         String = ""
    @State private var phone:        String = ""
    @State private var isLoginMode:  Bool   = true
    @State private var showingError: Bool   = false
    @State private var errorMessage: String = ""
    @State private var isLoading:    Bool   = false
    @State private var selectedRole: User.UserRole = .cliente

    // Social sign-in: usuario nuevo pendiente de elegir rol
    @State private var pendingSocialUser: User?
    @State private var showRoleSheet:     Bool = false

    private var isValid: Bool {
        if isLoginMode { return !email.isEmpty && !password.isEmpty }
        return !email.isEmpty && !password.isEmpty && !name.isEmpty
    }

    var body: some View {
        ZStack {
            Color.appBackground.ignoresSafeArea()

            VStack(spacing: 0) {
                // Header
                HStack {
                    Image("Logo")
                        .resizable()
                        .scaledToFit()
                        .frame(width: 36, height: 36)
                        .colorMultiply(.appPrimary)
                    Text("Zampa")
                        .font(.appSubheadline)
                        .fontWeight(.bold)
                        .foregroundColor(.appTextPrimary)
                    Spacer()
                }
                .padding(.horizontal)
                .padding(.top, 10)

                ScrollView {
                    VStack(alignment: .leading, spacing: 24) {
                        // Hero
                        ZStack {
                            RoundedRectangle(cornerRadius: 24)
                                .fill(Color.appPrimary)
                                .frame(height: 300)
                            VStack(spacing: 16) {
                                Image("Logo")
                                    .resizable()
                                    .scaledToFit()
                                    .frame(width: 140, height: 140)
                                Text("Zampa")
                                    .font(.system(size: 38, weight: .bold))
                                    .foregroundColor(.white)
                            }
                        }
                        .padding(.top)

                        VStack(alignment: .leading, spacing: 12) {
                            Text("Come bien,\n")
                                .font(.appHeadline)
                                .foregroundColor(.appTextPrimary)
                            + Text("paga lo justo.")
                                .font(.appHeadline)
                                .foregroundColor(.appPrimary)

                            Text("Explora los menús del día de tus restaurantes favoritos. Filtra por cercanía o precio y reserva tu mesa hoy mismo.")
                                .font(.appBody)
                                .foregroundColor(.appTextSecondary)
                                .lineSpacing(4)
                        }

                        VStack(spacing: 16) {
                            // ── Social buttons ───────────────────────────
                            SignInWithAppleButton(
                                .continue,
                                onRequest:  { _ in },
                                onCompletion: { _ in }   // handled manually below
                            )
                            .signInWithAppleButtonStyle(.black)
                            .frame(height: 50)
                            .cornerRadius(12)
                            .onTapGesture { handleAppleSignIn() }

                            #if canImport(GoogleSignIn)
                            GoogleSignInButton(action: handleGoogleSignIn)
                                .disabled(isLoading)
                                .opacity(isLoading ? 0.5 : 1)
                            #endif

                            // ── Separator ────────────────────────────────
                            HStack {
                                Rectangle().fill(Color.appTextSecondary.opacity(0.25)).frame(height: 1)
                                Text("o continúa con email")
                                    .font(.appCaption)
                                    .foregroundColor(.appTextSecondary)
                                    .fixedSize()
                                Rectangle().fill(Color.appTextSecondary.opacity(0.25)).frame(height: 1)
                            }

                            // ── Email / password form ────────────────────
                            CustomTextField(title: "Email", text: $email, icon: "envelope")
                                .keyboardType(.emailAddress)
                                .autocapitalization(.none)

                            CustomSecureField(title: "Contraseña", text: $password, icon: "lock")

                            if !isLoginMode {
                                CustomTextField(title: "Nombre", text: $name, icon: "person")
                                CustomTextField(title: "Teléfono (opcional)", text: $phone, icon: "phone")
                                    .keyboardType(.phonePad)

                                VStack(alignment: .leading, spacing: 12) {
                                    Text("Tipo de cuenta")
                                        .font(.appBody)
                                        .foregroundColor(.appTextSecondary)
                                    Picker("Rol", selection: $selectedRole) {
                                        Text("Comensal").tag(User.UserRole.cliente)
                                        Text("Restaurante").tag(User.UserRole.comercio)
                                    }
                                    .pickerStyle(SegmentedPickerStyle())
                                }
                                .padding(.horizontal, 4)
                            }

                            Button(action: {
                                isLoginMode ? handleLogin() : handleRegister()
                            }) {
                                if isLoading {
                                    ProgressView()
                                        .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                } else {
                                    Text(isLoginMode ? "Iniciar sesión" : "Registrarse")
                                }
                            }
                            .buttonStyle(AppDesign.ButtonStyle(isPrimary: true,
                                                               isDisabled: !isValid || isLoading))
                            .disabled(!isValid || isLoading)

                            Button {
                                withAnimation { isLoginMode.toggle() }
                            } label: {
                                Text(isLoginMode
                                     ? "¿No tienes cuenta? Regístrate"
                                     : "¿Ya tienes cuenta? Inicia sesión")
                                    .font(.appButton)
                                    .foregroundColor(.appPrimary)
                            }
                            .padding(.top, 8)
                        }
                        .padding(.top, 20)
                    }
                    .padding(24)
                }
            }
        }
        .alert("Error", isPresented: $showingError) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(errorMessage)
        }
        .sheet(isPresented: $showRoleSheet) {
            if let user = pendingSocialUser {
                SocialRoleSelectionView(user: user) { finalUser in
                    showRoleSheet = false
                    pendingSocialUser = nil
                    appState.setAuthenticated(user: finalUser)
                }
            }
        }
    }

    // MARK: - Email handlers

    private func handleLogin() {
        isLoading = true
        Task {
            do {
                let user = try await FirebaseService.shared.login(email: email, password: password)
                await MainActor.run { isLoading = false; appState.setAuthenticated(user: user) }
            } catch {
                await MainActor.run { isLoading = false; showError(error.localizedDescription) }
            }
        }
    }

    private func handleRegister() {
        isLoading = true
        Task {
            do {
                let user = try await FirebaseService.shared.register(
                    email: email, password: password,
                    name: name.isEmpty ? email.components(separatedBy: "@").first ?? "Usuario" : name,
                    role: selectedRole,
                    phone: phone.isEmpty ? nil : phone
                )
                await MainActor.run { isLoading = false; appState.setAuthenticated(user: user) }
            } catch {
                await MainActor.run { isLoading = false; showError(error.localizedDescription) }
            }
        }
    }

    // MARK: - Social handlers

    private func handleAppleSignIn() {
        isLoading = true
        Task {
            do {
                let result = try await SocialAuthService.shared.signInWithApple()
                await completeSocialSignIn(result)
            } catch SocialAuthError.cancelled {
                await MainActor.run { isLoading = false }
            } catch {
                await MainActor.run { isLoading = false; showError(error.localizedDescription) }
            }
        }
    }

    #if canImport(GoogleSignIn)
    private func handleGoogleSignIn() {
        isLoading = true
        Task {
            do {
                let result = try await SocialAuthService.shared.signInWithGoogle()
                await completeSocialSignIn(result)
            } catch SocialAuthError.cancelled {
                await MainActor.run { isLoading = false }
            } catch {
                await MainActor.run { isLoading = false; showError(error.localizedDescription) }
            }
        }
    }
    #endif

    private func completeSocialSignIn(_ result: SocialCredentialResult) async {
        do {
            let (user, isNewUser) = try await FirebaseService.shared.loginWithSocialCredential(
                result.credential, name: result.name, email: result.email
            )
            await MainActor.run {
                isLoading = false
                if isNewUser {
                    pendingSocialUser = user
                    showRoleSheet = true
                } else {
                    appState.setAuthenticated(user: user)
                }
            }
        } catch {
            await MainActor.run { isLoading = false; showError(error.localizedDescription) }
        }
    }

    private func showError(_ message: String) {
        errorMessage = message
        showingError = true
    }
}

// MARK: - Google Sign-In Button

#if canImport(GoogleSignIn)
private struct GoogleSignInButton: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 10) {
                Image(systemName: "globe")
                    .font(.system(size: 18, weight: .medium))
                    .foregroundColor(.primary)
                Text("Continuar con Google")
                    .font(.system(size: 16, weight: .medium))
                    .foregroundColor(.primary)
            }
            .frame(maxWidth: .infinity)
            .frame(height: 50)
            .background(Color(.systemBackground))
            .cornerRadius(12)
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(Color.secondary.opacity(0.35), lineWidth: 1)
            )
        }
    }
}
#endif

// MARK: - Role Selection Sheet (for new social users)

private struct SocialRoleSelectionView: View {
    let user: User
    let onComplete: (User) -> Void

    @State private var selectedRole: User.UserRole = .cliente
    @State private var displayName: String
    @State private var isLoading = false
    @State private var showError = false
    @State private var errorMsg  = ""

    init(user: User, onComplete: @escaping (User) -> Void) {
        self.user = user
        self.onComplete = onComplete
        _displayName = State(initialValue: user.name)
    }

    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                ScrollView {
                    VStack(alignment: .leading, spacing: 28) {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("¡Bienvenido/a!")
                                .font(.appHeadline)
                                .foregroundColor(.appTextPrimary)
                            Text("Cuéntanos un poco más para configurar tu cuenta.")
                                .font(.appBody)
                                .foregroundColor(.appTextSecondary)
                        }

                        VStack(alignment: .leading, spacing: 8) {
                            Text("Tu nombre")
                                .font(.appBody)
                                .foregroundColor(.appTextSecondary)
                            CustomTextField(title: "Nombre", text: $displayName, icon: "person")
                        }

                        VStack(alignment: .leading, spacing: 12) {
                            Text("¿Cómo quieres usar Zampa?")
                                .font(.appBody)
                                .foregroundColor(.appTextSecondary)

                            roleCard(
                                role: .cliente,
                                title: "Soy comensal",
                                subtitle: "Exploro menús y encuentro restaurantes cercanos.",
                                icon: "fork.knife"
                            )

                            roleCard(
                                role: .comercio,
                                title: "Tengo un restaurante",
                                subtitle: "Publico mi menú del día y atraigo más clientes.",
                                icon: "storefront"
                            )
                        }
                    }
                    .padding(24)
                }

                // CTA
                VStack(spacing: 0) {
                    Divider()
                    Button(action: finalize) {
                        if isLoading {
                            ProgressView()
                                .progressViewStyle(CircularProgressViewStyle(tint: .white))
                        } else {
                            Text("Empezar")
                        }
                    }
                    .buttonStyle(AppDesign.ButtonStyle(
                        isPrimary: true,
                        isDisabled: displayName.trimmingCharacters(in: .whitespaces).isEmpty || isLoading
                    ))
                    .disabled(displayName.trimmingCharacters(in: .whitespaces).isEmpty || isLoading)
                    .padding(24)
                }
                .background(Color.appBackground)
            }
            .navigationBarTitleDisplayMode(.inline)
        }
        .alert("Error", isPresented: $showError) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(errorMsg)
        }
    }

    @ViewBuilder
    private func roleCard(role: User.UserRole, title: String, subtitle: String, icon: String) -> some View {
        let isSelected = selectedRole == role
        Button { selectedRole = role } label: {
            HStack(spacing: 16) {
                ZStack {
                    Circle()
                        .fill(isSelected ? Color.appPrimary : Color.appInputBackground)
                        .frame(width: 48, height: 48)
                    Image(systemName: icon)
                        .font(.system(size: 20))
                        .foregroundColor(isSelected ? .white : .appTextSecondary)
                }
                VStack(alignment: .leading, spacing: 4) {
                    Text(title)
                        .font(.appBody.weight(.semibold))
                        .foregroundColor(.appTextPrimary)
                    Text(subtitle)
                        .font(.appCaption)
                        .foregroundColor(.appTextSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer()
                Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                    .foregroundColor(isSelected ? .appPrimary : .appTextSecondary.opacity(0.4))
                    .font(.system(size: 22))
            }
            .padding(16)
            .background(
                RoundedRectangle(cornerRadius: 14)
                    .fill(Color.appInputBackground)
                    .overlay(
                        RoundedRectangle(cornerRadius: 14)
                            .stroke(isSelected ? Color.appPrimary : Color.clear, lineWidth: 2)
                    )
            )
        }
        .buttonStyle(.plain)
    }

    private func finalize() {
        let trimmedName = displayName.trimmingCharacters(in: .whitespaces)
        isLoading = true
        Task {
            do {
                let finalUser = try await FirebaseService.shared.finalizeSocialRegistration(
                    userId: user.id,
                    role: selectedRole,
                    name: trimmedName,
                    email: user.email
                )
                await MainActor.run { onComplete(finalUser) }
            } catch {
                await MainActor.run {
                    isLoading = false
                    errorMsg  = error.localizedDescription
                    showError = true
                }
            }
        }
    }
}

#Preview {
    AuthView().environmentObject(AppState())
}
