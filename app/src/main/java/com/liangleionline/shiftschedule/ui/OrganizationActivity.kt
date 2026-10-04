package com.liangleionline.shiftschedule.ui

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.liangleionline.shiftschedule.R
import com.liangleionline.shiftschedule.data.*
import kotlinx.coroutines.launch

class OrganizationActivity : AppCompatActivity() {
    private lateinit var db: AppDatabase
    private lateinit var teamSpinner: Spinner
    private lateinit var groupList: LinearLayout
    private var teams = listOf<Team>()
    private var groups = listOf<Group>()
    private var selectedTeam: Team? = null
    private var suppressTeamCallback = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(15,23,42)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) window.decorView.systemUiVisibility = window.decorView.systemUiVisibility and android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        db = Room.databaseBuilder(this, AppDatabase::class.java, "shift-schedule.db").fallbackToDestructiveMigration().build()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(246,248,252)); setPadding(22,statusBarInset() + 28,22,22) }
        root.addView(TextView(this).apply { text = "组织架构管理"; textSize = 26f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.rgb(20,34,58)) })
        root.addView(TextView(this).apply { text = "维护班、小组；删除前会提示联动影响"; textSize = 14f; setTextColor(Color.rgb(99,115,139)); setPadding(0,8,0,18) })
        val teamRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        teamSpinner = Spinner(this)
        val renameTeam = Button(this).apply { text = "改班名" }
        val addTeam = Button(this).apply { text = "新增班" }
        val deleteTeam = Button(this).apply { text = "删班" }
        teamRow.addView(teamSpinner, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)); teamRow.addView(renameTeam); teamRow.addView(addTeam); teamRow.addView(deleteTeam)
        val groupActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0,20,0,8) }
        val addGroup = Button(this).apply { text = "新增小组" }
        groupActions.addView(TextView(this).apply { text = "小组列表"; textSize = 20f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.rgb(30,41,59)) }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        groupActions.addView(addGroup)
        val scroll = ScrollView(this)
        groupList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(groupList)
        root.addView(teamRow); root.addView(groupActions); root.addView(scroll, LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root)

        teamSpinner.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                if (suppressTeamCallback) return
                selectedTeam = teams.getOrNull(position)
                loadGroups()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        addTeam.setOnClickListener { button ->
            button.isEnabled = false
            textInputDialog("新增班", "请输入班名", onDismiss = { button.isEnabled = true }) { name ->
                lifecycleScope.launch { db.dao().insertUniqueTeam(name); reloadTeams(addNewest = true); button.isEnabled = true }
            }
        }
        renameTeam.setOnClickListener { val t = selectedTeam ?: return@setOnClickListener; textInputDialog("修改班名", "请输入新的班名", t.name) { name -> lifecycleScope.launch { db.dao().updateTeam(t.copy(name = name)); reloadTeams(addNewest = false, keepSelectedId = t.id) } } }
        deleteTeam.setOnClickListener { val t = selectedTeam ?: return@setOnClickListener; confirm("删除班", "删除「${t.name}」会同时删除其小组和人员，确定删除？") { lifecycleScope.launch { db.dao().deleteTeam(t); reloadTeams(addNewest = false) } } }
        addGroup.setOnClickListener { button ->
            button.isEnabled = false
            val t = selectedTeam ?: run { button.isEnabled = true; return@setOnClickListener }
            textInputDialog("新增小组", "请输入小组名称", onDismiss = { button.isEnabled = true }) { name ->
                lifecycleScope.launch { db.dao().insertUniqueGroup(t.id, name); loadGroups(); button.isEnabled = true }
            }
        }
        reloadTeams(addNewest = false)
    }

    private fun statusBarInset(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

    private fun reloadTeams(keepSelectedId: Long? = null, addNewest: Boolean): kotlinx.coroutines.Job = lifecycleScope.launch {
        db.dao().cleanupDuplicates()
        teams = db.dao().allTeams()
        val adapter = ArrayAdapter(this@OrganizationActivity, R.layout.item_spinner, teams.map { it.name })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        suppressTeamCallback = true
        teamSpinner.adapter = adapter
        selectedTeam = when {
            keepSelectedId != null -> teams.firstOrNull { it.id == keepSelectedId }
            addNewest -> teams.maxByOrNull { it.id }
            else -> selectedTeam?.let { current -> teams.firstOrNull { it.id == current.id } } ?: teams.firstOrNull()
        }
        val index = teams.indexOfFirst { it.id == selectedTeam?.id }.coerceAtLeast(0)
        if (teams.isNotEmpty()) teamSpinner.setSelection(index)
        suppressTeamCallback = false
        loadGroups()
    }

    private fun loadGroups(): kotlinx.coroutines.Job = lifecycleScope.launch {
        groupList.removeAllViews()
        val team = selectedTeam
        groups = if (team == null) emptyList() else db.dao().groupsOnce(team.id)
        if (groups.isEmpty()) groupList.addView(TextView(this@OrganizationActivity).apply { text = "暂无小组，请点击右上角新增"; setPadding(12,28,12,28); setTextColor(Color.GRAY) })
        groups.forEach { group ->
            val count = db.dao().staffByGroup(group.id).size
            val card = LinearLayout(this@OrganizationActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(20,16,16,16)
                background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 24f; setColor(Color.WHITE); setStroke(1, Color.rgb(226,232,240)) } }
            val info = LinearLayout(this@OrganizationActivity).apply { orientation = LinearLayout.VERTICAL }
            info.addView(TextView(this@OrganizationActivity).apply { text = group.name; textSize = 18f; typeface = Typeface.DEFAULT_BOLD })
            info.addView(TextView(this@OrganizationActivity).apply { text = "$count 人"; textSize = 13f; setTextColor(Color.GRAY); setPadding(0,4,0,0) })
            val rename = Button(this@OrganizationActivity).apply { text = "改名" }
            val delete = Button(this@OrganizationActivity).apply { text = "删除" }
            rename.setOnClickListener { textInputDialog("修改小组名", "请输入新的小组名称", group.name) { name -> lifecycleScope.launch { db.dao().updateGroup(group.copy(name = name)); loadGroups() } } }
            delete.setOnClickListener { confirm("删除小组", "删除「${group.name}」会同时删除组内人员，确定删除？") { lifecycleScope.launch { db.dao().deleteGroup(group); loadGroups() } } }
            card.addView(info, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)); card.addView(rename); card.addView(delete)
            card.layoutParams = LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 14 }
            groupList.addView(card)
        }
    }

    private fun textInputDialog(title: String, hint: String, old: String = "", onDismiss: (() -> Unit)? = null, action: (String) -> Unit) {
        val input = EditText(this).apply { setText(old); this.hint = hint }
        AlertDialog.Builder(this).setTitle(title).setView(input).setPositiveButton("保存") { _, _ ->
            val value = input.text.toString().trim()
            if (value.isNotBlank()) action(value) else { Toast.makeText(this, "名称不能为空", Toast.LENGTH_SHORT).show(); onDismiss?.invoke() }
        }.setNegativeButton("取消") { _, _ -> onDismiss?.invoke() }.setOnCancelListener { onDismiss?.invoke() }.show()
    }
    private fun confirm(title: String, msg: String, action: () -> Unit) = AlertDialog.Builder(this).setTitle(title).setMessage(msg).setPositiveButton("确定") { _, _ -> action() }.setNegativeButton("取消", null).show()
}
