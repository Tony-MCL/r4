package com.morningcoffeelabs.r4

import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.UUID
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
        isRunning = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        repository = MessageRepository(this)
        showCollapsedOverlay()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        removeOverlayView()
        super.onDestroy()
    }

    private fun showCollapsedOverlay() {
        removeOverlayView()

        val bubble = ImageView(this).apply {
            setImageResource(R.drawable.r4_logo)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = roundedBackground(
                color = 0xFF333333.toInt(),
                radiusDp = 18f,
            )
        }

        val bubbleSize = dp(52)
        val bounds = currentScreenBounds()
        val params = createWindowParams(
            width = bubbleSize,
            height = bubbleSize,
            focusable = false,
        ).apply {
            x = positionPreferences.getInt(KEY_X, 24).coerceIn(0, max(0, bounds.width() - bubbleSize))
            y = positionPreferences.getInt(KEY_Y, 180).coerceIn(0, max(0, bounds.height() - bubbleSize))
        }

        attachDragAndClick(
            view = bubble,
            params = params,
            onClick = { showMessagesOverlay() },
        )

        overlayView = bubble
        windowManager.addView(bubble, params)
    }

    private fun showMessagesOverlay() {
        removeOverlayView()

        val messages = repository.loadMessages()
        val geometry = overlayGeometry()
        val outer = FrameLayout(this)
        val panel = createPanel()
        val header = createHeader(
            activeTab = OverlayTab.MESSAGES,
            geometry = geometry,
            onMessages = {},
            onNew = { showNewMessageOverlay() },
        )
        panel.addView(header.view)

        val listContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(4))
        }

        if (messages.isEmpty()) {
            listContent.addView(
                createListRow(
                    title = getString(R.string.overlay_no_saved_messages),
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
                            Toast.makeText(this, getString(R.string.copied), Toast.LENGTH_SHORT).show()
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

        val params = createExpandedParams(geometry, focusable = false)
        attachDragAndClick(
            view = header.logo,
            params = params,
            onClick = { showCollapsedOverlay() },
        )

        overlayView = outer
        windowManager.addView(outer, params)
    }

    private fun showNewMessageOverlay() {
        removeOverlayView()

        val geometry = overlayGeometry()
        val outer = FrameLayout(this)
        val panel = createPanel()
        val header = createHeader(
            activeTab = OverlayTab.NEW,
            geometry = geometry,
            onMessages = { showMessagesOverlay() },
            onNew = {},
        )
        panel.addView(header.view)

        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(6), dp(10), dp(10))
        }

        val titleInput = createEditorField(
            hintText = getString(R.string.title_label),
            singleLine = true,
        )
        val textInput = createEditorField(
            hintText = getString(R.string.text_label),
            singleLine = false,
        ).apply {
            minLines = 5
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }

        form.addView(titleInput)
        form.addView(
            textInput,
            LinearLayout.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ).apply { topMargin = dp(8) },
        )

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
        }

        val paste = createActionButton(getString(R.string.overlay_paste)) {
            textInput.requestFocus()
            textInput.post {
                textInput.setSelection(textInput.text.length)
                val pasted = textInput.onTextContextMenuItem(android.R.id.paste)
                if (!pasted) {
                    Toast.makeText(
                        this,
                        getString(R.string.overlay_no_clipboard_text),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }

        val cancel = createActionButton(getString(R.string.cancel)) {
            hideKeyboard(titleInput)
            showMessagesOverlay()
        }

        val save = createActionButton(getString(R.string.save), emphasized = true) {
            val title = titleInput.text.toString()
            val text = textInput.text.toString()
            if (title.isBlank()) {
                Toast.makeText(this, getString(R.string.overlay_title_required), Toast.LENGTH_SHORT).show()
            } else {
                val now = System.currentTimeMillis()
                val updated = repository.loadMessages().toMutableList().apply {
                    add(
                        Message(
                            id = UUID.randomUUID().toString(),
                            title = title,
                            text = text,
                            createdAt = now,
                            updatedAt = now,
                        )
                    )
                }
                repository.saveMessages(updated)
                hideKeyboard(titleInput)
                Toast.makeText(this, getString(R.string.overlay_saved), Toast.LENGTH_SHORT).show()
                showMessagesOverlay()
            }
        }

        actions.addView(paste, LinearLayout.LayoutParams(0, dp(38), 1f))
        actions.addView(cancel, LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginStart = dp(6) })
        actions.addView(save, LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginStart = dp(6) })
        form.addView(actions)

        panel.addView(
            form,
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

        val params = createExpandedParams(geometry, focusable = true)
        attachDragAndClick(
            view = header.logo,
            params = params,
            onClick = {
                hideKeyboard(titleInput)
                showCollapsedOverlay()
            },
        )

        overlayView = outer
        windowManager.addView(outer, params)
        titleInput.requestFocus()
        titleInput.post {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(titleInput, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun createPanel(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(
                color = 0xF22A2A2A.toInt(),
                radiusDp = 12f,
            )
        }
    }

    private data class HeaderViews(
        val view: LinearLayout,
        val logo: ImageView,
    )

    private fun createHeader(
        activeTab: OverlayTab,
        geometry: OverlayGeometry,
        onMessages: () -> Unit,
        onNew: () -> Unit,
    ): HeaderViews {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(3), dp(4), dp(3))
        }

        val logo = ImageView(this).apply {
            setImageResource(R.drawable.r4_logo)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }

        val messagesTab = createTab(
            text = getString(R.string.overlay_messages_tab),
            active = activeTab == OverlayTab.MESSAGES,
            onClick = onMessages,
        )
        val newTab = createTab(
            text = getString(R.string.overlay_new_tab),
            active = activeTab == OverlayTab.NEW,
            onClick = onNew,
        )
        val close = TextView(this).apply {
            text = "×"
            textSize = 20f
            setTextColor(0xFFE0E0E0.toInt())
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(1), dp(6), dp(1))
            isClickable = true
            isFocusable = true
            setOnClickListener { stopSelf() }
        }

        val logoParams = LinearLayout.LayoutParams(dp(42), dp(34))
        if (geometry.openToRight) {
            header.addView(logo, logoParams)
            header.addView(messagesTab, LinearLayout.LayoutParams(0, dp(34), 1f))
            header.addView(newTab, LinearLayout.LayoutParams(0, dp(34), 0.72f))
            header.addView(close)
        } else {
            header.addView(close)
            header.addView(messagesTab, LinearLayout.LayoutParams(0, dp(34), 1f))
            header.addView(newTab, LinearLayout.LayoutParams(0, dp(34), 0.72f))
            header.addView(logo, logoParams)
        }

        return HeaderViews(header, logo)
    }

    private fun createTab(text: String, active: Boolean, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(if (active) 0xFF9BE13A.toInt() else 0xFFBDBDBD.toInt())
            background = if (active) {
                roundedBackground(0x223FAE2A, 8f)
            } else {
                roundedBackground(0x00000000, 8f)
            }
            isClickable = !active
            isFocusable = !active
            if (!active) setOnClickListener { onClick() }
        }
    }

    private fun createEditorField(hintText: String, singleLine: Boolean): EditText {
        return EditText(this).apply {
            hint = hintText
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF8A8A8A.toInt())
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setSingleLine(singleLine)
            background = roundedBackground(0xFF1C1C1C.toInt(), 8f, strokeColor = 0xFF555555.toInt())
        }
    }

    private fun createActionButton(text: String, emphasized: Boolean = false, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(if (emphasized) 0xFF101010.toInt() else 0xFFE0E0E0.toInt())
            background = roundedBackground(
                color = if (emphasized) 0xFF9BE13A.toInt() else 0xFF3A3A3A.toInt(),
                radiusDp = 8f,
            )
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }

    private fun createListRow(
        title: String,
        enabled: Boolean,
        onClick: () -> Unit,
    ): TextView {
        return TextView(this).apply {
            text = title
            textSize = 15f
            setTextColor(if (enabled) 0xFFFFFFFF.toInt() else 0xFF8A8A8A.toInt())
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            isClickable = enabled
            isFocusable = enabled
            if (enabled) setOnClickListener { onClick() }
        }
    }

    private data class OverlayGeometry(
        val screenWidth: Int,
        val screenHeight: Int,
        val bubbleX: Int,
        val bubbleY: Int,
        val panelWidth: Int,
        val panelHeight: Int,
        val bubbleApproxSize: Int,
        val openToRight: Boolean,
        val openDown: Boolean,
    )

    private fun overlayGeometry(): OverlayGeometry {
        val bounds = currentScreenBounds()
        val screenWidth = bounds.width()
        val screenHeight = bounds.height()
        val bubbleX = positionPreferences.getInt(KEY_X, 24)
        val bubbleY = positionPreferences.getInt(KEY_Y, 180)
        val panelWidth = min(dp(220), max(dp(190), screenWidth - dp(24)))
        val panelHeight = max(dp(190), (screenHeight * 0.42f).toInt())
        val bubbleApproxSize = dp(52)
        val openToRight = bubbleX + bubbleApproxSize / 2 < screenWidth / 2
        val openDown = bubbleY + bubbleApproxSize / 2 < screenHeight / 2
        return OverlayGeometry(
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            bubbleX = bubbleX,
            bubbleY = bubbleY,
            panelWidth = panelWidth,
            panelHeight = panelHeight,
            bubbleApproxSize = bubbleApproxSize,
            openToRight = openToRight,
            openDown = openDown,
        )
    }

    private fun createExpandedParams(geometry: OverlayGeometry, focusable: Boolean): WindowManager.LayoutParams {
        val desiredX = if (geometry.openToRight) {
            geometry.bubbleX
        } else {
            geometry.bubbleX + geometry.bubbleApproxSize - geometry.panelWidth
        }
        val desiredY = if (geometry.openDown) {
            geometry.bubbleY
        } else {
            geometry.bubbleY + geometry.bubbleApproxSize - geometry.panelHeight
        }

        return createWindowParams(
            width = geometry.panelWidth,
            height = geometry.panelHeight,
            focusable = focusable,
        ).apply {
            x = desiredX.coerceIn(0, max(0, geometry.screenWidth - geometry.panelWidth))
            y = desiredY.coerceIn(0, max(0, geometry.screenHeight - geometry.panelHeight))
            if (focusable) softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
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
                    if (abs(dx) > dp(4) || abs(dy) > dp(4)) moved = true
                    val viewWidth = if (params.width > 0) params.width else dp(64)
                    val viewHeight = if (params.height > 0) params.height else dp(64)
                    params.x = (initialX + dx).coerceIn(0, max(0, boundsNow.width() - viewWidth))
                    params.y = (initialY + dy).coerceIn(0, max(0, boundsNow.height() - viewHeight))
                    overlayView?.let { windowManager.updateViewLayout(it, params) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    positionPreferences.edit().putInt(KEY_X, params.x).putInt(KEY_Y, params.y).apply()
                    if (!moved) onClick()
                    true
                }
                else -> false
            }
        }
    }

    private fun createWindowParams(width: Int, height: Int, focusable: Boolean): WindowManager.LayoutParams {
        val flags = if (focusable) {
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        return WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
    }

    private fun hideKeyboard(view: View) {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun currentScreenBounds() = windowManager.currentWindowMetrics.bounds

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText(getString(R.string.clipboard_label), text)
        )
    }

    private fun removeOverlayView() {
        overlayView?.let { view -> runCatching { windowManager.removeView(view) } }
        overlayView = null
    }

    private fun roundedBackground(
        color: Int,
        radiusDp: Float,
        strokeColor: Int? = null,
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
            strokeColor?.let { setStroke(dp(1), it) }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()

    private enum class OverlayTab {
        MESSAGES,
        NEW,
    }

    companion object {
        @Volatile
        var isRunning: Boolean = false
            private set

        private const val PREFS_OVERLAY_POSITION = "r4_overlay_position"
        private const val KEY_X = "x"
        private const val KEY_Y = "y"
    }
}
