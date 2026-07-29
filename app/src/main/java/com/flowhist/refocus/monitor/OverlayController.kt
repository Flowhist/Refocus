package com.flowhist.refocus.monitor

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.PathInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import kotlin.math.ceil

class OverlayController(private val service: RefocusAccessibilityService) {
    enum class Kind { PURPOSE, COMPLETION, GRACE, OVERDUE }

    private val windowManager =
        service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val inputMethodManager =
        service.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

    private var rootView: FrameLayout? = null
    private var cardView: View? = null
    private var scrimView: View? = null
    private var countdownView: TextView? = null
    private var backDispatcher: OnBackInvokedDispatcher? = null
    private var backCallback: OnBackInvokedCallback? = null

    var kind: Kind? = null
        private set

    fun showPurpose(
        appLabel: String,
        onConfirm: (purpose: String, minutes: Int) -> Unit,
    ) {
        val content = panel()
        content.addView(eyebrow("REFOCUS  ·  $appLabel"))
        content.addView(title("这次来做什么？"))

        val purposeInput = EditText(service).apply {
            hint = "写下唯一目标"
            setTextColor(TEXT)
            setHintTextColor(MUTED)
            textSize = 18f
            minLines = 1
            maxLines = 2
            inputType =
                InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions = EditorInfo.IME_ACTION_DONE
            background = fieldBackground()
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    hideKeyboardAndClearFocus()
                    true
                } else {
                    false
                }
            }
        }
        content.addView(
            purposeInput,
            LinearLayout.LayoutParams(MATCH, dp(54)).apply { topMargin = dp(18) },
        )

        content.addView(sectionLabel("计划时长"))
        val durationInput = EditText(service).apply {
            setText("10")
            setSelectAllOnFocus(true)
            gravity = Gravity.CENTER
            setTextColor(TEXT)
            setHintTextColor(MUTED)
            textSize = 16f
            inputType = InputType.TYPE_CLASS_NUMBER
            imeOptions = EditorInfo.IME_ACTION_DONE
            background = fieldBackground()
            setPadding(dp(10), 0, dp(10), 0)
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    hideKeyboardAndClearFocus()
                    true
                } else {
                    false
                }
            }
        }
        val chips = mutableListOf<Pair<Int, TextView>>()
        val durationRow = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        fun refreshChips() {
            val enteredMinutes = durationInput.text.toString().toIntOrNull()
            chips.forEach { (minutes, chip) ->
                val selected = minutes == enteredMinutes
                chip.setTextColor(if (selected) Color.WHITE else TEXT)
                chip.background = clickableBackground(
                    fill = if (selected) ACCENT else SURFACE_TINT,
                    radiusDp = 14,
                )
            }
        }
        listOf(5, 10, 15, 30).forEach { minutes ->
            val chip = TextView(service).apply {
                text = "$minutes min"
                gravity = Gravity.CENTER
                textSize = 13f
                typeface = MEDIUM
                setOnClickListener {
                    durationInput.setText(minutes.toString())
                    durationInput.setSelection(durationInput.text.length)
                    durationInput.clearFocus()
                    hideKeyboard(durationInput)
                }
            }
            chips += minutes to chip
            durationRow.addView(
                chip,
                LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                    marginStart = dp(3)
                    marginEnd = dp(3)
                },
            )
        }
        durationInput.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    value: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) = Unit

                override fun onTextChanged(
                    value: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int,
                ) = Unit

                override fun afterTextChanged(value: Editable?) {
                    refreshChips()
                }
            },
        )
        refreshChips()
        content.addView(durationRow, LinearLayout.LayoutParams(MATCH, WRAP))
        val customDurationRow = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                TextView(service).apply {
                    text = "自定义"
                    setTextColor(MUTED)
                    textSize = 13f
                    typeface = MEDIUM
                },
                LinearLayout.LayoutParams(0, WRAP, 1f),
            )
            addView(durationInput, LinearLayout.LayoutParams(dp(88), dp(44)))
            addView(
                TextView(service).apply {
                    text = "分钟"
                    setTextColor(MUTED)
                    textSize = 13f
                    setPadding(dp(8), 0, 0, 0)
                },
                LinearLayout.LayoutParams(WRAP, WRAP),
            )
        }
        content.addView(
            customDurationRow,
            LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(10) },
        )

        val error = TextView(service).apply {
            setTextColor(DANGER)
            textSize = 13f
            typeface = MEDIUM
            visibility = View.GONE
        }
        content.addView(
            error,
            LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(10) },
        )

        content.addView(
            primaryButton("开始专注  →") {
                val purpose = purposeInput.text.toString().trim()
                val minutes = durationInput.text.toString().toIntOrNull()
                if (purpose.isBlank()) {
                    error.text = "先写下一件要完成的事"
                    error.visibility = View.VISIBLE
                    purposeInput.requestFocus()
                    inputMethodManager.showSoftInput(
                        purposeInput,
                        InputMethodManager.SHOW_IMPLICIT,
                    )
                } else if (minutes == null || minutes !in 1..1440) {
                    error.text = "请输入 1–1440 分钟"
                    error.visibility = View.VISIBLE
                    durationInput.requestFocus()
                    durationInput.selectAll()
                    inputMethodManager.showSoftInput(
                        durationInput,
                        InputMethodManager.SHOW_IMPLICIT,
                    )
                } else {
                    dismiss()
                    onConfirm(purpose, minutes)
                }
            },
            LinearLayout.LayoutParams(MATCH, dp(52)).apply { topMargin = dp(18) },
        )

        show(Kind.PURPOSE, content)
    }

    fun showCompletion(
        appLabel: String,
        purpose: String,
        penaltyApplied: Boolean,
        onAnswer: (completed: Boolean) -> Unit,
    ) {
        val content = panel()
        content.addView(eyebrow(appLabel.uppercase()))
        content.addView(title("目标完成了吗？"))
        content.addView(purposeCard(purpose))
        if (penaltyApplied) {
            content.addView(statusText("已超时 · 本次 -1", DANGER))
        }

        val buttons = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        buttons.addView(
            secondaryButton("还没有") {
                dismiss()
                onAnswer(false)
            },
            LinearLayout.LayoutParams(0, dp(50), 1f).apply { marginEnd = dp(5) },
        )
        buttons.addView(
            primaryButton("完成  ✓") {
                dismiss()
                onAnswer(true)
            },
            LinearLayout.LayoutParams(0, dp(50), 1f).apply { marginStart = dp(5) },
        )
        content.addView(
            buttons,
            LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(18) },
        )
        show(Kind.COMPLETION, content)
    }

    fun showGrace(appLabel: String, remainingMs: Long, onExit: () -> Unit) {
        val content = panel()
        content.addView(eyebrow("TIMEBOX  ·  $appLabel"))
        content.addView(title("该收尾了"))
        countdownView = TextView(service).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setTextColor(ACCENT)
            textSize = 58f
            typeface = BOLD
            includeFontPadding = false
        }
        content.addView(
            countdownView,
            LinearLayout.LayoutParams(MATCH, dp(72)).apply { topMargin = dp(12) },
        )
        content.addView(
            caption("在倒计时结束前离开，完成目标仍可 +1"),
            LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(4) },
        )
        content.addView(
            primaryButton("现在退出") { onExit() },
            LinearLayout.LayoutParams(MATCH, dp(50)).apply { topMargin = dp(18) },
        )
        show(Kind.GRACE, content)
        updateCountdown(remainingMs)
    }

    fun showOverdue(
        appLabel: String,
        purpose: String,
        onExit: () -> Unit,
        onContinue: () -> Unit,
    ) {
        val content = panel()
        content.addView(eyebrow("OVERTIME  ·  $appLabel", DANGER))
        content.addView(title("时间已经超出"))
        content.addView(purposeCard(purpose))
        content.addView(statusText("本次记录 -1", DANGER))
        content.addView(
            primaryButton("退出应用") { onExit() },
            LinearLayout.LayoutParams(MATCH, dp(50)).apply { topMargin = dp(18) },
        )
        content.addView(
            textButton("继续 5 秒") {
                dismiss()
                onContinue()
            },
            LinearLayout.LayoutParams(MATCH, dp(44)).apply { topMargin = dp(6) },
        )
        show(Kind.OVERDUE, content)
    }

    fun updateCountdown(remainingMs: Long) {
        countdownView?.text =
            ceil(remainingMs.coerceAtLeast(0L) / 1000.0).toInt().toString()
    }

    fun dismiss(animated: Boolean = true) {
        val root = rootView ?: return
        val card = cardView
        val scrim = scrimView

        rootView = null
        cardView = null
        scrimView = null
        countdownView = null
        kind = null
        unregisterBackHandler()
        hideKeyboard(root)

        if (!animated || !root.isAttachedToWindow) {
            runCatching { windowManager.removeViewImmediate(root) }
            return
        }

        card?.animate()
            ?.alpha(0f)
            ?.scaleX(0.98f)
            ?.scaleY(0.98f)
            ?.translationY(dp(10).toFloat())
            ?.setDuration(EXIT_ANIMATION_MS)
            ?.setInterpolator(DecelerateInterpolator())
            ?.start()
        scrim?.animate()
            ?.alpha(0f)
            ?.setDuration(EXIT_ANIMATION_MS)
            ?.start()
        root.postDelayed(
            { runCatching { windowManager.removeViewImmediate(root) } },
            EXIT_ANIMATION_MS + 20L,
        )
    }

    private fun show(newKind: Kind, content: View) {
        dismiss(animated = false)
        val scrim = View(service).apply {
            background = acrylicBackdrop()
            alpha = 0f
        }
        val scroll = ScrollView(service).apply {
            isFillViewport = false
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(content, FrameLayout.LayoutParams(MATCH, WRAP))
        }
        val scrollParams =
            FrameLayout.LayoutParams(
                MATCH,
                WRAP,
                Gravity.CENTER,
            )
        val root = FrameLayout(service).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setBackgroundColor(Color.TRANSPARENT)
            addView(scrim, FrameLayout.LayoutParams(MATCH, MATCH))
            addView(scroll, scrollParams)
            setOnKeyListener { _, keyCode, event ->
                if (
                    keyCode == KeyEvent.KEYCODE_BACK &&
                    event.action == KeyEvent.ACTION_UP
                ) {
                    hideKeyboardAndClearFocus()
                    true
                } else {
                    false
                }
            }
            setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                val ime = insets.getInsets(WindowInsets.Type.ime())
                scrollParams.setMargins(
                    dp(18),
                    bars.top + dp(24),
                    dp(18),
                    bars.bottom + dp(18),
                )
                scroll.layoutParams = scrollParams
                view.post {
                    updateKeyboardOffset(
                        scroll = scroll,
                        root = view,
                        topInset = bars.top,
                        imeBottom = ime.bottom,
                    )
                }
                insets
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_BLUR_BEHIND or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            setFitInsetsTypes(0)
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            setBlurBehindRadius(dp(BLUR_RADIUS_DP))
            softInputMode =
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
            title = "Refocus"
        }

        windowManager.addView(root, params)
        rootView = root
        cardView = content
        scrimView = scrim
        kind = newKind
        registerBackHandler(root)
        root.requestApplyInsets()

        content.alpha = 0f
        content.scaleX = 0.96f
        content.scaleY = 0.96f
        content.translationY = dp(18).toFloat()
        content.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setDuration(ENTER_ANIMATION_MS)
            .setInterpolator(DecelerateInterpolator())
            .start()
        scrim.animate()
            .alpha(1f)
            .setDuration(ENTER_ANIMATION_MS)
            .setInterpolator(KEYBOARD_INTERPOLATOR)
            .start()
    }

    private fun updateKeyboardOffset(
        scroll: View,
        root: View,
        topInset: Int,
        imeBottom: Int,
    ) {
        if (!scroll.isLaidOut || root.height == 0) return

        val gap = dp(18)
        val targetOffset =
            if (imeBottom > 0) {
                val keyboardTop = root.height - imeBottom
                val overlap = (scroll.bottom + gap - keyboardTop).coerceAtLeast(0)
                val availableUpwardSpace =
                    (scroll.top - topInset - gap).coerceAtLeast(0)
                -minOf(overlap, availableUpwardSpace).toFloat()
            } else {
                0f
            }

        if (scroll.translationY == targetOffset) return
        scroll.animate()
            .cancel()
        scroll.animate()
            .translationY(targetOffset)
            .setDuration(if (imeBottom > 0) 240L else 200L)
            .setInterpolator(KEYBOARD_INTERPOLATOR)
            .start()
    }

    private fun registerBackHandler(root: View) {
        if (Build.VERSION.SDK_INT < 33) return
        root.post {
            if (rootView !== root) return@post
            val dispatcher = root.findOnBackInvokedDispatcher() ?: return@post
            val callback = OnBackInvokedCallback { hideKeyboardAndClearFocus() }
            dispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_OVERLAY,
                callback,
            )
            backDispatcher = dispatcher
            backCallback = callback
        }
    }

    private fun unregisterBackHandler() {
        if (Build.VERSION.SDK_INT < 33) return
        val dispatcher = backDispatcher
        val callback = backCallback
        if (dispatcher != null && callback != null) {
            runCatching { dispatcher.unregisterOnBackInvokedCallback(callback) }
        }
        backDispatcher = null
        backCallback = null
    }

    private fun hideKeyboardAndClearFocus() {
        val root = rootView ?: return
        root.findFocus()?.clearFocus()
        hideKeyboard(root)
        root.requestFocus()
    }

    private fun hideKeyboard(root: View) {
        val token = root.findFocus()?.windowToken ?: root.windowToken
        token?.let { inputMethodManager.hideSoftInputFromWindow(it, 0) }
    }

    private fun panel(): LinearLayout = LinearLayout(service).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(20), dp(20), dp(18))
        background =
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(PANEL)
                setStroke(dp(1), STROKE)
                cornerRadius = dp(24).toFloat()
            }
        clipToOutline = true
        elevation = 0f
    }

    private fun eyebrow(value: String, color: Int = ACCENT) = TextView(service).apply {
        text = value
        setTextColor(color)
        textSize = 11f
        typeface = BOLD
        letterSpacing = 0.12f
        maxLines = 1
    }

    private fun title(value: String) = TextView(service).apply {
        text = value
        setTextColor(TEXT)
        textSize = 26f
        typeface = BOLD
        includeFontPadding = false
        setPadding(0, dp(7), 0, 0)
    }

    private fun sectionLabel(value: String) = TextView(service).apply {
        text = value
        setTextColor(MUTED)
        textSize = 12f
        typeface = MEDIUM
        setPadding(0, dp(16), 0, dp(8))
    }

    private fun caption(value: String) = TextView(service).apply {
        text = value
        gravity = Gravity.CENTER
        setTextColor(MUTED)
        textSize = 13f
    }

    private fun statusText(value: String, color: Int) = TextView(service).apply {
        text = value
        setTextColor(color)
        textSize = 13f
        typeface = BOLD
        setPadding(0, dp(12), 0, 0)
    }

    private fun purposeCard(value: String) = TextView(service).apply {
        text = value
        setTextColor(TEXT)
        textSize = 16f
        typeface = MEDIUM
        background = rounded(SURFACE_TINT, 16)
        setPadding(dp(14), dp(12), dp(14), dp(12))
        maxLines = 3
        layoutParams =
            LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(16) }
    }

    private fun primaryButton(text: String, onClick: () -> Unit) =
        actionButton(
            text = text,
            textColor = Color.WHITE,
            fill = ACCENT,
            onClick = onClick,
        )

    private fun secondaryButton(text: String, onClick: () -> Unit) =
        actionButton(
            text = text,
            textColor = TEXT,
            fill = SURFACE_TINT,
            onClick = onClick,
        )

    private fun textButton(text: String, onClick: () -> Unit) =
        actionButton(
            text = text,
            textColor = MUTED,
            fill = Color.TRANSPARENT,
            onClick = onClick,
        )

    private fun actionButton(
        text: String,
        textColor: Int,
        fill: Int,
        onClick: () -> Unit,
    ) = TextView(service).apply {
        this.text = text
        gravity = Gravity.CENTER
        textSize = 15f
        typeface = BOLD
        setTextColor(textColor)
        background = clickableBackground(fill, 16)
        setOnClickListener { onClick() }
    }

    private fun fieldBackground() = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(FIELD)
        setStroke(dp(1), STROKE)
        cornerRadius = dp(16).toFloat()
    }

    private fun clickableBackground(fill: Int, radiusDp: Int): RippleDrawable =
        RippleDrawable(
            ColorStateList.valueOf(Color.argb(32, 255, 255, 255)),
            rounded(fill, radiusDp),
            null,
        )

    private fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun acrylicBackdrop() =
        GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                Color.argb(54, 232, 236, 233),
                Color.argb(68, 121, 131, 124),
                Color.argb(86, 38, 45, 41),
            ),
        )

    private fun dp(value: Int): Int =
        (value * service.resources.displayMetrics.density).toInt()

    private companion object {
        const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
        const val ENTER_ANIMATION_MS = 240L
        const val EXIT_ANIMATION_MS = 180L
        const val BLUR_RADIUS_DP = 48

        val BOLD: Typeface = Typeface.create("sans-serif", Typeface.BOLD)
        val MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        val KEYBOARD_INTERPOLATOR = PathInterpolator(0.2f, 0f, 0f, 1f)
        val TEXT = Color.rgb(18, 24, 20)
        val MUTED = Color.rgb(99, 111, 103)
        val ACCENT = Color.rgb(45, 99, 70)
        val DANGER = Color.rgb(181, 61, 50)
        val PANEL = Color.rgb(250, 252, 249)
        val FIELD = Color.rgb(244, 247, 243)
        val SURFACE_TINT = Color.rgb(230, 238, 232)
        val STROKE = Color.rgb(213, 222, 215)
    }
}
