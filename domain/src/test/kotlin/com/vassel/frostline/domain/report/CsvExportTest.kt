// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.domain.report

import com.vassel.frostline.domain.model.Excursion
import com.vassel.frostline.domain.model.ExcursionType
import com.vassel.frostline.domain.model.Reading
import kotlin.test.Test
import kotlin.test.assertEquals

class CsvExportTest {
    @Test
    fun readingsCsvHasHeaderIsoUtcTimestampsAndExactTwoDecimalTemperatures() {
        val rs = listOf(
            Reading(1_767_225_600_000L, 1, 5.0, 61.5),
            Reading(1_767_225_660_123L, 2, -0.05, null),
            Reading(1_767_225_720_000L, 3, 4.999, null),
        )
        val csv = CsvExport.readings("trip-1", rs)
        assertEquals(
            "ts_utc_iso,temp_c,humidity,trip_id\n" +
                "2026-01-01T00:00:00.000Z,5.00,61.50,trip-1\n" +
                "2026-01-01T00:01:00.123Z,-0.05,,trip-1\n" +
                "2026-01-01T00:02:00.000Z,5.00,,trip-1\n",
            csv,
        )
    }

    @Test
    fun negativeValueRoundingToZeroHasNoMinusSign() {
        assertEquals("0.00", CsvExport.centi(0))
        assertEquals("-0.01", CsvExport.centi(-1))
        assertEquals("-12.30", CsvExport.centi(-1230))
    }

    @Test
    fun fieldsWithSeparatorsAreQuoted() {
        val csv = CsvExport.readings("a,\"b\"", listOf(Reading(0, 0, 1.0)))
        assertEquals("1970-01-01T00:00:00.000Z,1.00,,\"a,\"\"b\"\"\"", csv.lines()[1])
    }

    @Test
    fun excursionsCsvHasDurationAndPeakAndEmptyEndWhenOpen() {
        val ex = listOf(
            Excursion("t", ExcursionType.HEAT, 1_767_225_840_000L, 1_767_226_080_000L, 9.3),
            Excursion("t", ExcursionType.FREEZE, 1_767_226_200_000L, null, -1.2),
        )
        assertEquals(
            "trip_id,type,start_utc_iso,end_utc_iso,duration_s,peak_c\n" +
                "t,HEAT,2026-01-01T00:04:00.000Z,2026-01-01T00:08:00.000Z,240,9.30\n" +
                "t,FREEZE,2026-01-01T00:10:00.000Z,,,-1.20\n",
            CsvExport.excursions(ex),
        )
    }
}
