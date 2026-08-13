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
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var repository: MessageRepository

    private var overlayView: View? = null

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

        val bounds = currentScreenBounds()
        val params = createWindowParams(
            width = WindowManager.LayoutParams.WRAP_CONTENT,
            height = WindowManager.LayoutParams.WRAP_CONTENT,
        ).apply {
            x = positionPreferences.getInt(KEY_X, 24).coerceIn(0, max(0, bounds.width() - dp(64)))
            y = positionPreferences.getInt(KEY_Y, 180).coerceIn(0, max(0, bounds.height() - dp(64)))
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
        removeOverlayView()

        val messages = repository.loadMessages()
        val bounds = currentScreenBounds()
        val screenWidth = bounds.width()
        val screenHeight = bounds.height()

        val bubbleX = positionPreferences.getInt(KEY_X, 24)
        val bubbleY = positionPreferences.getInt(KEY_Y, 180)
        val panelWidth = min(dp(190), max(dp(160), screenWidth - dp(24)))
        val maxPanelHeight = max(dp(150), (screenHeight * 0.34f).toInt())
        val bubbleApproxSize = dp(52)

        val openToRight = bubbleX + bubbleApproxSize / 2 < screenWidth / 2
        val openDown = bubbleY + bubbleApproxSize / 2 < screenHeight / 2

        val outer = FrameLayout(this)

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(
                color = 0xF22A2A2A.toInt(),
                radiusDp = 12f,
            )
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(3), dp(4), dp(3))
        }

        val title = TextView(this).apply {
            text = "R4"
            textSize = 13f
            setTextColor(0xFFBDBDBD.toInt())
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(3), dp(4), dp(3))
        }

        val close = TextView(this).apply {
            text = "×"
            textSize = 20f
            setTextColor(0xFFE0E0E0.toInt())
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(1), dp(8), dp(1))
            isClickable = true
            isFocusable = true
            setOnClickListener { stopSelf() }
        }

        if (openToRight) {
            header.addView(
                title,
                LinearLayout.LayoutParams(0, WindowManager.LayoutParams.WRAP_CONTENT, 1f),
            )
            header.addView(close)
        } else {
            header.addView(close)
            header.addView(
                title,
                LinearLayout.LayoutParams(0, WindowManager.LayoutParams.WRAP_CONTENT, 1f),
            )
        }

        panel.addView(header)

        val listContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(4))
        }

        if (messages.isEmpty()) {
            listContent.addView(
                createListRow(
                    title = "Ingen lagrede meldinger",
                    enabled = false,
                    onClick = {},
                )
            )
        } else {
            messages.forEach { message ->
                listContent.addView(
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
            isVerticalScrollBarEnabled = true
            addView(listContent)
        }

        panel.addView(
            scrollView,
            LinearLayout.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )

        outer.addView(
            panel,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        val desiredX = if (openToRight) {
            bubbleX
        } else {
            bubbleX + bubbleApproxSize - panelWidth
        }

        val desiredY = if (openDown) {
            bubbleY
        } else {
            bubbleY + bubbleApproxSize - maxPanelHeight
        }

        val params = createWindowParams(
            width = panelWidth,
            height = maxPanelHeight,
        ).apply {
            x = desiredX.coerceIn(0, max(0, screenWidth - panelWidth))
            y = desiredY.coerceIn(0, max(0, screenHeight - maxPanelHeight))
        }

        attachDragAndClick(
            view = title,
            params = params,
            onClick = { showCollapsedOverlay() },
        )

        overlayView = outer
        windowManager.addView(outer, params)
    }

    private fun createListRow(
        title: String,
        enabled: Boolean,
        onClick: () -> Unit,
    ): TextView {
        return TextView(this).apply {
            text = title
            textSize = 15f
            setTextColor(
                if (enabled) 0xFFFFFFFF.toInt() else 0xFF8A8A8A.toInt()
            )
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
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
                    val boundsNow = currentScreenBounds()

                    if (abs(dx) > dp(4) || abs(dy) > dp(4)) {
                        moved = true
                    }

                    val viewWidth = if (params.width > 0) params.width else dp(64)
                    val viewHeight = if (params.height > 0) params.height else dp(64)

                    params.x = (initialX + dx).coerceIn(0, max(0, boundsNow.width() - viewWidth))
                    params.y = (initialY + dy).coerceIn(0, max(0, boundsNow.height() - viewHeight))
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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
    }

    private fun currentScreenBounds() = windowManager.currentWindowMetrics.bounds

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
