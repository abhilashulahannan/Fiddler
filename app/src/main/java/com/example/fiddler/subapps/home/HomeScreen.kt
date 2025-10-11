package com.example.fiddler.subapps.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fiddler.R

@Composable
fun HomeScreen() {
    val fontHead = FontFamily(Font(R.font.font_head))
    val fontBody = FontFamily(Font(R.font.font_body))
    val fontHandwriting = FontFamily(Font(R.font.font_handwriting))

    var ntspdChecked by remember { mutableStateOf(true) }
    var rngtnsChecked by remember { mutableStateOf(true) }
    var fidlndChecked by remember { mutableStateOf(false) }
    var secgrpChecked by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        Spacer(modifier = Modifier.height(60.dp))

        Text(
            text = "Fiddler",
            fontFamily = fontHead,
            fontSize = 72.sp
        )

        Text(
            text = "Fiddler is a super app for customizing your phone and adding unique features.",
            fontFamily = fontHandwriting,
            fontSize = 20.sp,
            modifier = Modifier.padding(top = 8.dp)
        )

        Spacer(modifier = Modifier.height(64.dp))

        Text(
            text = "Enabled Sub-apps:",
            fontFamily = fontBody,
            fontSize = 28.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = ntspdChecked,
                onCheckedChange = { ntspdChecked = it }
            )
            Text(
                text = "Net Speed Indicator",
                fontFamily = fontHandwriting,
                fontSize = 20.sp,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = rngtnsChecked,
                onCheckedChange = { rngtnsChecked = it }
            )
            Text(
                text = "Ringtones",
                fontFamily = fontHandwriting,
                fontSize = 20.sp,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = fidlndChecked,
                onCheckedChange = { fidlndChecked = it }
            )
            Text(
                text = "Fidland",
                fontFamily = fontHandwriting,
                fontSize = 20.sp,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = secgrpChecked,
                onCheckedChange = { secgrpChecked = it }
            )
            Text(
                text = "Secure Groups",
                fontFamily = fontHandwriting,
                fontSize = 20.sp,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(64.dp))
    }
}
