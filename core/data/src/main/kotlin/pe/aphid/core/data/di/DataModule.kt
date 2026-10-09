package pe.aphid.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import pe.aphid.core.data.repository.BackupRepositoryImpl
import pe.aphid.core.data.repository.OfflineAlertRepository
import pe.aphid.core.data.repository.OfflineCropRepository
import pe.aphid.core.data.repository.OfflineDiagnosisRepository
import pe.aphid.core.data.repository.OfflineFertilizerRepository
import pe.aphid.core.data.repository.OfflineFormulaRepository
import pe.aphid.core.data.repository.OfflinePestRepository
import pe.aphid.core.data.repository.OfflineReadingRepository
import pe.aphid.core.data.repository.OfflineSystemRepository
import pe.aphid.core.data.repository.OfflineTaskRepository
import pe.aphid.core.data.repository.OfflineWaterRepository
import pe.aphid.core.data.repository.SettingsDataStore
import pe.aphid.core.domain.repository.AlertRepository
import pe.aphid.core.domain.repository.BackupRepository
import pe.aphid.core.domain.repository.CropRepository
import pe.aphid.core.domain.repository.DiagnosisRepository
import pe.aphid.core.domain.repository.FertilizerRepository
import pe.aphid.core.domain.repository.FormulaRepository
import pe.aphid.core.domain.repository.PestRepository
import pe.aphid.core.domain.repository.ReadingRepository
import pe.aphid.core.domain.repository.SettingsRepository
import pe.aphid.core.domain.repository.SystemRepository
import pe.aphid.core.domain.repository.TaskRepository
import pe.aphid.core.domain.repository.WaterRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds abstract fun crops(impl: OfflineCropRepository): CropRepository

    @Binds abstract fun fertilizers(impl: OfflineFertilizerRepository): FertilizerRepository

    @Binds abstract fun water(impl: OfflineWaterRepository): WaterRepository

    @Binds abstract fun systems(impl: OfflineSystemRepository): SystemRepository

    @Binds abstract fun readings(impl: OfflineReadingRepository): ReadingRepository

    @Binds abstract fun formulas(impl: OfflineFormulaRepository): FormulaRepository

    @Binds abstract fun tasks(impl: OfflineTaskRepository): TaskRepository

    @Binds abstract fun alerts(impl: OfflineAlertRepository): AlertRepository

    @Binds abstract fun diagnoses(impl: OfflineDiagnosisRepository): DiagnosisRepository

    @Binds abstract fun pests(impl: OfflinePestRepository): PestRepository

    @Binds abstract fun settings(impl: SettingsDataStore): SettingsRepository

    @Binds abstract fun backup(impl: BackupRepositoryImpl): BackupRepository
}
