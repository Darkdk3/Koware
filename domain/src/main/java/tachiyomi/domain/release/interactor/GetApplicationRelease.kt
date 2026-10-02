package tachiyomi.domain.release.interactor

import tachiyomi.domain.release.model.Release
import tachiyomi.domain.release.service.ReleaseService

class GetApplicationRelease(
    private val service: ReleaseService,
) {

    suspend fun await(arguments: Arguments): Result {
        val release = service.latest(arguments) ?: return Result.NoNewUpdate

        val isNewVersion = isNewVersion(
            arguments.isPreview,
            arguments.commitCount,
            arguments.versionName,
            release.version,
        )

        return when {
            isNewVersion -> Result.NewUpdate(release)
            else -> Result.NoNewUpdate
        }
    }

    private fun isNewVersion(
        isPreview: Boolean,
        commitCount: Int,
        versionName: String,
        versionTag: String,
    ): Boolean {
        val digitsOnly = "[^\\d.]".toRegex()

        return if (isPreview) {
            // Preview/nightly builds: tags like "r1234", compared by commit count
            val newCount = versionTag.replace(digitsOnly, "").toIntOrNull() ?: return false
            newCount > commitCount
        } else {
            // Release builds: tags like "v0.1.2", compared field by field
            val newSemVer = versionTag.substringBefore("-").replace(digitsOnly, "")
                .split(".").map { it.toIntOrNull() ?: 0 }
            val oldSemVer = versionName.substringBefore("-").replace(digitsOnly, "")
                .split(".").map { it.toIntOrNull() ?: 0 }

            for (i in 0 until maxOf(newSemVer.size, oldSemVer.size)) {
                val n = newSemVer.getOrElse(i) { 0 }
                val o = oldSemVer.getOrElse(i) { 0 }
                if (n > o) return true
                if (n < o) return false
            }
            false
        }
    }

    data class Arguments(
        val isFoss: Boolean,
        val isPreview: Boolean,
        val commitCount: Int,
        val versionName: String,
        val repository: String,
        val forceCheck: Boolean = false,
    )

    sealed interface Result {
        data class NewUpdate(val release: Release) : Result
        data object NoNewUpdate : Result
        data object OsTooOld : Result
    }
}