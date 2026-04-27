import SwiftUI

enum TourTarget: String, Hashable {
    case feedCard
    case filterButton
    case mapToggle
    case favoritesTab
    case merchantDashboardTab
    case merchantCreateButton
    case merchantStatsGrid
}

struct TourStep {
    let target: TourTarget
    let titleKey: String
    let descKey: String

    static let clientSteps: [TourStep] = [
        TourStep(target: .feedCard,       titleKey: "tour_feed_title",     descKey: "tour_feed_desc"),
        TourStep(target: .filterButton,   titleKey: "tour_filters_title",  descKey: "tour_filters_desc"),
        TourStep(target: .mapToggle,      titleKey: "tour_map_title",      descKey: "tour_map_desc"),
        TourStep(target: .favoritesTab,   titleKey: "tour_favorites_title",descKey: "tour_favorites_desc"),
    ]

    static let merchantSteps: [TourStep] = [
        TourStep(target: .merchantDashboardTab,  titleKey: "tour_merchant_dashboard_title", descKey: "tour_merchant_dashboard_desc"),
        TourStep(target: .merchantCreateButton,  titleKey: "tour_merchant_create_title",    descKey: "tour_merchant_create_desc"),
        TourStep(target: .merchantStatsGrid,     titleKey: "tour_merchant_stats_title",     descKey: "tour_merchant_stats_desc"),
    ]
}

// MARK: - Preference key + modifier for registering element bounds

struct TourBoundsKey: PreferenceKey {
    typealias Value = [TourTarget: CGRect]
    static var defaultValue: [TourTarget: CGRect] = [:]
    static func reduce(value: inout Value, nextValue: () -> Value) {
        value.merge(nextValue(), uniquingKeysWith: { $1 })
    }
}

struct TourTargetModifier: ViewModifier {
    let target: TourTarget
    @EnvironmentObject var tourManager: TourManager

    func body(content: Content) -> some View {
        content
            .background(
                GeometryReader { geo in
                    Color.clear.preference(
                        key: TourBoundsKey.self,
                        value: [target: geo.frame(in: .global)]
                    )
                }
            )
            .onPreferenceChange(TourBoundsKey.self) { bounds in
                Task { @MainActor in
                    for (t, rect) in bounds {
                        tourManager.register(target: t, bounds: rect)
                    }
                }
            }
    }
}

extension View {
    func tourTarget(_ target: TourTarget) -> some View {
        modifier(TourTargetModifier(target: target))
    }

    @ViewBuilder
    func tourTarget(_ target: TourTarget, when condition: Bool) -> some View {
        if condition {
            modifier(TourTargetModifier(target: target))
        } else {
            self
        }
    }
}
