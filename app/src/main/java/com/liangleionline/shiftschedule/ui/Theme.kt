package com.liangleionline.shiftschedule.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

object Palette {
    val bg = Color.rgb(237, 241, 247)
    val card = Color.WHITE
    val ink = Color.rgb(15, 23, 42)
    val sub = Color.rgb(100, 116, 139)
    val faint = Color.rgb(148, 163, 184)
    val line = Color.rgb(226, 232, 240)
    val soft = Color.rgb(241, 245, 249)
    val primary = Color.rgb(37, 99, 235)
    val primaryDeep = Color.rgb(79, 70, 229)
    val primarySoft = Color.rgb(219, 234, 254)
    val green = Color.rgb(22, 163, 74)
    val greenSoft = Color.rgb(220, 252, 231)
    val orange = Color.rgb(234, 88, 12)
    val orangeSoft = Color.rgb(255, 237, 213)
}

fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
fun dpF(context: Context, value: Float): Float = value * context.resources.displayMetrics.density

fun solid(fill: Int, radiusPx: Float, stroke: Int? = null, strokeWidthPx: Int = 1): GradientDrawable =
    GradientDrawable().apply {
        cornerRadius = radiusPx
        setColor(fill)
        stroke?.let { setStroke(strokeWidthPx.coerceAtLeast(1), it) }
    }

fun gradientBg(colors: IntArray, radiusPx: Float, orientation: GradientDrawable.Orientation = GradientDrawable.Orientation.TL_BR): GradientDrawable =
    GradientDrawable(orientation, colors).apply { cornerRadius = radiusPx }

fun rippleable(context: Context, fill: Int, radiusDp: Float = 16f, stroke: Int? = null): RippleDrawable {
    val r = dpF(context, radiusDp)
    val content = solid(fill, r, stroke)
    val mask = solid(Color.rgb(203, 213, 225), r)
    return RippleDrawable(ColorStateList.valueOf(Color.argb(36, 30, 41, 59)), content, mask)
}

fun pill(
    context: Context,
    text: String,
    fill: Int = Palette.primary,
    textColor: Int = Color.WHITE,
    stroke: Int? = null,
    textSize: Float = 14.5f,
    bold: Boolean = true,
    onClick: ((View) -> Unit)? = null
): TextView = TextView(context).apply {
    this.text = text
    gravity = Gravity.CENTER
    this.textSize = textSize
    typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    setTextColor(textColor)
    setPadding(dp(context, 14), dp(context, 11), dp(context, 14), dp(context, 11))
    background = rippleable(context, fill, 15f, stroke)
    if (onClick != null) {
        isClickable = true
        setOnClickListener(onClick)
    }
}

/**
 * 纯黑不透明状态栏：退出 edge-to-edge，让系统用黑色填充状态栏区域，
 * 图标/文字强制为浅色（白色），在任何页面配色下都清晰可读。
 */
fun AppCompatActivity.applyOpaqueStatusBar() {
    WindowCompat.setDecorFitsSystemWindows(window, true)
    window.statusBarColor = Color.BLACK
    WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
}
