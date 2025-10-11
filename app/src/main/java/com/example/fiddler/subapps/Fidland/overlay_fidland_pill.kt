import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.pager.*
import androidx.compose.ui.draw.clip

@Composable
fun PillLayout(
    showUpload: Boolean = false,
    uploadText: String = "",
    showDownload: Boolean = false,
    downloadText: String = "",
    leftIconsVisibility: List<Boolean> = listOf(false, false, false, false),
    rightIconsVisibility: List<Boolean> = listOf(false, false, false, false, false),
    expandedPhase: Int? = null // null = collapsed
) {
    Box(
        modifier = Modifier
            .wrapContentSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(2.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.wrapContentSize()
        ) {
            // Left segment
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.wrapContentSize()
            ) {
                if (showUpload) {
                    Text(
                        text = uploadText,
                        fontSize = 9.sp,
                        color = Color.White
                    )
                }
                if (showDownload) {
                    Text(
                        text = downloadText,
                        fontSize = 9.sp,
                        color = Color.White
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    leftIconsVisibility.forEach { visible ->
                        if (visible) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(Color.Black, shape = CircleShape)
                            )
                        }
                    }
                }
            }

            // Center camera mask
            Box(
                modifier = Modifier
                    .size(25.dp)
                    .padding(horizontal = 4.dp)
                    .background(Color.Gray, shape = CircleShape)
            )

            // Right segment
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                rightIconsVisibility.forEach { visible ->
                    if (visible) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(Color.Black, shape = CircleShape)
                        )
                    }
                }
            }
        }

        // Expanded Phase 4 container
        expandedPhase?.let {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            ) {
                val pagerState = rememberPagerState()
                HorizontalPager(
                    count = 1, // replace with your actual page count
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth().height(200.dp) // adjust height
                ) { page ->
                    // Content for each page
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.LightGray),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Page $page")
                    }
                }
            }
        }
    }
}
