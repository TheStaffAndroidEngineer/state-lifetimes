package staff.android.lifetimes

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java.io.Closeable
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

fun interface QuantityPreviewer {
    suspend fun preview(raw: String): String
}

class DelayedQuantityPreviewer : QuantityPreviewer {
    override suspend fun preview(raw: String): String {
        delay(400)
        val quantity = raw.trim().toIntOrNull()?.takeIf { it in 0..5 }
        return if (quantity == null) "Enter a quantity from 0 to 5"
        else "Preview: $quantity camera(s); not confirmed"
    }
}

@Composable
fun QuantityPreview(
    raw: String,
    previewer: QuantityPreviewer,
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
) {
    var preview by remember(raw, previewer, lifecycleOwner) {
        mutableStateOf("Preview pending")
    }
    LaunchedEffect(raw, previewer, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            preview = "Preview pending"
            val result = previewer.preview(raw)
            currentCoroutineContext().ensureActive()
            preview = result
        }
    }
    Text(preview, Modifier.testTag("preview"))
}

/** Main-thread fixture: subscription is removable, but a copied callback may still arrive. */
class StockFeed {
    private val listeners = mutableSetOf<(String) -> Unit>()
    val activeCount: Int get() = listeners.size
    fun subscribe(listener: (String) -> Unit): Closeable {
        listeners += listener
        return Closeable { listeners -= listener }
    }
    fun queued(text: String): () -> Unit {
        val copied = listeners.toList()
        return { copied.forEach { it(text) } }
    }
    fun emit(text: String) = queued(text).invoke()
}

@Composable
fun ObserveWhileMounted(feed: StockFeed, onStock: (String) -> Unit) {
    val latest by rememberUpdatedState(onStock)
    DisposableEffect(feed) {
        var active = true
        val subscription = feed.subscribe { if (active) latest(it) }
        onDispose {
            active = false
            subscription.close()
        }
    }
}

@Composable
fun ObserveWhileStarted(
    feed: StockFeed,
    onStock: (String) -> Unit,
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
) {
    val latest by rememberUpdatedState(onStock)
    LifecycleStartEffect(feed, lifecycleOwner = lifecycleOwner) {
        var active = true
        val subscription = feed.subscribe { if (active) latest(it) }
        onStopOrDispose {
            active = false
            subscription.close()
        }
    }
}

class CompositionRecord { var lastQuantity: Int? = null }

@Composable
fun RecordAppliedQuantity(quantity: Int, record: CompositionRecord) {
    SideEffect { record.lastQuantity = quantity }
}

@Composable
fun ReviewFromTop(listState: LazyListState) {
    val showReturn by remember(listState) {
        derivedStateOf { listState.firstVisibleItemIndex > 0 }
    }
    val scope = rememberCoroutineScope()
    if (showReturn) {
        Button(onClick = { scope.launch { listState.animateScrollToItem(0) } }) {
            Text("Return to quantity")
        }
    }
}
