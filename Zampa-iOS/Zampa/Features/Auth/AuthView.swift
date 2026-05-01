import SwiftUI
import AuthenticationServices

#if canImport(GoogleSignIn)
import GoogleSignIn
#endif

struct AuthView: View {
    @EnvironmentObject var appState: AppState
    @ObservedObject var localization = LocalizationManager.shared
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
                ScrollView {
                    VStack(alignment: .leading, spacing: 24) {
                        // ── Hero edge-to-edge, naranja hasta arriba ──────
                        // El naranja se extiende por detrás del status bar (safe area
                        // top ignorada). Logo + "Zampa" ahora dentro del hero, en blanco.
                        ZStack(alignment: .topLeading) {
                            Color.appPrimary
                                .ignoresSafeArea(edges: .top)

                            // Decoración: logo grande translúcido saliendo por la derecha
                            Image("Logo")
                                .resizable()
                                .scaledToFit()
                                .foregroundColor(.white)
                                .opacity(0.18)
                                .frame(width: 280, height: 280)
                                .offset(x: 80, y: 30)
                                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .trailing)
                                .clipped()

                            // Logo + "Zampa" top-left (integrado en el hero)
                            HStack(spacing: 8) {
                                Image("Logo")
                                    .resizable()
                                    .scaledToFit()
                                    .frame(width: 40, height: 40)
                                    .foregroundColor(.white)
                                Text("Zampa")
                                    .font(.custom("Sora-Bold", size: 20))
                                    .foregroundColor(.white)
                            }
                            .padding(.top, 12)
                            .padding(.leading, 20)

                            // Eyebrow + headline bottom-left
                            VStack(alignment: .leading, spacing: 8) {
                                Text(localization.t("auth_eyebrow"))
                                    .font(.custom("Sora-SemiBold", size: 12))
                                    .foregroundColor(.white.opacity(0.92))
                                    .kerning(1.5)
                                    .textCase(.uppercase)
                                Text(localization.t("auth_headline"))
                                    .font(.custom("Sora-Bold", size: 34))
                                    .foregroundColor(.white)
                                    .fixedSize(horizontal: false, vertical: true)
                            }
                            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomLeading)
                            .padding(.leading, 24)
                            .padding(.bottom, 24)
                        }
                        .frame(height: 320)
                        .frame(maxWidth: .infinity)

                        // ── Subtitle ──────────────────────────────────────
                        Text(localization.t("auth_body_subtitle"))
                            .font(.appBody)
                            .foregroundColor(.appTextSecondary)
                            .lineSpacing(4)
                            .padding(.horizontal, 24)

