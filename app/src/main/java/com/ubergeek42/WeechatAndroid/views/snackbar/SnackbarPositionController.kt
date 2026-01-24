package com.ubergeek42.WeechatAndroid.views.snackbar

import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.view.ViewTreeObserver
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.marginBottom
import androidx.core.view.marginTop
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.ubergeek42.WeechatAndroid.R
import com.ubergeek42.WeechatAndroid.fragments.BufferFragment
import com.ubergeek42.WeechatAndroid.upload.f
import com.ubergeek42.WeechatAndroid.utils.applicationContext
import com.ubergeek42.WeechatAndroid.utils.ulet


/**
 * This controls the position of the snackbar in the chat activities.
 * As buffers change, the height of the bottom bar may change, or it may completely disappear.
 * This moves the snackbar up and down to be above the bottom bar and the bottom gesture area,
 * animating the movement on pager changes.
 *
 * To achieve this, we overtake both the anchoring and inset-observing logic,
 * as you can't reliably reposition the snackbar by changing its anchor.
 *
 * We reposition the snackbar by changing its margins.
 * While it's possible to simply translate it,
 * `ViewDragHelper` seem to be ignoring translation when capturing a view for dragging,
 * and the snackbar can't be dragged or flung away.
 *
 * TODO use weak reference for the snackbar?
 * TODO perhaps track any scheduled snackbars?
 */
class SnackbarPositionController {
    private var snackbar: Snackbar? = null
    private var anchor: View? = null
    private var windowInsets: WindowInsetsCompat = WindowInsetsCompat.CONSUMED

    private var animateOnNextLayout = false

    fun setSnackbar(snackbar: Snackbar) {
        this.snackbar = snackbar
        animateOnNextLayout = false
        ViewCompat.setOnApplyWindowInsetsListener(snackbar.view) { _, windowInsets ->
            this@SnackbarPositionController.windowInsets = windowInsets
            recalculateAndUpdateMargins(animate = false)
            windowInsets
        }
        snackbar.view.requestApplyInsets()
    }

    fun setAnchor(view: View?) {
        if (anchor == view) return

        anchor?.viewTreeObserver?.removeOnGlobalLayoutListener(onGlobalLayoutListener)
        view?.viewTreeObserver?.addOnGlobalLayoutListener(onGlobalLayoutListener)
        anchor = view

        if (view == null || view.isLaidOut) {
            recalculateAndUpdateMargins(animate = true)
        } else {
            animateOnNextLayout = true
        }
    }

    // Note that this listener may be called a lot
    private val onGlobalLayoutListener =
        ViewTreeObserver.OnGlobalLayoutListener {
            recalculateAndUpdateMargins(animate = animateOnNextLayout)
            animateOnNextLayout = false
        }

    // Calling `doOnLayout` because:
    // In the specific case when there are no buffers open,
    // and a buffer gets open, a snackbar should move up, however
    // its `top` value is not changed until a few frames into animation,
    // which results in it briefly appearing way lower than it should be.
    private fun recalculateAndUpdateMargins(animate: Boolean) = ulet (snackbar) { snackbar ->
        if (anchor?.isLaidOut == false) return@ulet
        val oldMarginBottom = snackbar.view.marginBottom
        snackbar.recalculateAndUpdateMargins(windowInsets, anchor)
        if (animate) {
            val newMarginBottom = snackbar.view.marginBottom
            if (oldMarginBottom != newMarginBottom) {
                snackbar.view.doOnLayout {
                    it.translationY = newMarginBottom - oldMarginBottom.f
                    it.animate().translationY(0f).setDuration(shortAnimTime).start()
                }
            }
        }
    }
}


/**
 * When there's a change in the pager,
 * we might not have a buffer fragment that has its view ready,
 * or in fact we might not have it at all.
 * This schedules setting of the anchor at the time when it's available.
 *
 * Note that the anchor view may be not yet laid out at the time of setting it.
 */
