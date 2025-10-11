import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.fiddler.ui.theme.White

@Composable
fun WhiteDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = White,
        text = { content() },
        confirmButton = {} // empty for now, optional
    )
}
