package com.whiplash.music.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType

/**
 * Modern, fully opaque search text field. Unlike other Glass* surfaces,
 * a search bar sits directly above scrolling list content, so translucency
 * here reads as a bug (content bleeding through) rather than a premium
 * "glass" effect — this uses a solid tonal surface instead.
 */
@Composable
fun GlassSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
    onSearchAction: () -> Unit = {},
) {
    val shape = RoundedCornerShape(WhiplashRadius.pill)

    Box(
        modifier = modifier
            .clip(shape)
            .background(WhiplashColors.surfaceSheet)
            .border(GlassTokens.borderWidth, WhiplashColors.glassBorder, shape)
            .padding(horizontal = GlassTokens.spaceLg, vertical = GlassTokens.spaceMd),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (query.isEmpty()) {
            Text(text = placeholder, style = WhiplashTypography.bodyLarge, color = WhiplashColors.textSecondary)
        }
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            textStyle = LocalTextStyle.current.copy(
                color = WhiplashColors.textPrimary,
                fontSize = WhiplashTypography.bodyLarge.fontSize,
            ),
            singleLine = true,
            cursorBrush = SolidColor(WhiplashColors.accent),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Search,
            ),
            keyboardActions = KeyboardActions(
                onSearch = { onSearchAction() },
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
