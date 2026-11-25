package cn.drdream.deepseek_accountlogin

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.drdream.deepseek_accountlogin.data.AttachmentStore
import cn.drdream.deepseek_accountlogin.data.history.HistoryArchive
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipFile

/**
 * Optional real-sample test. The archive is intentionally not committed; when
 * app/src/androidTest/assets/sample.zip is absent this test is skipped.
 */
@RunWith(AndroidJUnit4::class)
class HistoryArchiveSampleTest {

    @Test
    fun importsAndReexportsOfficialArchive() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val input = runCatching {
            instrumentation.context.assets.open("sample.zip")
        }.getOrNull()
        assumeTrue("sample.zip fixture is not present", input != null)
        val context = instrumentation.targetContext
        val sample = File(context.cacheDir, "history-sample.zip")
        input!!.use { source ->
            sample.outputStream().use { target -> source.copyTo(target) }
        }
        val archive = HistoryArchive(context, AttachmentStore(context))
        val imported = archive.importArchive(Uri.fromFile(sample))
        assertTrue(imported.sessions.isNotEmpty())
        val messageCount = imported.sessions.sumOf { it.messages.size }
        assertTrue(messageCount > 0)

        val exportedFile = File(context.cacheDir, "history-exported.zip")
        val exported = archive.exportArchive(Uri.fromFile(exportedFile), imported.sessions)
        assertEquals(imported.sessions.size, exported.sessionCount)
        ZipFile(exportedFile).use { zip ->
            val manifestEntry = zip.entries().nextElement()
            assertEquals("manifest.json", manifestEntry.name)
            val manifest = zip.getInputStream(manifestEntry).use {
                JSONObject(it.readBytes().toString(Charsets.UTF_8))
            }
            assertEquals(3, manifest.optInt("export_format_version"))
            val index = zip.getEntry("chat_session_index.json")
            assertNotNull(index)
            val entries = zip.getInputStream(index).use {
                JSONArray(it.readBytes().toString(Charsets.UTF_8))
            }
            assertEquals(imported.sessions.size, entries.length())
        }
        val reimported = archive.importArchive(Uri.fromFile(exportedFile))
        assertEquals(imported.sessions.size, reimported.sessions.size)

        println(
            "HISTORY_TEST sessions=${imported.sessions.size} messages=$messageCount " +
                "attachments=${imported.attachmentCount} missing=${imported.missingAttachmentCount} " +
                "images=${exported.imageCount} reimported=${reimported.sessions.size}",
        )
    }
}
