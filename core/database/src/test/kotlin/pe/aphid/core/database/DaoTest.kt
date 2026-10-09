package pe.aphid.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pe.aphid.core.model.Crop
import pe.aphid.core.model.CropStageTarget
import pe.aphid.core.model.Element
import pe.aphid.core.model.GrowSystem
import pe.aphid.core.model.GrowSystemType
import pe.aphid.core.model.GrowthStage
import pe.aphid.core.model.Reading
import pe.aphid.core.model.ValueRange

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DaoTest {
    private lateinit var db: AphidDatabase

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AphidDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun tearDown() = db.close()

    @Test
    fun readingsSinceAndCascade() = runTest {
        val id = db.systemDao().upsert(GrowSystem(name = "DWC 1", type = GrowSystemType.DWC, volumeL = 100.0).toEntity())
        db.readingDao().insert(Reading(systemId = id, timestampMillis = 1_000, ph = 5.8).toEntity())
        db.readingDao().insert(Reading(systemId = id, timestampMillis = 5_000, ph = 6.1).toEntity())
        assertEquals(1, db.readingDao().since(id, 2_000).size)
        assertEquals(6.1, db.readingDao().observeLatest().first()!!.ph!!, 1e-9)
        db.systemDao().delete(id)
        assertEquals(0, db.readingDao().all().size)
    }

    @Test
    fun pendingTargetsRoundTrip() = runTest {
        db.cropDao().upsertCrops(listOf(Crop("aji", "Ají", "Capsicum baccatum", "Solanaceae").toEntity()))
        val t = CropStageTarget("aji", GrowthStage.VEGETATIVA, mapOf(Element.Ca to null, Element.K to 200.0), ph = ValueRange(null, 6.0))
        db.cropDao().upsertTargets(listOf(t.toEntity()))
        val back = db.cropDao().target("aji", GrowthStage.VEGETATIVA)!!.toModel()
        assertEquals(null, back.targetsPpm[Element.Ca])
        assertEquals(200.0, back.targetsPpm[Element.K]!!, 1e-9)
        assertEquals(6.0, back.ph.max!!, 1e-9)
    }
}
