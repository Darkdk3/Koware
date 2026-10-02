package eu.kanade.tachiyomi.ui.reader.viewer.text.textview

import android.os.Build
import android.text.PrecomputedText
import android.text.SpannableStringBuilder

internal object PlatformPrecomputedText {

    /**
     * Returns [text] copied into a plain [SpannableStringBuilder] when it is a framework
     * [PrecomputedText], or null when it is anything else.
     *
     * [PrecomputedText] only exists on API 28+ and minSdk is 26, so the SDK check must
     * short-circuit before the `is` check: on API 26/27 executing it fails with
     * `NoClassDefFoundError: Failed resolution of: Landroid/text/PrecomputedText;`.
     * Nothing is lost there, since TextViewCompat never sets platform precomputed text
     * below API 28.
     */
    fun toPlainSpannable(text: CharSequence, sdkInt: Int = Build.VERSION.SDK_INT): CharSequence? =
        if (sdkInt >= Build.VERSION_CODES.P && text is PrecomputedText) {
            SpannableStringBuilder(text)
        } else {
            null
        }
}
