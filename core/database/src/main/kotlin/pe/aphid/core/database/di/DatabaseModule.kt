package pe.aphid.core.database.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import pe.aphid.core.database.AphidDatabase

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AphidDatabase =
        Room.databaseBuilder(context, AphidDatabase::class.java, AphidDatabase.NAME).build()

    @Provides fun cropDao(db: AphidDatabase) = db.cropDao()

    @Provides fun fertilizerDao(db: AphidDatabase) = db.fertilizerDao()

    @Provides fun waterDao(db: AphidDatabase) = db.waterDao()

    @Provides fun systemDao(db: AphidDatabase) = db.systemDao()

    @Provides fun readingDao(db: AphidDatabase) = db.readingDao()

    @Provides fun formulaDao(db: AphidDatabase) = db.formulaDao()

    @Provides fun taskDao(db: AphidDatabase) = db.taskDao()

    @Provides fun alertDao(db: AphidDatabase) = db.alertDao()

    @Provides fun diagnosisDao(db: AphidDatabase) = db.diagnosisDao()

    @Provides fun pestDao(db: AphidDatabase) = db.pestDao()
}
