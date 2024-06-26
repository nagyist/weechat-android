// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.

package com.ubergeek42.WeechatAndroid.views

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.UiThread
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView


enum class Animation {
    None,
    Default,
    LastLineAdded,
    NewLinesFetched,
}


class AnimatedRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : RecyclerView(context, attrs), OnJumpedUpWhileScrollingListener {

    private val manager = LinearLayoutManager(getContext(), VERTICAL, false)
            .apply { stackFromEnd = true }

    private val animator = CustomAdditionItemAnimator()

    init {
        layoutManager = manager
        itemAnimator = animator
        setHasFixedSize(true)
        clipToPadding = false
    }

    fun setAnimation(animation: Animation) {
        val itemAnimator = if (animation == Animation.None) null else animator
        if (this.itemAnimator != itemAnimator) this.itemAnimator = itemAnimator

        animator.animationProvider = when(animation) {
            Animation.None -> return
            Animation.Default -> DefaultAnimationProvider
            Animation.LastLineAdded -> SlidingFromBottomAnimationProvider
            Animation.NewLinesFetched -> FlickeringAnimationProvider
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    // this is needed because awakenScrollbars is protected
    @UiThread fun flashScrollbar() {
        awakenScrollBars()
    }

    // itemAnimator may be null. also see documentation for isRunning()
    @UiThread fun smoothScrollToPositionAfterAnimation(position: Int) {
        itemAnimator?.isRunning { jumpThenSmoothScrollCentering(position) }
                ?: jumpThenSmoothScrollCentering(position)
    }

    var onJumpedUpWhileScrollingListener: OnJumpedUpWhileScrollingListener? = null

    override fun onJumpedUpWhileScrolling() {
        onJumpedUpWhileScrollingListener?.onJumpedUpWhileScrolling()
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    var onBottom = true
        private set

    var onTop = false
        private set

    // todo is this useful?
    override fun onScrolled(dx: Int, dy: Int) {
        if (dy == 0 || adapter == null) return

        val lastVisible = manager.findLastVisibleItemPosition() == adapter!!.itemCount - 1
        val firstVisible = manager.findFirstVisibleItemPosition() == 0

        if (dy < 0 && !lastVisible) {
            onBottom = false
        } else if (dy > 0 && lastVisible) {
            onBottom = true
        }

        if (dy > 0 && !firstVisible) {
            onTop = false
        } else if (dy < 0 && firstVisible) {
            onTop = true
        }
    }

    fun recheckTopBottom() {
        if (adapter == null) return
        val firstCompletelyVisible = manager.findFirstCompletelyVisibleItemPosition()
        if (firstCompletelyVisible == NO_POSITION) return
        val lastVisible = manager.findLastVisibleItemPosition()
        onTop = firstCompletelyVisible == 0
        onBottom = lastVisible == adapter!!.itemCount - 1
    }
}