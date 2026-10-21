package com.example.nutrilog

import android.content.Context
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDate
import java.time.Period

/** Global lock state. */
object AppLock {
    var locked by mutableStateOf(false)
    /** Set before launching a system screen (permission dialog, folder picker) so it doesn't trigger a re-lock. */
    var skipNext = false
}

data class Profile(
    val first: String = "",
    val last: String = "",
    val nick: String = "",
    val email: String = "",
    val birth: String = "",
    val sex: Int = 0,        // 0 not set, 1 male, 2 female, 3 other
    val goalType: Int = 1,   // 0 cut, 1 maintain, 2 bulk
    val avatar: String = AvatarSpec().encode()
) {
    val displayName: String
        get() = nick.ifBlank { first.ifBlank { "You" } }

    val fullName: String
        get() = listOf(first, last).filter { it.isNotBlank() }.joinToString(" ")

    fun age(): Int? = try {
        if (birth.isBlank()) null else Period.between(LocalDate.parse(birth), LocalDate.now()).years
    } catch (e: Exception) { null }

    fun toJson(): JSONObject = JSONObject()
        .put("first", first).put("last", last).put("nick", nick).put("email", email)
        .put("birth", birth).put("sex", sex).put("goalType", goalType).put("avatar", avatar)

    companion object {
        fun fromJson(s: String?): Profile {
            if (s == null) return Profile()
            return try {
                val o = JSONObject(s)
                Profile(
                    o.optString("first"), o.optString("last"), o.optString("nick"), o.optString("email"),
                    o.optString("birth"), o.optInt("sex", 0), o.optInt("goalType", 1),
                    o.optString("avatar", AvatarSpec().encode())
                )
            } catch (e: Exception) { Profile() }
        }
    }
}

/** Everything the user can configure. Backed by SharedPreferences; reads are Compose state. */
class Settings(ctx: Context) {
    private val app = ctx.applicationContext
    private val p = app.getSharedPreferences("nutrilog", Context.MODE_PRIVATE)

    var themeMode by mutableIntStateOf(p.getInt("theme", 0))      // 0 system, 1 light, 2 dark
        private set
    var units by mutableIntStateOf(p.getInt("units", 0))          // 0 metric, 1 imperial
        private set
    var fastingTarget by mutableIntStateOf(p.getInt("fast_target", 16))  // hours, 0 = no ring
        private set
    var lockMode by mutableIntStateOf(p.getInt("lock_mode", 0))   // 0 off, 1 PIN, 2 biometric + PIN
        private set
    var lockTimeout by mutableIntStateOf(p.getInt("lock_timeout", 60))   // seconds
        private set
    var hideRecents by mutableStateOf(p.getBoolean("hide_recents", true))
        private set
    var backupTree by mutableStateOf(p.getString("backup_tree", null))
        private set
    var backupEveryHours by mutableIntStateOf(p.getInt("backup_every", 24))  // 0 manual, 1 after changes
        private set
    var backupKeep by mutableIntStateOf(p.getInt("backup_keep", 10))
        private set
    var lastBackupAt by mutableLongStateOf(p.getLong("backup_last", 0L))
        private set
    var lastBackupMsg by mutableStateOf(p.getString("backup_msg", "") ?: "")
        private set
    var profile by mutableStateOf(Profile.fromJson(p.getString("profile", null)))
        private set
    var reminders by mutableStateOf(Reminders.parse(p.getString("reminders", null)))
        private set

    fun setTheme(v: Int) { themeMode = v; p.edit().putInt("theme", v).apply() }
    fun setUnits(v: Int) { units = v; p.edit().putInt("units", v).apply() }
    fun setFastingTarget(v: Int) { fastingTarget = v; p.edit().putInt("fast_target", v).apply() }
    fun setLockTimeout(v: Int) { lockTimeout = v; p.edit().putInt("lock_timeout", v).apply() }
    fun setHideRecents(v: Boolean) { hideRecents = v; p.edit().putBoolean("hide_recents", v).apply() }
    fun setLockMode(v: Int) { lockMode = v; p.edit().putInt("lock_mode", v).apply() }
    fun setBackupTree(v: String?) { backupTree = v; p.edit().putString("backup_tree", v).apply() }
    fun setBackupEvery(v: Int) { backupEveryHours = v; p.edit().putInt("backup_every", v).apply() }
    fun setBackupKeep(v: Int) { backupKeep = v; p.edit().putInt("backup_keep", v).apply() }
    fun setLastBackup(at: Long, msg: String) {
        lastBackupAt = at; lastBackupMsg = msg
        p.edit().putLong("backup_last", at).putString("backup_msg", msg).apply()
    }
    fun saveProfile(v: Profile) { profile = v; p.edit().putString("profile", v.toJson().toString()).apply() }
    fun saveReminders(v: List<Reminder>) {
        val old = reminders
        reminders = v
        p.edit().putString("reminders", Reminders.toJson(v)).apply()
        Reminders.apply(app, old, v)
    }

    // ── PIN (salted hash, never stored in plain text) ──
    val pinSet: Boolean get() = p.getString("pin_hash", null) != null
    val pinLength: Int get() = p.getInt("pin_len", 4)

    private fun sha(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val saltStr = Base64.encodeToString(salt, Base64.NO_WRAP)
        p.edit().putString("pin_salt", saltStr).putString("pin_hash", sha(saltStr + pin)).putInt("pin_len", pin.length).apply()
    }

    fun checkPin(pin: String): Boolean {
        val salt = p.getString("pin_salt", null) ?: return false
        val hash = p.getString("pin_hash", null) ?: return false
        return MessageDigest.isEqual(hash.toByteArray(), sha(salt + pin).toByteArray())
    }

    fun clearPin() {
        p.edit().remove("pin_salt").remove("pin_hash").remove("pin_len").apply()
    }

    // ── Entries deleted inside the app: must not be resurrected from backups ──
    fun tombstones(): Set<String> = p.getStringSet("tombstones", emptySet()) ?: emptySet()
    fun addTombstone(key: String) {
        val next = HashSet(tombstones()); next.add(key)
        p.edit().putStringSet("tombstones", next).apply()
    }

    // ── Included in backup files (never the PIN or folder choice) ──
    fun exportJson(): JSONObject = JSONObject()
        .put("theme", themeMode).put("units", units).put("fastingTarget", fastingTarget)
        .put("backupEvery", backupEveryHours).put("backupKeep", backupKeep)
        .put("profile", profile.toJson())
        .put("reminders", org.json.JSONArray(Reminders.toJson(reminders)))

    fun importJson(o: JSONObject) {
        if (o.has("theme")) setTheme(o.getInt("theme"))
        if (o.has("units")) setUnits(o.getInt("units"))
        if (o.has("fastingTarget")) setFastingTarget(o.getInt("fastingTarget"))
        if (o.has("backupEvery")) setBackupEvery(o.getInt("backupEvery"))
        if (o.has("backupKeep")) setBackupKeep(o.getInt("backupKeep"))
        if (o.has("profile")) saveProfile(Profile.fromJson(o.getJSONObject("profile").toString()))
        if (o.has("reminders")) saveReminders(Reminders.parse(o.getJSONArray("reminders").toString()))
    }
}
