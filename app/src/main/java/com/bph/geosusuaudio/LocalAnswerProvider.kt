package com.bph.geosusuaudio

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object LocalAnswerProvider {

    fun findAnswer(rawQuestion: String): String? {
        val q = ResponseRepository.normalize(rawQuestion)
            .replace("\\s+".toRegex(), " ")
            .trim()
        if (q.isBlank()) return null

        // Feriados y dias no laborables cambian por pais, region y decretos.
        // Esas preguntas deben pasar a NET/API para evitar datos fijos desactualizados.
        if (q.contains("feriado") || q.contains("feriados") || q.contains("no laborable") || q.contains("laborables")) {
            return null
        }

        val now = Calendar.getInstance()

        relativeDateAnswer(q, now)?.let { return it }

        if (isCurrentDateQuestion(q)) {
            return formatDateAnswer("Hoy", now)
        }

        if (isCurrentTimeQuestion(q)) {
            val time = SimpleDateFormat("HH:mm", Locale("es", "PE")).format(now.time)
            return "Son las $time."
        }

        if (isCurrentYearQuestion(q)) {
            return "Estamos en el año ${now.get(Calendar.YEAR)}."
        }

        if (asksLeapYear(q)) {
            val years = referencedYears(q, now.get(Calendar.YEAR))
                .ifEmpty { listOf(now.get(Calendar.YEAR)) }
            return buildLeapAnswer(years)
        }

        if (asksDaysInYear(q)) {
            val years = referencedYears(q, now.get(Calendar.YEAR))
            if (years.isNotEmpty()) return buildDaysAnswer(years, q)
        }

        return null
    }

    private fun relativeDateAnswer(q: String, now: Calendar): String? {
        if (q.contains("cuantos dias")) return null

        val offset = when {
            q.contains("pasado manana") -> 2
            q.contains("manana") -> 1
            q.contains("anteayer") -> -2
            q.contains("ayer") -> -1
            else -> null
        } ?: return null

        val asksDateOrDay = q.contains("que dia") ||
            q.contains("que fecha") ||
            q.contains("dia sera") ||
            q.contains("fecha sera") ||
            q.contains("dia es") ||
            q.contains("fecha es")
        if (!asksDateOrDay) return null

        val target = now.clone() as Calendar
        target.add(Calendar.DAY_OF_MONTH, offset)
        val label = when (offset) {
            -2 -> "Anteayer fue"
            -1 -> "Ayer fue"
            1 -> "Mañana será"
            2 -> "Pasado mañana será"
            else -> "Será"
        }
        return formatDateAnswer(label, target)
    }

    private fun isCurrentDateQuestion(q: String): Boolean {
        if (q.contains("cuantos dias")) return false
        if (q.contains("fecha de hoy") || q.contains("que fecha es hoy") || q.contains("fecha actual")) return true
        if (q.contains("que dia es hoy") || q.contains("dia de hoy") || q.contains("dia estamos hoy")) return true
        if (q == "que dia es" || q == "que fecha es") return true
        return false
    }

    private fun isCurrentTimeQuestion(q: String): Boolean {
        return q.contains("que hora es") ||
            q.contains("hora actual") ||
            q == "la hora" ||
            q == "hora"
    }

    private fun isCurrentYearQuestion(q: String): Boolean {
        if (q.contains("cuantos dias")) return false
        return q.contains("en que ano estamos") ||
            q.contains("que ano es") ||
            q.contains("ano actual") ||
            q == "este ano"
    }

    private fun asksDaysInYear(q: String): Boolean {
        val hasDays = q.contains("cuantos dias") || q.contains("cuanto dias") || q.contains("dias tiene") || q.contains("dias tendra")
        val hasYearContext = q.contains(" ano") || q.contains("anos") || q.contains("este ano") || Regex("\\b\\d{2,4}\\b").containsMatchIn(q)
        return hasDays && hasYearContext
    }

    private fun asksLeapYear(q: String): Boolean {
        return q.contains("bisiesto") || q.contains("bisiesta")
    }


    private fun referencedYears(q: String, currentYear: Int): List<Int> {
        val years = linkedSetOf<Int>()
        years.addAll(extractYears(q, currentYear))

        if (q.contains("este ano") || q.contains("ano actual")) years.add(currentYear)
        if (q.contains("ano pasado") || q.contains("ano anterior") || q.contains("el anterior")) {
            years.add(currentYear - 1)
        }
        if (q.contains("proximo ano") || q.contains("ano proximo") ||
            q.contains("siguiente ano") || q.contains("ano siguiente") || q.contains("el proximo")) {
            years.add(currentYear + 1)
        }

        return years.toList()
    }

    private fun extractYears(q: String, currentYear: Int): List<Int> {
        val rawNumbers = Regex("\\b\\d{1,4}\\b").findAll(q)
            .mapNotNull { it.value.toIntOrNull() }
            .toList()
        if (rawNumbers.isEmpty()) return emptyList()

        val baseCentury = (currentYear / 100) * 100
        val firstFullYear = rawNumbers.firstOrNull { it in 1000..9999 }
        val inferredCentury = firstFullYear?.let { (it / 100) * 100 } ?: baseCentury

        return rawNumbers.mapNotNull { value ->
            when {
                value in 1000..9999 -> value
                value in 0..99 -> {
                    var year = inferredCentury + value
                    if (firstFullYear == null && year < currentYear - 70) year += 100
                    year
                }
                else -> null
            }
        }.distinct()
    }

    private fun buildDaysAnswer(years: List<Int>, q: String): String {
        val future = q.contains("tendra") || q.contains("tendran") || q.contains("proximo") || q.contains("siguiente")
        val past = q.contains("tuvo") || q.contains("pasado") || q.contains("anterior")
        if (years.size == 1) {
            val year = years.first()
            val verb = when {
                past && year < Calendar.getInstance().get(Calendar.YEAR) -> "tuvo"
                future && year > Calendar.getInstance().get(Calendar.YEAR) -> "tendrá"
                else -> "tiene"
            }
            val days = daysInYear(year)
            val extra = if (isLeap(year)) ", porque es año bisiesto" else ""
            return "El año $year $verb $days días$extra."
        }

        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val parts = years.map { year ->
            val verb = when {
                past && year < currentYear -> "tuvo"
                future && year > currentYear -> "tendrá"
                else -> "tiene"
            }
            "el año $year $verb ${daysInYear(year)} días"
        }
        val leapYears = years.filter { isLeap(it) }
        val base = joinSpanish(parts).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("es", "PE")) else it.toString() }
        val extra = if (leapYears.isNotEmpty()) ", porque ${joinSpanish(leapYears.map { it.toString() })} ${if (leapYears.size == 1) "es" else "son"} bisiesto${if (leapYears.size == 1) "" else "s"}" else ""
        return "$base$extra."
    }

    private fun buildLeapAnswer(years: List<Int>): String {
        if (years.size == 1) {
            val year = years.first()
            return if (isLeap(year)) {
                "Sí, el año $year es bisiesto y tiene 366 días."
            } else {
                "No, el año $year no es bisiesto y tiene 365 días."
            }
        }
        val parts = years.map { year -> "$year ${if (isLeap(year)) "sí" else "no"}" }
        return "Bisiesto: ${parts.joinToString(", ")}."
    }

    private fun joinSpanish(parts: List<String>): String {
        return when (parts.size) {
            0 -> ""
            1 -> parts.first()
            2 -> parts[0] + " y " + parts[1]
            else -> parts.dropLast(1).joinToString(", ") + " y " + parts.last()
        }
    }

    private fun isLeap(year: Int): Boolean {
        return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
    }

    private fun daysInYear(year: Int): Int = if (isLeap(year)) 366 else 365

    private fun formatDateAnswer(prefix: String, calendar: Calendar): String {
        return "$prefix ${dayName(calendar)} ${calendar.get(Calendar.DAY_OF_MONTH)} de ${monthName(calendar)} de ${calendar.get(Calendar.YEAR)}."
    }

    private fun dayName(calendar: Calendar): String {
        return SimpleDateFormat("EEEE", Locale("es", "PE")).format(calendar.time).lowercase(Locale("es", "PE"))
    }

    private fun monthName(calendar: Calendar): String {
        return SimpleDateFormat("MMMM", Locale("es", "PE")).format(calendar.time).lowercase(Locale("es", "PE"))
    }
}
