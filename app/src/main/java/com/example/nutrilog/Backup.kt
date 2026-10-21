package com.example.nutrilog

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Volume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class BackupFile(val name: String, val uri: Uri, val modified: Long, val size: Long)

data class BackupData(
    val entries: List<String>,
    val water: List<Pair<Long, Double>>,   // epoch second to ml
    val goals: String?,
    val waterGoal: Double?,
    val settings: JSONObject?,
    val createdAt: String
)

data class RestorePlan(
    val data: BackupData,
    val missingRecent: List<NutritionRecord>,
    val missingOld: List<NutritionRecord>,
    val missingWater: List<HydrationRecord>
)

/**
 * Health Connect is the source of truth. A backup is a snapshot of what Health Connect holds,
 * merged with older backups (so history older than Health Connect's 30-day read window is kept).
 * Restore only adds what Health Connect is missing.
 */
object Backup {
    private const val PREFIX = "nutrilog-backup-"
    private const val READ_WINDOW_DAYS = 30L

    fun due(s: Settings, changed: Boolean): Boolean {
        if (s.backupTree == null) return false
        return when (val every = s.backupEveryHours) {
            0 -> false
            1 -> changed || s.lastBackupAt == 0L
            else -> System.currentTimeMillis() - s.lastBackupAt >= every * 3_600_000L
        }
    }

    fun folderName(ctx: Context, tree: String?): String? =
        tree?.let { DocumentFile.fromTreeUri(ctx, Uri.parse(it))?.name }

    fun list(ctx: Context, tree: String?): List<BackupFile> {
        if (tree == null) return emptyList()
        val dir = DocumentFile.fromTreeUri(ctx, Uri.parse(tree)) ?: return emptyList()
        return dir.listFiles()
            .filter { (it.name ?: "").startsWith(PREFIX) }
            .map { BackupFile(it.name ?: "", it.uri, it.lastModified(), it.length()) }
            .sortedByDescending { it.name }
    }

    suspend fun read(ctx: Context, uri: Uri): BackupData = withContext(Dispatchers.IO) {
        val text = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: throw IllegalStateException("Can't open file")
        val o = JSONObject(text)
        if (o.optString("app") != "NutriLog") throw IllegalStateException("Not a NutriLog backup")
        val entries = o.optJSONArray("entries")?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList()
        val water = o.optJSONArray("water")?.let { a ->
            List(a.length()) { val w = a.getJSONObject(it); w.getLong("t") to w.getDouble("ml") }
        } ?: emptyList()
        BackupData(
            entries, water,
            o.optString("goals").ifBlank { null },
            if (o.has("waterGoal")) o.getDouble("waterGoal") else null,
            o.optJSONObject("settings"),
            o.optString("createdAt")
        )
    }

