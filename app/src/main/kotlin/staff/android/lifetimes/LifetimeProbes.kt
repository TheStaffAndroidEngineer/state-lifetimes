package staff.android.lifetimes

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

@Composable
fun LocalDraftProbe() {
    var open by rememberSaveable { mutableStateOf(true) }
    Column {
        Text("Accepted: 2")
        Button(onClick = { open = !open }) {
            Text(if (open) "Hide" else "Show")
        }
        if (open) {
            var draft by remember { mutableStateOf("2") }
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text("Draft") },
                modifier = Modifier.testTag("probe-draft"),
            )
        }
    }
}

@Composable
fun HoistedDraftProbe() {
    var open by rememberSaveable { mutableStateOf(true) }
    var draft by rememberSaveable { mutableStateOf("2") }
    Column {
        Text("Accepted: 2")
        Button(onClick = { open = !open }) {
            Text(if (open) "Hide" else "Show")
        }
        if (open) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text("Draft") },
                modifier = Modifier.testTag("probe-draft"),
            )
        }
    }
}
