package com.lastexit.app.ui.kit

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Wraps one row and lets the user swipe it away in either direction. A red layer with a trash icon
 * is revealed underneath. TalkBack users get an equivalent "Delete" accessibility action.
 */
@SuppressLint("ViewConstructor")
class SwipeToDeleteLayout(
    context: Context,
    private val content: View,
    private val dangerColor: Int,
    private val onDangerColor: Int,
    private val onDelete: () -> Unit,
) : FrameLayout(context) {
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trash: Drawable = context.icon(com.lastexit.app.R.drawable.ic_delete, onDangerColor)
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var velocity: VelocityTracker? = null
    private var dismissed = false

    init {
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        setWillNotDraw(false)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                dragging = false
                velocity?.recycle()
                velocity = VelocityTracker.obtain().also { it.addMovement(ev) }
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (!dragging && abs(dx) > touchSlop && abs(dx) > abs(dy) * 1.5f) {
                    dragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
        }
        return dragging
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (dismissed) return false
        velocity?.addMovement(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && abs(event.x - downX) > touchSlop) dragging = true
                if (dragging) {
                    content.translationX = event.x - downX
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val tracker = velocity
                tracker?.computeCurrentVelocity(1000)
                val vx = tracker?.xVelocity ?: 0f
                val dx = content.translationX
                val flung = abs(vx) > 1200f && vx * dx > 0
                if (event.actionMasked == MotionEvent.ACTION_UP && (abs(dx) > width * 0.38f || flung)) {
                    dismiss(if (dx >= 0) 1f else -1f)
                } else {
                    content.animate().translationX(0f).setDuration(180).withEndAction { invalidate() }.start()
                }
                dragging = false
                velocity?.recycle()
                velocity = null
            }
        }
        return true
    }

    private fun dismiss(direction: Float) {
        dismissed = true
        content.animate().translationX(direction * width).alpha(0f).setDuration(200).withEndAction { onDelete() }.start()
    }

    override fun onDraw(canvas: Canvas) {
        val dx = content.translationX
        if (dx == 0f) return
        paint.color = dangerColor
        val radius = dp(16f)
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, paint)
        val size = dpi(22)
        val top = (height - size) / 2
        val left = if (dx > 0) dpi(20) else width - dpi(20) - size
        trash.setBounds(left, top, left + size, top + size)
        trash.draw(canvas)
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction(ACTION_DELETE_ID, "Delete entry"))
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        if (action == ACTION_DELETE_ID) {
            onDelete()
            return true
        }
        return super.performAccessibilityAction(action, arguments)
    }

    private companion object {
        val ACTION_DELETE_ID = com.lastexit.app.R.id.action_delete_entry
    }
}
