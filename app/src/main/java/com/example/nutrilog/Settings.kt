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

    // State lives in private backing fields; public properties are read-only so Kotlin
    // does not generate setX() methods that would clash with the setX() functions below.
    private val _themeMode = mutableIntStateOf(p.getInt("theme", 0))          // 0 system, 1 light, 2 dark
    private val _units = mutableIntStateOf(p.getInt("units", 0))              // 0 metric, 1 imperial
    private val _fastingTarget = mutableIntStateOf(p.getInt("fast_target", 16)) // hours, 0 = no ring
    private val _lockMode = mutableIntStateOf(p.getInt("lock_mode", 0))       // 0 off, 1 PIN, 2 biometric + PIN
    private val _lockTimeout = mutableIntStateOf(p.getInt("lock_timeout", 60)) // seconds
    private val _hideRecents = mutableStateOf(p.getBoolean("hide_recents", true))
    private val _backupTree = mutableStateOf<String?>(p.getString("backup_tree", null))
    private val _backupEvery = mutableIntStateOf(p.getInt("backup_every", 24)) // 0 manual, 1 after changes
    private val _backupKeep = mutableIntStateOf(p.getInt("backup_keep", 10))
    private val _lastBackupAt = mutableLongStateOf(p.getLong("backup_last", 0L))
    private val _lastBackupMsg = mutableStateOf(p.getString("backup_msg", "") ?: "")
    private val _profile = mutableStateOf(Profile.fromJson(p.getString("profile", null)))
    private val _reminders = mutableStateOf(Reminders.parse(p.getString("reminders", null)))
    private val _smart = mutableStateOf(p.getBoolean("smart", true))
    private val _smartGap = mutableIntStateOf(p.getInt("smart_gap", 60))
    private val _smartGoal = mutableStateOf(p.getBoolean("smart_goal", true))

    val themeMode: Int get() = _themeMode.intValue
    val units: Int get() = _units.intValue
    val fastingTarget: Int get() = _fastingTarget.intValue
    val lockMode: Int get() = _lockMode.intValue
    val lockTimeout: Int get() = _lockTimeout.intValue
    val hideRecents: Boolean get() = _hideRecents.value
    val backupTree: String? get() = _backupTree.value
    val backupEveryHours: Int get() = _backupEvery.intValue
    val backupKeep: Int get() = _backupKeep.intValue
    val lastBackupAt: Long get() = _lastBackupAt.longValue
    val lastBackupMsg: String get() = _lastBackupMsg.value
    val profile: Profile get() = _profile.value
    val reminders: List<Reminder> get() = _reminders.value
    val smartReminders: Boolean get() = _smart.value
    val smartGap: Int get() = _smartGap.intValue
    val smartGoal: Boolean get() = _smartGoal.value

    fun setSmart(v: Boolean) { _smart.value = v; p.edit().putBoolean("smart", v).apply() }
    fun setSmartGap(v: Int) { _smartGap.intValue = v; p.edit().putInt("smart_gap", v).apply() }
    fun setSmartGoal(v: Boolean) { _smartGoal.value = v; p.edit().putBoolean("smart_goal", v).apply() }
    fun setTheme(v: Int) { _themeMode.intValue = v; p.edit().putInt("theme", v).apply() }
    fun setUnits(v: Int) { _units.intValue = v; p.edit().putInt("units", v).apply() }
    fun setFastingTarget(v: Int) { _fastingTarget.intValue = v; p.edit().putInt("fast_target", v).apply() }
    fun setLockTimeout(v: Int) { _lockTimeout.intValue = v; p.edit().putInt("lock_timeout", v).apply() }
    fun setHideRecents(v: Boolean) { _hideRecents.value = v; p.edit().putBoolean("hide_recents", v).apply() }
    fun setLockMode(v: Int) { _lockMode.intValue = v; p.edit().putInt("lock_mode", v).apply() }
    fun setBackupTree(v: String?) { _backupTree.value = v; p.edit().putString("backup_tree", v).apply() }
    fun setBackupEvery(v: Int) { _backupEvery.intValue = v; p.edit().putInt("backup_every", v).apply() }
    fun setBackupKeep(v: Int) { _backupKeep.intValue = v; p.edit().putInt("backup_keep", v).apply() }
    fun setLastBackup(at: Long, msg: String) {
        _lastBackupAt.longValue = at
        _lastBackupMsg.value = msg
        p.edit().putLong("backup_last", at).putString("backup_msg", msg).apply()
    }
    fun saveProfile(v: Profile) { _profile.value = v; p.edit().putString("profile", v.toJson().toString()).apply() }
    fun saveReminders(v: List<Reminder>) {
        val old = _reminders.value
        _reminders.value = v
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
