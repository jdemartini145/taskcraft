package pe.aphid.feature.systems

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pe.aphid.core.domain.repository.SystemRepository
import pe.aphid.core.model.GrowSystem
import pe.aphid.core.model.GrowSystemType
import pe.aphid.core.model.Planting

private class FakeSystems : SystemRepository {
    val list = MutableStateFlow<List<GrowSystem>>(emptyList())
    override fun systems(): Flow<List<GrowSystem>> = list
    override suspend fun get(id: Long) = list.value.firstOrNull { it.id == id }
    override suspend fun upsert(system: GrowSystem): Long {
        list.value = list.value + system
        return system.id
    }
    override suspend fun delete(id: Long) {}
    override fun plantings(systemId: Long): Flow<List<Planting>> = flowOf(emptyList())
    override suspend fun plantingsNow(systemId: Long) = emptyList<Planting>()
    override suspend fun upsertPlanting(planting: Planting) = 0L
    override suspend fun deletePlanting(id: Long) {}
}

@OptIn(ExperimentalCoroutinesApi::class)
class SystemsViewModelTest {
    @BeforeEach fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach fun tearDown() = Dispatchers.resetMain()

    @Test
    fun emitsSystems() = runTest {
        val repo = FakeSystems()
        val vm = SystemsViewModel(repo)
        vm.systems.test {
            var first = awaitItem()
            if (first == null) first = awaitItem()
            assertEquals(emptyList<GrowSystem>(), first)
            repo.upsert(GrowSystem(id = 1, name = "NFT", type = GrowSystemType.NFT, volumeL = 60.0))
            assertEquals("NFT", awaitItem().single().name)
        }
    }
}
