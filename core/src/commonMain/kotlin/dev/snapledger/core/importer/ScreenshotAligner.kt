package dev.snapledger.core.importer

import dev.snapledger.core.model.normalizeText

data class AlignmentResult(
    /** Rows of the whole batch in display order (newest first), overlaps removed, dates filled in. */
    val rows: List<ImportedRow>,
    /** How many rows were dropped because they repeated an overlapping part of another screenshot. */
    val overlapRemoved: Int,
    /** Screenshots that share no rows with the ones before them: something may be missing in between. */
    val screenshotsWithoutOverlap: List<Int>,
    /** Rows that look like repeats of another screenshot although the screenshots did not line up. */
    val suspectedRepeats: Set<Int>,
)

/**
 * Merges overlapping screenshots of one batch by matching contiguous runs of rows (docs/02 section 4.2).
 * Comparing runs rather than single rows keeps two genuine identical purchases on the same day.
 */
object ScreenshotAligner {
    fun align(rows: List<ImportedRow>): AlignmentResult {
        val screenshots =
            rows
                .groupBy { it.screenshot }
                .toSortedMap()
                .values
                .map { shot -> shot.sortedBy { it.position } }
        if (screenshots.isEmpty()) return AlignmentResult(emptyList(), 0, emptyList(), emptySet())

        val merged = screenshots.first().toMutableList()
        var removed = 0
        val gaps = mutableListOf<Int>()
        val suspects = mutableSetOf<Int>()

        for (shot in screenshots.drop(1)) {
            val forward = maxOverlap(tail = merged, head = shot)
            if (forward > 0) {
                // Keep the earlier copy, but let it borrow a date the later copy could read.
                for (i in 0 until forward) {
                    val keptIndex = merged.size - forward + i
                    merged[keptIndex] = preferKnownDate(merged[keptIndex], shot[i])
                }
                merged += shot.drop(forward)
                removed += forward
                continue
            }
            val backward = maxOverlap(tail = shot, head = merged)
            if (backward > 0) {
                // Screenshot sent out of order: it sits above everything merged so far.
                val prefix = shot.dropLast(backward)
                for (i in 0 until backward) merged[i] = preferKnownDate(merged[i], shot[shot.size - backward + i])
                merged.addAll(0, prefix)
                removed += backward
                continue
            }
            gaps += shot.first().screenshot
            shot.filter { candidate -> merged.any { sameRow(it, candidate) } }.forEach { suspects += it.id }
            merged += shot
        }
        return AlignmentResult(fillMissingDates(merged), removed, gaps, suspects)
    }

    private fun maxOverlap(
        tail: List<ImportedRow>,
        head: List<ImportedRow>,
    ): Int {
        for (k in minOf(tail.size, head.size) downTo 1) {
            if ((0 until k).all { sameRow(tail[tail.size - k + it], head[it]) }) return k
        }
        return 0
    }

    internal fun sameRow(
        a: ImportedRow,
        b: ImportedRow,
    ): Boolean =
        a.direction == b.direction &&
            a.amountMinor == b.amountMinor &&
            a.currency == b.currency &&
            normalizeText(a.rawDescription) == normalizeText(b.rawDescription) &&
            (a.isDateUncertain || b.isDateUncertain || a.date == b.date)

    private fun preferKnownDate(
        kept: ImportedRow,
        other: ImportedRow,
    ): ImportedRow =
        if (kept.isDateUncertain &&
            !other.isDateUncertain
        ) {
            kept.copy(date = other.date, uncertainFields = kept.uncertainFields - "date")
        } else {
            kept
        }

    /** A row without a date belongs to the same day as the row above it (the last day header seen). */
    private fun fillMissingDates(rows: List<ImportedRow>): List<ImportedRow> {
        val result = rows.toMutableList()
        for (i in result.indices) {
            if (result[i].date == null && i > 0 && result[i - 1].date != null) {
                result[i] = result[i].copy(date = result[i - 1].date, uncertainFields = result[i].uncertainFields + "date")
            }
        }
        return result
    }
}
