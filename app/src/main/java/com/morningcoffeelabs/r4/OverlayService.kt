package com.morningcoffeelabs.r4

import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var repository: MessageRepository

    private var overlayView: View? = null
    private var isExpanded = false

    private val positionPreferences by lazy {
        getSharedPreferences(PREFS_OVERLAY_POSITION, Context.MODE_PRIVATE)
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        repository = MessageRepository(this)
        showCollapsedOverlay()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        removeOverlayView()
        super.onDestroy()
    }

    private fun showCollapsedOverlay() {
        isExpanded = false
        removeOverlayView()

        val bubble = TextView(this).apply {
            text = "R4"
            gravity = Gravity.CENTER
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = roundedBackground(
                color = 0xFF333333.toInt(),
                radiusDp = 18f,
            )
        }

        val params = createWindowParams(
            width = WindowManager.LayoutParams.WRAP_CONTENT,
            height = WindowManager.LayoutParams.WRAP_CONTENT,
        ).apply {
            x = positionPreferences.getInt(KEY_X, 24)
            y = positionPreferences.getInt(KEY_Y, 180)
        }

        attachDragAndClick(
            view = bubble,
            params = params,
            onClick = { showExpandedOverlay() },
        )

        overlayView = bubble
        windowManager.addView(bubble, params)
    }

    private fun showExpandedOverlay() {
        isExpanded = true
        removeOverlayView()

        val messages = repository.loadMessages()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(
                color = 0xF22A2A2A.toInt(),
                radiusDp = 12f,
            )
            setPadding(0, dp(6), 0, dp(6))
        }

        val header = TextView(this).apply {
            text = "R4"
            textSize = 14f
            setTextColor(0xFFBDBDBD.toInt())
            setPadding(dp(14), dp(6), dp(14), dp(8))
        }
        root.addView(header)

        if (messages.isEmpty()) {
            root.addView(
                createListRow(
                    title = "Ingen lagrede meldinger",
                    enabled = false,
                    onClick = {},
                )
            )
        } else {
            messages.forEach { message ->
                root.addView(
                    createListRow(
                        title = message.title,
                        enabled = true,
                        onClick = {
                            copyToClipboard(message.text)
                            Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
                            showCollapsedOverlay()
                        },
                    )
                )
            }
        }

        val scrollView = ScrollView(this).apply {
            isFillViewport = false
            addView(root)
        }

        val params = createWindowParams(
            width = dp(220),
            height = WindowManager.LayoutParams.WRAP_CONTENT,
        ).apply {
            x = positionPreferences.getInt(KEY_X, 24)
            y = positionPreferences.getInt(KEY_Y, 180)
        }

        attachDragAndClick(
            view = header,
            params = params,
            onClick = { showCollapsedOverlay() },
        )

        overlayView = scrollView
        windowManager.addView(scrollView, params)
    }

    private fun createListRow(
        title: String,
        enabled: Boolean,
        onClick: () -> Unit,
    ): TextView {
        return TextView(this).apply {
            text = title
            textSize = 16f
            setTextColor(
                if (enabled) 0xFFFFFFFF.toInt() else 0xFF8A8A8A.toInt()
            )
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            isClickable = enabled
            isFocusable = enabled
            if (enabled) {
                setOnClickListener { onClick() }
            }
        }
    }

    private fun attachDragAndClick(
        view: View,
        params: WindowManager.LayoutParams,
        onClick: () -> Unit,
    ) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var moved = false

        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    moved = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()

                    if (abs(dx) > dp(4) || abs(dy) > dp(4)) {
                        moved = true
                    }

                    params.x = initialX + dx
                    params.y = initialY + dy
                    overlayView?.let { windowManager.updateViewLayout(it, params) }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    positionPreferences.edit()
                        .putInt(KEY_X, params.x)
                        .putInt(KEY_Y, params.y)
                        .apply()

                    if (!moved) {
                        onClick()
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun createWindowParams(width: Int, height: Int): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("R4 message", text))
    }

    private fun removeOverlayView() {
        overlayView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        overlayView = null
    }

    private fun roundedBackground(color: Int, radiusDp: Float): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val PREFS_OVERLAY_POSITION = "r4_overlay_position"
        private const val KEY_X = "x"
        private const val KEY_Y = "y"
    }
}
