package staff.android.lifetimes

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class OwnerAndStoreTest {
    private class Repository : CartRepository {
        val mutable = MutableStateFlow(CartLine())
        var failLoad = false
        var loadAttempts = 0
        var confirmCalls = 0
        var gate: CompletableDeferred<Unit>? = null
        override val line: Flow<CartLine> = flow {
            loadAttempts++
            if (failLoad) throw IOException("controlled read failure")
            emitAll(mutable)
        }
        override suspend fun confirm(baseRevision: Long, quantity: Int) {
            confirmCalls++
            gate?.await()
            if (mutable.value.revision != baseRevision) throw RevisionConflict()
            mutable.value = CartLine(quantity, baseRevision + 1, mutable.value.confirmations + 1)
        }
        override suspend fun externalUpdate() { mutable.value = CartLine(4, 1) }
        override suspend fun reset() { mutable.value = CartLine() }
    }
    private suspend fun ready(vm: CartEditorViewModel, test: (EditorState) -> Boolean = { it.cart != null }) =
        withTimeout(5_000) { vm.state.first(test) }

    private suspend fun owner(handle: SavedStateHandle, repo: CartRepository): Pair<ViewModelStore, CartEditorViewModel> =
        withContext(Dispatchers.Main) {
            val store = ViewModelStore()
            val vm = CartEditorViewModel(handle, repo)
            store.put("editor", vm)
            store to vm
        }

    @Test fun restoredEmptyDraftWaitsForAuthoritativeDataWithoutBeingReplaced() = runBlocking<Unit> {
        val handle = SavedStateHandle(mapOf("draft" to DraftCodec.encode(QuantityDraft("", 0))))
        val (store, vm) = owner(handle, Repository())
        try {
            assertEquals("", ready(vm).draft!!.raw)
            assertFalse(vm.state.value.canConfirm)
        } finally { withContext(Dispatchers.Main) { store.clear() } }
    }

    @Test fun failedInitialReadIsVisibleAndExplicitRetryKeepsTheDraft() = runBlocking<Unit> {
        val repo = Repository().apply { failLoad = true }
        val handle = SavedStateHandle(mapOf("draft" to DraftCodec.encode(QuantityDraft("3", 0))))
        val (store, vm) = owner(handle, repo)
        try {
            val failed = ready(vm) { it.message != null }
            assertNull(failed.cart); assertEquals("3", failed.draft!!.raw)
            withContext(Dispatchers.Main) { repo.failLoad = false; vm.retryLoad() }
            assertEquals("3", ready(vm).draft!!.raw)
            assertEquals(2, repo.loadAttempts)
        } finally { withContext(Dispatchers.Main) { store.clear() } }
    }

    @Test fun doubleClickCannotStartTwoPendingCommands() = runBlocking<Unit> {
        val repo = Repository().apply { gate = CompletableDeferred() }
        val (store, vm) = owner(SavedStateHandle(), repo)
        try {
            ready(vm)
            withContext(Dispatchers.Main) { vm.open(); vm.change("3"); vm.confirm(); vm.confirm() }
            ready(vm) { it.busy }
            assertEquals(1, repo.confirmCalls)
            repo.gate!!.complete(Unit)
            ready(vm) { it.cart?.quantity == 3 && it.draft == null && !it.busy }
        } finally { withContext(Dispatchers.Main) { store.clear() } }
    }

    @Test fun ownerConstructionWithRestoredStaleDraftNeverReplaysConfirmation() = runBlocking<Unit> {
        val repo = Repository().apply { mutable.value = CartLine(3, 1, 1) }
        val handle = SavedStateHandle(mapOf("draft" to DraftCodec.encode(QuantityDraft("3", 0))))
        val (store, vm) = owner(handle, repo)
        try {
            val restored = ready(vm)
            assertTrue(restored.conflict)
            assertEquals(0, repo.confirmCalls)
            assertEquals("3", restored.draft!!.raw)
            withContext(Dispatchers.Main) { vm.reload() }
            ready(vm) { it.draft?.baseRevision == 1L }
            assertEquals(0, repo.confirmCalls)
        } finally { withContext(Dispatchers.Main) { store.clear() } }
    }

    @Test fun revisionIsComparedInDurableTransactionAndReadBackAfterNewStore() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "cart-${UUID.randomUUID()}.preferences_pb")
        var job = SupervisorJob()
        try {
            val firstStore = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) { file }
            val repo = DataStoreCartRepository(firstStore)
            assertEquals(CartLine(), repo.line.first())
            repo.confirm(0, 3)
            try { repo.confirm(0, 5); fail("A stale revision must be rejected") }
            catch (_: RevisionConflict) { }
            assertEquals(CartLine(3, 1, 1), repo.line.first())
            job.cancelAndJoin()
            job = SupervisorJob()
            val fresh = DataStoreCartRepository(PreferenceDataStoreFactory.create(
                scope = CoroutineScope(Dispatchers.IO + job),
            ) { file })
            assertEquals(CartLine(3, 1, 1), fresh.line.first())
            fresh.confirm(1, 3)
            assertEquals(CartLine(3, 1, 1), fresh.line.first())
        } finally { job.cancelAndJoin(); file.delete() }
    }
}
