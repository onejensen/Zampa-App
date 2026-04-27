package com.sozolab.zampa.data

/**
 * Validación de NIF/CIF/NIE españoles. Input se asume trim()+uppercase().
 *
 * Formatos aceptados (9 caracteres):
 *  - NIF persona física: 8 dígitos + letra de control (mod 23 sobre "TRWAGMYFPDXBNJZSQVHLCKE")
 *  - NIE: X/Y/Z + 7 dígitos + letra de control (prefijo se sustituye por 0/1/2)
 *  - NIF empresa (legacy CIF): A/B/C/D/E/F/G/H/J/N/P/Q/R/S/U/V/W + 7 dígitos + carácter de control
 *    (dígito o letra según letra inicial; algoritmo de suma de dígitos en pos pares/impares)
 */
object TaxIdValidator {
    private const val CONTROL_LETTERS = "TRWAGMYFPDXBNJZSQVHLCKE"
    private const val CIF_CONTROL_LETTERS = "JABCDEFGHI"
    private val CIF_FIRST_LETTERS = setOf('A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'J', 'N', 'P', 'Q', 'R', 'S', 'U', 'V', 'W')

    fun isValid(input: String): Boolean {
        if (input.length != 9) return false
        val first = input[0]
        return when {
            first.isDigit() -> isValidNif(input)
            first == 'X' || first == 'Y' || first == 'Z' -> isValidNie(input)
            first in CIF_FIRST_LETTERS -> isValidCif(input)
            else -> false
        }
    }

    private fun isValidNif(s: String): Boolean {
        val num = s.substring(0, 8).toIntOrNull() ?: return false
        return s[8] == CONTROL_LETTERS[num % 23]
    }

    private fun isValidNie(s: String): Boolean {
        val prefix = when (s[0]) { 'X' -> "0"; 'Y' -> "1"; 'Z' -> "2"; else -> return false }
        val num = (prefix + s.substring(1, 8)).toIntOrNull() ?: return false
        return s[8] == CONTROL_LETTERS[num % 23]
    }

    private fun isValidCif(s: String): Boolean {
        val digits = s.substring(1, 8)
        if (!digits.all { it.isDigit() }) return false

        var sumEven = 0
        var sumOddTransformed = 0
        digits.forEachIndexed { i, c ->
            val d = c.digitToInt()
            if (i % 2 == 0) {
                // Posiciones impares (1ª, 3ª, 5ª, 7ª contando desde 1): multiplicar por 2 y sumar dígitos
                val doubled = d * 2
                sumOddTransformed += if (doubled >= 10) doubled - 9 else doubled
            } else {
                sumEven += d
            }
        }
        val total = sumOddTransformed + sumEven
        val controlDigit = (10 - total % 10) % 10
        val controlLetter = CIF_CONTROL_LETTERS[controlDigit]

        val check = s[8]
        val first = s[0]
        // Entidades que sólo aceptan letra: P, Q, R, S, W, N. Otras dígito o ambos.
        val onlyLetter = first in setOf('P', 'Q', 'R', 'S', 'W', 'N')
        val onlyDigit = first in setOf('A', 'B', 'E', 'H')
        return when {
            onlyLetter -> check == controlLetter
            onlyDigit -> check.isDigit() && check == controlDigit.digitToChar()
            else -> check == controlLetter || (check.isDigit() && check == controlDigit.digitToChar())
        }
    }
}
