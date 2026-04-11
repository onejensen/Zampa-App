import Foundation

extension Date {
    /// Formatea la fecha como string ISO8601
    var iso8601String: String {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter.string(from: self)
    }
    
    /// Crea una fecha desde un string ISO8601
    static func from(iso8601String: String) -> Date? {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter.date(from: iso8601String)
    }
    
    /// Formatea la fecha para mostrar en la UI (ej: "25 Nov 2025")
    func formattedDate() -> String {
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .none
        formatter.locale = Locale(identifier: "es_ES")
        return formatter.string(from: self)
    }
    
    /// Formatea la fecha y hora para mostrar en la UI
    func formattedDateTime() -> String {
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        formatter.locale = Locale(identifier: "es_ES")
        return formatter.string(from: self)
    }
    
    /// Verifica si la fecha es hoy
    var isToday: Bool {
        Calendar.current.isDateInToday(self)
    }
    
    /// Verifica si la fecha es mañana
    var isTomorrow: Bool {
        Calendar.current.isDateInTomorrow(self)
    }
    
    /// Obtiene el inicio del día
    var startOfDay: Date {
        Calendar.current.startOfDay(for: self)
    }
    
    /// Obtiene el final del día
    var endOfDay: Date {
        var components = DateComponents()
        components.day = 1
        components.second = -1
        return Calendar.current.date(byAdding: components, to: startOfDay) ?? self
    }
}





