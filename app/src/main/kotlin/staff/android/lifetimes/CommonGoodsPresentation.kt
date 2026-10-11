package staff.android.lifetimes

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

@Composable
fun CommonGoodsHeader() {
    Text("COMMON GOODS", style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary)
}

@Composable
fun ProductPhoto(id: String, modifier: Modifier = Modifier) {
    Image(painterResource(if (id == "camera") R.drawable.product_camera else R.drawable.product_tea),
        contentDescription = null, contentScale = ContentScale.Crop,
        modifier = modifier.clip(RoundedCornerShape(16.dp)))
}

@Composable
fun LabControls(content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    HorizontalDivider(Modifier.padding(top = 16.dp))
    TextButton(onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth().testTag("lab-controls").semantics {
            stateDescription = if (expanded) "Expanded" else "Collapsed"
        }) {
        Text(if (expanded) "Hide lab controls" else "Lab controls")
    }
    if (expanded) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
fun CartHeading() {
        CommonGoodsHeader()
        Text("Cart", style = MaterialTheme.typography.headlineLarge)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ProductPhoto("camera", Modifier.size(88.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Camera", style = MaterialTheme.typography.titleLarge)
                    Text("Make room for the everyday.", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
}
