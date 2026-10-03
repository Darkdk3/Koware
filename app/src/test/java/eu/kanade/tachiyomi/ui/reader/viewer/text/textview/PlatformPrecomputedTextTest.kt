package eu.kanade.tachiyomi.ui.reader.viewer.text.textview

import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.lang.reflect.InvocationTargetException

class PlatformPrecomputedTextTest {

    @Test
    fun `does not resolve PrecomputedText below API 28`() {
        assertNull(toPlainSpannableWithoutPrecomputedText("chapter text", sdkInt = 26))
        assertNull(toPlainSpannableWithoutPrecomputedText("chapter text", sdkInt = 27))
    }

    @Test
    fun `resolves PrecomputedText from API 28`() {
        // Proves the harness reproduces the API 26 crash once the check is reached.
        assertThrows<NoClassDefFoundError> {
            toPlainSpannableWithoutPrecomputedText("chapter text", sdkInt = 28)
        }
    }

    @Test
    fun `leaves text that is not PrecomputedText alone on API 28+`() {
        assertNull(PlatformPrecomputedText.toPlainSpannable("chapter text", sdkInt = 28))
        assertNull(PlatformPrecomputedText.toPlainSpannable("chapter text", sdkInt = 34))
    }

    /**
     * Calls [PlatformPrecomputedText.toPlainSpannable] from a fresh copy of the class whose
     * class loader cannot find android.text.PrecomputedText, like an API 26/27 device.
     */
    private fun toPlainSpannableWithoutPrecomputedText(text: CharSequence, sdkInt: Int): Any? {
        val loader = MissingPrecomputedTextClassLoader(javaClass.classLoader!!)
        val cls = loader.loadClass(PlatformPrecomputedText::class.java.name)
        val instance = cls.getField("INSTANCE").get(null)
        val method = cls.getMethod("toPlainSpannable", CharSequence::class.java, Int::class.javaPrimitiveType)
        return try {
            method.invoke(instance, text, sdkInt)
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
    }

    private class MissingPrecomputedTextClassLoader(parent: ClassLoader) : ClassLoader(parent) {
        private val isolated = PlatformPrecomputedText::class.java.name

        override fun loadClass(name: String, resolve: Boolean): Class<*> = synchronized(this) {
            when {
                name.startsWith("android.text.PrecomputedText") -> throw ClassNotFoundException(name)
                name == isolated -> findLoadedClass(name) ?: run {
                    val bytes = parent.getResourceAsStream(name.replace('.', '/') + ".class")!!
                        .use { it.readBytes() }
                    defineClass(name, bytes, 0, bytes.size)
                }
                else -> super.loadClass(name, resolve)
            }
        }
    }
}
