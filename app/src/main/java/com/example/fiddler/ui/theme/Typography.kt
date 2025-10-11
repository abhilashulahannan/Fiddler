package com.example.fiddler.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.example.fiddler.R
import com.example.fiddler.ui.theme.Black



// Font families
// Use FontFamily.Default temporarily if you don't have font files
val HeadFont: FontFamily = try {
    FontFamily(Font(R.font.head, weight = FontWeight.Bold))
} catch (e: Exception) {
    FontFamily.Default
}

val TitleFont: FontFamily = try {
    FontFamily(Font(R.font.title, weight = FontWeight.SemiBold))
} catch (e: Exception) {
    FontFamily.Default
}

val BodyFont: FontFamily = try {
    FontFamily(Font(R.font.body, weight = FontWeight.Normal))
} catch (e: Exception) {
    FontFamily.Default
}

// Typography object
val FiddlerTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = HeadFont,
        fontSize = 54.sp,
        color = Black
    ),
    titleLarge = TextStyle(
        fontFamily = TitleFont,
        fontSize = 28.sp,
        color = Black
    ),
    titleMedium = TextStyle(
        fontFamily = TitleFont,
        fontSize = 22.sp,
        color = Black
    ),
    bodyLarge = TextStyle(
        fontFamily = BodyFont,
        fontSize = 20.sp,
        color = Black
    ),
    bodyMedium = TextStyle(
        fontFamily = BodyFont,
        fontSize = 16.sp,
        color = Black
    ),
    labelLarge = TextStyle(
        fontFamily = BodyFont,
        fontSize = 15.sp,
        color = Black
    )
)
