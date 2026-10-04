package com.liangleionline.shiftschedule.ui

import android.app.DatePickerDialog
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.liangleionline.shiftschedule.ScheduleParser
import com.liangleionline.shiftschedule.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import kotlin.coroutines.resume

class MainActivity : AppCompatActivity() {
    private lateinit var db: AppDatabase
    private lateinit var weekContainer: LinearLayout
    private lateinit var leftColumn: LinearLayout
    private lateinit var rightColumn: LinearLayout
    private lateinit var teamSpinner: Spinner
    private var teams = listOf<Team>()
    private val baseDate = LocalDate.now()
    private var selectedDate = LocalDate.now()
    private var weekOffset = 0L
    private var firstSetupShown = false
    private var suppressTeamCallback = true
    private var currentTeamId: Long? = null
    private var scheduleGeneration = 0L
    private var scheduleJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyDarkStatusBar()
        db = Room.databaseBuilder(this, AppDatabase::class.java, "shift-schedule.db").fallbackToDestructiveMigration().build()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(246,248,252)); setPadding(20, statusBarInset() + 24,20,18) }
        val header = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        header.addView(TextView(this).apply { text = "班组排班助手"; textSize = 27f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.rgb(20,34,58)) })
        header.addView(TextView(this).apply { text = "查看今日上班与休息安排"; textSize = 14f; setTextColor(Color.rgb(99,115,139)); setPadding(0,6,0,0) })
        val weekCard = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(14,14,14,14) }
        weekCard.background = rounded(Color.WHITE, 28f, Color.rgb(226,232,240))
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val prev = Button(this).apply { text = "‹ 上一周" }
        val next = Button(this).apply { text = "下一周 ›" }
        weekContainer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; weightSum = 7f; setPadding(0,10,0,0) }
        val weekTitle = TextView(this).apply {
            text = "本周"
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.rgb(21,101,192))
            setPadding(8,8,8,8)
        }
        top.addView(prev, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(weekTitle, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(next, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        weekCard.addView(top); weekCard.addView(weekContainer)
        teamSpinner = Spinner(this)
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0,8,0,8) }
        val importRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val manageRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0,8,0,0) }
        val import = Button(this).apply { text = "粘贴导入" }
        val cyclic = Button(this).apply { text = "循环排班" }
        val settings = Button(this).apply { text = "设置" }
        val org = Button(this).apply { text = "组织架构" }
        val manage = Button(this).apply { text = "人员管理" }
        importRow.addView(teamSpinner, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        importRow.addView(import)
        importRow.addView(cyclic)
        listOf(settings, org, manage).forEach { manageRow.addView(it, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)) }
        buttons.addView(importRow); buttons.addView(manageRow)
        val columns = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        leftColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(8,8,8,8) }
        rightColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(8,8,8,8) }
        columns.addView(ScrollView(this).apply { addView(leftColumn) }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        columns.addView(ScrollView(this).apply { addView(rightColumn) }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        root.addView(header)
        root.addView(weekCard, LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT).withMargins(0,18,0,12))
        root.addView(buttons, LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT).withMargins(0,0,0,8))
        root.addView(columns, LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root)
        prev.setOnClickListener { weekOffset--; renderWeek(); refreshSchedule() }
        next.setOnClickListener { weekOffset++; renderWeek(); refreshSchedule() }
        weekTitle.setOnClickListener { weekOffset = 0L; selectedDate = LocalDate.now(); renderWeek(); refreshSchedule() }
        teamSpinner.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) {
                val newTeam = teams.getOrNull(pos) ?: return
                if (suppressTeamCallback || newTeam.id == currentTeamId) return
                currentTeamId = newTeam.id
                refreshSchedule()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        import.setOnClickListener { showImportDialog() }
        cyclic.setOnClickListener { showCyclicImportFlow() }
        settings.setOnClickListener { startActivity(Intent(this, ScheduleSettingsActivity::class.java)) }
        org.setOnClickListener { startActivity(Intent(this, OrganizationActivity::class.java)) }
        manage.setOnClickListener { startActivity(Intent(this, StaffManageActivity::class.java)) }
        observeData()
    }

    private fun applyDarkStatusBar() {
        window.statusBarColor = Color.rgb(15, 23, 42)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
    }

    private fun statusBarInset(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

    private fun LinearLayout.LayoutParams.withMargins(l: Int,t:Int,r:Int,b:Int) = apply { setMargins(l,t,r,b) }
    private fun rounded(fill: Int, radius: Float, stroke: Int? = null) = GradientDrawable().apply { cornerRadius = radius; setColor(fill); stroke?.let { setStroke(1,it) } }

    override fun onResume() { super.onResume(); refreshSchedule() }

    override fun onDestroy() {
        scheduleJob?.cancel()
        super.onDestroy()
    }

    private fun observeData() = lifecycleScope.launch {
        db.dao().teams().collect { list ->
            teams = list.distinctBy { it.id }.sortedBy { it.id }
            val names = teams.map { it.name }
            val existingAdapter = teamSpinner.adapter as? ArrayAdapter<*>
            val adapterChanged = existingAdapter == null || existingAdapter.count != names.size || names.indices.any { existingAdapter.getItem(it) != names[it] }
            if (adapterChanged) {
                val adapter = ArrayAdapter(this@MainActivity, com.liangleionline.shiftschedule.R.layout.item_spinner, names)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                suppressTeamCallback = true
                teamSpinner.adapter = adapter
                val preferredId = currentTeamId ?: teams.firstOrNull()?.id
                currentTeamId = preferredId
                val index = teams.indexOfFirst { it.id == preferredId }.coerceAtLeast(0)
                if (teams.isNotEmpty()) teamSpinner.setSelection(index)
                suppressTeamCallback = false
            }
            if (list.isEmpty()) {
                if (!firstSetupShown) {
                    firstSetupShown = true
                    showFirstSetup()
                }
            } else {
                renderWeek()
                refreshSchedule()
            }
        }
    }

    private fun renderWeek() {
        weekContainer.removeAllViews()
        val monday = baseDate.minusDays((baseDate.dayOfWeek.value - 1).toLong()).plusWeeks(weekOffset)
        for (i in 0..6) {
            val date = monday.plusDays(i.toLong())
            val selected = date == selectedDate
            val tv = TextView(this).apply {
                text = date.dayOfMonth.toString(); gravity = Gravity.CENTER; setPadding(4,12,4,12); textSize = 16f
                setTextColor(if (selected) Color.WHITE else Color.rgb(51,65,85)); typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                background = rounded(if (selected) Color.rgb(21,101,192) else Color.TRANSPARENT, 24f)
            }
            tv.setOnClickListener { selectedDate = date; renderWeek(); refreshSchedule() }
            weekContainer.addView(tv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    private fun currentTeam(): Team? = teams.firstOrNull { it.id == currentTeamId } ?: teams.firstOrNull().also { currentTeamId = it?.id }

    private fun refreshSchedule() {
        val generation = ++scheduleGeneration
        scheduleJob?.cancel()
        scheduleJob = lifecycleScope.launch {
            if (!::leftColumn.isInitialized) return@launch
            leftColumn.removeAllViews(); rightColumn.removeAllViews()
            val team = currentTeam() ?: return@launch
            val staff = db.dao().staffByTeam(team.id).first().distinctBy { it.id }
            val groups = db.dao().groups(team.id).first().distinctBy { it.id }.sortedBy { it.id }
            val record = db.dao().schedule(team.id, selectedDate.toString().substring(5))
            val working = if (record == null) emptyList() else if (record.mode == "WORK") staff.filter { it.id in record.staffIds.distinct() } else staff.filterNot { it.id in record.staffIds.distinct() }
            val resting = staff - working.toSet()
            if (generation != scheduleGeneration) return@launch
            renderColumn(leftColumn, "当天上班", Color.rgb(220,252,231), Color.rgb(22,101,52), groups, working, staff, working)
            renderColumn(rightColumn, "当天休息", Color.rgb(241,245,249), Color.rgb(71,85,105), groups, resting, staff, working)
        }
    }

    private fun renderColumn(container: LinearLayout, title: String, badgeBg: Int, badgeText: Int, groups: List<Group>, staff: List<Staff>, allStaff: List<Staff>, workingStaff: List<Staff>) {
        val titleCard = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18,18,18,18); background = rounded(badgeBg, 24f); isClickable = true }
        titleCard.addView(TextView(this).apply { text = "$title  ✎"; textSize = 20f; typeface = Typeface.DEFAULT_BOLD; setTextColor(badgeText) })
        titleCard.addView(TextView(this).apply { text = "${staff.size} 人 · 点击编辑"; textSize = 13f; setTextColor(badgeText); setPadding(0,6,0,0) })
        titleCard.setOnClickListener { showDayStaffEditor(allStaff, workingStaff) }
        container.addView(titleCard, LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 12; rightMargin = 6; leftMargin = 6 })
        groups.forEach { g ->
            val people = staff.filter { it.groupId == g.id }.sortedByDescending { it.role == "班长" }
            if (people.isNotEmpty()) {
                val groupCard = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16,14,16,14); background = rounded(Color.WHITE,22f,Color.rgb(226,232,240)) }
                val lp = LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 12; rightMargin = 6; leftMargin = 6 }
                groupCard.addView(TextView(this).apply { text = g.name; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.rgb(30,41,59)); setPadding(0,0,0,8) })
                people.forEach { person ->
                    groupCard.addView(TextView(this).apply {
                        text = if (person.role == "班长") "● ${person.name}  · 班长" else "○ ${person.name}  · 组员"
                        textSize = 15f; setPadding(2,6,2,6); setTextColor(if (person.role == "班长") Color.rgb(21,101,192) else Color.rgb(51,65,85))
                    })
                }
                container.addView(groupCard, lp)
            }
        }
        if (staff.isEmpty()) container.addView(TextView(this).apply { text = "暂无排班数据"; gravity = Gravity.CENTER; setPadding(8,32,8,8); setTextColor(Color.rgb(100,116,139)) })
    }

    private fun showDayStaffEditor(allStaff: List<Staff>, workingStaff: List<Staff>) = lifecycleScope.launch {
        val team = currentTeam() ?: return@launch
        val groups = db.dao().groupsOnce(team.id).sortedBy { it.id }
        val sortedStaff = allStaff.sortedWith(compareBy<Staff> { person -> groups.indexOfFirst { it.id == person.groupId } }.thenByDescending { it.role == "班长" }.thenBy { it.id })
        val checks = sortedStaff.map { person ->
            android.widget.CheckBox(this@MainActivity).apply {
                val groupName = groups.firstOrNull { it.id == person.groupId }?.name.orEmpty()
                text = "$groupName · ${person.name}${if (person.role == "班长") "（班长）" else ""}"
                isChecked = workingStaff.any { it.id == person.id }
            }
        }
        val scroll = ScrollView(this@MainActivity)
        val panel = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 12, 24, 0) }
        groups.forEach { group ->
            val people = sortedStaff.filter { it.groupId == group.id }
            if (people.isNotEmpty()) {
                panel.addView(TextView(this@MainActivity).apply { text = group.name; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.rgb(30,41,59)); setPadding(0,12,0,4) })
                people.forEach { person -> panel.addView(checks[sortedStaff.indexOf(person)]) }
            }
        }
        scroll.addView(panel)
        AlertDialog.Builder(this@MainActivity)
            .setTitle("调整 ${formatMd(selectedDate)} 上班人员")
            .setMessage("勾选为上班，取消勾选自动为休息。")
            .setView(scroll)
            .setPositiveButton("保存") { _, _ ->
                val selectedIds = sortedStaff.mapIndexedNotNull { index, person -> if (checks[index].isChecked) person.id else null }.distinct()
                lifecycleScope.launch {
                    db.dao().upsertSchedule(ScheduleRecord(teamId = team.id, dateKey = dateKey(selectedDate), mode = "WORK", staffIds = selectedIds))
                    refreshSchedule()
                    Toast.makeText(this@MainActivity, "当天排班已更新", Toast.LENGTH_SHORT).show()
                }
            }.setNegativeButton("取消", null).show()
    }

    private fun showFirstSetup() {
        val input = android.widget.EditText(this).apply { hint = "输入班名" }
        val dialog = AlertDialog.Builder(this).setTitle("首次使用，请先完善组织架构").setView(input).setPositiveButton("新建班", null).setCancelable(false).show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { button ->
            button.isEnabled = false
            lifecycleScope.launch {
                val teamName = input.text.toString().ifBlank { "一班" }
                val tid = db.dao().insertUniqueTeam(teamName)
                val gid = db.dao().insertUniqueGroup(tid, "第一组")
                if (db.dao().staffByGroup(gid).none { it.role == "班长" }) {
                    db.dao().insertStaff(Staff(groupId = gid, name = "新班长", role = "班长"))
                }
                dialog.dismiss()
            }
        }
    }

    private fun showCyclicImportFlow() = lifecycleScope.launch {
        val team = currentTeam() ?: return@launch
        val groups = db.dao().groupsOnce(team.id)
        val allStaff = db.dao().allStaff().filter { person -> groups.any { it.id == person.groupId } }
        if (groups.isEmpty()) { Toast.makeText(this@MainActivity, "请先创建小组", Toast.LENGTH_SHORT).show(); return@launch }
        var start = LocalDate.now()
        var end = start.plusDays(1)
        var cycleDays = 2
        val panel = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 12, 24, 0) }
        val rangeButton = Button(this@MainActivity)
        val cycleInput = EditText(this@MainActivity).apply { hint = "几天一循环，例如 2"; setText(cycleDays.toString()) }
        fun updateRangeText() { rangeButton.text = "日期区间：${formatMd(start)} 至 ${formatMd(end)}" }
        rangeButton.setOnClickListener {
            DatePickerDialog(this@MainActivity, { _, y, m, d ->
                start = LocalDate.of(y, m + 1, d)
                DatePickerDialog(this@MainActivity, { _, y2, m2, d2 ->
                    end = LocalDate.of(y2, m2 + 1, d2)
                    if (end.isBefore(start)) { end = start; Toast.makeText(this@MainActivity, "结束日期已自动调整为开始日期", Toast.LENGTH_SHORT).show() }
                    updateRangeText()
                }, end.year, end.monthValue - 1, end.dayOfMonth).show()
            }, start.year, start.monthValue - 1, start.dayOfMonth).show()
        }
        updateRangeText()
        panel.addView(TextView(this@MainActivity).apply { text = "选择日期区间"; setTextColor(Color.rgb(51,65,85)); setPadding(0,8,0,4) })
        panel.addView(rangeButton)
        panel.addView(TextView(this@MainActivity).apply { text = "循环天数"; setTextColor(Color.rgb(51,65,85)); setPadding(0,14,0,4) })
        panel.addView(cycleInput)
        AlertDialog.Builder(this@MainActivity).setTitle("循环排班").setView(panel)
            .setPositiveButton("下一步") { _, _ ->
                cycleDays = cycleInput.text.toString().toIntOrNull()?.coerceIn(1, 31) ?: 2
                showCycleGroupSetup(team, groups, allStaff, start, end, cycleDays)
            }.setNegativeButton("取消", null).show()
    }

    private fun showCycleGroupSetup(team: Team, groups: List<Group>, allStaff: List<Staff>, start: LocalDate, end: LocalDate, cycleDays: Int) {
        val selectedByCycle = (0 until cycleDays).map { index ->
            val checks = groups.map { group ->
                android.widget.CheckBox(this).apply { text = group.name; isChecked = index == 0 }
            }
            index to checks
        }
        val scroll = ScrollView(this)
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 12, 24, 0) }
        selectedByCycle.forEach { (index, checks) ->
            panel.addView(TextView(this).apply { text = "第 ${index + 1} 天上班小组"; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.rgb(30,41,59)); setPadding(0,14,0,6) })
            checks.forEach { panel.addView(it) }
        }
        scroll.addView(panel)
        AlertDialog.Builder(this).setTitle("设置循环规则").setView(scroll)
            .setPositiveButton("生成预览") { _, _ ->
                val workingGroupIdsByCycle = selectedByCycle.map { (_, checks) -> checks.mapIndexedNotNull { i, box -> if (box.isChecked) groups[i].id else null } }
                generateCyclicPreview(team, groups, allStaff, start, end, workingGroupIdsByCycle)
            }.setNegativeButton("取消", null).show()
    }

    private fun generateCyclicPreview(team: Team, groups: List<Group>, allStaff: List<Staff>, start: LocalDate, end: LocalDate, workingGroupIdsByCycle: List<List<Long>>) = lifecycleScope.launch {
        var cursor = start
        val daily = mutableListOf<Pair<LocalDate, MutableList<Staff>>>()
        var index = 0
        while (!cursor.isAfter(end)) {
            val groupIds = workingGroupIdsByCycle.getOrNull(index % workingGroupIdsByCycle.size.coerceAtLeast(1)).orEmpty()
            daily.add(cursor to allStaff.filter { it.groupId in groupIds }.sortedWith(compareByDescending<Staff> { it.role == "班长" }.thenBy { it.id }).toMutableList())
            cursor = cursor.plusDays(1); index++
        }
        showCyclicPreview(team, groups, allStaff, daily)
    }

    private fun showCyclicPreview(team: Team, groups: List<Group>, allStaff: List<Staff>, daily: MutableList<Pair<LocalDate, MutableList<Staff>>>) {
        val scroll = ScrollView(this)
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 10, 20, 0) }
        fun rebuild() {
            panel.removeAllViews()
            daily.forEachIndexed { dayIndex, (date, people) ->
                val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16,14,16,14); background = rounded(Color.WHITE, 22f, Color.rgb(226,232,240)); layoutParams = LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 12 } }
                val titleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                titleRow.addView(TextView(this).apply { text = "${formatMd(date)}  上班 ${people.size} 人"; typeface = Typeface.DEFAULT_BOLD; textSize = 17f; setTextColor(Color.rgb(30,41,59)) }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                val edit = Button(this).apply { text = "调整" }
                titleRow.addView(edit)
                card.addView(titleRow)
                card.addView(TextView(this).apply { text = people.joinToString("、") { it.name }.ifBlank { "无人上班" }; setTextColor(Color.rgb(71,85,105)); setPadding(0,8,0,0) })
                edit.setOnClickListener { editCyclicDay(team, groups, allStaff, daily, dayIndex) { rebuild() } }
                panel.addView(card)
            }
        }
        rebuild(); scroll.addView(panel)
        AlertDialog.Builder(this).setTitle("循环排班预览").setView(scroll)
            .setPositiveButton("保存导入") { _, _ -> saveCyclicSchedule(team, daily) }
            .setNegativeButton("取消", null).show()
    }

    private fun editCyclicDay(team: Team, groups: List<Group>, allStaff: List<Staff>, daily: MutableList<Pair<LocalDate, MutableList<Staff>>>, dayIndex: Int, changed: () -> Unit) {
        val current = daily[dayIndex].second
        val checks = allStaff.sortedBy { it.groupId }.map { person -> android.widget.CheckBox(this).apply { text = "${groups.firstOrNull { it.id == person.groupId }?.name.orEmpty()} · ${person.name}${if (person.role == "班长") "（班长）" else ""}"; isChecked = current.any { it.id == person.id } } }
        val scroll = ScrollView(this)
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 12, 24, 0) }
        checks.forEach { panel.addView(it) }; scroll.addView(panel)
        AlertDialog.Builder(this).setTitle("调整 ${formatMd(daily[dayIndex].first)} 上班人员").setView(scroll)
            .setPositiveButton("确定") { _, _ ->
                daily[dayIndex] = daily[dayIndex].copy(second = allStaff.filter { person -> checks[allStaff.indexOf(person)].isChecked }.toMutableList())
                changed()
            }.setNegativeButton("取消", null).show()
    }

    private fun saveCyclicSchedule(team: Team, daily: List<Pair<LocalDate, MutableList<Staff>>>) = lifecycleScope.launch {
        if (daily.isNotEmpty()) {
            val snapshot = db.dao().allSchedules().filter { it.teamId == team.id }
            db.dao().insertHistory(ScheduleImportHistory(teamId = team.id, createdAt = System.currentTimeMillis(), snapshotJson = scheduleSnapshotJson(snapshot)))
        }
        daily.forEach { (date, people) ->
            db.dao().upsertSchedule(ScheduleRecord(teamId = team.id, dateKey = dateKey(date), mode = "WORK", staffIds = people.map { it.id }.distinct()))
        }
        db.dao().trimHistory(team.id)
        Toast.makeText(this@MainActivity, "循环排班已导入", Toast.LENGTH_SHORT).show()
        refreshSchedule()
    }

    private fun formatMd(date: LocalDate) = "%d月%d日".format(date.monthValue, date.dayOfMonth)
    private fun dateKey(date: LocalDate) = "%02d-%02d".format(date.monthValue, date.dayOfMonth)

    private fun showImportDialog() {
        val input = android.widget.EditText(this).apply { hint = "在此粘贴排班文本"; minLines = 8 }
        AlertDialog.Builder(this).setTitle("导入排班").setView(input).setPositiveButton("解析并导入") { _, _ -> importText(input.text.toString()) }.setNegativeButton("清空") { _, _ -> input.setText("") }.show()
    }

    private fun importText(text: String) = lifecycleScope.launch {
        val team = currentTeam() ?: return@launch
        val modeStatus = ScheduleParser.detectModeStatus(text)
        val mode = if (modeStatus == "REST") "REST" else if (modeStatus == "WORK") "WORK" else askImportModeAndWait(modeStatus) ?: return@launch
        val parsedLines = ScheduleParser.parse(text)
        var staff = db.dao().allStaff().filter { person -> db.dao().groups(team.id).first().any { it.id == person.groupId } }
        val aliases = db.dao().aliases().associate { it.rawName to it.staffId }.toMutableMap()
        val handledRawNames = mutableSetOf<String>()
        val resolvedRawToStaff = mutableMapOf<String, Staff?>()

        parsedLines.flatMap { it.rawNames }.distinct().forEach { raw ->
            val aliased = aliases[raw]?.let { id -> staff.firstOrNull { it.id == id } }
            val exact = staff.firstOrNull { it.name == raw }
            val candidate = exact ?: aliased
            when {
                candidate != null -> resolvedRawToStaff[raw] = candidate
                else -> {
                    val fuzzy = ScheduleParser.fuzzyCandidates(raw, staff)
                    when {
                        fuzzy.size == 1 -> {
                            resolvedRawToStaff[raw] = fuzzy.first()
                            db.dao().saveAlias(NameAlias(raw, fuzzy.first().id))
                        }
                        raw in handledRawNames -> Unit
                        else -> {
                            handledRawNames += raw
                            val chosen = showUnknownPickerAndWait(raw, staff, fuzzy, team)
                            if (chosen != null) {
                                resolvedRawToStaff[raw] = chosen
                                db.dao().saveAlias(NameAlias(raw, chosen.id))
                                if (staff.none { it.id == chosen.id }) staff = staff + chosen
                            }
                        }
                    }
                }
            }
        }

        if (parsedLines.isNotEmpty()) {
            val beforeSnapshot = db.dao().allSchedules().filter { it.teamId == team.id }.let { records -> scheduleSnapshotJson(records) }
            db.dao().insertHistory(ScheduleImportHistory(teamId = team.id, createdAt = System.currentTimeMillis(), snapshotJson = beforeSnapshot))
        }

        parsedLines.forEach { line ->
            val ids = line.rawNames.mapNotNull { raw -> resolvedRawToStaff[raw]?.id }.distinct()
            db.dao().upsertSchedule(ScheduleRecord(teamId = team.id, dateKey = line.dateKey, mode = mode, staffIds = ids))
        }
        if (parsedLines.isNotEmpty()) db.dao().trimHistory(team.id)
        refreshSchedule()
    }

    private fun scheduleSnapshotJson(records: List<ScheduleRecord>): String {
        val array = JSONArray()
        records.forEach { record ->
            val ids = JSONArray()
            record.staffIds.forEach { ids.put(it) }
            array.put(JSONObject()
                .put("id", record.id)
                .put("dateKey", record.dateKey)
                .put("mode", record.mode)
                .put("staffIds", ids))
        }
        return array.toString()
    }

    private suspend fun askImportModeAndWait(status: String): String? {
        val message = if (status == "AMBIGUOUS") "同时识别到了“上班”和“休息”，请选择这份排班表的类型。" else "没有识别到“上班”或“休息”，请选择这份排班表的类型。"
        return suspendCancellableCoroutine { continuation ->
            val dialog = AlertDialog.Builder(this).setTitle("选择排班类型").setMessage(message)
                .setPositiveButton("上班表") { _, _ -> if (continuation.isActive) continuation.resume("WORK") }
                .setNegativeButton("休息表") { _, _ -> if (continuation.isActive) continuation.resume("REST") }
                .setNeutralButton("取消") { _, _ -> if (continuation.isActive) continuation.resume(null) }
                .setOnCancelListener { if (continuation.isActive) continuation.resume(null) }
                .show()
            continuation.invokeOnCancellation { dialog.dismiss() }
        }
    }

    private suspend fun showUnknownPickerAndWait(raw: String, staff: List<Staff>, candidates: List<Staff>, team: Team): Staff? {
        return suspendCancellableCoroutine { continuation ->
            val candidateNames = (if (candidates.isNotEmpty()) candidates else staff).map { it.name }
            val options = candidateNames + listOf("新增人员", "忽略")
            val dialog = AlertDialog.Builder(this).setTitle("无法确定：$raw").setItems(options.toTypedArray()) { _, i ->
                when {
                    i < candidateNames.size -> continuation.resume((if (candidates.isNotEmpty()) candidates else staff)[i])
                    i == candidateNames.size -> showAddUnknownStaffDialog(raw, team, continuation)
                    else -> continuation.resume(null)
                }
            }.setOnCancelListener {
                if (continuation.isActive) continuation.resume(null)
            }.show()
            continuation.invokeOnCancellation { dialog.dismiss() }
        }
    }

    private fun showAddUnknownStaffDialog(raw: String, team: Team, continuation: kotlinx.coroutines.CancellableContinuation<Staff?>) {
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 12, 28, 0) }
        val nameInput = android.widget.EditText(this).apply { setText(raw) }
        val groupSpinner = Spinner(this)
        var groups = emptyList<Group>()
        lifecycleScope.launch {
            groups = db.dao().groupsOnce(team.id)
            groupSpinner.adapter = ArrayAdapter(this@MainActivity, com.liangleionline.shiftschedule.R.layout.item_spinner, groups.map { it.name })
        }
        panel.addView(android.widget.TextView(this).apply { text = "姓名" })
        panel.addView(nameInput)
        panel.addView(android.widget.TextView(this).apply { text = "添加到小组"; setPadding(0, 14, 0, 0) })
        panel.addView(groupSpinner)
        AlertDialog.Builder(this).setTitle("新增人员：$raw").setView(panel).setPositiveButton("保存") { _, _ ->
            val name = nameInput.text.toString().trim()
            val group = groups.getOrNull(groupSpinner.selectedItemPosition)
            if (name.isBlank() || group == null) {
                Toast.makeText(this, "请填写姓名并选择小组", Toast.LENGTH_SHORT).show()
            } else lifecycleScope.launch {
                val created = Staff(groupId = group.id, name = name, role = "组员")
                val id = db.dao().insertStaff(created)
                if (continuation.isActive) continuation.resume(created.copy(id = id))
            }
        }.setNegativeButton("取消") { _, _ -> if (continuation.isActive) continuation.resume(null) }
            .setOnCancelListener { if (continuation.isActive) continuation.resume(null) }
            .show()
    }
}
