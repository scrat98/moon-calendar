package com.example.mooncalendar.utils

import com.example.mooncalendar.R
import net.time4j.Moment
import net.time4j.scale.TimeScale
import java.time.*

private typealias Time4JMoonPhase = net.time4j.calendar.astro.MoonPhase

private fun Time4JMoonPhase.toMoonPhase(): MoonPhase {
    return when (this) {
        Time4JMoonPhase.NEW_MOON -> MoonPhase.NEW_MOON
        Time4JMoonPhase.FIRST_QUARTER -> MoonPhase.FIRST_QUARTER
        Time4JMoonPhase.FULL_MOON -> MoonPhase.FULL_MOON
        Time4JMoonPhase.LAST_QUARTER -> MoonPhase.LAST_QUARTER
    }
}

enum class MoonPhase(val imageId: Int) {
    NEW_MOON(R.drawable.new_moon),
    WAXING_CRESCENT(R.drawable.waxing_crescent),
    FIRST_QUARTER(R.drawable.first_quarter_moon),
    WAXING_GIBBOUS(R.drawable.waxing_gibbous),
    FULL_MOON(R.drawable.full_moon),
    WANING_GIBBOUS(R.drawable.wanning_gibbous),
    LAST_QUARTER(R.drawable.last_quarter_moon),
    WANING_CRESCENT(R.drawable.wanning_crescent);

    fun nextPhase(): MoonPhase {
        val values = values()
        val nextOrdinal = this.ordinal + 1
        return values[nextOrdinal % values.size]
    }

    fun prevPhase(): MoonPhase {
        val values = values()
        val nextOrdinal = (this.ordinal - 1) + values.size
        return values[nextOrdinal % values.size]
    }
}

class MoonPhaseCalculator {

    // TODO: support eviction strategy
    private val calculated = mutableMapOf<YearMonth, List<Pair<Instant, Time4JMoonPhase>>>()

    fun getPhase(date: LocalDate, zone: ZoneId): MoonPhase {
        val startOfTheDay = date
            .atTime(LocalTime.MIN)
            .atZone(zone)
            .withZoneSameInstant(ZoneOffset.UTC)
        val endOfTheDay = date
            .atTime(LocalTime.MAX)
            .atZone(zone)
            .withZoneSameInstant(ZoneOffset.UTC)

        val phases = (
                getOrCalculate(YearMonth.from(startOfTheDay)) +
                getOrCalculate(YearMonth.from(endOfTheDay))
                ).toSet()

        val endOfTheDayUtc = endOfTheDay.toInstant()
        val found = phases.findLast { !it.first.isAfter(endOfTheDayUtc) }
        return if (found == null) {
            phases.first().second.toMoonPhase().prevPhase()
        } else {
            val timespan = Duration.between(found.first, endOfTheDay)
            if (timespan < Duration.ofDays(1)) {
                found.second.toMoonPhase()
            } else {
                found.second.toMoonPhase().nextPhase()
            }
        }
    }

    private fun getOrCalculate(yearMonth: YearMonth): List<Pair<Instant, Time4JMoonPhase>> {
        return calculated.computeIfAbsent(yearMonth) {
            val startOfTheMonth = yearMonth
                .atDay(1)
                .atTime(LocalTime.MIN)
                .toInstant(ZoneOffset.UTC)
            val endOfTheMonth = yearMonth
                .atEndOfMonth()
                .atTime(LocalTime.MAX)
                .toInstant(ZoneOffset.UTC)
            findMomentsForAllMoonPhases(startOfTheMonth..endOfTheMonth)
        }
    }

    fun findMomentsForAllMoonPhases(
        range: ClosedRange<Instant>
    ): List<Pair<Instant, Time4JMoonPhase>> {
        return Time4JMoonPhase.values()
            .flatMap { phase ->
                val moments = findMomentsForMoonPhase(phase, range)
                moments.map { it to phase }
            }
            .sortedBy { it.first }
    }

    fun findMomentsForMoonPhase(
        phase: Time4JMoonPhase,
        range: ClosedRange<Instant>
    ): Collection<Instant> {
        val startMoment = range.start.toMoment()
        val endMoment = range.endInclusive.toMoment()
        return generateSequence(phase.atOrAfter(startMoment)) { phase.after(it) }
            .takeWhile { it.isBeforeOrEqual(endMoment) }
            .map { it.toInstant() }
            .toList()
    }

    private fun Instant.toMoment(): Moment {
        return Moment.of(this.epochSecond, this.nano, TimeScale.POSIX)
    }

    private fun Moment.toInstant(): Instant {
        return Instant.ofEpochSecond(this.posixTime, this.nanosecond.toLong())
    }

    private fun Moment.isBeforeOrEqual(temporal: Moment): Boolean {
        return !this.isAfter(temporal)
    }
}
