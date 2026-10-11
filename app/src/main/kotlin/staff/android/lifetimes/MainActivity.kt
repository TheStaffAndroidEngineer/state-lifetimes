package staff.android.lifetimes

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

class LifetimeApplication : Application() {
    val repository: CartRepository by lazy { DataStoreCartRepository(cartDataStore) }
    val previewer: QuantityPreviewer = DelayedQuantityPreviewer()
    val stockFeed = StockFeed()
}

class MainActivity : ComponentActivity() {
    val editor: CartEditorViewModel by viewModels {
        viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as LifetimeApplication
                CartEditorViewModel(createSavedStateHandle(), app.repository)
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as LifetimeApplication
        setContent {
            CommonGoodsTheme {
                Surface(Modifier.fillMaxSize()) {
                    CartRoute(editor, app.previewer, app.stockFeed)
                }
            }
        }
    }
}

@Composable
fun CartRoute(
    editor: CartEditorViewModel,
    previewer: QuantityPreviewer,
    stockFeed: StockFeed,
) {
    val state by editor.state.collectAsStateWithLifecycle()
    var visible by rememberSaveable { mutableStateOf(true) }
    var stock by remember { mutableStateOf("Stock notices appear here") }
    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
            .semantics { testTagsAsResourceId = true }
            .verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CartHeading()
        val cart = state.cart
        if (cart == null) {
            Text(state.message ?: "Loading cart", Modifier.testTag("loading"))
            if (state.message != null) {
                Button(editor::retryLoad, Modifier.testTag("retry")) { Text("Retry loading") }
            }
        } else {
            Text("Accepted: ${cart.quantity}", Modifier.testTag("accepted"))
            if (visible && state.draft != null) {
                val draft = requireNotNull(state.draft)
                OutlinedTextField(
                    value = draft.raw,
                    onValueChange = editor::change,
                    enabled = !state.busy,
                    label = { Text("Draft quantity (0 to 5)") },
                    modifier = Modifier.fillMaxWidth().testTag("draft"),
                )
                QuantityPreview(draft.raw, previewer)
                ObserveWhileStarted(stockFeed, onStock = { stock = it })
                Text(stock, Modifier.testTag("stock"))
                if (state.conflict) Text(
                    "Cart changed. Your draft is preserved; reload to review.",
                    Modifier.testTag("conflict"),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(editor::confirm, enabled = state.canConfirm,
                        modifier = Modifier.testTag("confirm")) { Text("Confirm") }
                    OutlinedButton(editor::cancel, enabled = !state.busy,
                        modifier = Modifier.testTag("cancel")) { Text("Cancel") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(editor::reload, enabled = !state.busy,
                        modifier = Modifier.testTag("reload")) { Text("Reload current") }
                    OutlinedButton({ visible = false }, enabled = !state.busy,
                        modifier = Modifier.testTag("hide")) { Text("Hide") }
                }
            } else {
                Button({ editor.open(); visible = true }, enabled = !state.busy,
                    modifier = Modifier.testTag("edit")) {
                    Text(if (state.draft == null) "Edit quantity" else "Show editor")
                }
            }
            state.message?.let { Text(it, Modifier.testTag("message")) }
            LabControls {
                Text("Revision ${cart.revision}; confirmations ${cart.confirmations}",
                    Modifier.testTag("record"))
                OutlinedButton(editor::externalUpdate, enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth().testTag("external")) { Text("Simulate external update") }
                OutlinedButton({ stockFeed.emit("Stock checked") },
                    modifier = Modifier.fillMaxWidth().testTag("stock-event")) { Text("Emit stock notice") }
                OutlinedButton(editor::reset, enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth().testTag("reset")) { Text("Reset lab") }
            }
        }
    }
}
