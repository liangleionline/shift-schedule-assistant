package com.liangleionline.shiftschedule.ui

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.liangleionline.shiftschedule.R
import com.liangleionline.shiftschedule.data.*
import kotlinx.coroutines.launch

class StaffManageActivity : AppCompatActivity() {
    private lateinit var db: AppDatabase
    private lateinit var container: LinearLayout
    private lateinit var teamSpinner: Spinner
    private lateinit var groupSpinner: Spinner
    private var teams = listOf<Team>()
    private var groups = listOf<Group>()
    private var selectedTeam: Team? = null
    private var selectedGroup: Group? = null
    private var suppress = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(15,23,42)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
        db = Room.databaseBuilder(this, AppDatabase::class.java, "shift-schedule.db").fallbackToDestructiveMigration().build()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(246,248,252)); setPadding(22,statusBarInset() + 28,22,22) }
        root.addView(TextView(this).apply { text = "人员管理"; textSize = 26f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.rgb(20,34,58)) })
        root.addView(TextView(this).apply { text = "选择班和小组后维护人员"; textSize = 14f; setTextColor(Color.rgb(99,115,139)); setPadding(0,8,0,18) })
        teamSpinner = Spinner(this); groupSpinner = Spinner(this)
        val addButton = Button(this).apply { text = "新增人员" }
        val filters = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0,8,0,8) }
        filters.addView(teamSpinner, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)); filters.addView(groupSpinner, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)); filters.addView(addButton)
        val scroll = ScrollView(this)
        container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0,12,0,0) }
        scroll.addView(container)
        root.addView(filters); root.addView(scroll, LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root)
        addButton.setOnClickListener { showEditDialog(null) }
        teamSpinner.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { if (!suppress) { selectedTeam = teams.getOrNull(position); selectedGroup = null; loadGroups() } }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        groupSpinner.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { if (!suppress) { selectedGroup = groups.getOrNull(position); loadStaff() } }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        loadTeams(keepSelection = true)
    }

    override fun onResume() { super.onResume(); if (::db.isInitialized) loadTeams(keepSelection = true) }

    private fun spinnerAdapter(items: List<String>) = ArrayAdapter(this, R.layout.item_spinner, items).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

    private fun statusBarInset(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

    private fun loadTeams(keepSelection: Boolean) = lifecycleScope.launch {
        teams = db.dao().allTeams()
        suppress = true
        teamSpinner.adapter = spinnerAdapter(teams.map { it.name })
        selectedTeam = if (keepSelection) selectedTeam?.let { current -> teams.firstOrNull { it.id == current.id } } ?: teams.firstOrNull() else teams.firstOrNull()
        teamSpinner.setSelection(teams.indexOfFirst { it.id == selectedTeam?.id }.coerceAtLeast(0))
        suppress = false
        loadGroups()
    }

    private fun loadGroups() = lifecycleScope.launch {
        val team = selectedTeam
        groups = if (team == null) emptyList() else db.dao().groupsOnce(team.id)
        suppress = true
        groupSpinner.adapter = spinnerAdapter(groups.map { it.name })
        selectedGroup = selectedGroup?.let { current -> groups.firstOrNull { it.id == current.id } } ?: groups.firstOrNull()
        groupSpinner.setSelection(groups.indexOfFirst { it.id == selectedGroup?.id }.coerceAtLeast(0))
        suppress = false
        loadStaff()
    }

    private fun loadStaff() = lifecycleScope.launch {
        container.removeAllViews()
        val group = selectedGroup
        if (group == null) { container.addView(message("请先在组织架构中创建班和小组")); return@launch }
        val people = db.dao().staffByGroup(group.id)
        if (people.isEmpty()) container.addView(message("当前小组还没有人员，点击右上角新增"))
        people.forEach { staff -> container.addView(staffCard(staff)) }
    }
    private fun message(text: String) = TextView(this).apply { this.text = text; gravity = Gravity.CENTER; setPadding(16,44,16,44); setTextColor(Color.rgb(100,116,139)) }
    private fun staffCard(staff: Staff): View {
        val card = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(22,18,18,18); background = GradientDrawable().apply { cornerRadius = 28f; setColor(Color.WHITE); setStroke(1, Color.rgb(226,232,240)) }; layoutParams = LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 16 } }
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        info.addView(TextView(this).apply { text = staff.name; textSize = 19f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.rgb(24,35,55)) })
        info.addView(TextView(this).apply { text = if (staff.role == "班长") "职位：班长" else "职位：组员"; textSize = 14f; setTextColor(if (staff.role == "班长") Color.rgb(21,101,192) else Color.rgb(100,116,139)); setPadding(0,6,0,0) })
        val edit = Button(this).apply { text = "编辑" }; val delete = Button(this).apply { text = "删除" }
        edit.setOnClickListener { showEditDialog(staff) }; delete.setOnClickListener { confirmDelete(staff) }
        card.addView(info, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)); card.addView(edit); card.addView(delete)
        return card
    }

    private fun showEditDialog(staff: Staff?) = lifecycleScope.launch {
        val allTeams = db.dao().allTeams()
        var chosenTeam = if (staff != null) allTeams.firstOrNull { team -> db.dao().groupsOnce(team.id).any { it.id == staff.groupId } } else selectedTeam
        var dialogGroups = chosenTeam?.let { db.dao().groupsOnce(it.id) } ?: emptyList()
        var chosenGroup = if (staff != null) dialogGroups.firstOrNull { it.id == staff.groupId } else selectedGroup ?: dialogGroups.firstOrNull()
        val panel = LinearLayout(this@StaffManageActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(28,12,28,0) }
        val nameInput = EditText(this@StaffManageActivity).apply { setText(staff?.name ?: ""); hint = "请输入姓名" }
        val teamSelect = Spinner(this@StaffManageActivity); val groupSelect = Spinner(this@StaffManageActivity)
        var suppressDialogCallbacks = true
        val radioGroup = RadioGroup(this@StaffManageActivity).apply { orientation = RadioGroup.HORIZONTAL; setPadding(0,16,0,0) }
        val leader = RadioButton(this@StaffManageActivity).apply { text = "班长"; id = View.generateViewId() }; val member = RadioButton(this@StaffManageActivity).apply { text = "组员"; id = View.generateViewId() }
        if (staff?.role == "班长") leader.isChecked = true else member.isChecked = true
        radioGroup.addView(leader); radioGroup.addView(member)
        fun refreshGroupSpinner() { groupSelect.adapter = spinnerAdapter(dialogGroups.map { it.name }); groupSelect.setSelection(dialogGroups.indexOfFirst { it.id == chosenGroup?.id }.coerceAtLeast(0)) }
        teamSelect.adapter = spinnerAdapter(allTeams.map { it.name }); chosenTeam?.let { teamSelect.setSelection(allTeams.indexOfFirst { t -> t.id == it.id }.coerceAtLeast(0)) }; refreshGroupSpinner(); suppressDialogCallbacks = false
        teamSelect.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (suppressDialogCallbacks) return
                lifecycleScope.launch {
                    chosenTeam = allTeams.getOrNull(pos)
                    dialogGroups = chosenTeam?.let { db.dao().groupsOnce(it.id) } ?: emptyList()
                    chosenGroup = dialogGroups.firstOrNull { it.id == selectedGroup?.id } ?: dialogGroups.firstOrNull()
                    refreshGroupSpinner()
                }
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        groupSelect.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { if (!suppressDialogCallbacks) chosenGroup = dialogGroups.getOrNull(pos) }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        panel.addView(label("姓名")); panel.addView(nameInput); panel.addView(label("所属班", 14)); panel.addView(teamSelect); panel.addView(label("所属小组", 14)); panel.addView(groupSelect); panel.addView(label("职位", 14)); panel.addView(radioGroup)
        AlertDialog.Builder(this@StaffManageActivity).setTitle(if (staff == null) "新增人员" else "编辑人员").setView(panel).setPositiveButton("保存") { _, _ -> saveStaff(staff, chosenGroup, nameInput.text.toString().trim(), if (leader.isChecked) "班长" else "组员") }.setNegativeButton("取消", null).show()
    }
    private fun label(text: String, top: Int = 0) = TextView(this).apply { this.text = text; setTextColor(Color.rgb(71,85,105)); setPadding(0,top,0,0) }
    private fun saveStaff(old: Staff?, group: Group?, name: String, role: String) = lifecycleScope.launch {
        if (name.isBlank()) { Toast.makeText(this@StaffManageActivity, "姓名不能为空", Toast.LENGTH_SHORT).show(); return@launch }
        if (group == null) { Toast.makeText(this@StaffManageActivity, "请先选择小组", Toast.LENGTH_SHORT).show(); return@launch }
        if (old != null && old.role == "班长" && (old.groupId != group.id || role != "班长") && db.dao().leaderCount(old.groupId) <= 1) { Toast.makeText(this@StaffManageActivity, "原小组至少保留一个班长", Toast.LENGTH_SHORT).show(); return@launch }
        if (old == null) db.dao().insertStaff(Staff(groupId = group.id, name = name, role = role)) else db.dao().updateStaff(old.copy(groupId = group.id, name = name, role = role))
        selectedTeam = teams.firstOrNull { it.id == group.teamId }
        selectedGroup = group
        suppress = true
        teamSpinner.setSelection(teams.indexOfFirst { it.id == selectedTeam?.id }.coerceAtLeast(0))
        val latestGroups = db.dao().groupsOnce(group.teamId)
        groups = latestGroups
        groupSpinner.adapter = spinnerAdapter(latestGroups.map { it.name })
        groupSpinner.setSelection(latestGroups.indexOfFirst { it.id == group.id }.coerceAtLeast(0))
        suppress = false
        loadStaff()
    }
    private fun confirmDelete(staff: Staff) = AlertDialog.Builder(this).setTitle("删除人员").setMessage("确定删除「${staff.name}」吗？").setPositiveButton("删除") { _, _ -> lifecycleScope.launch {
        if (staff.role == "班长" && db.dao().leaderCount(staff.groupId) <= 1) Toast.makeText(this@StaffManageActivity, "每个小组至少保留一个班长", Toast.LENGTH_SHORT).show() else { db.dao().deleteStaff(staff); loadStaff() }
    }}.setNegativeButton("取消", null).show()
}
