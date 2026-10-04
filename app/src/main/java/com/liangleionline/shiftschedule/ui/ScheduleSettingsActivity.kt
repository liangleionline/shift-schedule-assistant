package com.liangleionline.shiftschedule.ui

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.liangleionline.shiftschedule.data.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

class ScheduleSettingsActivity : AppCompatActivity() {
    private lateinit var db: AppDatabase
    private lateinit var teamSpinner: Spinner
    private lateinit var undoButton: Button
    private var teams = listOf<Team>()
    private var currentTeamId: Long? = null
    private var suppress = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(15,23,42)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
        db = Room.databaseBuilder(this, AppDatabase::class.java, "shift-schedule.db").fallbackToDestructiveMigration().build()

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(246,248,252)); setPadding(22,statusBarInset()+28,22,22) }
        root.addView(TextView(this).apply { text = "排班设置"; textSize = 26f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.rgb(20,34,58)) })
        root.addView(TextView(this).apply { text = "清空、按日期范围删除或撤销导入"; textSize = 14f; setTextColor(Color.rgb(99,115,139)); setPadding(0,8,0,20) })
        teamSpinner = Spinner(this)
        root.addView(teamSpinner)

        undoButton = Button(this).apply { text = "撤销上一次导入" }
        val clearRangeButton = Button(this).apply { text = "清空指定时间段排班" }
        val clearAllButton = Button(this).apply { text = "清空所有排班数据" }
        listOf(undoButton, clearRangeButton, clearAllButton).forEach { button ->
            button.textSize = 17f
            button.layoutParams = LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 22 }
            root.addView(button)
        }
        setContentView(root)

        teamSpinner.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                if (suppress) return
                currentTeamId = teams.getOrNull(position)?.id
                refreshUndoState()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        undoButton.setOnClickListener { confirmUndo() }
        clearRangeButton.setOnClickListener { pickDateRange() }
        clearAllButton.setOnClickListener { confirmClearAll() }
        loadTeams()
    }

    private fun statusBarInset(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

    private fun loadTeams() = lifecycleScope.launch {
        teams = db.dao().allTeams().distinctBy { it.id }.sortedBy { it.id }
        currentTeamId = teams.firstOrNull()?.id
        val adapter = ArrayAdapter(this@ScheduleSettingsActivity, com.liangleionline.shiftschedule.R.layout.item_spinner, teams.map { it.name })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        suppress = true
        teamSpinner.adapter = adapter
        teamSpinner.setSelection(0)
        suppress = false
        refreshUndoState()
    }

    private fun team() = teams.firstOrNull { it.id == currentTeamId } ?: teams.firstOrNull()

    private fun refreshUndoState() = lifecycleScope.launch {
        val t = team()
        val count = if (t == null) 0 else db.dao().historyCount(t.id)
        undoButton.isEnabled = count > 0
        undoButton.text = if (count > 0) "撤销上一次导入（剩余 $count 次）" else "撤销上一次导入（暂无可撤销内容）"
    }

    private fun confirmUndo() {
        val t = team() ?: return
        AlertDialog.Builder(this).setTitle("撤销导入").setMessage("确定撤销最近一次导入吗？最多可连续撤销 3 次。")
            .setPositiveButton("撤销") { _, _ -> lifecycleScope.launch {
                val history = db.dao().latestHistory(t.id)
                if (history != null) {
                    restoreSnapshot(t.id, history.snapshotJson)
                    db.dao().deleteHistory(history.id)
                    Toast.makeText(this@ScheduleSettingsActivity, "已撤销最近一次导入", Toast.LENGTH_SHORT).show()
                }
                refreshUndoState()
            }}.setNegativeButton("取消", null).show()
    }

    private suspend fun restoreSnapshot(teamId: Long, json: String) {
        db.dao().clearTeamSchedules(teamId)
        val array = JSONArray(json)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val ids = obj.getJSONArray("staffIds").let { idsArray -> (0 until idsArray.length()).map { idsArray.getLong(it) } }
            db.dao().upsertSchedule(ScheduleRecord(
                id = obj.getLong("id"),
                teamId = teamId,
                dateKey = obj.getString("dateKey"),
                mode = obj.getString("mode"),
                staffIds = ids
            ))
        }
    }

    private fun pickDateRange() {
        val today = LocalDate.now()
        DatePickerDialog(this, { _, y, m, d ->
            val start = LocalDate.of(y, m + 1, d)
            DatePickerDialog(this, { _, y2, m2, d2 ->
                val end = LocalDate.of(y2, m2 + 1, d2)
                if (end.isBefore(start)) {
                    Toast.makeText(this, "结束日期不能早于开始日期", Toast.LENGTH_SHORT).show()
                } else confirmClearRange(start, end)
            }, today.year, today.monthValue - 1, today.dayOfMonth).apply { setTitle("选择结束日期") }.show()
        }, today.year, today.monthValue - 1, today.dayOfMonth).apply { setTitle("选择开始日期") }.show()
    }

    private fun confirmClearRange(start: LocalDate, end: LocalDate) {
        val t = team() ?: return
        AlertDialog.Builder(this).setTitle("清空时间段排班")
            .setMessage("确定清空 ${formatDate(start)} 至 ${formatDate(end)} 的排班数据吗？")
            .setPositiveButton("清空") { _, _ -> lifecycleScope.launch {
                db.dao().deleteSchedulesInRange(t.id, key(start), key(end))
                Toast.makeText(this@ScheduleSettingsActivity, "指定时间段排班已清空", Toast.LENGTH_SHORT).show()
            }}.setNegativeButton("取消", null).show()
    }

    private fun confirmClearAll() {
        val t = team() ?: return
        AlertDialog.Builder(this).setTitle("清空所有排班")
            .setMessage("确定清空当前班「${t.name}」的所有排班数据吗？人员和组织架构不会删除。")
            .setPositiveButton("清空") { _, _ -> lifecycleScope.launch {
                db.dao().clearTeamSchedules(t.id)
                Toast.makeText(this@ScheduleSettingsActivity, "所有排班已清空", Toast.LENGTH_SHORT).show()
            }}.setNegativeButton("取消", null).show()
    }

    private fun key(date: LocalDate) = "%02d-%02d".format(date.monthValue, date.dayOfMonth)
    private fun formatDate(date: LocalDate) = "%d月%d日".format(date.monthValue, date.dayOfMonth)
}
