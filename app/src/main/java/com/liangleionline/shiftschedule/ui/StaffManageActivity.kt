package com.liangleionline.shiftschedule.ui

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
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
            setPadding(dp(this@StaffManageActivity, 14), dp(this@StaffManageActivity, 14), dp(this@StaffManageActivity, 14), dp(this@StaffManageActivity, 16))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(this@StaffManageActivity, 20), dp(this@StaffManageActivity, 18), dp(this@StaffManageActivity, 20), dp(this@StaffManageActivity, 18))
            background = gradientBg(intArrayOf(Palette.primary, Palette.primaryDeep, Color.rgb(124, 58, 237)), dpF(this@StaffManageActivity, 26f))
            elevation = dpF(this@StaffManageActivity, 6f)
        }
        header.addView(TextView(this).apply { text = "人员管理"; textSize = 22f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE) })
        header.addView(TextView(this).apply { text = "选择班和小组后维护人员"; textSize = 12.5f; setTextColor(Color.argb(220, 255, 255, 255)); setPadding(0, dp(this@StaffManageActivity, 6), 0, 0) })
        root.addView(header)

        val filterCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(this@StaffManageActivity, 14), dp(this@StaffManageActivity, 14), dp(this@StaffManageActivity, 14), dp(this@StaffManageActivity, 14))
            background = solid(Palette.card, dpF(this@StaffManageActivity, 22f), Palette.line)
            layoutParams = LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(this@StaffManageActivity, 14) }
            elevation = dpF(this@StaffManageActivity, 2f)
        }
        teamSpinner = styledSpinner()
        groupSpinner = styledSpinner()
        val addButton = pill(this, "＋ 新增人员", Palette.primary)
        val filters = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        filters.addView(teamSpinner, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(this@StaffManageActivity, 8) })
        filters.addView(groupSpinner, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(this@StaffManageActivity, 8) })
        filters.addView(addButton, LinearLayout.LayoutParams(-2, LinearLayout.LayoutParams.WRAP_CONTENT))
        filterCard.addView(filters)
        root.addView(filterCard)

        val scroll = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(Palette.bg) }
        container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(this@StaffManageActivity, 2), dp(this@StaffManageActivity, 12), dp(this@StaffManageActivity, 2), 0) }
        scroll.addView(container)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        shell.addView(root, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(shell)

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

    private fun styledSpinner() = Spinner(this).apply {
        background = solid(Palette.soft, dpF(this@StaffManageActivity, 13f), Palette.line)
        setPadding(dp(this@StaffManageActivity, 10), dp(this@StaffManageActivity, 8), dp(this@StaffManageActivity, 10), dp(this@StaffManageActivity, 8))
    }

    override fun onResume() { super.onResume(); if (::db.isInitialized) loadTeams(keepSelection = true) }

    private fun spinnerAdapter(items: List<String>) = ArrayAdapter(this, R.layout.item_spinner, items).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

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
        if (people.isEmpty()) container.addView(message("当前小组还没有人员，点击上方新增"))
        people.forEach { staff -> container.addView(staffCard(staff)) }
    }

    private fun message(text: String) = TextView(this).apply {
        this.text = text; gravity = Gravity.CENTER
        setPadding(dp(this@StaffManageActivity, 16), dp(this@StaffManageActivity, 44), dp(this@StaffManageActivity, 16), dp(this@StaffManageActivity, 30))
        setTextColor(Palette.faint); textSize = 13.5f
    }

    private fun staffCard(staff: Staff): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(this@StaffManageActivity, 16), dp(this@StaffManageActivity, 14), dp(this@StaffManageActivity, 14), dp(this@StaffManageActivity, 14))
            background = solid(Palette.card, dpF(this@StaffManageActivity, 16f), Palette.line)
            layoutParams = LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(this@StaffManageActivity, 10) }
            elevation = dpF(this@StaffManageActivity, 1.5f)
        }
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        info.addView(TextView(this).apply { text = staff.name; textSize = 17.5f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Palette.ink) })
        info.addView(TextView(this).apply {
            text = if (staff.role == "班长") "班长" else "组员"
            textSize = 12f
            setTextColor(if (staff.role == "班长") Palette.primary else Palette.sub)
            setPadding(0, dp(this@StaffManageActivity, 4), 0, 0)
        })
        val edit = pill(this, "编辑", Palette.primarySoft, Palette.primary, textSize = 13f)
        val delete = pill(this, "删除", Palette.orangeSoft, Palette.orange, textSize = 13f)
        edit.setOnClickListener { showEditDialog(staff) }; delete.setOnClickListener { confirmDelete(staff) }
        card.addView(info, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(edit, LinearLayout.LayoutParams(-2, LinearLayout.LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(this@StaffManageActivity, 8) })
        card.addView(delete)
        return card
    }

    private fun showEditDialog(staff: Staff?) = lifecycleScope.launch {
        val allTeams = db.dao().allTeams()
        var chosenTeam = if (staff != null) allTeams.firstOrNull { team -> db.dao().groupsOnce(team.id).any { it.id == staff.groupId } } else selectedTeam
        var dialogGroups = chosenTeam?.let { db.dao().groupsOnce(it.id) } ?: emptyList()
        var chosenGroup = if (staff != null) dialogGroups.firstOrNull { it.id == staff.groupId } else selectedGroup ?: dialogGroups.firstOrNull()
        val panel = LinearLayout(this@StaffManageActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(this@StaffManageActivity, 28), dp(this@StaffManageActivity, 12), dp(this@StaffManageActivity, 28), 0) }
        val nameInput = EditText(this@StaffManageActivity).apply { setText(staff?.name ?: ""); hint = "请输入姓名" }
        val teamSelect = styledSpinner(); val groupSelect = styledSpinner()
        var suppressDialogCallbacks = true
        val radioGroup = RadioGroup(this@StaffManageActivity).apply { orientation = RadioGroup.HORIZONTAL; setPadding(0, dp(this@StaffManageActivity, 14), 0, 0) }
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

    private fun label(text: String, top: Int = 0) = TextView(this).apply {
        this.text = text; setTextColor(Palette.sub)
        setPadding(0, if (top == 0) 0 else dp(this@StaffManageActivity, top), 0, 0)
        textSize = 13f
    }

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
