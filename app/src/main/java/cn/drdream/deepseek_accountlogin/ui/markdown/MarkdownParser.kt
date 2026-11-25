package cn.drdream.deepseek_accountlogin.ui.markdown

sealed interface MdBlock {
    data class Paragraph(val text: String) : MdBlock
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Code(val language: String, val code: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class BulletList(val items: List<MdItem>) : MdBlock
    data class OrderedList(val start: Int, val items: List<MdItem>) : MdBlock
    data class Table(val header: List<String>, val rows: List<List<String>>) : MdBlock
    data object Divider : MdBlock
    data class Math(val latex: String) : MdBlock
}

data class MdItem(val level: Int, val marker: String, val text: String)

/**
 * Small, dependency-free Markdown reader aimed at model output. It deliberately
 * understands block structure only; inline styles are handled by the renderer.
 */
object MarkdownParser {

    private val heading = Regex("^(#{1,6})\\s+(.+)$")
    private val bullet = Regex("^(\\s*)([-*+])\\s+(.*)$")
    private val ordered = Regex("^(\\s*)(\\d+)[.)]\\s+(.*)$")
    private val divider = Regex("^\\s{0,3}(?:-{3,}|\\*{3,}|_{3,})\\s*$")
    private val tableSeparator = Regex("^:?-{2,}:?$")

    fun parse(source: String): List<MdBlock> {
        val lines = source.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val blocks = ArrayList<MdBlock>()
        val paragraph = StringBuilder()
        var index = 0

        fun flushParagraph() {
            if (paragraph.isNotBlank()) {
                blocks += MdBlock.Paragraph(paragraph.toString().trim())
                paragraph.setLength(0)
            }
        }

        while (index < lines.size) {
            val line = lines[index]
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                flushParagraph()
                index++
                continue
            }

            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                flushParagraph()
                val fence = trimmed.take(3)
                val language = trimmed.drop(3).trim().substringBefore(' ')
                val code = StringBuilder()
                index++
                while (index < lines.size && !lines[index].trim().startsWith(fence)) {
                    code.appendLine(lines[index])
                    index++
                }
                if (index < lines.size) index++
                blocks += MdBlock.Code(language, code.toString().trimEnd())
                continue
            }

            if (trimmed.startsWith("$$") || trimmed.startsWith("\\[")) {
                flushParagraph()
                val closing = if (trimmed.startsWith("$$")) "$$" else "\\]"
                val opening = if (trimmed.startsWith("$$")) "$$" else "\\["
                val body = trimmed.removePrefix(opening)
                if (body.endsWith(closing) && body.length >= closing.length) {
                    blocks += MdBlock.Math(body.dropLast(closing.length).trim())
                    index++
                    continue
                }
                val math = StringBuilder(body)
                index++
                while (index < lines.size && !lines[index].trim().endsWith(closing)) {
                    math.appendLine(lines[index])
                    index++
                }
                if (index < lines.size) {
                    math.appendLine(lines[index].trim().removeSuffix(closing))
                    index++
                }
                blocks += MdBlock.Math(math.toString().trim())
                continue
            }

            val headingMatch = heading.find(trimmed)
            if (headingMatch != null) {
                flushParagraph()
                blocks += MdBlock.Heading(
                    level = headingMatch.groupValues[1].length,
                    text = headingMatch.groupValues[2].trim(),
                )
                index++
                continue
            }

            if (divider.matches(trimmed)) {
                flushParagraph()
                blocks += MdBlock.Divider
                index++
                continue
            }

            if (trimmed.startsWith(">")) {
                flushParagraph()
                val quote = StringBuilder()
                while (index < lines.size && lines[index].trim().startsWith(">")) {
                    quote.appendLine(lines[index].trim().removePrefix(">").trimStart())
                    index++
                }
                blocks += MdBlock.Quote(quote.toString().trim())
                continue
            }

            val tableResult = parseTable(lines, index)
            if (tableResult != null) {
                flushParagraph()
                blocks += tableResult.first
                index = tableResult.second
                continue
            }

            if (bullet.containsMatchIn(line)) {
                flushParagraph()
                val (list, next) = parseList(lines, index, ordered = false)
                blocks += list
                index = next
                continue
            }

            if (ordered.containsMatchIn(line)) {
                flushParagraph()
                val (list, next) = parseList(lines, index, ordered = true)
                blocks += list
                index = next
                continue
            }

            paragraph.appendLine(trimmed)
            index++
        }
        flushParagraph()
        return blocks
    }

    private fun parseList(lines: List<String>, start: Int, ordered: Boolean): Pair<MdBlock, Int> {
        val items = ArrayList<MdItem>()
        val pattern = if (ordered) this.ordered else bullet
        var index = start
        var startNumber = 1
        while (index < lines.size) {
            val line = lines[index]
            if (line.isBlank()) break
            val match = pattern.find(line)
            if (match == null) {
                val trimmed = line.trim()
                if (items.isNotEmpty() && line.firstOrNull()?.isWhitespace() == true &&
                    !isBlockStart(lines, index)
                ) {
                    val previous = items.removeAt(items.lastIndex)
                    items += previous.copy(text = previous.text + " " + trimmed)
                    index++
                    continue
                }
                break
            }
            val indent = match.groupValues[1].length
            val level = (indent / 2).coerceAtMost(4)
            val marker = match.groupValues[2]
            val text = match.groupValues[3]
            if (ordered && items.isEmpty()) startNumber = marker.toIntOrNull() ?: 1
            items += MdItem(level = level, marker = marker, text = text.trim())
            index++
        }
        val block: MdBlock = if (ordered) {
            MdBlock.OrderedList(startNumber, items)
        } else {
            MdBlock.BulletList(items)
        }
        return block to index
    }

    private fun parseTable(lines: List<String>, start: Int): Pair<MdBlock, Int>? {
        if (start + 1 >= lines.size) return null
        val headerLine = lines[start].trim()
        val separatorLine = lines[start + 1].trim()
        if (!headerLine.contains('|') || !separatorLine.contains('|')) return null
        val header = splitCells(headerLine)
        val separator = splitCells(separatorLine)
        if (header.isEmpty() || separator.size != header.size) return null
        if (separator.any { !tableSeparator.matches(it) }) return null

        val rows = ArrayList<List<String>>()
        var index = start + 2
        while (index < lines.size) {
            val row = lines[index].trim()
            if (row.isEmpty() || !row.contains('|')) break
            val cells = splitCells(row)
            rows += header.indices.map { cells.getOrElse(it) { "" } }
            index++
        }
        return MdBlock.Table(header, rows) to index
    }

    private fun splitCells(line: String): List<String> {
        val normalized = line.trim().removePrefix("|").removeSuffix("|")
        val placeholder = "\u0000"
        return normalized.replace("\\|", placeholder)
            .split('|')
            .map { it.trim().replace(placeholder, "|") }
    }

    private fun isBlockStart(lines: List<String>, index: Int): Boolean {
        val trimmed = lines[index].trim()
        if (trimmed.isEmpty()) return true
        if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) return true
        if (trimmed.startsWith("$$") || trimmed.startsWith("\\[")) return true
        if (heading.matches(trimmed)) return true
        if (divider.matches(trimmed)) return true
        if (trimmed.startsWith(">")) return true
        if (bullet.containsMatchIn(lines[index])) return true
        if (ordered.containsMatchIn(lines[index])) return true
        return parseTable(lines, index) != null
    }
}
