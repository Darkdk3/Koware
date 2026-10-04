package tachiyomi.data.release

import android.os.Build
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimitExempt
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import tachiyomi.domain.release.interactor.GetApplicationRelease
import tachiyomi.domain.release.model.Release
import tachiyomi.domain.release.service.ReleaseService

class ReleaseServiceImpl(
    private val networkService: NetworkHelper,
    private val json: Json,
) : ReleaseService {

    override suspend fun latest(arguments: GetApplicationRelease.Arguments): Release? {
        // GitHub's release API isn't a novel/manga source - exempt it same as the sibling
        // AppUpdateDownloadJob (which downloads the APK this check points to).
        val release = with(json) {
            networkService.client.rateLimitExempt()
                .newCall(GET("https://api.github.com/repos/${arguments.repository}/releases/latest"))
                .awaitSuccess()
                .parseAs<GithubRelease>()
        }

        val downloadLink = getDownloadLink(release = release, isFoss = arguments.isFoss) ?: return null

        // Process the release info (body)
        val processedInfo = release.info.substringBeforeLast("<!-->").replace(gitHubUsernameMentionRegex) { mention ->
            "[${mention.value}](https://github.com/${mention.value.substring(1)})"
        }

        // If release info is empty or just whitespace, try to generate info from recent commits
        val finalInfo = if (processedInfo.isBlank()) {
            generateInfoFromCommits(arguments.repository)
        } else {
            processedInfo
        }

        return Release(
            version = release.version,
            info = finalInfo,
            releaseLink = release.releaseLink,
            downloadLink = downloadLink,
        )
    }

    private suspend fun generateInfoFromCommits(repository: String): String {
        // Fetch recent commits (limit to 10) to generate a changelog
        val commitsResponse = with(json) {
            networkService.client.rateLimitExempt()
                .newCall(GET("https://api.github.com/repos/$repository/commits?per_page=10"))
                .awaitSuccess()
                .parseAs<List<GitHubCommit>>()
        }

        if (commitsResponse.isEmpty()) {
            return "No changelog available."
        }

        // Format commit messages into a changelog
        val changelog = StringBuilder()
        changelog.append("## Recent Changes\n\n")

        commitsResponse.forEach { commit ->
            val message = commit.commit.message.trim()
            // Take first line of commit message as summary
            val summary = message.lines().firstOrNull() ?: message
            // Clean up the summary (remove common prefixes like [feat], [fix], etc.)
            val cleanSummary = summary.replace("""^\[.*?\]\s*""".toRegex(), "").trim()
            if (cleanSummary.isNotBlank()) {
                changelog.append("- $cleanSummary\n")
            }
        }

        return changelog.toString().trimEnd()
    }

    private fun getDownloadLink(release: GithubRelease, isFoss: Boolean): String? {
        val map = release.assets.associate { asset ->
            BUILD_TYPES.find { "-$it" in asset.name } to asset.downloadLink
        }

        return if (!isFoss) {
            map[Build.SUPPORTED_ABIS[0]] ?: map[null]
        } else {
            map[FOSS]
        }
    }

    companion object {
        private const val FOSS = "foss"
        private val BUILD_TYPES = listOf(FOSS, "arm64-v8a", "armeabi-v7a", "x86_64", "x86")

        /**
         * Regular expression that matches a mention to a valid GitHub username, like it's
         * done in GitHub Flavored Markdown. It follows these constraints:
         *
         * - Alphanumeric with single hyphens (no consecutive hyphens)
         * - Cannot begin or end with a hyphen
         * - Max length of 39 characters
         *
         * Reference: https://stackoverflow.com/a/30281147
         */
        private val gitHubUsernameMentionRegex = """\B@([a-z0-9](?:-(?=[a-z0-9])|[a-z0-9]){0,38}(?<=[a-z0-9]))"""
            .toRegex(RegexOption.IGNORE_CASE)
    }
}

// Data class for parsing GitHub commit response
@Serializable
data class GitHubCommit(
    @SerialName("sha") val sha: String,
    @SerialName("commit") val commit: CommitDetails
) {
    @Serializable
    data class CommitDetails(
        @SerialName("message") val message: String
    )
}
