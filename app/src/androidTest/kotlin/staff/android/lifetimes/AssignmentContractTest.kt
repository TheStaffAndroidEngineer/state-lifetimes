package staff.android.lifetimes

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import staff.android.lifetimes.exercise.AssignmentNotes
import staff.android.lifetimes.solution.AssignmentNotesSolution

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ExerciseOnly

abstract class AssignmentContract {
    @get:Rule val compose = createComposeRule()
    protected abstract val reference: Boolean
    private val assignments = listOf(Assignment("A", "Pump"), Assignment("B", "Meter"))
    private val selected = mutableStateOf("A")
    private val prefix = mutableStateOf("old")
    private val previews = mutableListOf<String>()
    private val submissions = mutableListOf<String>()
    private data class Call(val id: String, val note: String,
        val gate: CompletableDeferred<String> = CompletableDeferred())
    private val calls = mutableListOf<Call>()
    private val previewer = NotePreviewer { id, note ->
        val call = Call(id, note); calls += call; call.gate.await()
    }

    @Composable private fun Content() {
        val captured = prefix.value
        MaterialTheme {
            val onPreview: (String, String) -> Unit = { id, value -> previews += "$captured/$id/$value" }
            val onSubmit: (String, String) -> Unit = { id, value -> submissions += "$captured/$id/$value" }
            if (reference) AssignmentNotesSolution(assignments, selected.value, previewer, onPreview, onSubmit)
            else AssignmentNotes(assignments, selected.value, previewer, onPreview, onSubmit)
        }
    }
    private fun text() = compose.onNodeWithTag("note").fetchSemanticsNode()
        .config[SemanticsProperties.EditableText].text
    private fun type(value: String) = compose.onNodeWithTag("note").performTextReplacement(value)
    private fun select(id: String) = compose.runOnIdle { selected.value = id }

    @Test fun draftsBelongToIdsAndReturnIndependently() {
        compose.setContent { Content() }
        type("Pump note"); select("B")
        assertEquals("", text())
        type("Meter note"); select("A")
        assertEquals("Pump note", text())
        select("B"); assertEquals("Meter note", text())
    }
    @Test fun saveableRestorationIncludesHiddenDraftAndEmptyCurrentDraft() {
        val tester = StateRestorationTester(compose)
        tester.setContent { Content() }
        type("Pump note"); select("B"); type("")
        tester.emulateSavedInstanceStateRestore()
        assertEquals("", text()); select("A")
        assertEquals("Pump note", text())
    }
    @Test fun replacedCallbackReceivesPendingPreviewWithoutRestart() {
        compose.setContent { Content() }
        compose.runOnIdle { assertEquals(1, calls.size); prefix.value = "new" }
        compose.runOnIdle { assertEquals(1, calls.size); calls.single().gate.complete("preview") }
        compose.runOnIdle { assertEquals(listOf("new/A/preview"), previews) }
    }
    @Test fun oldAssignmentCompletionCannotPublishAfterSwitch() {
        compose.setContent { Content() }
        type("Pump note")
        select("B"); type("Meter note")
        compose.runOnIdle {
            calls.filter { it.id == "A" }.forEach { it.gate.complete("obsolete") }
            calls.last().gate.complete("current")
        }
        compose.runOnIdle { assertEquals(listOf("old/B/current"), previews) }
    }
    @Test fun onlyExplicitSubmitUsesLatestCallbackAndRetainsDraft() {
        compose.setContent { Content() }
        type("  inspect valve  ")
        compose.runOnIdle { prefix.value = "new"; assertTrue(submissions.isEmpty()) }
        compose.onNodeWithTag("submit").performClick()
        compose.runOnIdle { assertEquals(listOf("new/A/  inspect valve  "), submissions) }
        assertEquals("  inspect valve  ", text())
    }
    @Test fun whitespaceDoesNotSubmitAndOverlongReplacementIsRejected() {
        compose.setContent { Content() }
        type("   ")
        compose.onNodeWithTag("submit").assertIsNotEnabled()
        type("x".repeat(80)); assertEquals(80, text().length)
        type("y".repeat(81)); assertEquals("x".repeat(80), text())
    }
}

class AssignmentReferenceTest : AssignmentContract() { override val reference = true }

@ExerciseOnly
class AssignmentLearnerTest : AssignmentContract() { override val reference = false }