fun SnackbarPositionController.setOrScheduleSettingAnchorAfterPagerChange(
    currentBufferPointer: Long,
    currentBufferFragment: BufferFragment?,
    fragmentManager: FragmentManager,
) {
    if (currentBufferPointer == 0L) {
        setAnchor(null)
    } else {
        if (currentBufferFragment != null && currentBufferFragment.isVisible) {
            setAnchor(currentBufferFragment.requireView().findViewById(R.id.bottom_bar))
        } else {
            fragmentManager.registerFragmentLifecycleCallbacks(object : FragmentLifecycleCallbacks() {
                override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
                    if (f is BufferFragment && f.pointer == currentBufferPointer) {
                        setAnchor(f.requireView().findViewById(R.id.bottom_bar))
                        fragmentManager.unregisterFragmentLifecycleCallbacks(this)
                    }
                }
            }, false)
        }
    }
}


private val shortAnimTime = applicationContext
        .resources.getInteger(android.R.integer.config_shortAnimTime).toLong()


/**
 * In [BaseTransientBottomBar], the inset listener stores the bottom system inset
 * as `extraBottomMarginWindowInset` that it gets from [WindowInsetsCompat.getSystemWindowInsetBottom]
 * and then unconditionally adds it to the original bottom margin.
 * That method is deprecated and may or may not include IME insets.
 * It is simply buggy as it's unreliable,
 * and it doesn't allow us to skip applying the intrinsic snackbar bottom margin in some cases.
 *
 * This overrides the original behavior.
 *
 * Note that the original method is still called via `BaseTransientBottomBar`'s `onAttachedToWindow`.
 */
fun Snackbar.reactToInsetChangesProperly() {
    ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
        recalculateAndUpdateMargins(windowInsets, null)
        windowInsets
    }
}


/**
 * When calculating margins, for simplicity we assume
 * that it has the same intrinsic margins on all sides.
 * The default implementation records all of the original margins.
 * The intrinsic margin is added to the extra bottom when the snackbar:
 *   * is sitting on top of an anchor view, or
 *   * is sitting on the very bottom of the screen;
 * it is not added when it:
 *   * is sitting on top of the bottom gesture area or the navigation buttons.
 */
private fun Snackbar.recalculateAndUpdateMargins(windowInsets: WindowInsetsCompat, anchor: View?) {
    val snackbarView = view
    val snackbarViewParent = snackbarView.parent

    if (!isShown || snackbarViewParent !is View) return

    val originalSnackbarViewMargin = snackbarView.marginTop

    val systemBarInsets = windowInsets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars())
    val systemGestureInsets = windowInsets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.mandatorySystemGestures())
    val imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime())

    val leftRightInsets = Insets.max(systemBarInsets, imeInsets)

    val leftMargin = leftRightInsets.left + originalSnackbarViewMargin
    val rightMargin = leftRightInsets.right + originalSnackbarViewMargin

    val bottomMargin = when {
        anchor != null -> snackbarViewParent.height - anchor.top + originalSnackbarViewMargin
        imeInsets.bottom > 0 -> imeInsets.bottom + originalSnackbarViewMargin
        systemBarInsets.bottom > 0 -> systemBarInsets.bottom
        else -> originalSnackbarViewMargin
    }

    val adjustedBottomMargin = maxOf(bottomMargin, systemGestureInsets.bottom)

    snackbarView.updateMarginsIfChanged(leftMargin, rightMargin, adjustedBottomMargin)
}


private fun View.updateMarginsIfChanged(leftMargin: Int, rightMargin: Int, bottomMargin: Int) {
    val layoutParams = layoutParams as MarginLayoutParams

    val oldLeftMargin = layoutParams.leftMargin
    val oldRightMargin = layoutParams.rightMargin
    val oldBottomMargin = layoutParams.bottomMargin

    val marginsChanged = oldLeftMargin != leftMargin
            || oldRightMargin != rightMargin
            || oldBottomMargin != bottomMargin

    if (marginsChanged) {
        layoutParams.leftMargin = leftMargin
        layoutParams.rightMargin = rightMargin
        layoutParams.bottomMargin = bottomMargin

        requestLayout()
    }
}
