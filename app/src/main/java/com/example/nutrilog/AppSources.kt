package com.example.nutrilog

import android.content.Context
import android.content.Intent

/** Turns the package name stored on Health Connect data into an app name, and opens that app. */
object AppSources {
    private val known = mapOf(
        "com.sec.android.app.shealth" to "Samsung Health",
        "com.samsung.android.wear.shealth" to "Samsung Health (watch)",
        "com.google.android.apps.fitness" to "Google Fit",
        "com.fitbit.FitbitMobile" to "Fitbit",
        "com.garmin.android.apps.connectmobile" to "Garmin Connect",
        "com.strava" to "Strava",
        "com.google.android.apps.healthdata" to "Health Connect",
        "com.huawei.health" to "Huawei Health",
        "com.xiaomi.hm.health" to "Zepp",
        "com.polar.polarflow" to "Polar Flow",
        "com.wahoofitness.fitness" to "Wahoo",
        "com.ouraring.oura" to "Oura",
        "com.whoop.android" to "WHOOP",
        "com.myfitnesspal.android" to "MyFitnessPal"
    )

    fun label(ctx: Context, pkg: String): String {
        if (pkg.isBlank()) return "Unknown app"
        try {
            val pm = ctx.packageManager
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) { }
        known[pkg]?.let { return it }
        return pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
    }

    fun launchIntent(ctx: Context, pkg: String): Intent? = try {
        ctx.packageManager.getLaunchIntentForPackage(pkg)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    } catch (e: Exception) { null }
}
