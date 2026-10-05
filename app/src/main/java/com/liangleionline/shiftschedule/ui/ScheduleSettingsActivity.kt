package com.liangleionline.shiftschedule.ui

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Context
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
import java.time.LocalDate

class ScheduleSettingsActivity : AppCompatActivity() {
    private lateinit var db: AppDatabase
    private lateinit var teamSpinner: Spinner
    private lateinit var undoButton: TextView
    private lateinit var nameButton: TextView
    private var teams = listOf<Team>()
    private var currentTeamId: Long? = null
    private var suppress = true

    private fun prefs() = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureDarkStatusBar()
        db = Room.databaseBuilder(this, AppDatabase::class.java, "shift-schedule.db").fallbackToDestructiveMigration().build()

        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(0, statusBarHeightPx(), 0, 0)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Palette.bg)
            setPadding(dp(this@ScheduleSettingsActivity, 14), dp(this@ScheduleSettingsActivity, 14), dp(this@ScheduleSettingsActivity, 14), dp(this@ScheduleSettingsActivity, 16))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(this@ScheduleSettingsActivity, 20), dp(this@ScheduleSettingsActivity, 18), dp(this@ScheduleSettingsActivity, 20), dp(this@ScheduleSettingsActivity, 18))
            background = gradientBg(intArrayOf(Palette.primary, Palette.primaryDeep, Color.rgb(124, 58, 237)), dpF(this@ScheduleSettingsActivity, 26f))
            elevation = dpF(this@ScheduleSettingsActivity, 6f)
        }
        header.addView(TextView(this).apply { text = "排班设置"; textSize = 22f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE) })
        header.addView(TextView(this).apply { text = "清空、按日期范围删除或撤销导入"; textSize = 12.5f; setTextColor(Color.argb(220, 255, 255, 255)); setPadding(0, dp(this@ScheduleSettingsActivity, 6), 0, 0) })
        val spinnerRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(this@ScheduleSettingsActivity, 14), 0, 0) }
        spinnerRow.addView(TextView(this).apply { text = "当前班"; setTextColor(Color.argb(225, 255, 255, 255)); textSize = 13f; setPadding(0, 0, dp(this@ScheduleSettingsActivity, 10), 0) })
        teamSpinner = Spinner(this).apply {
            background = solid(Color.WHITE, dpF(this@ScheduleSettingsActivity, 14f))
            setPadding(dp(this@ScheduleSettingsActivity, 14), dp(this@ScheduleSettingsActivity, 9), dp(this@ScheduleSettingsActivity, 14), dp(this@ScheduleSettingsActivity, 9))
        }
        spinnerRow.addView(teamSpinner, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(spinnerRow)
        root.addView(header)

        val nameCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(this@ScheduleSettingsActivity, 16), dp(this@ScheduleSettingsActivity, 16), dp(this@ScheduleSettingsActivity, 16), dp(this@ScheduleSettingsActivity, 16))
            background = solid(Palette.card, dpF(this@ScheduleSettingsActivity, 22f), Palette.line)
            layoutParams = LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(this@ScheduleSettingsActivity, 14) }
            elevation = dpF(this@ScheduleSettingsActivity, 2f)
        }
        nameCard.addView(TextView(this).apply { text = "我的姓名"; textSize = 15f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Palette.ink) })
        nameButton = pill(this, "点击设置", Palette.primarySoft, Palette.primary)
        nameCard.addView(nameButton, LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(this@ScheduleSettingsActivity, 12) })
        root.addView(nameCard)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(this@ScheduleSettingsActivity, 16), dp(this@ScheduleSettingsActivity, 16), dp(this@ScheduleSettingsActivity, 16), dp(this@ScheduleSettingsActivity, 18))
            background = solid(Palette.card, dpF(this@ScheduleSettingsActivity, 22f), Palette.line)
            layoutParams = LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(this@ScheduleSettingsActivity, 14) }
            elevation = dpF(this@ScheduleSettingsActivity, 2f)
        }
        undoButton = pill(this, "撤销上一次导入", Palette.primary)
        val clearRangeButton = pill(this, "清空指定时间段排班", Palette.soft, Palette.ink, Palette.line)
        val clearAllButton = pill(this, "清空所有排班数据", Palette.orangeSoft, Palette.orange)
        card.addView(undoButton)
        card.addView(clearRangeButton, LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(this@ScheduleSettingsActivity, 12) })
        card.addView(clearAllButton, LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(this@ScheduleSettingsActivity, 12) })
        root.addView(card)
        shell.addView(root, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(shell)

        teamSpinner.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                if (suppress) return
                currentTeamId = teams.getOrNull(position)?.id
                refreshUndoState()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        undoButton.setOnClickListener { if (undoButton.isEnabled) confirmUndo() }
        nameButton.setOnClickListener { showUserNameDialog() }
        clearRangeButton.setOnClickListener { pickDateRange() }
        clearAllButton.setOnClickListener { confirmClearAll() }
        refreshUserNameLabel()
        loadTeams()
    }

    override fun onResume() {
        super.onResume()
        if (::nameButton.isInitialized) refreshUserNameLabel()
    }

    private fun refreshUserNameLabel() {
        val name = prefs().getString("user_name", "").orEmpty().trim()
        nameButton.text = if (name.isEmpty()) "点击设置" else "已设置：$name（点击修改）"
    }

    private fun showUserNameDialog() {
        val current = prefs().getString("user_name", "").orEmpty()
        val input = android.widget.EditText(this).apply {
            setText(current)
            hint = "请输入你在排班表中的姓名"
            setSelection(current.length)
        }
        AlertDialog.Builder(this)
            .setTitle("我的姓名")
            .setMessage("设置后，首页今日/明日排班名单中与该姓名一致的人员会加粗显示。留空保存即取消。")
            .setView(input)
            .setPositiveButton("保存") { _, _ ->
                val value = input.text.toString().trim()
                prefs().edit().putString("user_name", value).apply()
                refreshUserNameLabel()
                Toast.makeText(this, if (value.isEmpty()) "已取消姓名标记" else "已保存", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
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
        undoButton.alpha = if (count > 0) 1f else 0.45f
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
