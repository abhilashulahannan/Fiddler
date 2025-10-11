import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NetSpeedOverlay(
    uploadText: String,
    downloadText: String,
    uploadWeight: Float = 0.4f,
    downloadWeight: Float = 0.6f,
    uploadBold: Boolean = false,
    downloadBold: Boolean = false,
    uploadFontSize: Float = 10f,
    downloadFontSize: Float = 10f
) {
    Column(
        modifier = Modifier
            .background(Color.Transparent)
            .wrapContentWidth()
            .height(25.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Box(
            modifier = Modifier
                .weight(uploadWeight)
                .fillMaxWidth(),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Text(
                text = uploadText,
                color = Color.White,
                fontSize = uploadFontSize.sp,
                fontWeight = if (uploadBold) FontWeight.Bold else FontWeight.Normal
            )
        }

        Box(
            modifier = Modifier
                .weight(downloadWeight)
                .fillMaxWidth(),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Text(
                text = downloadText,
                color = Color.White,
                fontSize = downloadFontSize.sp,
                fontWeight = if (downloadBold) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
