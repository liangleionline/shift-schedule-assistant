package com.liangleionline.shiftschedule

import com.liangleionline.shiftschedule.data.Staff

data class ParsedLine(val dateKey: String, val rawNames: List<String>)

object ScheduleParser {
    private val homophoneGroups = listOf(
        setOf('雨','语','宇','羽','玉','域'),
        setOf('晓','小','筱','肖'),
        setOf('侯','候'),
        setOf('飞','菲','非','霏'),
        setOf('晗','涵','寒','含'),
        setOf('璐','路','露','鲁'),
        setOf('冉','然','染'),
        setOf('魏','卫','位'),
        setOf('梁','良','亮'),
        setOf('郭','国','果'),
        setOf('张','章'),
        setOf('杜','度'),
        setOf('高','皋'),
        setOf('王','望'),
        setOf('许','徐','旭'),
        setOf('李','理','立','力'),
        setOf('石','时','史','士')
    )

    fun detectModeStatus(text: String): String {
        val hasRest = text.contains("休息")
        val hasWork = text.contains("上班")
        return when {
            hasRest && hasWork -> "AMBIGUOUS"
            hasRest -> "REST"
            hasWork -> "WORK"
            else -> "NONE"
        }
    }

    fun parse(text: String): List<ParsedLine> = text.lineSequence().mapNotNull { line ->
        val regex = Regex("""(\d{1,2})\s*[月.]\s*(\d{1,2})\s*日?""")
        val date = regex.find(line) ?: return@mapNotNull null
        val month = date.groupValues[1].padStart(2, '0')
        val day = date.groupValues[2].padStart(2, '0')
        val after = line.substringAfter('：', line.substringAfter(':', line)).trim()
        val names = after.split(Regex("""[\s,，、。.；;]+""")).map { it.trim() }.filter { it.length in 2..4 && it.none(Char::isDigit) }
        ParsedLine("$month-$day", names)
    }.toList()

    fun fuzzyCandidates(raw: String, staff: List<Staff>): List<Staff> {
        return staff.filter { person ->
            val target = person.name
            target.endsWith(raw) || raw.endsWith(target) || target.last() == raw.last() ||
                missingCharMatch(raw, target) || homophoneMatch(raw, target)
        }.distinctBy { it.id }
    }

    private fun missingCharMatch(raw: String, target: String): Boolean {
        if (raw.length >= target.length) return false
        val short = if (target.length - raw.length == 1) target.drop(1) else target
        return short == raw || short.contains(raw)
    }

    private fun samePinyinGroup(a: Char, b: Char): Boolean = homophoneGroups.any { a in it && b in it }

    private fun homophoneMatch(raw: String, target: String): Boolean {
        val a = if (target.length > raw.length) target.drop(1) else target
        if (a.length != raw.length) return false
        return a.indices.count { i -> a[i] != raw[i] && samePinyinGroup(a[i], raw[i]) } in 1..2 &&
            a.indices.count { i -> a[i] == raw[i] } >= a.length - 2
    }
}
