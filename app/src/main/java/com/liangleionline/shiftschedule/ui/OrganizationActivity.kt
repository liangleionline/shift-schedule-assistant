package com.liangleionline.shiftschedule.ui

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.liangleionline.shiftschedule.R
import com.liangleionline.shiftschedule.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class OrganizationActivity : AppCompatActivity() {
    private lateinit var db: AppDatabase
    private lateinit var teamSpinner: Spinner
    private lateinit var groupList: LinearLayout
    private var teams = listOf<Team>()
    private var selectedTeam: Team? = null
    private var suppressTeamCallback = true
    private var renderGeneration = 0L
    private var renderJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Palette.primaryDeep
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
        db = Room.databaseBuilder(this, AppDatabase::class.java, "shift-schedule.db").fallbackToDestructiveMigration().build()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Palette.bg)
            setPadding(dp(this@OrganizationActivity, 14), statusBarInset() + dp(this@OrganizationActivity, 14), dp(this@OrganizationActivity, 14), dp(this@OrganizationActivity, 16))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(this@OrganizationActivity, 20), dp(this@OrganizationActivity, 18), dp(this@OrganizationActivity, 20), dp(this@OrganizationActivity, 18))
            background = gradientBg(intArrayOf(Palette.primary, Palette.primaryDeep, Color.rgb(124, 58, 237)), dpF(this@OrganizationActivity, 26f))
            elevation = dpF(this@OrganizationActivity, 6f)
        }
        header.addView(TextView(this).apply { text = "组织架构"; textSize = 22f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE) })
        header.addView(TextView(this).apply { text = "维护班、小组，删除前会提示联动影响"; textSize = 12.5f; setTextColor(Color.argb(220, 255, 255, 255)); setPadding(0, dp(this@OrganizationActivity, 6), 0, 0) })
        val teamRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(this@OrganizationActivity, 14), 0, 0) }
        teamSpinner = Spinner(this).apply {
            background = solid(Color.WHITE, dpF(this@OrganizationActivity, 14f))
            setPadding(dp(this@OrganizationActivity, 14), dp(this@OrganizationActivity, 9), dp(this@OrganizationActivity, 14), dp(this@OrganizationActivity, 9))
        }
        val editTeamInfo = pill(this, "编辑班信息", Color.WHITE, Palette.primary)
        teamRow.addView(teamSpinner, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2.2f).apply { rightMargin = dp(this@OrganizationActivity, 10) })
        teamRow.addView(editTeamInfo, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.3f))
        header.addView(teamRow)
        root.addView(header)

        val groupCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(this@OrganizationActivity, 14), dp(this@OrganizationActivity, 14), dp(this@OrganizationActivity, 14), dp(this@OrganizationActivity, 14))
            background = solid(Palette.card, dpF(this@OrganizationActivity, 22f), Palette.line)
            layoutParams = LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(this@OrganizationActivity, 14) }
            elevation = dpF(this@OrganizationActivity, 2f)
        }
        val groupActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val addGroup = pill(this, "＋ 新增小组", Palette.primary)
        groupActions.addView(TextView(this).apply { text = "小组列表"; textSize = 18f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Palette.ink) }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        groupActions.addView(addGroup)
        groupCard.addView(groupActions)
        groupList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(this@OrganizationActivity, 10), 0, 0)
        }
        groupCard.addView(groupList)

        val scroll = ScrollView(this).apply { isFillViewport = true }
        scroll.addView(groupCard)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        teamSpinner.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val newTeam = teams.getOrNull(position) ?: return
                if (suppressTeamCallback || newTeam.id == selectedTeam?.id) return
                selectedTeam = newTeam
                render()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        editTeamInfo.setOnClickListener { showTeamEditSheet() }
        addGroup.setOnClickListener { button ->
            button.isEnabled = false
            val team = selectedTeam ?: run { button.isEnabled = true; return@setOnClickListener }
            textInputDialog("新增小组", "请输入小组名称", onDismiss = { button.isEnabled = true }) { name ->
                lifecycleScope.launch {
                    db.dao().insertUniqueGroup(team.id, name)
                    button.isEnabled = true
                    render(keepTeamId = team.id)
                }
            }
        }
        render(selectFirstTeam = true)
    }

    override fun onDestroy() {
        renderJob?.cancel()
        super.onDestroy()
    }

    private fun showTeamEditSheet() {
        val options = arrayOf("修改班名", "新增班", "删除班")
        AlertDialog.Builder(this).setTitle("编辑班信息").setItems(options) { _, which ->
            when (which) {
                0 -> {
                    val t = selectedTeam ?: return@setItems
                    textInputDialog("修改班名", "请输入新的班名", t.name) { name ->
                        lifecycleScope.launch { db.dao().updateTeam(t.copy(name = name)); render(keepTeamId = t.id) }
                    }
                }
                1 -> textInputDialog("新增班", "请输入班名") { name ->
                    lifecycleScope.launch { db.dao().insertUniqueTeam(name); render(selectNewestTeam = true) }
                }
                2 -> {
                    val t = selectedTeam ?: return@setItems
                    confirm("删除班", "删除「${t.name}」会同时删除其小组和人员，确定删除？") {
                        lifecycleScope.launch { db.dao().deleteTeam(t); render(selectFirstTeam = true) }
                    }
                }
            }
        }.setNegativeButton("取消", null).show()
    }

    private fun statusBarInset(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

    private fun render(
        keepTeamId: Long? = selectedTeam?.id,
        selectNewestTeam: Boolean = false,
        selectFirstTeam: Boolean = false
    ) {
        val generation = ++renderGeneration
        renderJob?.cancel()
        renderJob = lifecycleScope.launch {
            val latestTeams = db.dao().allTeams().distinctBy { it.id }.sortedBy { it.id }
            teams = latestTeams
            val teamToSelect = when {
                selectFirstTeam -> latestTeams.firstOrNull()
                selectNewestTeam -> latestTeams.maxByOrNull { it.id }
                keepTeamId != null -> latestTeams.firstOrNull { it.id == keepTeamId }
                else -> selectedTeam?.let { current -> latestTeams.firstOrNull { it.id == current.id } } ?: latestTeams.firstOrNull()
            }
            selectedTeam = teamToSelect

            val teamNames = latestTeams.map { it.name }
            val currentAdapter = teamSpinner.adapter as? ArrayAdapter<*>
            if (currentAdapter == null || currentAdapter.count != teamNames.size || teamNames.indices.any { currentAdapter.getItem(it) != teamNames[it] }) {
                val adapter = ArrayAdapter(this@OrganizationActivity, R.layout.item_spinner, teamNames)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                suppressTeamCallback = true
                teamSpinner.adapter = adapter
                val selectedIndex = latestTeams.indexOfFirst { it.id == teamToSelect?.id }
                if (selectedIndex >= 0) teamSpinner.setSelection(selectedIndex)
                suppressTeamCallback = false
            }
            if (generation != renderGeneration) return@launch

            groupList.removeAllViews()
            val team = teamToSelect
            if (team == null) {
                addEmptyGroupMessage("暂无班组")
                return@launch
            }

            // 数据库快照 + UI 前再次按 id 去重；正常情况下这里不会有重复项。
            val groupsSnapshot = db.dao().groupsOnce(team.id).distinctBy { it.id }.sortedBy { it.id }
            if (groupsSnapshot.isEmpty()) {
                addEmptyGroupMessage("暂无小组，点击上方新增")
                return@launch
            }

            groupsSnapshot.forEach { group ->
                val count = db.dao().staffByGroup(group.id).size
                addGroupCard(group, count)
            }
        }
    }

    private fun addEmptyGroupMessage(text: String) {
        groupList.addView(TextView(this).apply {
            this.text = text
            gravity = Gravity.CENTER
            setPadding(dp(this@OrganizationActivity, 12), dp(this@OrganizationActivity, 30), dp(this@OrganizationActivity, 12), dp(this@OrganizationActivity, 20))
            setTextColor(Palette.faint)
            textSize = 13.5f
        })
    }

    private fun addGroupCard(group: Group, count: Int) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(this@OrganizationActivity, 16), dp(this@OrganizationActivity, 14), dp(this@OrganizationActivity, 14), dp(this@OrganizationActivity, 14))
            background = solid(Palette.soft, dpF(this@OrganizationActivity, 16f))
            layoutParams = LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(this@OrganizationActivity, 10) }
        }
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        info.addView(TextView(this).apply { text = group.name; textSize = 17f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Palette.ink) })
        info.addView(TextView(this).apply { text = "$count 人"; textSize = 12.5f; setTextColor(Palette.sub); setPadding(0, dp(this@OrganizationActivity, 4), 0, 0) })
        val rename = pill(this, "改名", Palette.card, Palette.ink, Palette.line, textSize = 13f)
        val delete = pill(this, "删除", Palette.orangeSoft, Palette.orange, textSize = 13f)
        rename.setOnClickListener {
            textInputDialog("修改小组名", "请输入新的小组名称", group.name) { name ->
                lifecycleScope.launch { db.dao().updateGroup(group.copy(name = name)); render(keepTeamId = group.teamId) }
            }
        }
        delete.setOnClickListener {
            confirm("删除小组", "删除「${group.name}」会同时删除组内人员，确定删除？") {
                lifecycleScope.launch { db.dao().deleteGroup(group); render(keepTeamId = group.teamId) }
            }
        }
        card.addView(info, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(rename, LinearLayout.LayoutParams(-2, LinearLayout.LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(this@OrganizationActivity, 8) })
        card.addView(delete)
        groupList.addView(card)
    }

    private fun textInputDialog(title: String, hint: String, old: String = "", onDismiss: (() -> Unit)? = null, action: (String) -> Unit) {
        val input = EditText(this).apply { setText(old); this.hint = hint }
        AlertDialog.Builder(this).setTitle(title).setView(input).setPositiveButton("保存") { _, _ ->
            val value = input.text.toString().trim()
            if (value.isNotBlank()) action(value) else {
                Toast.makeText(this, "名称不能为空", Toast.LENGTH_SHORT).show()
                onDismiss?.invoke()
            }
        }.setNegativeButton("取消") { _, _ -> onDismiss?.invoke() }.setOnCancelListener { onDismiss?.invoke() }.show()
    }

    private fun confirm(title: String, msg: String, action: () -> Unit) =
        AlertDialog.Builder(this).setTitle(title).setMessage(msg).setPositiveButton("确定") { _, _ -> action() }.setNegativeButton("取消", null).show()
}
