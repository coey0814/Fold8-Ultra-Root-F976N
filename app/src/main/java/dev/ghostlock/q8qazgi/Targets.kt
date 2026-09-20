package dev.ghostlock.q8qazgi

import android.os.Build

object Targets {

    data class Target(
        val id: String,
        val match: String,
        val preload: String,
        val ksud: String,
        val label: String
    )

    val ALL: List<Target> = listOf(
        Target("AZGI", "F976NKSU1AZGI", "preload.so", "ksud", "F976NKSU1AZGI (rev1)"),
        Target("AZH7", "F976NKSS2AZH7", "preload-azh7.so", "ksud-azh7", "F976NKSS2AZH7 (rev2)"),
        Target("AZI5", "F976NKSU3AZI5", "preload-azi5.so", "ksud-azi5", "F976NKSU3AZI5 (rev3)")
    )

    @Volatile
    private var overrideId: String? = null

    fun setOverride(id: String?) {
        overrideId = id
    }

    fun getOverride(): String? = overrideId

    fun detected(): Target? {
        val fingerprint = Build.FINGERPRINT ?: ""
        val display = Build.DISPLAY ?: ""
        val id = Build.ID ?: ""
        return ALL.firstOrNull { t ->
            fingerprint.contains(t.match) || display.contains(t.match) || id.contains(t.match)
        }
    }

    fun current(): Target {
        overrideId?.let { o -> ALL.firstOrNull { it.id == o }?.let { return it } }
        return detected() ?: ALL[0]
    }
}
