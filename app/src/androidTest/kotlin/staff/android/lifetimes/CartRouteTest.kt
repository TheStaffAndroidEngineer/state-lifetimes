package staff.android.lifetimes

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CartRouteTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun reset() {
        compose.waitUntil(5_000) { compose.activity.editor.state.value.cart != null }
        compose.runOnIdle { compose.activity.editor.reset() }
        compose.waitUntil(5_000) {
            val state = compose.activity.editor.state.value
            state.cart == CartLine() && state.draft == null && !state.busy
        }
        compose.waitForIdle()
    }

    private fun edit(raw: String) {
        compose.onNodeWithTag("edit").performScrollTo().performClick()
        compose.onNodeWithTag("draft").performTextReplacement(raw)
    }
    private fun draft() = compose.onNodeWithTag("draft")
        .fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    @Test fun draftSurvivesHideAndActivityRecreationWithoutConfirming() {
        edit("3")
        compose.onNodeWithTag("hide").performScrollTo().performClick()
        compose.onNodeWithTag("edit").performScrollTo().performClick()
        assertEquals("3", draft())
        val owner = compose.activity.editor
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        assertSame(owner, compose.activity.editor)
        assertEquals("3", draft())
        compose.onNodeWithTag("accepted").assertTextEquals("Accepted: 2")
        assertEquals(0, compose.activity.editor.state.value.cart!!.confirmations)
    }

    @Test fun emptyDraftRestoresAndCannotConfirm() {
        edit("")
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        assertEquals("", draft())
        compose.onNodeWithTag("confirm").assertIsNotEnabled()
        compose.onNodeWithTag("accepted").assertTextEquals("Accepted: 2")
    }

    @Test fun externalChangePreservesDraftUntilReloadThenCommitsExplicitly() {
        edit("3")
        compose.runOnIdle { compose.activity.editor.externalUpdate() }
        compose.waitUntil(5_000) { compose.activity.editor.state.value.cart!!.quantity == 4 }
        assertEquals("3", draft())
        compose.onNodeWithTag("confirm").assertIsNotEnabled()
        compose.onNodeWithTag("conflict").assertExists()
        compose.onNodeWithTag("reload").performScrollTo().performClick()
        assertEquals("4", draft())
        compose.onNodeWithTag("draft").performTextReplacement("5")
        compose.onNodeWithTag("confirm").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.activity.editor.state.value.cart!!.quantity == 5 }
        compose.onNodeWithTag("accepted").assertTextEquals("Accepted: 5")
        assertEquals(1, compose.activity.editor.state.value.cart!!.confirmations)
    }

    @Test fun cancelKeepsLatestExternalAcceptedValue() {
        edit("3")
        compose.runOnIdle { compose.activity.editor.externalUpdate() }
        compose.waitUntil(5_000) { compose.activity.editor.state.value.cart!!.quantity == 4 }
        compose.onNodeWithTag("cancel").performScrollTo().performClick()
        compose.onNodeWithTag("draft").assertDoesNotExist()
        compose.onNodeWithTag("accepted").assertTextEquals("Accepted: 4")
    }
}