                        VStack(spacing: 16) {
                            // ── Social buttons ───────────────────────────
                            // Apple = CTA primario (naranja brand, texto oscuro).
                            // Botón custom porque SignInWithAppleButton no permite
                            // color custom. Se dispara el mismo flujo nativo.
                            Button(action: handleAppleSignIn) {
                                HStack(spacing: 10) {
                                    Image(systemName: "apple.logo")
                                        .font(.custom("Sora-Medium", size: 18))
                                    Text(localization.t("auth_continue_apple"))
                                        .font(.custom("Sora-Medium", size: 16))
                                }
                                .foregroundColor(.appTextPrimary)
                                .frame(maxWidth: .infinity)
                                .frame(height: 50)
                                .background(Color.appPrimary)
                                .cornerRadius(12)
                            }
                            .disabled(isLoading)
                            .opacity(isLoading ? 0.5 : 1)

                            #if canImport(GoogleSignIn)
                            Button(action: handleGoogleSignIn) {
                                HStack(spacing: 10) {
                                    Image("GoogleLogo")
                                        .resizable()
                                        .scaledToFit()
                                        .frame(width: 20, height: 20)
                                    Text(localization.t("auth_continue_google"))
                                        .font(.custom("Sora-Medium", size: 16))
                                        .foregroundColor(.white)
                                }
                                .frame(maxWidth: .infinity)
                                .frame(height: 50)
                                .background(Color.black)
                                .cornerRadius(12)
                            }
                            .disabled(isLoading)
                            .opacity(isLoading ? 0.5 : 1)
                            #endif

                            // ── Separator ────────────────────────────────
                            HStack {
                                Rectangle().fill(Color.appTextSecondary.opacity(0.25)).frame(height: 1)
                                Text(localization.t("auth_or_continue_email"))
                                    .font(.appCaption)
                                    .foregroundColor(.appTextSecondary)
                                    .fixedSize()
                                Rectangle().fill(Color.appTextSecondary.opacity(0.25)).frame(height: 1)
                            }

                            // ── Email / password form ────────────────────
                            CustomTextField(title: localization.t("auth_email"), text: $email, icon: "envelope")
                                .keyboardType(.emailAddress)
                                .autocapitalization(.none)

                            CustomSecureField(title: localization.t("auth_password"), text: $password, icon: "lock")

                            if !isLoginMode {
                                CustomTextField(title: localization.t("auth_name"), text: $name, icon: "person")
                                CustomTextField(title: localization.t("auth_phone"), text: $phone, icon: "phone")
                                    .keyboardType(.phonePad)

                                VStack(alignment: .leading, spacing: 12) {
                                    Text(localization.t("auth_account_type"))
                                        .font(.appBody)
                                        .foregroundColor(.appTextSecondary)
                                    Picker(localization.t("auth_account_type"), selection: $selectedRole) {
                                        Text(localization.t("auth_diner")).tag(User.UserRole.cliente)
                                        Text(localization.t("auth_restaurant")).tag(User.UserRole.comercio)
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
                                    Text(isLoginMode ? localization.t("auth_login") : localization.t("auth_register"))
                                }
                            }
                            .buttonStyle(AppDesign.ButtonStyle(isPrimary: true,
                                                               isDisabled: !isValid || isLoading))
                            .disabled(!isValid || isLoading)

                            Button {
                                withAnimation { isLoginMode.toggle() }
                            } label: {
                                Text(isLoginMode
                                     ? localization.t("auth_no_account")
                                     : localization.t("auth_has_account"))
                                    .font(.appButton)
                                    .foregroundColor(.appPrimary)
                            }
                            .padding(.top, 8)
                        }
                        .padding(.horizontal, 24)
                        .padding(.top, 20)
                    }
                    .padding(.bottom, 24)
                }
            }
        }
        .alert(localization.t("common_error"), isPresented: $showingError) {
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


// MARK: - Role Selection Sheet (for new social users)

private struct SocialRoleSelectionView: View {
    let user: User
    let onComplete: (User) -> Void

    @ObservedObject var localization = LocalizationManager.shared
    @Environment(\.dismiss) var dismiss
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
                            Text(localization.t("auth_welcome"))
                                .font(.appHeadline)
                                .foregroundColor(.appTextPrimary)
                            Text(localization.t("auth_welcome_subtitle"))
                                .font(.appBody)
                                .foregroundColor(.appTextSecondary)
                        }

                        VStack(alignment: .leading, spacing: 8) {
                            Text(localization.t("auth_your_name"))
                                .font(.appBody)
                                .foregroundColor(.appTextSecondary)
                            CustomTextField(title: localization.t("auth_name"), text: $displayName, icon: "person")
                        }

                        VStack(alignment: .leading, spacing: 12) {
                            Text(localization.t("auth_how_use"))
                                .font(.appBody)
                                .foregroundColor(.appTextSecondary)

                            roleCard(
                                role: .cliente,
                                title: localization.t("auth_i_am_diner"),
                                subtitle: localization.t("auth_diner_desc"),
                                icon: "fork.knife"
                            )

                            roleCard(
                                role: .comercio,
                                title: localization.t("auth_i_have_restaurant"),
                                subtitle: localization.t("auth_restaurant_desc"),
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
                            Text(localization.t("auth_start"))
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
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(action: { dismiss() }) {
                        Image(systemName: "xmark.circle.fill")
                            .font(.title2)
                            .symbolRenderingMode(.hierarchical)
                            .foregroundColor(.appTextSecondary)
                    }
                }
            }
        }
        .alert(localization.t("common_error"), isPresented: $showError) {
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
                        .font(.custom("Sora-Regular", size: 20))
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
                    .font(.custom("Sora-Regular", size: 22))
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
