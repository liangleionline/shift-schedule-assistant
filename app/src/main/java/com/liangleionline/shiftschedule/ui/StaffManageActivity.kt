package com.liangleionline.shiftschedule.ui

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.liangleionline.shiftschedule.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class StaffManageActivity : AppCompatActivity() {
    private lateinit var db: AppDatabase
    private lateinit var container: LinearLayout
    private lateinit var groupSpinner: Spinner
    private lateinit var addButton: Button
    private var groups = listOf<Group>()
    private var currentGroup: Group? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = Room.databaseBuilder(this, AppDatabase::class.java, "shift-schedule.db").fallbackToDestructiveMigration().build()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(246,248,252)); setPadding(24,28,24,24) }
        val title = TextView(this).apply { text = "人员管理"; textSize = 26f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.rgb(24,35,55)) }
        val subtitle = TextView(this).apply { text = "按小组维护人员，班长将自动排在最前面"; textSize = 14f; setTextColor(Color.rgb(98,112,135)); setPadding(0,8,0,20) }
        groupSpinner = Spinner(this)
        addButton = Button(this).apply { text = "新增人员" }
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0,12,0,12) }
        actions.addView(groupSpinner, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        actions.addView(addButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        val scroll = ScrollView(this)
        container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(container)
        root.addView(title); root.addView(subtitle); root.addView(actions); root.addView(scroll, LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root)
        addButton.setOnClickListener { showEditDialog(null) }
        observeGroups()
    }

    private fun observeGroups() = lifecycleScope.launch {
        val teamId = intent.getLongExtra("teamId", -1L)
        db.dao().groups(teamId).collect { list ->
            groups = list
            currentGroup = currentGroup?.let { current -> list.firstOrNull { it.id == current.id } } ?: list.firstOrNull()
            groupSpinner.adapter = ArrayAdapter(this@StaffManageActivity, android.R.layout.simple_spinner_dropdown_item, list.map { it.name })
            renderStaff()
        }
        groupSpinner.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { currentGroup = groups.getOrNull(position); renderStaff() }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun renderStaff() = lifecycleScope.launch {
        container.removeAllViews()
        val group = currentGroup ?: return@launch
        val people = db.dao().staffByGroup(group.id)
        if (people.isEmpty()) container.addView(emptyView("当前小组还没有人员"))
        people.forEach { staff -> container.addView(staffCard(staff)) }
    }

    private fun emptyView(text: String) = TextView(this).apply {
        this.text = text; gravity = Gravity.CENTER; setPadding(24,48,24,48); setTextColor(Color.rgb(120,130,145))
    }

    private fun staffCard(staff: Staff): View {
        val card = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(24,18,20,18) }
        val bg = GradientDrawable().apply { cornerRadius = 28f; setColor(Color.WHITE); setStroke(1, Color.rgb(226,232,240)) }
        card.background = bg
        val params = LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 18 }
        card.layoutParams = params
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val name = TextView(this).apply { text = staff.name; textSize = 19f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.rgb(24,35,55)) }
        val role = TextView(this).apply {
            text = if (staff.role == "班长") "职位：班长" else "职位：组员"; textSize = 14f; setTextColor(if (staff.role == "班长") Color.rgb(21,101,192) else Color.rgb(100,116,139)); setPadding(0,6,0,0)
        }
        info.addView(name); info.addView(role)
        val edit = Button(this).apply { text = "编辑" }
        val delete = Button(this).apply { text = "删除" }
        edit.setOnClickListener { showEditDialog(staff) }
        delete.setOnClickListener { confirmDelete(staff) }
        card.addView(info, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(edit)
        card.addView(delete)
        return card
    }

    private fun showEditDialog(staff: Staff?) {
        val group = currentGroup ?: return
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28,12,28,0) }
        val nameInput = EditText(this).apply { hint = "请输入姓名"; setText(staff?.name ?: "") }
        val radioGroup = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL; setPadding(0,18,0,0) }
        val leader = RadioButton(this).apply { text = "班长"; id = View.generateViewId() }
        val member = RadioButton(this).apply { text = "组员"; id = View.generateViewId() }
        if (staff?.role == "班长") leader.isChecked = true else member.isChecked = true
        radioGroup.addView(leader); radioGroup.addView(member)
        panel.addView(TextView(this).apply { text = "姓名"; setTextColor(Color.rgb(71,85,105)) })
        panel.addView(nameInput)
        panel.addView(TextView(this).apply { text = "职位"; setTextColor(Color.rgb(71,85,105)); setPadding(0,16,0,0) })
        panel.addView(radioGroup)
        AlertDialog.Builder(this)
            .setTitle(if (staff == null) "新增人员" else "编辑人员")
            .setView(panel)
            .setPositiveButton("保存") { _, _ -> saveStaff(staff, group, nameInput.text.toString().trim(), if (leader.isChecked) "班长" else "组员") }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun saveStaff(old: Staff?, group: Group, name: String, role: String) = lifecycleScope.launch {
        if (name.isBlank()) { toast("姓名不能为空"); return@launch }
        if (old != null && old.role == "班长" && role != "班长" && db.dao().leaderCount(group.id) <= 1) {
            toast("每个小组至少保留一个班长"); return@launch
        }
        if (old == null) db.dao().insertStaff(Staff(groupId = group.id, name = name, role = role))
        else db.dao().updateStaff(old.copy(name = name, role = role))
        renderStaff()
    }

    private fun confirmDelete(staff: Staff) {
        AlertDialog.Builder(this).setTitle("删除人员").setMessage("确定删除「${staff.name}」吗？")
            .setPositiveButton("删除") { _, _ -> lifecycleScope.launch {
                if (staff.role == "班长" && db.dao().leaderCount(staff.groupId) <= 1) toast("每个小组至少保留一个班长")
                else { db.dao().deleteStaff(staff); renderStaff() }
            }}.setNegativeButton("取消", null).show()
    }

    private fun toast(text: String) = runOnUiThread { Toast.makeText(this, text, Toast.LENGTH_SHORT).show() }
}
