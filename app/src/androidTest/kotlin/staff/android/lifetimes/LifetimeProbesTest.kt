package staff.android.lifetimes

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LifetimeProbesTest {
    @get:Rule val compose = createComposeRule()
    private fun draft() = compose.onNodeWithTag("probe-draft")
        .fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    @Test fun childOwnedDraftIsLostWhenChildLeaves() {
        compose.setContent { MaterialTheme { LocalDraftProbe() } }
        compose.onNodeWithTag("probe-draft").performTextReplacement("3")
        compose.onNodeWithText("Hide").performClick()
        compose.onNodeWithText("Show").performClick()
        assertEquals("2", draft())
        compose.onNodeWithText("Accepted: 2").assertIsDisplayed()
    }

    @Test fun hoistedDraftSurvivesChildRemoval() {
        compose.setContent { MaterialTheme { HoistedDraftProbe() } }
        compose.onNodeWithTag("probe-draft").performTextReplacement("3")
        compose.onNodeWithText("Hide").performClick()
        compose.onNodeWithText("Show").performClick()
        assertEquals("3", draft())
    }

    @Test fun saveableRestorationPreservesEmptyText() {
        val tester = StateRestorationTester(compose)
        tester.setContent { MaterialTheme { HoistedDraftProbe() } }
        compose.onNodeWithTag("probe-draft").performTextReplacement("")
        tester.emulateSavedInstanceStateRestore()
        assertEquals("", draft())
    }

    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Test fun previewCancelsOldInputAndStopsBelowStarted() {
        val raw = mutableStateOf("2")
        val gates = mutableMapOf<String, CompletableDeferred<String>>()
        val cancelled = mutableListOf<String>()
        lateinit var owner: Owner
        compose.runOnUiThread {
            owner = Owner().also { it.registry.currentState = Lifecycle.State.STARTED }
        }
        val previewer = QuantityPreviewer { value ->
            try { gates.getOrPut(value) { CompletableDeferred() }.await() }
            finally { cancelled += value }
        }
        compose.setContent { QuantityPreview(raw.value, previewer, owner) }
        compose.runOnIdle { assertTrue(gates.containsKey("2")); raw.value = "3" }
        compose.runOnIdle { assertTrue(cancelled.contains("2")); gates.getValue("3").complete("new3") }
        compose.onNodeWithTag("preview").assertTextEquals("new3")
        compose.runOnIdle { raw.value = "4" }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        compose.runOnIdle { assertTrue(cancelled.contains("4")) }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        compose.runOnIdle { gates.getValue("4").complete("new4") }
        compose.onNodeWithTag("preview").assertTextEquals("new4")
    }

    @Test fun startedListenerUsesLatestCallbackAndInvalidatesQueuedDelivery() {
        val feed = StockFeed()
        val show = mutableStateOf(true)
        val events = mutableListOf<String>()
        val prefix = mutableStateOf("old")
        lateinit var owner: Owner
        lateinit var queued: () -> Unit
        compose.runOnUiThread {
            owner = Owner().also { it.registry.currentState = Lifecycle.State.STARTED }
        }
        compose.setContent {
            val captured = prefix.value
            if (show.value) ObserveWhileStarted(feed, { events += "$captured:$it" }, owner)
        }
        compose.runOnIdle { assertEquals(1, feed.activeCount); prefix.value = "new" }
        compose.runOnIdle {
            assertEquals(1, feed.activeCount)
            feed.emit("one")
            assertEquals(listOf("new:one"), events)
            queued = feed.queued("late")
            owner.registry.currentState = Lifecycle.State.CREATED
        }
        compose.runOnIdle { assertEquals(0, feed.activeCount); queued(); assertEquals(1, events.size) }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        compose.runOnIdle { assertEquals(1, feed.activeCount); show.value = false }
        compose.runOnIdle { assertEquals(0, feed.activeCount) }
    }

    @Test fun compositionListenerDisposesOnFeedReplacementAndRemoval() {
        val first = StockFeed(); val second = StockFeed()
        val feed = mutableStateOf(first); val show = mutableStateOf(true)
        compose.setContent { if (show.value) ObserveWhileMounted(feed.value) {} }
        compose.runOnIdle { assertEquals(1, first.activeCount); feed.value = second }
        compose.runOnIdle { assertEquals(0, first.activeCount); assertEquals(1, second.activeCount); show.value = false }
        compose.runOnIdle { assertEquals(0, second.activeCount) }
    }

    @Test fun unkeyedSideEffectPublishesAppliedQuantityToExternalRecord() {
        val quantity = mutableStateOf(2)
        val record = CompositionRecord()
        compose.setContent { RecordAppliedQuantity(quantity.value, record) }
        compose.runOnIdle { assertEquals(2, record.lastQuantity); quantity.value = 3 }
        compose.runOnIdle { assertEquals(3, record.lastQuantity) }
    }

    @Test fun scrollThresholdOffersReturnAndClickMovesToFirstItem() {
        compose.setContent {
            val list = rememberLazyListState()
            Column {
                LazyColumn(Modifier.height(120.dp).testTag("help-list"), state = list) {
                    items(20) { Text("Help $it", Modifier.height(50.dp)) }
                }
                ReviewFromTop(list)
            }
        }
        compose.onNodeWithText("Return to quantity").assertDoesNotExist()
        compose.onNodeWithTag("help-list").performScrollToIndex(3)
        compose.onNodeWithText("Return to quantity").performClick()
        compose.onNodeWithText("Return to quantity").assertDoesNotExist()
        compose.onNodeWithText("Help 0").assertIsDisplayed()
    }
}
