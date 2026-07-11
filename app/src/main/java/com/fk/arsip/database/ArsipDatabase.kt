package com.fk.arsip.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ArsipEntity::class], version = 1, exportSchema = false)
abstract class ArsipDatabase : RoomDatabase() {

    abstract fun arsipDao(): ArsipDao

    companion object {
        @Volatile
        private var MESIN_INDUK: ArsipDatabase? = null

        fun operasikanMesin(konteks: Context): ArsipDatabase {
            return MESIN_INDUK ?: synchronized(this) {
                val rakitanMesin = Room.databaseBuilder(
                    konteks.applicationContext,
                    ArsipDatabase::class.java,
                    "arsip_zuhri_formalism.db"
                )
                // Katup Darurat: Hancurkan dan bangun ulang rak jika dimensi struktur berubah di masa depan
                .fallbackToDestructiveMigration() 
                .build()
                
                MESIN_INDUK = rakitanMesin
                rakitanMesin
            }
        }
    }
}
