package com.liangleionline.shiftschedule.data

import androidx.room.*

@Entity data class Team(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String)
@Entity(foreignKeys = [ForeignKey(Team::class, parentColumns = ["id"], childColumns = ["teamId"], onDelete = ForeignKey.CASCADE)], indices = [Index("teamId")])
data class Group(@PrimaryKey(autoGenerate = true) val id: Long = 0, val teamId: Long, val name: String)
@Entity(foreignKeys = [ForeignKey(Group::class, parentColumns = ["id"], childColumns = ["groupId"], onDelete = ForeignKey.CASCADE)], indices = [Index("groupId")])
data class Staff(@PrimaryKey(autoGenerate = true) val id: Long = 0, val groupId: Long, val name: String, val role: String = "组员")
@Entity data class NameAlias(@PrimaryKey val rawName: String, val staffId: Long)
@Entity(indices = [Index(value=["teamId","dateKey"], unique=true)])
data class ScheduleRecord(@PrimaryKey(autoGenerate = true) val id: Long = 0, val teamId: Long, val dateKey: String, val mode: String, val staffIds: List<Long>)
data class GroupStaff(val group: Group, val staff: List<Staff>)

class Converters {
    @TypeConverter fun idsToString(ids: List<Long>) = ids.joinToString(",")
    @TypeConverter fun stringToIds(value: String) = if (value.isBlank()) emptyList() else value.split(",").mapNotNull { it.toLongOrNull() }
}
