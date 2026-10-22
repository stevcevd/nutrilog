package com.example.nutrilog

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BoneMassRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.reflect.KClass

/** Read-only body + activity data pulled from Health Connect. Nothing is entered in this app. */
val BODY_PERMISSIONS: Set<String> = setOf(
    HealthPermission.getReadPermission(WeightRecord::class),
    HealthPermission.getReadPermission(HeightRecord::class),
    HealthPermission.getReadPermission(BodyFatRecord::class),
    HealthPermission.getReadPermission(LeanBodyMassRecord::class),
    HealthPermission.getReadPermission(BoneMassRecord::class),
    HealthPermission.getReadPermission(BasalMetabolicRateRecord::class),
    HealthPermission.getReadPermission(StepsRecord::class),
    HealthPermission.getReadPermission(ExerciseSessionRecord::class),
    HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
    HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class)
)

data class Workout(val title: String, val start: Instant, val minutes: Long)

data class BodySnapshot(
    val weightKg: Double?,
    val weightSeries: List<Pair<LocalDate, Double>>,
    val weightChange30: Double?,
    val bodyFatPct: Double?,
    val heightM: Double?,
    val leanKg: Double?,
    val boneKg: Double?,
    val bmr: Double?,
    val steps: Long,
    val activeKcal: Double,
    val totalKcal: Double,
    val workouts: List<Workout>
) {
    val bmi: Double?
        get() = if (weightKg != null && heightM != null && heightM > 0) weightKg / (heightM * heightM) else null
}

private suspend fun <T> safe(block: suspend () -> T?): T? = try { block() } catch (e: Exception) { null }

private suspend fun <T : Record> latestOf(client: HealthConnectClient, type: KClass<T>, days: Long = 365): T? {
    val now = Instant.now()
    val resp = client.readRecords(
        ReadRecordsRequest(
            type, TimeRangeFilter.between(now.minus(Duration.ofDays(days)), now),
            ascendingOrder = false, pageSize = 1
        )
    )
    return resp.records.firstOrNull()
}

private fun exerciseName(r: ExerciseSessionRecord): String {
    val t = r.title
    if (!t.isNullOrBlank()) return t
    return when (r.exerciseType) {
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "Run"
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Walk"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "Cycling"
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL -> "Swim"
        ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "Yoga"
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "Hike"
        ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> "Weights"
        else -> "Workout"
    }
}

suspend fun readBody(client: HealthConnectClient): BodySnapshot {
    val now = Instant.now()
    val zone = ZoneId.systemDefault()
    val dayStart = LocalDate.now().atStartOfDay(zone).toInstant()

    val weights = safe {
        client.readRecords(
            ReadRecordsRequest(WeightRecord::class, TimeRangeFilter.between(now.minus(Duration.ofDays(90)), now))
        ).records.sortedBy { it.time }
    } ?: emptyList()
    val series = weights
        .map { it.time.atZone(zone).toLocalDate() to it.weight.inKilograms }
        .groupBy({ it.first }, { it.second })
        .map { (d, v) -> d to v.last() }
        .sortedBy { it.first }
    val latestW = weights.lastOrNull()?.weight?.inKilograms
    val change = if (series.size >= 2) series.last().second - series.first().second else null

    val bodyFat = safe { latestOf(client, BodyFatRecord::class)?.percentage?.value }
    val height = safe { latestOf(client, HeightRecord::class, 3650)?.height?.inMeters }
    val lean = safe { latestOf(client, LeanBodyMassRecord::class)?.mass?.inKilograms }
    val bone = safe { latestOf(client, BoneMassRecord::class)?.mass?.inKilograms }
    val bmr = safe { latestOf(client, BasalMetabolicRateRecord::class)?.basalMetabolicRate?.inKilocaloriesPerDay }

    val today = TimeRangeFilter.between(dayStart, now)
    val steps = safe {
        client.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), today))[StepsRecord.COUNT_TOTAL]
    } ?: 0L
    val active = safe {
        client.aggregate(AggregateRequest(setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL), today))[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories
    } ?: 0.0
    val total = safe {
        client.aggregate(AggregateRequest(setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL), today))[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories
    } ?: 0.0

    val workouts = safe {
        client.readRecords(
            ReadRecordsRequest(
                ExerciseSessionRecord::class,
                TimeRangeFilter.between(now.minus(Duration.ofDays(14)), now),
                ascendingOrder = false, pageSize = 5
            )
        ).records.map { Workout(exerciseName(it), it.startTime, Duration.between(it.startTime, it.endTime).toMinutes()) }
    } ?: emptyList()

    return BodySnapshot(latestW, series, change, bodyFat, height, lean, bone, bmr, steps, active, total, workouts)
}

/** Raw weigh-ins (instant, kg) from Health Connect for the last [days] days. */
suspend fun readWeightPoints(client: HealthConnectClient, days: Long): List<Pair<Instant, Double>> {
    val now = Instant.now()
    return client.readRecords(
        ReadRecordsRequest(WeightRecord::class, TimeRangeFilter.between(now.minus(Duration.ofDays(days)), now))
    ).records.sortedBy { it.time }.map { it.time to it.weight.inKilograms }
}
