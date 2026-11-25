package cn.drdream.deepseek_accountlogin

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.drdream.deepseek_accountlogin.data.ChatDatabase
import cn.drdream.deepseek_accountlogin.data.SecureStore
import cn.drdream.deepseek_accountlogin.data.model.ChatMessage
import cn.drdream.deepseek_accountlogin.data.model.ChatSession
import cn.drdream.deepseek_accountlogin.data.model.MessageStatus
import cn.drdream.deepseek_accountlogin.data.model.ReplyBlock
import cn.drdream.deepseek_accountlogin.data.model.Role
import cn.drdream.deepseek_accountlogin.data.model.SearchSource
import cn.drdream.deepseek_accountlogin.data.model.Usage
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith

/** Seeds a demo account and a Markdown-rich conversation for UI verification. */
@RunWith(AndroidJUnit4::class)
class SeedUiTest {

    @Test
    fun seedDemoContent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val secure = SecureStore(context)
        secure.put("api_key", "sk-demo-ui-1234567890")
        secure.put("key_source", "MANUAL")
        secure.put(
            "account_profile",
            JSONObject().apply {
                put("id", "demo-user")
                put("name", "Demo User")
                put("email", "demo@example.com")
            }.toString(),
        )
        secure.put(
            "account_balance",
            JSONArray().put(
                JSONObject().apply {
                    put("currency", "CNY")
                    put("total", "12.34")
                    put("granted", "1.00")
                    put("toppedUp", "11.34")
                },
            ).toString(),
        )

        val now = System.currentTimeMillis()
        val user = ChatMessage(
            role = Role.USER,
            createdAt = now - 30_000,
            content = "请展示 Markdown、代码、表格、引用和公式。",
        )
        val answer = """
            # 欢迎使用 DeepSeek

            这是一个 **加粗**、*斜体*、`行内代码` 和 [链接](https://platform.deepseek.com) 的示例。

            ## Kotlin 代码

            ```kotlin
            fun main() {
                println("Hello, DeepSeek!")
            }
            ```

            ## 表格

            | 功能 | 状态 |
            | --- | --- |
            | 流式输出 | ✅ |
            | 深度思考 | ✅ |
            | 联网搜索 | ✅ |

            > 这是一段引用，用来检查左边框样式。

            - 列表项一
            - 列表项二

            行内公式 ${'$'}E=mc^2${'$'}，块公式：

            ${'$'}${'$'}
            \int_0^\infty e^{-x} dx = 1
            ${'$'}${'$'}

            以上是渲染测试。
        """.trimIndent()
        val assistant = ChatMessage(
            role = Role.ASSISTANT,
            createdAt = now - 20_000,
            status = MessageStatus.DONE,
            stopReason = "end_turn",
            thinkingEnabled = true,
            searchEnabled = true,
            usage = Usage(inputTokens = 120, outputTokens = 260),
            blocks = listOf(
                ReplyBlock.Thinking(
                    text = "用户希望看到渲染效果。我需要展示标题、代码、表格、引用、列表和公式。",
                    startedAt = now - 19_000,
                    endedAt = now - 16_000,
                ),
                ReplyBlock.Text(text = answer),
                ReplyBlock.SearchResults(
                    sources = listOf(
                        SearchSource(
                            title = "DeepSeek Platform",
                            url = "https://platform.deepseek.com",
                            snippet = "DeepSeek 开放平台，提供 API 与模型服务。",
                            siteName = "platform.deepseek.com",
                            pageAge = "2026-11-20",
                        ),
                        SearchSource(
                            title = "API Docs",
                            url = "https://api-docs.deepseek.com",
                            snippet = "Messages API、Files API 与模型调用文档。",
                            siteName = "api-docs.deepseek.com",
                            pageAge = null,
                        ),
                    ),
                ),
            ),
        )
        val session = ChatSession(
            title = "Markdown 渲染演示",
            createdAt = now - 60_000,
            updatedAt = now,
            messages = listOf(user, assistant),
        )
        val db = ChatDatabase(context)
        try {
            db.deleteAll()
            db.upsert(session)
        } finally {
            db.close()
        }
    }
}
