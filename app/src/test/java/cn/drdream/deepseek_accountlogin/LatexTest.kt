package cn.drdream.deepseek_accountlogin

import cn.drdream.deepseek_accountlogin.ui.markdown.Latex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LatexTest {

    @Test
    fun convertsFractionsAndRoots() {
        assertEquals("(1)/(2)", Latex.toUnicode("\\frac{1}{2}"))
        assertEquals("√(x)", Latex.toUnicode("\\sqrt{x}"))
    }

    @Test
    fun convertsGreekAndOperators() {
        val result = Latex.toUnicode("\\alpha \\le \\beta \\times 2")
        assertTrue(result.contains("α"))
        assertTrue(result.contains("≤"))
        assertTrue(result.contains("β"))
        assertTrue(result.contains("×"))
    }

    @Test
    fun convertsScripts() {
        assertEquals("x²", Latex.toUnicode("x^2"))
        assertEquals("a₁", Latex.toUnicode("a_1"))
    }
}
