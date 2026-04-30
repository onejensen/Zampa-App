import SwiftUI

struct SubscriptionView: View {
    @ObservedObject var localization = LocalizationManager.shared
    @ObservedObject var storeKit = StoreKitManager.shared
    @Environment(\.presentationMode) var presentationMode
    @EnvironmentObject var appState: AppState

    @State private var promoFreeUntil: Date? = nil
    @State private var purchaseSuccessful: Bool = false

    private var merchant: Merchant? { appState.merchantProfile }
    private var status: SubscriptionStatus { merchant?.subscriptionStatus ?? .trial }
    private var trialDays: Int? { merchant?.trialDaysRemaining() }
    private var promoActive: Bool { (promoFreeUntil ?? .distantPast) > Date() }
    private var canPublish: Bool { promoActive || (merchant?.canPublish() ?? true) }

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 24) {
                    statusBanner
                    valueProps
                    priceAndCTA
                }
                .padding(24)
                .padding(.bottom, 40)
            }
            .task {
                do {
                    if let ms = try await FirebaseService.shared.getPromoFreeUntilMs() {
                        promoFreeUntil = Date(timeIntervalSince1970: Double(ms) / 1000)
                    }
                } catch {}
                await storeKit.loadProduct()
            }
            .background(Color.appBackground.ignoresSafeArea())
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    Text(localization.t("subscription_title"))
                        .font(.custom("Sora-Bold", size: 17))
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(localization.t("common_close")) {
                        presentationMode.wrappedValue.dismiss()
                    }
                }
            }
        }
    }

    // MARK: - Estado (trial / activa / expirada)
    @ViewBuilder
    private var statusBanner: some View {
        let colors = bannerColors()
        VStack(spacing: 8) {
            Image(systemName: "star.circle.fill")
                .font(.custom("Sora-Regular", size: 48))
                .foregroundColor(colors.fg)
            Text(bannerTitle())
                .font(.custom("Sora-Bold", size: 20))
                .foregroundColor(colors.fg)
                .multilineTextAlignment(.center)
            if let body = bannerBody() {
                Text(body)
                    .font(.appBody)
                    .foregroundColor(colors.fg.opacity(0.85))
                    .multilineTextAlignment(.center)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(24)
        .background(RoundedRectangle(cornerRadius: 20).fill(colors.bg))
    }

    private func bannerColors() -> (bg: Color, fg: Color) {
        if !canPublish { return (Color.red.opacity(0.12), Color.red) }
        return (Color.appPrimary.opacity(0.12), Color.appPrimary)
    }

    private func bannerTitle() -> String {
        if promoActive { return localization.t("subscription_promo_title") }
        if !canPublish { return localization.t("subscription_expired_title") }
        if status == .active { return localization.t("subscription_active_title") }
        // trial
        guard let days = trialDays else { return localization.t("subscription_title") }
        if days <= 0 { return localization.t("subscription_trial_ends_today") }
        return String(format: localization.t("subscription_trial_days_remaining"), days)
    }

    private func bannerBody() -> String? {
        if promoActive, let until = promoFreeUntil {
            let fmt = DateFormatter()
            fmt.dateStyle = .long
            fmt.locale = Locale.current
            return String(format: localization.t("subscription_promo_body"), fmt.string(from: until))
        }
        if !canPublish { return localization.t("subscription_expired_body") }
        return nil
    }

    // MARK: - Value props
    private var valueProps: some View {
        VStack(alignment: .leading, spacing: 14) {
            ValueRow(text: localization.t("subscription_value_unlimited"))
            ValueRow(text: localization.t("subscription_value_push"))
            ValueRow(text: localization.t("subscription_value_stats"))
            ValueRow(text: localization.t("subscription_value_profile"))
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 20).fill(Color.appSurface))
    }

    // MARK: - Precio + CTA
    private var priceAndCTA: some View {
        VStack(spacing: 16) {
            // Precio: si StoreKit cargó el producto, interpolamos su `displayPrice`
            // (formato localizado por Apple) en el sufijo localizado. Sin producto
            // cargado, fallback al string completo traducido.
            Text(storeKit.product.map {
                String(format: localization.t("subscription_price_format"), $0.displayPrice)
            } ?? localization.t("subscription_price_monthly"))
                .font(.custom("Sora-Bold", size: 28))
                .foregroundColor(.appPrimary)

            Button(action: {
                Task {
                    let ok = await storeKit.purchase()
                    if ok {
                        purchaseSuccessful = true
                        // Refrescar el merchantProfile tras unos segundos para
                        // recoger el cambio de subscriptionStatus que escribe el webhook.
                        try? await Task.sleep(nanoseconds: 3_000_000_000)
                        if let uid = appState.currentUser?.id,
                           let m = try? await FirebaseService.shared.getMerchantProfile(merchantId: uid) {
                            appState.merchantProfile = m
                        }
                    }
                }
            }) {
                VStack(spacing: 4) {
                    HStack(spacing: 10) {
                        if storeKit.isPurchasing {
                            ProgressView().progressViewStyle(.circular).tint(.white)
                        }
                        Text(localization.t("subscription_subscribe_cta_now"))
                            .font(.appHeadline)
                            .fontWeight(.bold)
                    }
                    Text(localization.t("subscription_no_commitment"))
                        .font(.custom("Sora-Regular", size: 12))
                        .opacity(0.85)
                }
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .background(RoundedRectangle(cornerRadius: 12).fill(
                    canPurchase ? Color.appPrimary : Color.appPrimary.opacity(0.5)
                ))
            }
            .disabled(!canPurchase)

            if let err = storeKit.lastError {
                Text(err)
                    .font(.appCaption)
                    .foregroundColor(.red)
                    .multilineTextAlignment(.center)
            }
        }
        .alert(localization.t("subscription_active_title"), isPresented: $purchaseSuccessful) {
            Button(localization.t("common_ok"), role: .cancel) {
                presentationMode.wrappedValue.dismiss()
            }
        }
    }

    /// Sólo permitir comprar si: (1) producto cargado, (2) no hay compra en curso,
    /// (3) no estamos en periodo gratis (promo o trial activo).
    private var canPurchase: Bool {
        storeKit.product != nil
            && !storeKit.isPurchasing
            && !promoActive
            && status != .active
    }
}

private struct ValueRow: View {
    let text: String
    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "checkmark.circle.fill")
                .font(.custom("Sora-Regular", size: 18))
                .foregroundColor(.appPrimary)
            Text(text)
                .font(.appSubheadline)
                .foregroundColor(.appTextPrimary)
            Spacer()
        }
    }
}

#Preview {
    SubscriptionView()
        .environmentObject(AppState())
}