    /** Writes a new backup file. Returns a short status message. */
    suspend fun run(
        ctx: Context,
        client: HealthConnectClient,
        settings: Settings,
        goals: Map<String, Double>,
        waterGoal: Double
    ): String {
        val tree = settings.backupTree ?: return "No backup folder chosen"
        val tomb = settings.tombstones()

        // 1) what Health Connect holds right now
        val today = LocalDate.now()
        val hcNutrition = readNutrition(client, today.minusDays(400), today.plusDays(1))
        val hcWater = try {
            client.readRecords(
                ReadRecordsRequest(
                    HydrationRecord::class,
                    TimeRangeFilter.between(today.minusDays(400).atStartOfDay(), today.plusDays(1).atStartOfDay())
                )
            ).records
        } catch (e: Exception) { emptyList() }

        // 2) older entries from the most recent backup
        val previous = list(ctx, tree).firstOrNull()?.let { runCatching { read(ctx, it.uri) }.getOrNull() }

        val entries = LinkedHashMap<String, String>()
        previous?.entries?.forEach { line ->
            runCatching { NutritionParser.parseLine(line) }.getOrNull()?.let { r ->
                val k = Format.entryKey(r)
                if (k !in tomb) entries[k] = line
            }
        }
        hcNutrition.forEach { r -> entries[Format.entryKey(r)] = Format.entryLine(r) }   // Health Connect wins
        val waterMap = LinkedHashMap<Long, Double>()
        previous?.water?.forEach { (t, ml) -> if ("w|$t" !in tomb) waterMap[t] = ml }
        hcWater.forEach { waterMap[it.startTime.epochSecond] = it.volume.inMilliliters }

        // 3) write file
        return withContext(Dispatchers.IO) {
            val json = JSONObject()
                .put("app", "NutriLog").put("version", 1)
                .put("createdAt", LocalDateTime.now().toString())
                .put("entries", JSONArray(entries.values.sorted()))
                .put("water", JSONArray().also { a -> waterMap.forEach { (t, ml) -> a.put(JSONObject().put("t", t).put("ml", ml)) } })
                .put("goals", Format.goalsString(goals))
                .put("waterGoal", waterGoal)
                .put("settings", settings.exportJson())

            val dir = DocumentFile.fromTreeUri(ctx, Uri.parse(tree)) ?: return@withContext "Backup folder unavailable"
            val name = PREFIX + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm")) + ".json"
            val file = dir.createFile("application/octet-stream", name) ?: return@withContext "Couldn't create file"
            ctx.contentResolver.openOutputStream(file.uri, "w")?.bufferedWriter()?.use { it.write(json.toString()) }
                ?: return@withContext "Couldn't write file"

            // prune
            list(ctx, tree).drop(settings.backupKeep.coerceAtLeast(1)).forEach { runCatching { DocumentFile.fromSingleUri(ctx, it.uri)?.delete() } }
            "Saved ${entries.size} entries, ${waterMap.size} water logs"
        }
    }

    /** Works out what Health Connect is missing compared to a backup. */
    suspend fun plan(client: HealthConnectClient, settings: Settings, data: BackupData): RestorePlan {
        val tomb = settings.tombstones()
        val today = LocalDate.now()
        val hcKeys = readNutrition(client, today.minusDays(400), today.plusDays(1)).map { Format.entryKey(it) }.toSet()
        val cutoff = LocalDateTime.now().minusDays(READ_WINDOW_DAYS)

        val recent = ArrayList<NutritionRecord>()
        val old = ArrayList<NutritionRecord>()
        data.entries.forEach { line ->
            val r = runCatching { NutritionParser.parseLine(line) }.getOrNull() ?: return@forEach
            val k = Format.entryKey(r)
            if (k in tomb || k in hcKeys) return@forEach
            if (Format.localStart(r).isBefore(cutoff)) old += r else recent += r
        }

        val hcWater = try {
            client.readRecords(
                ReadRecordsRequest(
                    HydrationRecord::class,
                    TimeRangeFilter.between(today.minusDays(400).atStartOfDay(), today.plusDays(1).atStartOfDay())
                )
            ).records.map { it.startTime.epochSecond }.toSet()
        } catch (e: Exception) { emptySet() }
        val zone = ZoneId.systemDefault()
        val water = data.water
            .filter { (t, _) -> t !in hcWater && "w|$t" !in tomb && Instant.ofEpochSecond(t).isAfter(Instant.now().minusSeconds(READ_WINDOW_DAYS * 86400)) }
            .map { (t, ml) ->
                val st = Instant.ofEpochSecond(t)
                val off = zone.rules.getOffset(st)
                HydrationRecord(
                    metadata = Metadata(clientRecordId = Format.hash("w|$t")),
                    startTime = st, startZoneOffset = off, endTime = st.plusSeconds(60), endZoneOffset = off,
                    volume = Volume.milliliters(ml)
                )
            }
        return RestorePlan(data, recent, old, water)
    }

    suspend fun apply(client: HealthConnectClient, plan: RestorePlan, includeOld: Boolean): Int {
        val recs = plan.missingRecent + (if (includeOld) plan.missingOld else emptyList())
        if (recs.isNotEmpty()) client.insertRecords(recs)
        if (plan.missingWater.isNotEmpty()) client.insertRecords(plan.missingWater)
        return recs.size + plan.missingWater.size
    }
}
