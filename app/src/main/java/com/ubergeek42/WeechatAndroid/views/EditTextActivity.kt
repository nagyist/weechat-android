package com.ubergeek42.WeechatAndroid.views

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.updatePadding
import com.ubergeek42.WeechatAndroid.databinding.EditTextActivityBinding
import com.ubergeek42.WeechatAndroid.utils.ThemeFix.fixLightStatusAndNavigationBar


// To better mimic dialogs and to use the code that worked well before, here we:
//   * Set a window flag that effectively positions the window on top of the input method.
//     This makes the keyboard, if open, stay open in the previous window.
//   * Ignore IME insets for the purpose of the UI.
//     As the keyboard is open, even if is not shown in this window, we get its insets here.
//
// As determined with emulator testing, the above only reliably works starting with API 33.
// On earlier APIs, rely on `android:windowSoftInputMode="stateAlwaysHidden"` in manifest
// to prevent the keyboard from appearing when the activity is first opened,
// and on `TextView.showSoftInputOnFocus` to prevent it from showing when the text is tapped.

// TextView does not support `setClipToPadding`,
// so we just don't draw it in the navigation area at all.
abstract class EditTextActivity(val allowSoftKeyboard: Boolean) : AppCompatActivity()  {
    lateinit var ui: EditTextActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        fixLightStatusAndNavigationBar(this)
        makeSystemBarsTransparent()

        ui = EditTextActivityBinding.inflate(layoutInflater)
        setContentView(ui.root)

        if (!allowSoftKeyboard) {
            if (Build.VERSION.SDK_INT >= 33) {
                window.setFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
                                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
            }

            ui.text.showSoftInputOnFocus = false
        }

        ui.layout.apply {
            val onInsetsChanged = if (allowSoftKeyboard)
                ::onSystemBarsAndImeInsetsChanged else ::onSystemBarsInsetsChanged
            onInsetsChanged { insets ->
                updatePadding(left = insets.left, top = insets.top, right = insets.right, bottom = insets.bottom)
            }
        }

        ui.toolbar.setNavigationOnClickListener { finish() }
    }
}