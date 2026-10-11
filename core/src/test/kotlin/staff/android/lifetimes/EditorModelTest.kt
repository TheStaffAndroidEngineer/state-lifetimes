package staff.android.lifetimes

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class EditorModelTest {
    @Test fun draftNeverChangesAcceptedQuantity() {
        val state = EditorState(CartLine(), QuantityDraft("3", 0))
        assertEquals(2, state.cart!!.quantity)
        assertEquals(3, state.candidate)
        assertTrue(state.canConfirm)
    }
    @Test fun emptyAndZeroHaveDifferentMeanings() {
        assertNull(EditorState(CartLine(), QuantityDraft("", 0)).candidate)
        assertEquals(0, EditorState(CartLine(), QuantityDraft("0", 0)).candidate)
    }
    @Test fun invalidInputCannotConfirm() {
        for (raw in listOf("six", "6", "-1", "99999999999")) {
            assertFalse(EditorState(CartLine(), QuantityDraft(raw, 0)).canConfirm)
        }
    }
    @Test fun externalChangeKeepsRawInputButRevokesConfirmation() {
        val draft = QuantityDraft("3", 0)
        val state = EditorState(CartLine(4, 1), draft)
        assertEquals("3", state.draft!!.raw)
        assertTrue(state.conflict)
        assertFalse(state.canConfirm)
    }
    @Test fun matchingQuantityDoesNotHideRevisionConflict() {
        assertFalse(EditorState(CartLine(3, 1), QuantityDraft("3", 0)).canConfirm)
    }
    @Test fun rebaseUsesCurrentRevision() {
        assertTrue(EditorState(CartLine(4, 1), QuantityDraft("4", 1)).canConfirm)
    }
    @Test fun loadingAndBusyCannotConfirm() {
        assertFalse(EditorState(draft = QuantityDraft("3", 0)).canConfirm)
        assertFalse(EditorState(CartLine(), QuantityDraft("3", 0), busy = true).canConfirm)
    }
}
