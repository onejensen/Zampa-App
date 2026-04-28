import SwiftUI

/// Day-of-week picker for permanent offers.
/// weekday convention: 0=Monday … 6=Sunday (European Mon-first order).
struct RecurringDaysPicker: View {
    @ObservedObject var localization = LocalizationManager.shared

    /// Days already occupied by other permanent offers of this merchant.
    let occupiedDays: Set<Int>
    /// The merchant's current selection for this offer (excludes occupied).
    @Binding var selectedDays: Set<Int>

    /// Calendar symbols ordered Mon…Sun. Uses device locale automatically.
    private var orderedSymbols: [String] {
        let symbols = Calendar.current.veryShortWeekdaySymbols // index 0=Sun,1=Mon…6=Sat
        // Reorder to Mon-first: [1,2,3,4,5,6,0]
        let order = [1, 2, 3, 4, 5, 6, 0]
        return order.map { symbols[$0] }
    }

    private var freeSlotsCount: Int {
        7 - occupiedDays.count
    }

    private var allOccupied: Bool {
        occupiedDays.count >= 7
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            // Header
            HStack {
                Text(localization.t("create_menu_recurring_days_title"))
                    .font(.custom("Sora-SemiBold", size: 14))
                    .foregroundColor(.appTextPrimary)
                Spacer()
                Text(String(format: localization.t("create_menu_recurring_days_slots_free"), freeSlotsCount))
                    .font(.custom("Sora-SemiBold", size: 12))
                    .foregroundColor(.appPrimary)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 3)
                    .background(Capsule().fill(Color.appPrimarySurface))
            }

            // Day buttons
            HStack(spacing: 6) {
                ForEach(0..<7, id: \.self) { dayIndex in
                    let label = orderedSymbols[dayIndex]
                    let isOccupied = occupiedDays.contains(dayIndex)
                    let isSelected = selectedDays.contains(dayIndex)

                    Button {
                        if !isOccupied {
                            if isSelected {
                                selectedDays.remove(dayIndex)
                            } else {
                                selectedDays.insert(dayIndex)
                            }
                        }
                    } label: {
                        Text(label)
                            .font(.custom("Sora-Bold", size: 13))
                            .frame(maxWidth: .infinity)
                            .frame(height: 38)
                            .foregroundColor(isSelected ? .white : isOccupied ? Color.appTextSecondary.opacity(0.4) : .appTextPrimary)
                            .background(
                                Circle().fill(
                                    isSelected ? Color.appPrimary
                                    : isOccupied ? Color.appInputBackground
                                    : Color.appSurface
                                )
                            )
                            .overlay(
                                Circle().stroke(
                                    isSelected ? Color.appPrimary
                                    : isOccupied ? Color.clear
                                    : Color.appTextSecondary.opacity(0.3),
                                    lineWidth: 1.5
                                )
                            )
                    }
                    .disabled(isOccupied)
                    .buttonStyle(.borderless)
                }
            }

            // All-occupied warning
            if allOccupied {
                Text(localization.t("create_menu_recurring_days_all_occupied"))
                    .font(.custom("Sora-Regular", size: 12))
                    .foregroundColor(.appTextSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(14)
        .background(RoundedRectangle(cornerRadius: 14).fill(Color.appPrimarySurface.opacity(0.5)))
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(Color.appPrimary.opacity(0.25), lineWidth: 1.5)
        )
    }
}

#Preview {
    VStack(spacing: 20) {
        RecurringDaysPicker(
            occupiedDays: [1, 3],
            selectedDays: .constant(Set([0, 2]))
        )
        RecurringDaysPicker(
            occupiedDays: Set(0...6),
            selectedDays: .constant(Set())
        )
    }
    .padding()
}
