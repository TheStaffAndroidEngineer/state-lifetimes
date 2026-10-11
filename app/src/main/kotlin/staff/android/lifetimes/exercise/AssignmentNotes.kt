package staff.android.lifetimes.exercise

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import staff.android.lifetimes.Assignment
import staff.android.lifetimes.NotePreviewer

/** Deliberately defective learner implementation. Preserve this file when comparing. */
@Composable
fun AssignmentNotes(
    assignments: List<Assignment>,
    selectedId: String,
    previewer: NotePreviewer,
    onPreview: (String, String) -> Unit,
    onSubmit: (String, String) -> Unit,
) {
    val assignment = assignments.first { it.id == selectedId }
    var note by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        onPreview(selectedId, previewer.preview(selectedId, note))
    }
    Column {
        Text(assignment.title)
        OutlinedTextField(note, { note = it },
            label = { Text("Note (maximum 80 characters)") },
            modifier = Modifier.testTag("note"))
        Button({}, modifier = Modifier.testTag("submit")) { Text("Submit") }
    }
}
