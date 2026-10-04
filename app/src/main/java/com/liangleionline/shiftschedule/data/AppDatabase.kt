package com.liangleionline.shiftschedule.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Query("SELECT * FROM Team") fun teams(): Flow<List<Team>>
    @Query("SELECT * FROM `Group` WHERE teamId=:teamId") fun groups(teamId: Long): Flow<List<Group>>
    @Query("SELECT * FROM Staff WHERE groupId IN (SELECT id FROM `Group` WHERE teamId=:teamId)") fun staffByTeam(teamId: Long): Flow<List<Staff>>
    @Query("SELECT * FROM Staff") suspend fun allStaff(): List<Staff>
    @Query("SELECT * FROM NameAlias") suspend fun aliases(): List<NameAlias>
    @Query("SELECT * FROM ScheduleRecord WHERE teamId=:teamId AND dateKey=:dateKey") suspend fun schedule(teamId: Long, dateKey: String): ScheduleRecord?
    @Insert suspend fun insertTeam(team: Team): Long
    @Insert suspend fun insertGroup(group: Group): Long
    @Insert suspend fun insertStaff(staff: Staff): Long
    @Update suspend fun updateStaff(staff: Staff)
    @Delete suspend fun deleteStaff(staff: Staff)
    @Query("SELECT COUNT(*) FROM Staff WHERE groupId=:groupId AND role='班长'") suspend fun leaderCount(groupId: Long): Int
    @Query("SELECT * FROM Staff WHERE groupId=:groupId ORDER BY CASE WHEN role='班长' THEN 0 ELSE 1 END, id") suspend fun staffByGroup(groupId: Long): List<Staff>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveAlias(alias: NameAlias)
    @Upsert suspend fun upsertSchedule(record: ScheduleRecord)
    @Query("DELETE FROM ScheduleRecord") suspend fun clearSchedules()
    @Query("DELETE FROM NameAlias WHERE rawName=:rawName") suspend fun deleteAlias(rawName: String)
}

@Database(entities = [Team::class, Group::class, Staff::class, NameAlias::class, ScheduleRecord::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() { abstract fun dao(): AppDao }
