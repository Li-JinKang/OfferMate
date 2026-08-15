package com.jk.offermate.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** 基于 Material3 默认排版，微调标题字重以贴近设计稿。 */
val OfferMateTypography = Typography().run {
    copy(
        titleMedium = titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 17.sp),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.SemiBold),
        bodyMedium = bodyMedium.copy(fontSize = 14.sp),
        labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    )
}
