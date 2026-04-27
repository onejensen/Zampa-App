import Foundation

/// Validación de NIF/CIF/NIE españoles. Input se asume trim + uppercase.
enum TaxIdValidator {
    private static let controlLetters: [Character] = Array("TRWAGMYFPDXBNJZSQVHLCKE")
    private static let cifControlLetters: [Character] = Array("JABCDEFGHI")
    private static let cifFirstLetters: Set<Character> = ["A","B","C","D","E","F","G","H","J","N","P","Q","R","S","U","V","W"]

    static func isValid(_ input: String) -> Bool {
        guard input.count == 9 else { return false }
        let chars = Array(input)
        let first = chars[0]
        if first.isNumber { return isValidNif(chars) }
        if first == "X" || first == "Y" || first == "Z" { return isValidNie(chars) }
        if cifFirstLetters.contains(first) { return isValidCif(chars) }
        return false
    }

    private static func isValidNif(_ chars: [Character]) -> Bool {
        let body = String(chars[0..<8])
        guard let n = Int(body) else { return false }
        return chars[8] == controlLetters[n % 23]
    }

    private static func isValidNie(_ chars: [Character]) -> Bool {
        let prefix: String
        switch chars[0] {
        case "X": prefix = "0"
        case "Y": prefix = "1"
        case "Z": prefix = "2"
        default: return false
        }
        let body = prefix + String(chars[1..<8])
        guard let n = Int(body) else { return false }
        return chars[8] == controlLetters[n % 23]
    }

    private static func isValidCif(_ chars: [Character]) -> Bool {
        let digits = chars[1..<8]
        guard digits.allSatisfy({ $0.isNumber }) else { return false }
        var sumEven = 0
        var sumOddTransformed = 0
        for (i, c) in digits.enumerated() {
            let d = c.wholeNumberValue ?? 0
            if i % 2 == 0 {
                let doubled = d * 2
                sumOddTransformed += doubled >= 10 ? doubled - 9 : doubled
            } else {
                sumEven += d
            }
        }
        let total = sumOddTransformed + sumEven
        let controlDigit = (10 - total % 10) % 10
        let controlLetter = cifControlLetters[controlDigit]
        let check = chars[8]
        let first = chars[0]
        let onlyLetter: Set<Character> = ["P", "Q", "R", "S", "W", "N"]
        let onlyDigit: Set<Character> = ["A", "B", "E", "H"]
        if onlyLetter.contains(first) { return check == controlLetter }
        if onlyDigit.contains(first) {
            return check.isNumber && check.wholeNumberValue == controlDigit
        }
        return check == controlLetter || (check.isNumber && check.wholeNumberValue == controlDigit)
    }
}
