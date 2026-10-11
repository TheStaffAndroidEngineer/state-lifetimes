package staff.android.lifetimes.solution

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import staff.android.lifetimes.Assignment
import staff.android.lifetimes.NotePreviewer
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

@Composable
fun AssignmentNotesSolution(
    assignments: List<Assignment>,
    selectedId: String,
    previewer: NotePreviewer,
    onPreview: (String, String) -> Unit,
    onSubmit: (String, String) -> Unit,
) {
    require(assignments.size == 2)
    require(assignments.all { it.id.isNotBlank() })
    require(assignments.map { it.id }.distinct().size == assignments.size)
    val assignment = requireNotNull(assignments.find { it.id == selectedId })
    val drafts = rememberSaveableStateHolder()
    drafts.SaveableStateProvider(assignment.id) {
        var note by rememberSaveable { mutableStateOf("") }
        val latestPreview by rememberUpdatedState(onPreview)
        LaunchedEffect(previewer, assignment.id, note) {
            val result = previewer.preview(assignment.id, note)
            currentCoroutineContext().ensureActive()
            latestPreview(assignment.id, result)
        }
        Column {
            Text(assignment.title)
            OutlinedTextField(note, { if (it.length <= 80) note = it },
                label = { Text("Note (maximum 80 characters)") },
                modifier = Modifier.testTag("note"))
            Button(
                onClick = { onSubmit(assignment.id, note) },
                enabled = note.isNotBlank(),
                modifier = Modifier.testTag("submit"),
            ) { Text("Submit") }
        }
    }
}
