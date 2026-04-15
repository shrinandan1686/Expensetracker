package com.trackit.expense.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dagger.hilt.android.EntryPointAccessors
import java.text.NumberFormat
import java.util.Locale

/**
 * TrackIt home screen widget.
 *
 * ## Size variants
 * | Alias       | Cells | Min size   | Layout                           |
 * |-------------|-------|------------|----------------------------------|
 * | [SMALL_BOX] | 2 × 1 | 110 × 40dp | Spent / Budget, colour-coded     |
 * | [MEDIUM_BOX]| 4 × 2 | 250 × 110dp| Header + progress + 3 expenses   |
 *
 * ## Data access
 * Glance widgets cannot receive Hilt constructor injection — they are instantiated
 * by the system.  [WidgetEntryPoint] + [EntryPointAccessors] reach the Hilt graph.
 */
class TrackItWidget : GlanceAppWidget() {

    companion object {
        val SMALL_BOX  = DpSize(110.dp, 40.dp)
        val MEDIUM_BOX = DpSize(250.dp, 110.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(SMALL_BOX, MEDIUM_BOX))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication<WidgetEntryPoint>(context)
        val data = try {
            entryPoint.widgetDataRepository().getWidgetData()
        } catch (e: Exception) {
            WidgetData("", "", 0.0, 0.0, emptyList())
        }

        provideContent {
            GlanceTheme {
                if (LocalSize.current.width >= MEDIUM_BOX.width) {
                    MediumWidget(data)
                } else {
                    SmallWidget(data)
                }
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Small  (2 × 1)
    // ──────────────────────────────────────────────────────────────────────────

    @Composable
    private fun SmallWidget(data: WidgetData) {
        val fmt        = NumberFormat.getNumberInstance(Locale("en", "IN"))
        val spendColor = spendColor(data.budgetProgress)
        val ctx        = LocalContext.current

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(BG))
                .cornerRadius(16.dp)
                .clickable(
                    actionStartActivity(
                        Intent(ctx, com.trackit.expense.MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                    )
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val budgetPart = if (data.budget > 0) " / ₹${fmt.format(data.budget)}" else ""
                Text(
                    text  = "₹${fmt.format(data.totalSpent)}$budgetPart",
                    style = TextStyle(
                        color      = ColorProvider(spendColor),
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                if (data.monthLabel.isNotBlank()) {
                    Spacer(GlanceModifier.height(2.dp))
                    Text(
                        text  = data.monthLabel,
                        style = TextStyle(color = ColorProvider(MUTED), fontSize = 10.sp)
                    )
                }
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Medium  (4 × 2)
    // ──────────────────────────────────────────────────────────────────────────

    @Composable
    private fun MediumWidget(data: WidgetData) {
        val fmt        = NumberFormat.getNumberInstance(Locale("en", "IN"))
        val spendColor = spendColor(data.budgetProgress)

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(BG))
                .cornerRadius(16.dp)
                .padding(12.dp)
        ) {
            // ── Header ───────────────────────────────────────────────────────
            Row(
                modifier          = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text  = data.monthLabel,
                        style = TextStyle(color = ColorProvider(MUTED), fontSize = 11.sp)
                    )
                    Text(
                        text  = "₹${fmt.format(data.totalSpent)}",
                        style = TextStyle(
                            color      = ColorProvider(spendColor),
                            fontSize   = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                // "+" → AddExpense deep link
                Box(
                    modifier         = GlanceModifier
                        .size(36.dp)
                        .background(ColorProvider(PRIMARY))
                        .cornerRadius(18.dp)
                        .clickable(
                            actionStartActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("trackit://add_expense")).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                                }
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text  = "+",
                        style = TextStyle(
                            color      = ColorProvider(Color.White),
                            fontSize   = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Spacer(GlanceModifier.height(6.dp))

            // ── Budget progress bar ──────────────────────────────────────────
            if (data.budget > 0) {
                LinearProgressIndicator(
                    progress        = data.budgetProgress,
                    modifier        = GlanceModifier.fillMaxWidth().height(5.dp),
                    color           = ColorProvider(spendColor),
                    backgroundColor = ColorProvider(SURFACE_VARIANT)
                )
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    text  = "Budget: ₹${fmt.format(data.budget)}",
                    style = TextStyle(color = ColorProvider(MUTED), fontSize = 9.sp)
                )
                Spacer(GlanceModifier.height(8.dp))
            } else {
                Spacer(GlanceModifier.height(4.dp))
            }

            // ── Last 3 expenses ───────────────────────────────────────────────
            if (data.recentExpenses.isEmpty()) {
                Text(
                    text  = "No expenses yet this month",
                    style = TextStyle(color = ColorProvider(MUTED), fontSize = 11.sp)
                )
            } else {
                data.recentExpenses.forEach { expense ->
                    ExpenseRow(expense, fmt)
                    Spacer(GlanceModifier.height(4.dp))
                }
            }
        }
    }

    @Composable
    private fun ExpenseRow(expense: RecentExpenseItem, fmt: NumberFormat) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .clickable(
                    actionStartActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("trackit://expense_detail/${expense.id}")
                        ).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                    )
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text     = expense.merchant,
                modifier = GlanceModifier.defaultWeight(),
                style    = TextStyle(color = ColorProvider(ON_BG), fontSize = 12.sp),
                maxLines = 1
            )
            Text(
                text  = "₹${fmt.format(expense.amount)}",
                style = TextStyle(
                    color      = ColorProvider(PRIMARY),
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Colour palette (hardcoded — cannot use @Composable theme values here)
    // ──────────────────────────────────────────────────────────────────────────

    private val BG             = Color(0xFF0D0D1A)   // DarkBackground
    private val SURFACE_VARIANT = Color(0xFF252540)  // DarkSurfaceVariant
    private val PRIMARY        = Color(0xFF6750A4)   // TrackItPrimary
    private val ON_BG          = Color(0xFFE8E8FF)   // DarkOnSurface
    private val MUTED          = Color(0xFFADADD4)   // DarkOnSurfaceVariant

    private fun spendColor(progress: Float): Color = when {
        progress < 0.5f -> Color(0xFF4CAF50)   // BudgetSuccess  green
        progress < 0.8f -> Color(0xFFFFC107)   // BudgetWarning  amber
        else            -> Color(0xFFF44336)   // BudgetError    red
    }
}
