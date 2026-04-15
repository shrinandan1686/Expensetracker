package com.trackit.expense.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─────────────────────────────────────────────────────────────────────────────
// Preview colours — mirrors widget hardcoded palette
// ─────────────────────────────────────────────────────────────────────────────

private val BgColor        = Color(0xFF1A1A2E)
private val PrimaryColor   = Color(0xFF6750A4)
private val OnBgPrimary    = Color(0xFFE8E8FF)
private val OnBgSecondary  = Color(0xFFADADD4)
private val SuccessGreen   = Color(0xFF4CAF50)
private val WarningAmber   = Color(0xFFFFC107)

// ─────────────────────────────────────────────────────────────────────────────
// Small widget preview (2 × 1)
// ─────────────────────────────────────────────────────────────────────────────

@Preview(name = "TrackIt Widget – Small (2×1)", widthDp = 180, heightDp = 60)
@Composable
fun TrackItWidgetSmallPreview() {
    Box(
        modifier          = Modifier
            .fillMaxSize()
            .background(BgColor, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment  = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text       = "₹12,450 / ₹20,000",
                color      = SuccessGreen,
                fontSize   = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text     = "April 2026",
                color    = OnBgSecondary,
                fontSize = 10.sp
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Medium widget preview (4 × 2)
// ─────────────────────────────────────────────────────────────────────────────

@Preview(name = "TrackIt Widget – Medium (4×2)", widthDp = 280, heightDp = 140)
@Composable
fun TrackItWidgetMediumPreview() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor, RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        // Header
        Row(
            modifier          = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "April 2026", color = OnBgSecondary, fontSize = 11.sp)
                Text(
                    text       = "₹12,450",
                    color      = SuccessGreen,
                    fontSize   = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            // + button
            Box(
                modifier         = Modifier
                    .size(36.dp)
                    .background(PrimaryColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "+", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(6.dp))

        // Budget progress
        LinearProgressIndicator(
            progress          = { 0.62f },
            modifier          = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp)),
            color             = SuccessGreen,
            trackColor        = Color(0xFF252540)
        )
        Spacer(Modifier.height(2.dp))
        Text(text = "Budget: ₹20,000", color = OnBgSecondary, fontSize = 9.sp)

        Spacer(Modifier.height(8.dp))

        // Recent expenses
        listOf(
            "Swiggy" to "₹450",
            "Amazon" to "₹1,299",
            "Zomato" to "₹320"
        ).forEach { (merchant, amount) ->
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text     = merchant,
                    modifier = Modifier.weight(1f),
                    color    = OnBgPrimary,
                    fontSize = 12.sp
                )
                Text(
                    text       = amount,
                    color      = PrimaryColor,
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
