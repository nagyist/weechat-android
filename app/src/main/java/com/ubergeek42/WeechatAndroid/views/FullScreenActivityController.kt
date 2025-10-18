package com.ubergeek42.WeechatAndroid.views

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.TypedValue
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils.setAlphaComponent
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.ubergeek42.WeechatAndroid.R


// Using `getInsetsIgnoringVisibility` for system bars as those can be temporarily hidden at times,
// particularly when clicking on the paperclip button and getting the “Complete action using” dialog.
fun View.onSystemBarsInsetsChanged(listener: (insets: androidx.core.graphics.Insets) -> Unit) {
    var oldInsets = androidx.core.graphics.Insets.NONE

    ViewCompat.setOnApplyWindowInsetsListener(this) { _, windowInsets ->
        val newInsets = windowInsets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars())

        if (oldInsets != newInsets) {
            listener(newInsets)
            oldInsets = newInsets
        }

        windowInsets
    }
}

// IME insets can't be queried ignoring visibility, yielding “Unable to query the maximum insets for IME”.
fun View.onSystemBarsAndImeInsetsChanged(listener: (insets: androidx.core.graphics.Insets) -> Unit) {
    var oldInsets = androidx.core.graphics.Insets.NONE

    ViewCompat.setOnApplyWindowInsetsListener(this) { _, windowInsets ->
        val newInsets = androidx.core.graphics.Insets.max(
            windowInsets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars()),
            windowInsets.getInsets(WindowInsetsCompat.Type.ime()),
        )

        if (oldInsets != newInsets) {
            listener(newInsets)
            oldInsets = newInsets
        }

        windowInsets
    }
}


fun Context.getActionBarHeight(): Int {
    val typedValue = TypedValue()
    return if (theme.resolveAttribute(android.R.attr.actionBarSize, typedValue, true)) {
        TypedValue.complexToDimensionPixelSize(typedValue.data, resources.displayMetrics)
    } else {
        0
    }
}


// API ≥ 35:
//   Setting a solid navigation bar color will *only* apply it to the buttoned navigation,
//   resulting in semi-transparent scrim with system-default transparency of 80%.
//   Gesture hint scrim is *not* affected by it.
//
// API < 35:
//   The color is set as is for every navigation mode.
@Suppress("DEPRECATION") // Works for now
fun Activity.applyNavigationBarScrim(@ColorInt opaqueColor: Int) {
    if (Build.VERSION.SDK_INT >= 35) {
        window.navigationBarColor = opaqueColor
    } else {
        window.navigationBarColor = setAlphaComponent(opaqueColor, 0xCC) // 80%
    }
}
