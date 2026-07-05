package com.example.cpreminder

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.min

/**
 * Circular countdown showing how much of the day is left (out of 24h).
 *
 * Renders a background ring plus a foreground arc that shrinks as timeLeft
 * decreases, with a configurable icon at the center. The arc, background
 * ring, and icon all use the same [accentColor]; the icon tints via
 * [setImageResource] + a tint that's applied here for convenience.
 */
class DayCountdownView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val foregroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val centerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val arcRect = RectF()

    /** Total span of the countdown in seconds (defaults to 24h). */
    var totalSeconds: Long = 24L * 3_600
        set(value) {
            field = value.coerceAtLeast(1L)
            invalidate()
        }

    /** Remaining time in seconds, in [0, totalSeconds]. */
    var remainingSeconds: Long = totalSeconds
        set(value) {
            field = value.coerceIn(0L, totalSeconds)
            invalidate()
        }

    /** Color of the arc and center icon. The background ring is a faded version of this. */
    var accentColor: Int = ContextCompat.getColor(context, R.color.streak_safe)
        set(value) {
            field = value
            backgroundPaint.color = applyAlpha(value, BACKGROUND_ALPHA)
            foregroundPaint.color = value
            centerTextPaint.color = value
            invalidate()
        }

    /** Optional text drawn centered inside the ring (e.g. "1/1"). Empty/null hides it. */
    var centerText: String? = null
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    init {
        backgroundPaint.color = applyAlpha(accentColor, BACKGROUND_ALPHA)
        foregroundPaint.color = accentColor
        centerTextPaint.color = accentColor
        // Ring thickness is ~7% of the smaller dimension, clamped to a sane range.
        val initialStroke = (min(120, 96) * STROKE_FRACTION).coerceIn(6f, 18f)
        backgroundPaint.strokeWidth = initialStroke
        foregroundPaint.strokeWidth = initialStroke
        centerTextPaint.textSize = (min(160, 96) * 0.18f).coerceIn(20f, 36f)
        centerTextPaint.isFakeBoldText = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val stroke = (min(w, h) * STROKE_FRACTION).coerceIn(6f, 18f)
        backgroundPaint.strokeWidth = stroke
        foregroundPaint.strokeWidth = stroke
        centerTextPaint.textSize = (min(w, h) * 0.18f).coerceIn(20f, 36f)
        centerTextPaint.isFakeBoldText = true
        // Inset the arc by half the stroke so the stroke doesn't get clipped at the edges.
        val inset = stroke / 2f
        arcRect.set(inset, inset, w - inset, h - inset)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawArc(arcRect, START_ANGLE, FULL_SWEEP, false, backgroundPaint)

        if (totalSeconds > 0L) {
            val sweep = FULL_SWEEP * (remainingSeconds.toFloat() / totalSeconds.toFloat())
            canvas.drawArc(arcRect, START_ANGLE, sweep, false, foregroundPaint)
        }

        val text = centerText
        if (!text.isNullOrEmpty()) {
            val x = arcRect.centerX()
            // Place the text BELOW the centered icon overlay: the View itself is below
            // the icon in z-order (FrameLayout draws children in order), so the only safe
            // spot is the lower portion of the ring, well below the icon's footprint.
            // We compensate by treating the fraction as the center of the glyph, not
            // the baseline, then derive the baseline via font metrics.
            val glyphCenterY = arcRect.top + arcRect.height() * CENTER_TEXT_VERTICAL_FRACTION
            val baselineY = glyphCenterY - (centerTextPaint.fontMetrics.ascent + centerTextPaint.fontMetrics.descent) / 2f
            canvas.drawText(text, x, baselineY, centerTextPaint)
        }
    }

    private fun applyAlpha(color: Int, alpha: Float): Int =
        (alpha * 255).toInt().coerceIn(0, 255).shl(24) or (color and 0x00FFFFFF)

    companion object {
        private const val START_ANGLE = -90f
        private const val FULL_SWEEP = 360f
        private const val STROKE_FRACTION = 0.07f
        private const val BACKGROUND_ALPHA = 0.25f
        private const val CENTER_TEXT_FRACTION = 0.10f
        // Center the text at ~75% down the ring's inner area — comfortably below the
        // centered icon overlay (which occupies the upper ~60% of the 160dp circle)
        // and well above the bottom of the ring.
        private const val CENTER_TEXT_VERTICAL_FRACTION = 0.75f
    }
}