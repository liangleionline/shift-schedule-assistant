package com.liangleionline.shiftschedule.ui

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.liangleionline.shiftschedule.ScheduleParser
import com.liangleionline.shiftschedule.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
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
        top.addView(prev, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(TextView(this).apply { text = "本周"; gravity = Gravity.CENTER; setTypeface(typeface, Typeface.BOLD) }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(next, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        weekCard.addView(top); weekCard.addView(weekContainer)
        teamSpinner = Spinner(this)
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0,8,0,8) }
        val import = Button(this).apply { text = "导入排班" }
        val org = Button(this).apply { text = "组织架构" }
        val manage = Button(this).apply { text = "人员管理" }
        buttons.addView(teamSpinner, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        buttons.addView(import)
        buttons.addView(org)
        buttons.addView(manage)
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
        org.setOnClickListener { startActivity(Intent(this, OrganizationActivity::class.java)) }
        manage.setOnClickListener { startActivity(Intent(this, StaffManageActivity::class.java)) }
        observeData()
    }

    private fun applyDarkStatusBar() {
        window.statusBarColor = Color.rgb(15, 23, 42)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        }
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
            renderColumn(leftColumn, "当天上班", Color.rgb(220,252,231), Color.rgb(22,101,52), groups, working)
            renderColumn(rightColumn, "当天休息", Color.rgb(241,245,249), Color.rgb(71,85,105), groups, resting)
        }
    }

    private fun renderColumn(container: LinearLayout, title: String, badgeBg: Int, badgeText: Int, groups: List<Group>, staff: List<Staff>) {
        val titleCard = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18,18,18,18); background = rounded(badgeBg, 24f) }
        titleCard.addView(TextView(this).apply { text = title; textSize = 20f; typeface = Typeface.DEFAULT_BOLD; setTextColor(badgeText) })
        titleCard.addView(TextView(this).apply { text = "${staff.size} 人"; textSize = 13f; setTextColor(badgeText); setPadding(0,6,0,0) })
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

    private fun showImportDialog() {
        val input = android.widget.EditText(this).apply { hint = "在此粘贴排班文本"; minLines = 8 }
        AlertDialog.Builder(this).setTitle("导入排班").setView(input).setPositiveButton("解析并导入") { _, _ -> importText(input.text.toString()) }.setNegativeButton("清空") { _, _ -> input.setText("") }.show()
    }

    private fun importText(text: String) = lifecycleScope.launch {
        val team = currentTeam() ?: return@launch
        val mode = ScheduleParser.detectMode(text)
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

        parsedLines.forEach { line ->
            val ids = line.rawNames.mapNotNull { raw -> resolvedRawToStaff[raw]?.id }.distinct()
            db.dao().upsertSchedule(ScheduleRecord(teamId = team.id, dateKey = line.dateKey, mode = mode, staffIds = ids))
        }
        refreshSchedule()
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
