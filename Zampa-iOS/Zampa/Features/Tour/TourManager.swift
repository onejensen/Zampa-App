import Foundation
import SwiftUI

@MainActor
final class TourManager: ObservableObject {
    @Published private(set) var isActive: Bool = false
    @Published private(set) var currentStepIndex: Int = 0
    @Published var targetBounds: [TourTarget: CGRect] = [:]

    private var steps: [TourStep] = []
    private var uid: String = ""

    // MARK: - Public API

    func start(for uid: String, isMerchant: Bool) {
        guard !UserDefaults.standard.bool(forKey: "hasSeenTour_\(uid)") else { return }
        self.uid = uid
        self.steps = isMerchant ? TourStep.merchantSteps : TourStep.clientSteps
        self.currentStepIndex = 0
        self.isActive = true
    }

    func next() {
        if currentStepIndex < steps.count - 1 {
            currentStepIndex += 1
        } else {
            finish()
        }
    }

    func skip() {
        finish()
    }

    func register(target: TourTarget, bounds: CGRect) {
        targetBounds[target] = bounds
    }

    // MARK: - Derived state

    var currentStep: TourStep? {
        guard isActive, currentStepIndex < steps.count else { return nil }
        return steps[currentStepIndex]
    }

    var isLastStep: Bool { currentStepIndex == steps.count - 1 }
    var totalSteps: Int { steps.count }

    var currentTargetBounds: CGRect? {
        guard let step = currentStep else { return nil }
        return targetBounds[step.target]
    }

    // MARK: - Private

    private func finish() {
        UserDefaults.standard.set(true, forKey: "hasSeenTour_\(uid)")
        isActive = false
    }
}
