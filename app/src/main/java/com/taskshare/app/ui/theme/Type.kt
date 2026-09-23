package com.taskshare.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * A refined pass over the M3 default type scale: firmer headline weight so screen titles read
 * as headlines rather than blending into body text, tightened letter spacing on large text
 * (default M3 spacing is tuned for small text and looks loose at headline size), and a touch
 * more spacing on labels (button/chip text) for a crisper, more deliberate feel.
 */
val TaskShareTypography = Typography().let { base ->
    base.copy(
        headlineSmall = base.headlineSmall.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.sp,
        ),
        titleLarge = base.titleLarge.copy(
            fontWeight = FontWeight.SemiBold,
        ),
        titleMedium = base.titleMedium.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.1.sp,
        ),
        titleSmall = base.titleSmall.copy(
            fontWeight = FontWeight.Medium,
        ),
        bodyLarge = base.bodyLarge.copy(
            letterSpacing = 0.2.sp,
        ),
        labelLarge = base.labelLarge.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.3.sp,
        ),
    )
}

/** Not part of the M3 scale — used for the small bold status badge on a task card ("Overdue", "Today"). */
val StatusBadge: TextStyle = TextStyle(
    fontWeight = FontWeight.Bold,
    fontSize = 12.sp,
    letterSpacing = 0.4.sp,
)
