package com.alignfit.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─── Shared UI building blocks ────────────────────────────────────────────────
// One visual language for every screen: big bold titles, flat gray panels,
// black primary actions, hairline borders.

internal val ScreenPadding = 20.dp

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Ink,
            contentColor = Paper,
            disabledContainerColor = Hairline,
            disabledContentColor = InkFaint
        )
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Hairline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

// Tab-root header: large bold title, optional trailing action (icon).
@Composable
fun PageHeader(title: String, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ScreenPadding, end = 8.dp, top = 28.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Ink,
            lineHeight = 36.sp,
            modifier = Modifier.weight(1f)
        )
        action?.invoke()
    }
}

// Sub-page header: back arrow + title, optional segmented progress (step of total).
@Composable
fun SubPageHeader(
    title: String,
    onBack: () -> Unit,
    step: Int? = null,
    totalSteps: Int = 3
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = ScreenPadding, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로", tint = Ink)
            }
            if (step != null) {
                Spacer(Modifier.width(8.dp))
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    repeat(totalSteps) { i ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (i < step) Ink else Hairline)
                        )
                    }
                }
            }
        }
        Text(
            title,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Ink,
            lineHeight = 32.sp,
            modifier = Modifier.padding(start = ScreenPadding, end = ScreenPadding, top = 12.dp, bottom = 16.dp)
        )
    }
}

// Flat gray panel used instead of elevated cards.
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Tint)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(20.dp),
        content = content
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = InkSoft,
        modifier = modifier.padding(bottom = 10.dp)
    )
}

// Toggleable pill: black when selected, hairline outline otherwise.
@Composable
fun SelectPill(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = if (selected) Paper else Ink,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Ink else Paper)
            .border(1.dp, if (selected) Ink else Hairline, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

// Small static tag.
@Composable
fun Tag(text: String, inverted: Boolean = false) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (inverted) Paper else Ink,
        modifier = Modifier
            .background(if (inverted) Ink else Paper, RoundedCornerShape(50))
            .border(1.dp, if (inverted) Ink else Hairline, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

// Quiet note box for cautions / disclaimers.
@Composable
fun NoteBox(text: String, modifier: Modifier = Modifier, title: String? = null) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Hairline, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        if (title != null) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ink)
            Spacer(Modifier.height(4.dp))
        }
        Text(text, fontSize = 12.sp, lineHeight = 18.sp, color = InkSoft)
    }
}

// Label / value row with a hairline under it.
@Composable
fun KeyValueRow(label: String, value: String, showDivider: Boolean = true) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 14.sp, color = InkSoft)
            Spacer(Modifier.width(12.dp))
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        }
        if (showDivider) Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
    }
}

// Pinned bottom action area above the navigation bar.
@Composable
fun BottomActions(content: @Composable RowScope.() -> Unit) {
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Paper)
                .padding(horizontal = ScreenPadding, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}
