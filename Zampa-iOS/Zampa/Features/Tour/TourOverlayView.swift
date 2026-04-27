import SwiftUI

struct TourOverlayView: View {
    @EnvironmentObject var tourManager: TourManager
    @ObservedObject var localization = LocalizationManager.shared

    var body: some View {
        GeometryReader { geo in
            // TourTargetModifier registra bounds en .global (origen = esquina
            // superior-izquierda física de la pantalla). Con .ignoresSafeArea()
            // este overlay también empieza en (0,0) físico, así que los
            // sistemas de coordenadas coinciden directamente — sin conversión.
            if let step = tourManager.currentStep {
                let targetRect = tourManager.currentTargetBounds ?? CGRect(
                    x: geo.size.width / 2 - 100,
                    y: geo.size.height / 2 - 50,
                    width: 200, height: 100
                )
                ZStack(alignment: .topLeading) {
                    SpotlightShape(cutout: targetRect.insetBy(dx: -6, dy: -6).with(cornerRadius: 12))
                        .fill(Color.black.opacity(0.78), style: FillStyle(eoFill: true))
                        .ignoresSafeArea()
                        .animation(.easeInOut(duration: 0.25), value: tourManager.currentStepIndex)

                    TourTooltipView(
                        titleKey: step.titleKey,
                        descKey: step.descKey,
                        stepIndex: tourManager.currentStepIndex,
                        totalSteps: tourManager.totalSteps,
                        isLast: tourManager.isLastStep,
                        targetRect: targetRect,
                        screenSize: geo.size
                    )
                }
            }
        }
        .ignoresSafeArea()
    }
}

// MARK: - Spotlight shape (dark mask with rounded-rect cutout)

struct SpotlightShape: Shape {
    var cutoutRect: CGRect
    var cornerRadius: CGFloat

    init(cutout: SpotlightCutout) {
        self.cutoutRect = cutout.rect
        self.cornerRadius = cutout.cornerRadius
    }

    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.addRect(rect)
        path.addRoundedRect(
            in: cutoutRect,
            cornerSize: CGSize(width: cornerRadius, height: cornerRadius)
        )
        return path
    }

    var animatableData: AnimatablePair<AnimatablePair<CGFloat, CGFloat>, AnimatablePair<CGFloat, CGFloat>> {
        get {
            AnimatablePair(
                AnimatablePair(cutoutRect.origin.x, cutoutRect.origin.y),
                AnimatablePair(cutoutRect.width, cutoutRect.height)
            )
        }
        set {
            cutoutRect = CGRect(
                x: newValue.first.first,
                y: newValue.first.second,
                width: newValue.second.first,
                height: newValue.second.second
            )
        }
    }
}

struct SpotlightCutout {
    let rect: CGRect
    let cornerRadius: CGFloat
}

extension CGRect {
    func with(cornerRadius: CGFloat) -> SpotlightCutout {
        SpotlightCutout(rect: self, cornerRadius: cornerRadius)
    }
}

// MARK: - Tooltip bubble

struct TourTooltipView: View {
    @EnvironmentObject var tourManager: TourManager
    @ObservedObject var localization = LocalizationManager.shared

    let titleKey: String
    let descKey: String
    let stepIndex: Int
    let totalSteps: Int
    let isLast: Bool
    let targetRect: CGRect
    let screenSize: CGSize

    private var showAbove: Bool {
        targetRect.midY > screenSize.height * 0.55
    }

    private var tooltipX: CGFloat {
        min(max(targetRect.midX - 130, 16), screenSize.width - 276)
    }

    private var tooltipY: CGFloat {
        showAbove
            ? targetRect.minY - 160
            : targetRect.maxY + 12
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if showAbove {
                tooltipCard
                arrowView(pointingDown: true)
                    .padding(.leading, arrowLeadingPadding)
            } else {
                arrowView(pointingDown: false)
                    .padding(.leading, arrowLeadingPadding)
                tooltipCard
            }
        }
        .frame(width: 260)
        .position(x: tooltipX + 130, y: tooltipY + (showAbove ? 60 : 60))
        .animation(.easeInOut(duration: 0.25), value: stepIndex)
    }

    private var arrowLeadingPadding: CGFloat {
        min(max(targetRect.midX - tooltipX - 8, 12), 230)
    }

    private var tooltipCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top) {
                Text(localization.t(titleKey))
                    .font(.custom("Sora-SemiBold", size: 14))
                    .foregroundColor(.black)
                Spacer()
                Text("\(stepIndex + 1) / \(totalSteps)")
                    .font(.custom("Sora-Regular", size: 11))
                    .foregroundColor(.gray)
            }
            Text(localization.t(descKey))
                .font(.custom("Sora-Regular", size: 12))
                .foregroundColor(Color(.systemGray))
                .lineSpacing(3)
                .fixedSize(horizontal: false, vertical: true)
            HStack {
                Button(localization.t("tour_skip")) { tourManager.skip() }
                    .font(.custom("Sora-Regular", size: 12))
                    .foregroundColor(.gray)
                Spacer()
                Button(isLast ? localization.t("tour_finish") : localization.t("tour_next")) {
                    tourManager.next()
                }
                .font(.custom("Sora-SemiBold", size: 13))
                .foregroundColor(.black)
                .padding(.horizontal, 14)
                .padding(.vertical, 7)
                .background(Color.appPrimary)
                .cornerRadius(8)
            }
        }
        .padding(14)
        .background(Color.white)
        .cornerRadius(12)
        .shadow(color: .black.opacity(0.2), radius: 12, y: 4)
    }

    @ViewBuilder
    private func arrowView(pointingDown: Bool) -> some View {
        TourArrowShape(pointingDown: pointingDown)
            .fill(Color.white)
            .frame(width: 16, height: 8)
    }
}

// MARK: - Triangle arrow shape

struct TourArrowShape: Shape {
    var pointingDown: Bool

    func path(in rect: CGRect) -> Path {
        var path = Path()
        if pointingDown {
            path.move(to: CGPoint(x: rect.minX, y: rect.minY))
            path.addLine(to: CGPoint(x: rect.maxX, y: rect.minY))
            path.addLine(to: CGPoint(x: rect.midX, y: rect.maxY))
        } else {
            path.move(to: CGPoint(x: rect.midX, y: rect.minY))
            path.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY))
            path.addLine(to: CGPoint(x: rect.minX, y: rect.maxY))
        }
        path.closeSubpath()
        return path
    }
}
