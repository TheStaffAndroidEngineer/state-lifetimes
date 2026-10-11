package staff.android.lifetimes

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

object DraftCodec {
    fun encode(draft: QuantityDraft) = Bundle().apply {
        putInt("schema", 1)
        putString("raw", draft.raw)
        putLong("baseRevision", draft.baseRevision)
    }

    fun decode(bundle: Bundle?): QuantityDraft? {
        if (bundle == null || bundle.getInt("schema") != 1) return null
        val raw = bundle.getString("raw") ?: return null
        if (!bundle.containsKey("baseRevision")) return null
        val revision = bundle.getLong("baseRevision")
        if (raw.length > 32 || revision < 0) return null
        return QuantityDraft(raw, revision)
    }
}

class CartEditorViewModel(
    private val savedState: SavedStateHandle,
    private val repository: CartRepository,
) : ViewModel() {
    private val savedDraft = savedState.getStateFlow<Bundle?>("draft", null)
    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val cart = MutableStateFlow<CartLine?>(null)
    private var loadJob: Job? = null
    val state = combine(cart, savedDraft, busy, message) { line, bundle, working, text ->
        EditorState(line, DraftCodec.decode(bundle), working, text)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, EditorState())

    init { retryLoad() }

    fun retryLoad() {
        if (loadJob?.isActive == true) return
        message.value = null
        loadJob = viewModelScope.launch {
            try {
                repository.line.collect { cart.value = it }
            } catch (error: IOException) {
                cart.value = null
                message.value = "Cart unavailable; retry loading"
            }
        }
    }

    private fun write(draft: QuantityDraft?) {
        savedState["draft"] = draft?.let(DraftCodec::encode)
    }

    fun open() {
        if (busy.value) return
        val line = state.value.cart ?: return
        if (DraftCodec.decode(savedDraft.value) == null) {
            write(QuantityDraft(line.quantity.toString(), line.revision))
        }
        message.value = null
    }

    fun change(raw: String) {
        if (busy.value) return
        val draft = DraftCodec.decode(savedDraft.value) ?: return
        if (raw.length <= 32) write(draft.copy(raw = raw))
        message.value = null
    }

    fun reload() {
        if (busy.value) return
        val line = state.value.cart ?: return
        write(QuantityDraft(line.quantity.toString(), line.revision))
        message.value = null
    }

    fun cancel() {
        if (busy.value) return
        write(null)
        message.value = null
    }

    fun confirm() {
        if (busy.value) return
        val draft = DraftCodec.decode(savedDraft.value) ?: return
        val quantity = draft.raw.trim().toIntOrNull()?.takeIf { it in 0..5 } ?: return
        busy.value = true
        viewModelScope.launch {
            try {
                repository.confirm(draft.baseRevision, quantity)
                write(null)
                message.value = "Quantity accepted"
            } catch (error: CancellationException) {
                throw error
            } catch (error: RevisionConflict) {
                message.value = error.message
            } catch (error: IOException) {
                message.value = "Could not save; your draft is still here"
            } finally {
                busy.value = false
            }
        }
    }

    fun externalUpdate() = runFixtureCommand { repository.externalUpdate() }

    fun reset() = runFixtureCommand {
        repository.reset()
        write(null)
    }

    private fun runFixtureCommand(action: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                action()
                message.value = null
            } catch (error: IOException) {
                message.value = "Fixture write failed"
            } finally {
                busy.value = false
            }
        }
    }
}
