package com.liangleionline.shiftschedule.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.liangleionline.shiftschedule.R
import com.liangleionline.shiftschedule.ScheduleParser
import com.liangleionline.shiftschedule.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : AppCompatActivity() {
    private lateinit var db: AppDatabase
    private lateinit var weekContainer: LinearLayout
    private lateinit var leftColumn: LinearLayout
    private lateinit var rightColumn: LinearLayout
    private lateinit var teamSpinner: Spinner
    private var teams = listOf<Team>()
    private var selectedDate = LocalDate.now()
    private var weekOffset = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = Room.databaseBuilder(this, AppDatabase::class.java, "shift-schedule.db").build()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16,16,16,16) }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val prev = Button(this).apply { text = "上一周" }
        val next = Button(this).apply { text = "下一周" }
        weekContainer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; weightSum = 7f }
        top.addView(prev, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(weekContainer, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 4f))
        top.addView(next, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        teamSpinner = Spinner(this)
        val import = Button(this).apply { text = "导入排班" }
        val manage = Button(this).apply { text = "人员管理" }
        val columns = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        leftColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(8,8,8,8) }
        rightColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(8,8,8,8) }
        columns.addView(ScrollView(this).apply { addView(leftColumn) }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        columns.addView(ScrollView(this).apply { addView(rightColumn) }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        root.addView(top); root.addView(teamSpinner); root.addView(import); root.addView(manage); root.addView(columns, LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root)
        prev.setOnClickListener { weekOffset--; renderWeek(); refreshSchedule() }
        next.setOnClickListener { weekOffset++; renderWeek(); refreshSchedule() }
        teamSpinner.onItemSelectedListener = object: AdapterView.OnItemSelectedListener { override fun onItemSelected(p: AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) { refreshSchedule() }; override fun onNothingSelected(p: AdapterView<*>?) {} }
        import.setOnClickListener { showImportDialog() }
        manage.setOnClickListener { showManageDialog() }
        observeData()
    }

    private fun observeData() = lifecycleScope.launch {
        db.dao().teams().collect { list ->
            teams = list
            teamSpinner.adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, list.map { it.name })
            if (list.isEmpty()) showFirstSetup() else renderWeek(); refreshSchedule()
        }
    }

    private fun renderWeek() {
        weekContainer.removeAllViews()
        val monday = selectedDate.minusDays((selectedDate.dayOfWeek.value - 1).toLong()).plusWeeks(weekOffset)
        for (i in 0..6) {
            val date = monday.plusDays(i.toLong())
            val tv = TextView(this).apply { text = date.dayOfMonth.toString(); gravity = Gravity.CENTER; setPadding(4,12,4,12) }
            tv.setOnClickListener { selectedDate = date; renderWeek(); refreshSchedule() }
            weekContainer.addView(tv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    private fun currentTeam(): Team? = teams.getOrNull(teamSpinner.selectedItemPosition)

    private fun refreshSchedule() = lifecycleScope.launch {
        leftColumn.removeAllViews(); rightColumn.removeAllViews()
        val team = currentTeam() ?: return@launch
        val staff = db.dao().staffByTeam(team.id).first()
        val groups = db.dao().groups(team.id).first()
        val record = db.dao().schedule(team.id, selectedDate.toString().substring(5))
        val working = if (record == null) emptyList() else if (record.mode == "WORK") staff.filter { it.id in record.staffIds } else staff.filterNot { it.id in record.staffIds }
        val resting = staff - working.toSet()
        renderColumn(leftColumn, "当天上班", groups, working)
        renderColumn(rightColumn, "当天休息", groups, resting)
    }

    private fun renderColumn(container: LinearLayout, title: String, groups: List<Group>, staff: List<Staff>) {
        container.addView(TextView(this).apply { text = title; textSize = 20f; setPadding(8,12,8,12) })
        groups.forEach { g ->
            val people = staff.filter { it.groupId == g.id }.sortedByDescending { it.role == "班长" }
            if (people.isNotEmpty()) {
                container.addView(TextView(this).apply { text = g.name; setPadding(8,12,8,4) })
                people.forEach { person -> container.addView(TextView(this).apply { text = person.name + if (person.role == "班长") "（班长）" else ""; setPadding(16,4,8,4) }) }
            }
        }
        if (staff.isEmpty()) container.addView(TextView(this).apply { text = "暂无排班数据"; setPadding(8,16,8,8) })
    }

    private fun showFirstSetup() {
        val input = EditText(this).apply { hint = "输入班名" }
        AlertDialog.Builder(this).setTitle("首次使用，请先完善组织架构").setView(input).setPositiveButton("新建班") { _, _ -> lifecycleScope.launch {
            val tid = db.dao().insertTeam(Team(name = input.text.toString().ifBlank { "一班" }))
            val gid = db.dao().insertGroup(Group(teamId = tid, name = "第一组"))
            db.dao().insertStaff(Staff(groupId = gid, name = "新班长", role = "班长"))
        }}.setCancelable(false).show()
    }

    private fun showManageDialog() {
        val input = EditText(this).apply { hint = "姓名，职位默认组员" }
        AlertDialog.Builder(this).setTitle("添加人员到第一组（演示）").setView(input).setPositiveButton("添加") { _, _ -> lifecycleScope.launch {
            val team = currentTeam() ?: return@launch
            val gid = db.dao().groups(team.id).first().firstOrNull()?.id ?: return@launch
            db.dao().insertStaff(Staff(groupId = gid, name = input.text.toString()))
            refreshSchedule()
        }}.setNegativeButton("取消", null).show()
    }

    private fun showImportDialog() {
        val input = EditText(this).apply { hint = "在此粘贴排班文本"; minLines = 8 }
        AlertDialog.Builder(this).setTitle("导入排班").setView(input).setPositiveButton("解析并导入") { _, _ -> importText(input.text.toString()) }.setNegativeButton("清空") { _, _ -> input.setText("") }.show()
    }

    private fun importText(text: String) = lifecycleScope.launch {
        val team = currentTeam() ?: return@launch
        val mode = ScheduleParser.detectMode(text)
        val staff = db.dao().allStaff().filter { person -> db.dao().groups(team.id).first().any { it.id == person.groupId } }
        val aliases = db.dao().aliases().associate { it.rawName to it.staffId }
        ScheduleParser.parse(text).forEach { line ->
            val ids = mutableListOf<Long>()
            line.rawNames.forEach { raw ->
                val aliased = aliases[raw]
                val exact = staff.firstOrNull { it.name == raw }
                val candidate = exact ?: aliased?.let { a -> staff.firstOrNull { it.id == a } }
                when {
                    candidate != null -> ids += candidate.id
                    else -> {
                        val fuzzy = ScheduleParser.fuzzyCandidates(raw, staff)
                        if (fuzzy.size == 1) { ids += fuzzy.first().id; db.dao().saveAlias(NameAlias(raw, fuzzy.first().id)) }
                        else runOnUiThread { showUnknownPicker(raw, staff, fuzzy) { chosen -> chosen?.let { ids += it.id; lifecycleScope.launch { db.dao().saveAlias(NameAlias(raw, it.id)) } } } }
                    }
                }
            }
            db.dao().upsertSchedule(ScheduleRecord(teamId = team.id, dateKey = line.dateKey, mode = mode, staffIds = ids.distinct()))
        }
        refreshSchedule()
    }

    private fun showUnknownPicker(raw: String, staff: List<Staff>, candidates: List<Staff>, callback: (Staff?) -> Unit) {
        val options = (if (candidates.isNotEmpty()) candidates else staff).map { it.name }.toMutableList().apply { add("忽略") }
        AlertDialog.Builder(this).setTitle("无法确定：$raw").setItems(options.toTypedArray()) { _, i -> if (i < options.lastIndex) callback(staff.getOrNull(i)) else callback(null) }.show()
    }
}
