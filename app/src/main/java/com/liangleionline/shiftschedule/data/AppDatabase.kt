package com.liangleionline.shiftschedule.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Query("SELECT * FROM Team") fun teams(): Flow<List<Team>>
    @Query("SELECT * FROM Team") suspend fun allTeams(): List<Team>
    @Query("SELECT * FROM `Group`") fun allGroups(): Flow<List<Group>>
    @Query("SELECT * FROM `Group` WHERE teamId=:teamId") fun groups(teamId: Long): Flow<List<Group>>
    @Query("SELECT * FROM `Group` WHERE teamId=:teamId") suspend fun groupsOnce(teamId: Long): List<Group>
    @Query("SELECT * FROM Staff WHERE groupId IN (SELECT id FROM `Group` WHERE teamId=:teamId)") fun staffByTeam(teamId: Long): Flow<List<Staff>>
    @Query("SELECT * FROM Staff") suspend fun allStaff(): List<Staff>
    @Query("SELECT * FROM NameAlias") suspend fun aliases(): List<NameAlias>
    @Query("SELECT * FROM ScheduleRecord WHERE teamId=:teamId AND dateKey=:dateKey") suspend fun schedule(teamId: Long, dateKey: String): ScheduleRecord?
    @Query("SELECT * FROM Team WHERE name=:name LIMIT 1") suspend fun teamByName(name: String): Team?
    @Query("SELECT * FROM `Group` WHERE teamId=:teamId AND name=:name LIMIT 1") suspend fun groupByName(teamId: Long, name: String): Group?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertTeamIgnore(team: Team): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertGroupIgnore(group: Group): Long
    @Insert suspend fun insertTeam(team: Team): Long
    @Update suspend fun updateTeam(team: Team)
    @Delete suspend fun deleteTeam(team: Team)
    @Insert suspend fun insertGroup(group: Group): Long
    @Transaction suspend fun insertUniqueTeam(name: String): Long {
        teamByName(name)?.let { return it.id }
        val inserted = insertTeamIgnore(Team(name = name))
        return if (inserted >= 0) inserted else teamByName(name)?.id ?: insertTeam(Team(name = name))
    }
    @Transaction suspend fun insertUniqueGroup(teamId: Long, name: String): Long {
        groupByName(teamId, name)?.let { return it.id }
        val inserted = insertGroupIgnore(Group(teamId = teamId, name = name))
        return if (inserted >= 0) inserted else groupByName(teamId, name)?.id ?: insertGroup(Group(teamId = teamId, name = name))
    }
    @Update suspend fun updateGroup(group: Group)
    @Delete suspend fun deleteGroup(group: Group)
    @Insert suspend fun insertStaff(staff: Staff): Long
    @Update suspend fun updateStaff(staff: Staff)
    @Delete suspend fun deleteStaff(staff: Staff)
    @Query("SELECT COUNT(*) FROM Staff WHERE groupId=:groupId AND role='班长'") suspend fun leaderCount(groupId: Long): Int
    @Query("SELECT * FROM Staff WHERE groupId=:groupId") suspend fun staffByGroupOnce(groupId: Long): List<Staff>
    @Query("SELECT * FROM Staff WHERE groupId=:groupId ORDER BY CASE WHEN role='班长' THEN 0 ELSE 1 END, id") suspend fun staffByGroup(groupId: Long): List<Staff>
    @Query("UPDATE Staff SET groupId=:targetGroupId WHERE id=:staffId") suspend fun moveStaff(staffId: Long, targetGroupId: Long)
    @Query("SELECT * FROM ScheduleRecord") suspend fun allSchedules(): List<ScheduleRecord>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveAlias(alias: NameAlias)
    @Upsert suspend fun upsertSchedule(record: ScheduleRecord)
    @Query("DELETE FROM ScheduleRecord") suspend fun clearSchedules()
    @Query("DELETE FROM NameAlias WHERE rawName=:rawName") suspend fun deleteAlias(rawName: String)

    @Transaction
    suspend fun cleanupDuplicates() {
        allTeams().forEach { team ->
            groupsOnce(team.id).groupBy { it.name }.filterValues { it.size > 1 }.forEach { (_, duplicates) ->
                val keep = duplicates.minByOrNull { it.id } ?: return@forEach
                duplicates.filter { it.id != keep.id }.forEach { duplicate ->
                    staffByGroupOnce(duplicate.id).forEach { staff ->
                        val existing = staffByGroupOnce(keep.id).firstOrNull { it.name == staff.name && it.role == staff.role }
                            ?: staffByGroupOnce(keep.id).firstOrNull { it.name == staff.name }
                        if (existing == null) moveStaff(staff.id, keep.id) else deleteStaff(staff)
                    }
                    deleteGroup(duplicate)
                }
            }
        }
        allSchedules().forEach { record ->
            val allIds = allStaff().map { it.id }.toSet()
            val validIds = record.staffIds.distinct().filter { it in allIds }
            if (validIds != record.staffIds) upsertSchedule(record.copy(staffIds = validIds))
        }
    }
}

@Database(entities = [Team::class, Group::class, Staff::class, NameAlias::class, ScheduleRecord::class], version = 3, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() { abstract fun dao(): AppDao }
