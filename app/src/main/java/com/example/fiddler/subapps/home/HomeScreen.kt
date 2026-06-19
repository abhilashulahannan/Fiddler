package com.example.fiddler.subapps.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fiddler.R
import com.example.fiddler.core.SubAppState

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val fontHead = FontFamily(Font(R.font.font_head))
    val fontHandwriting = FontFamily(Font(R.font.font_handwriting))
    val fontBody = FontFamily(Font(R.font.font_body))

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
            fontSize = 72.sp,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = "Fiddler is a super app for customizing your phone and adding unique features.",
            fontFamily = fontHandwriting,
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 8.dp)
        )

        Spacer(modifier = Modifier.height(64.dp))

        Text(
            text = "Enabled Sub-apps:",
            fontFamily = fontBody,
            fontSize = 28.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Simple checkboxes with no special behaviour
        listOf(
            "Net Speed Indicator" to SubAppState.ntspdEnabled,
            "Ringtones"           to SubAppState.rngtnsEnabled,
            "Secure Groups"       to SubAppState.secgrpEnabled,
        ).forEach { (label, state) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 2.dp)
            ) {
                Checkbox(
                    checked = state.value,
                    onCheckedChange = { state.value = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary
                    )
                )
                Text(
                    text = label,
                    fontFamily = fontHandwriting,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        // Fidland checkbox — runs the same full enable/disable logic as FidlandScreen
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 2.dp)
        ) {
            Checkbox(
                checked = SubAppState.fidlandEnabled.value,
                onCheckedChange = { SubAppState.setFidlandEnabled(context, it) },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary
                )
            )
            Text(
                text = "Fidland",
                fontFamily = fontHandwriting,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(64.dp))
    }
}