package com.example.fiddler.subapps.SecGrp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.LocalContext
import com.example.fiddler.R

@Composable
fun SecGrpScreen() {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        Spacer(modifier = Modifier.height(60.dp))

        // Title
        Text(
            text = "Secure Group",
            fontSize = 54.sp,
            fontFamily = FontFamily.Default, // Replace with your font resource
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // About description
        Text(
            text = "Create active group modes, to limit visibility of contents like photo and videos, when the access is restricted by the device is given to others.",
            fontSize = 20.sp,
            fontFamily = FontFamily.Cursive, // Replace with your handwriting font
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Section Header
        Text(
            text = "Groups",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Default,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Section description
        Text(
            text = "Currently configured groups",
            fontSize = 20.sp,
            fontFamily = FontFamily.Cursive,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(60.dp))
    }
}
