import SwiftUI

private struct OnboardingPage {
    let icon: String
    let title: String
    let description: String
    let color: Color
}

struct OnboardingView: View {
    let isMerchant: Bool
    let uid: String
    var onFinish: () -> Void

    @State private var currentPage = 0

    private var pages: [OnboardingPage] {
        isMerchant ? merchantPages : clientPages
    }

    private let clientPages: [OnboardingPage] = [
        OnboardingPage(
            icon: "fork.knife.circle.fill",
            title: "Descubre el menú del día",
            description: "Explora la oferta diaria cerca de ti.",
            color: Color(red: 1, green: 107/255, blue: 53/255)
        ),
        OnboardingPage(
            icon: "slider.horizontal.3",
            title: "Filtra a tu medida",
            description: "Por distancia, tipo de cocina o si el local está abierto ahora mismo.",
            color: .blue
        ),
        OnboardingPage(
            icon: "heart.fill",
            title: "Guarda tus favoritos",
            description: "Marca los restaurantes que más te gustan para seguirlos fácilmente.",
            color: .red
        ),
        OnboardingPage(
            icon: "bell.fill",
            title: "No te pierdas nada",
            description: "Activa las notificaciones y entérate de las nuevas ofertas al instante.",
            color: .purple
        )
    ]

    private let merchantPages: [OnboardingPage] = [
        OnboardingPage(
            icon: "plus.circle.fill",
            title: "Publica tu oferta del día",
            description: "Crea tu menú diario en segundos y llega a clientes cerca de tu local.",
            color: Color(red: 1, green: 107/255, blue: 53/255)
        ),
        OnboardingPage(
            icon: "location.fill",
            title: "Visibilidad local",
            description: "Aparece en el feed de usuarios cercanos exactamente cuando más te necesitan.",
            color: .blue
        ),
        OnboardingPage(
            icon: "chart.bar.fill",
            title: "Consulta tus estadísticas",
            description: "Revisa vistas, clics y favoritos diarios desde tu perfil de restaurante.",
            color: .green
        ),
        OnboardingPage(
            icon: "crown.fill",
            title: "Hazte Pro",
            description: "Con Zampa Pro publica varios menús al día y destaca con el badge «Destacado».",
            color: Color(red: 1, green: 0.76, blue: 0)
        )
    ]

    var body: some View {
        ZStack {
            Color.appBackground.ignoresSafeArea()

            VStack(spacing: 0) {
                // Skip button row
                HStack {
                    Spacer()
                    if currentPage < pages.count - 1 {
                        Button("Saltar") { finish() }
                            .font(.appBody)
                            .foregroundColor(.appTextSecondary)
                            .padding(.horizontal, 24)
                            .padding(.top, 16)
                    } else {
                        Color.clear.frame(height: 44).padding(.top, 16)
                    }
                }

                // Swipeable pages
                TabView(selection: $currentPage) {
                    ForEach(Array(pages.enumerated()), id: \.offset) { index, page in
                        OnboardingPageView(page: page).tag(index)
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
                .animation(.easeInOut, value: currentPage)

                // Dots indicator
                HStack(spacing: 8) {
                    ForEach(0..<pages.count, id: \.self) { i in
                        Capsule()
                            .fill(i == currentPage ? Color.appPrimary : Color.appTextSecondary.opacity(0.3))
                            .frame(width: i == currentPage ? 24 : 8, height: 8)
                            .animation(.spring(), value: currentPage)
                    }
                }
                .padding(.bottom, 32)

                // CTA button
                Button {
                    if currentPage < pages.count - 1 {
                        withAnimation { currentPage += 1 }
                    } else {
                        finish()
                    }
                } label: {
                    Text(currentPage < pages.count - 1 ? "Siguiente" : "¡Empezar!")
                        .font(.system(size: 17, weight: .bold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 16)
                        .background(
                            RoundedRectangle(cornerRadius: 16)
                                .fill(Color.appPrimary)
                                .shadow(color: Color.appPrimary.opacity(0.4), radius: 12, x: 0, y: 6)
                        )
                }
                .padding(.horizontal, 24)
                .padding(.bottom, 48)
            }
        }
    }

    private func finish() {
        UserDefaults.standard.set(true, forKey: "hasSeenOnboarding_\(uid)")
        onFinish()
    }
}

private struct OnboardingPageView: View {
    let page: OnboardingPage

    var body: some View {
        VStack(spacing: 32) {
            Spacer()

            ZStack {
                Circle()
                    .fill(page.color.opacity(0.12))
                    .frame(width: 160, height: 160)
                Image(systemName: page.icon)
                    .font(.system(size: 64))
                    .foregroundColor(page.color)
            }

            VStack(spacing: 16) {
                Text(page.title)
                    .font(.system(size: 26, weight: .bold))
                    .foregroundColor(.appTextPrimary)
                    .multilineTextAlignment(.center)

                Text(page.description)
                    .font(.appBody)
                    .foregroundColor(.appTextSecondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 32)
            }

            Spacer()
        }
    }
}
