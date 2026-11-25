package cn.drdream.deepseek_accountlogin.ui.markdown

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import cn.drdream.deepseek_accountlogin.ui.theme.DeepSeekColors

/** Lightweight lexer that colors comments, strings, numbers and keywords. */
object CodeHighlight {

    private val tokenRegex = Regex(
        "\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|//[^\\n]*|#[^\\n]*|/\\*[\\s\\S]*?\\*/|\\b\\d+(?:\\.\\d+)?\\b|\\b[A-Za-z_][A-Za-z0-9_]*\\b",
    )

    private val common = setOf(
        "if", "else", "for", "while", "return", "break", "continue", "class", "object",
        "fun", "val", "var", "def", "import", "from", "try", "catch", "finally", "throw",
        "new", "public", "private", "protected", "static", "final", "void", "int", "long",
        "double", "float", "boolean", "char", "true", "false", "null", "nil", "None",
        "True", "False", "this", "self", "super", "extends", "implements", "interface",
        "enum", "package", "const", "let", "async", "await", "yield", "lambda", "when",
        "switch", "case", "default", "struct", "type", "namespace", "using", "include",
        "select", "delete", "insert", "update", "where", "function", "export", "typeof",
    )

    fun highlight(code: String, language: String, colors: DeepSeekColors): AnnotatedString {
        if (language.lowercase() in setOf("text", "plain", "none", "mermaid", "")) {
            return AnnotatedString(code)
        }
        val base = SpanStyle(color = colors.codeText, fontFamily = FontFamily.Monospace)
        return buildAnnotatedString {
            var cursor = 0
            for (match in tokenRegex.findAll(code)) {
                if (match.range.first > cursor) {
                    withStyle(base) { append(code.substring(cursor, match.range.first)) }
                }
                val token = match.value
                val style = when {
                    token.startsWith("//") || token.startsWith("#") || token.startsWith("/*") ->
                        base.copy(color = colors.codeComment, fontStyle = FontStyle.Italic)

                    token.startsWith("\"") || token.startsWith("'") ->
                        base.copy(color = colors.codeString)

                    token.firstOrNull()?.isDigit() == true ->
                        base.copy(color = colors.codeNumber)

                    token in common ->
                        base.copy(color = colors.codeKeyword, fontWeight = FontWeight.Medium)

                    else -> base
                }
                withStyle(style) { append(token) }
                cursor = match.range.last + 1
            }
            if (cursor < code.length) {
                withStyle(base) { append(code.substring(cursor)) }
            }
        }
    }
}
