package com.example.nutrilog

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Volume
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

suspend fun readNutrition(client: HealthConnectClient, from: LocalDate, toExclusive: LocalDate): List<NutritionRecord> {
    val resp = client.readRecords(
        ReadRecordsRequest(
            NutritionRecord::class,
            TimeRangeFilter.between(from.atStartOfDay(), toExclusive.atStartOfDay())
        )
    )
    return resp.records.sortedBy { it.startTime }
}

suspend fun readWater(client: HealthConnectClient, date: LocalDate): List<HydrationRecord> {
    val resp = client.readRecords(
        ReadRecordsRequest(
            HydrationRecord::class,
            TimeRangeFilter.between(date.atStartOfDay(), date.plusDays(1).atStartOfDay())
        )
    )
    return resp.records.sortedBy { it.startTime }
}

suspend fun addWater(client: HealthConnectClient, ml: Double) {
    val now = Instant.now()
    val offset = ZoneId.systemDefault().rules.getOffset(now)
    client.insertRecords(
        listOf(
            HydrationRecord(
                startTime = now.minusSeconds(60),
                startZoneOffset = offset,
                endTime = now,
                endZoneOffset = offset,
                volume = Volume.milliliters(ml)
            )
        )
    )
}

fun totalsOf(records: List<NutritionRecord>): Map<String, Double> =
    Nutrients.all.associate { n -> n.key to records.sumOf { r -> n.read(r) ?: 0.0 } }

fun NutritionRecord.day(): LocalDate = Format.localStart(this).toLocalDate()
