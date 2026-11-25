package cn.drdream.deepseek_accountlogin

import cn.drdream.deepseek_accountlogin.ui.LatestReminder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date
import java.util.Locale

class LatestReminderTest {

    @Test
    fun usesThePlatformValueAcceptedByTheMessagesApi() {
        val text = LatestReminder.build(
            date = Date(0),
            locale = Locale.US,
            region = "United States",
            language = "English",
        )
        assertTrue(text.endsWith(",App,English"))
        assertFalse(text.contains(",Android,"))
        assertEquals(5, text.split(",").size)
    }

    @Test
    fun keepsTheChineseLanguageName() {
        val text = LatestReminder.build(
            date = Date(0),
            locale = Locale.SIMPLIFIED_CHINESE,
            region = "中国",
            language = "中文",
        )
        assertTrue(text.endsWith(",App,中文"))
    }
}
