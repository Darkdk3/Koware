package eu.kanade.tachiyomi.ui.more

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.more.NewUpdateScreen
import eu.kanade.presentation.util.Screen

class NewUpdateScreen(
    private val versionName: String,
    private val changelogInfo: String,
    private val releaseLink: String,
    private val downloadLink: String,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val changelogInfoNoChecksum = remember {
            changelogInfo.replace("""---(\R|.)*?Checksums(\R|.)*""".toRegex(), "")
        }
        val viewModel = viewModel<NewUpdateScreenModel>(
            factory = NewUpdateScreenModel.Factory,
            extras = CreationExtras {
                set(NewUpdateScreenModel.CHANGELOG_INFO_KEY, changelogInfoNoChecksum)
                set(NewUpdateScreenModel.DOWNLOAD_LINK_KEY, downloadLink)
            },
        )

        val state by viewModel.state.collectAsState()

        NewUpdateScreen(
            versionName = versionName,
            stage = state.stage,
            downloadProgress = { state.downloadProgress },
            changelogInfo = state.changelogInfo,
            onOpenInBrowser = { openLink(context, releaseLink) },
            onAcceptUpdate = {
                when (state.stage) {
                    NewUpdateScreenModel.Stage.Available, NewUpdateScreenModel.Stage.Failed -> viewModel.startDownload()
                    NewUpdateScreenModel.Stage.Downloaded -> viewModel.installUpdate()
                    else -> Unit
                }
            },
            onRejectUpdate = navigator::pop,
        )
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/** Opens [url] in the browser without crashing on non-Activity contexts. */
private fun openLink(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, url.toUri())
    val activity = context.findActivity()
    runCatching {
        if (activity != null) {
            activity.startActivity(intent)
        } else {
            // Not an Activity context: Android requires NEW_TASK here.
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}