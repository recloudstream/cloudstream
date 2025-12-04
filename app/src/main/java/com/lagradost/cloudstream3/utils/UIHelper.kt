package com.lagradost.cloudstream3.utils

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AppOpsManager
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.TransactionTooLargeException
import android.util.Log
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.ListAdapter
import android.widget.ListView
import android.widget.Toast.LENGTH_LONG
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.DimenRes
import androidx.annotation.StyleRes
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.PopupMenu
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.graphics.alpha
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.core.view.marginBottom
import androidx.core.view.marginLeft
import androidx.core.view.marginRight
import androidx.core.view.marginTop
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.palette.graphics.Palette
import androidx.preference.PreferenceManager
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipDrawable
import com.google.android.material.chip.ChipGroup
import com.lagradost.cloudstream3.CloudStreamApp.Companion.context
import com.lagradost.cloudstream3.CommonActivity.activity
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.PHONE
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.utils.AppContextUtils.isRtl
import com.lagradost.cloudstream3.utils.Coroutines.main
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import com.lagradost.cloudstream3.utils.BackPressedCallbackHelper.disableBackPressedCallback
import com.lagradost.cloudstream3.utils.BackPressedCallbackHelper.enableBackPressedCallback

object UIHelper {
    val Int.toPx: Int get() = (this * Resources.getSystem().displayMetrics.density).toInt()
    val Float.toPx: Float get() = (this * Resources.getSystem().displayMetrics.density)
    val Int.toDp: Int get() = (this / Resources.getSystem().displayMetrics.density).toInt()
    val Float.toDp: Float get() = (this / Resources.getSystem().displayMetrics.density)

    fun Context.checkWrite(): Boolean {
        return (ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
                == PackageManager.PERMISSION_GRANTED
                // Since Android 13, we can't request external storage permission,
                // so don't check it.
                || Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
    }

    fun populateChips(
        view: ChipGroup?,
        tags: List<String>,
        @StyleRes style: Int = R.style.ChipFilled
    ) {
        if (view == null) return
        view.removeAllViews()
        val context = view.context ?: return
        val maxTags = tags.take(10) // Limited because they are too much

        maxTags.forEach { tag ->
            val chip = Chip(context)
            val chipDrawable = ChipDrawable.createFromAttributes(
                context,
                null,
                0,
                style
            )
            chip.setChipDrawable(chipDrawable)
            chip.text = tag
            chip.isChecked = false
            chip.isCheckable = false
            chip.isFocusable = false
            chip.isClickable = false
            chip.setTextColor(context.colorFromAttribute(R.attr.white))
            view.addView(chip)
        }
    }

    fun Activity.requestRW() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.MANAGE_EXTERNAL_STORAGE
            ),
            1337
        )
    }

    fun clipboardHelper(label: UiText, text: CharSequence) {
        val ctx = context ?: return
        try {
            ctx.let {
                val clip = ClipData.newPlainText(label.asString(ctx), text)
                val labelSuffix = txt(R.string.toast_copied).asString(ctx)
                ctx.getSystemService<ClipboardManager>()?.setPrimaryClip(clip)

                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                    showToast("${label.asString(ctx)} $labelSuffix")
                }
            }
        } catch (t: Throwable) {
            Log.e("ClipboardService", "$t")
            when (t) {
                is SecurityException -> {
                    showToast(R.string.clipboard_permission_error)
                }

                is TransactionTooLargeException -> {
                    showToast(R.string.clipboard_too_large)
                }

                else -> {
                    showToast(R.string.clipboard_unknown_error, LENGTH_LONG)
                }
            }
        }
    }

    /**
     * Sets ListView height dynamically based on the height of the items.
     *
     * @param listView to be resized
     * @return true if the listView is successfully resized, false otherwise
     */
    fun setListViewHeightBasedOnItems(listView: ListView?) {
        val listAdapter: ListAdapter = listView?.adapter ?: return
        val numberOfItems: Int = listAdapter.count

        // Get total height of all items.
        var totalItemsHeight = 0
        for (itemPos in 0 until numberOfItems) {
            val item: View = listAdapter.getView(itemPos, null, listView)
            item.measure(0, 0)
            totalItemsHeight += item.measuredHeight
        }

        // Get total height of all item dividers.
        val totalDividersHeight: Int = listView.dividerHeight *
                (numberOfItems - 1)

        // Set list height.
        val params: ViewGroup.LayoutParams = listView.layoutParams
        params.height = totalItemsHeight + totalDividersHeight
        listView.layoutParams = params
        listView.requestLayout()
    }

    fun Context.getSpanCount(isHorizontal:Boolean=false): Int {
//        val compactView = false
        val spanCountLandscape = if (isHorizontal) 3 else 6
        val spanCountPortrait = if (isHorizontal) 2 else 3
        val orientation = resources.configuration.orientation

        return if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            spanCountLandscape
        } else spanCountPortrait
    }

    fun Fragment.hideKeyboard() {
        activity?.window?.decorView?.clearFocus()
        view?.let {
            hideKeyboard(it)
        }
    }

    fun View?.setAppBarNoScrollFlagsOnTV() {
        if (isLayout(TV or EMULATOR)) {
            this?.updateLayoutParams<AppBarLayout.LayoutParams> {
                scrollFlags = AppBarLayout.LayoutParams.SCROLL_FLAG_NO_SCROLL
            }
        }
    }

    fun Activity.hideKeyboard() {
        window?.decorView?.clearFocus()
        this.findViewById<View>(android.R.id.content)?.rootView?.let {
            hideKeyboard(it)
        }
    }

    fun Activity?.navigate(
        navigationId: Int,
        args: Bundle? = null,
        navOptions: NavOptions? = null // To control nav graph & manage back stack
    ) {
        val tag = "NavComponent"
        if (this is FragmentActivity) {
            try {
                runOnUiThread {
                    // Navigate using navigation ID
                    val navHostFragment =
                        supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
                    Log.i(tag, "Navigating to fragment: $navigationId")
                    navHostFragment?.navController?.navigate(navigationId, args, navOptions)
                }
            } catch (t: Throwable) {
                logError(t)
            }
        }
    }

    // Open activities from an activity outside the nav graph
    fun Context.openActivity(activity: Class<*>, args: Bundle? = null) {
        val tag = "NavComponent"
        try {
            val intent = Intent(this, activity)
            if (args != null) {
                intent.putExtras(args)
            }
            Log.i(tag, "Navigating to Activity: ${activity.simpleName}")
            startActivity(intent)
        } catch (t: Throwable) {
            logError(t)
        }
    }

    /** If you want to call this from a BackPressedCallback, pass the name of the callback to temporarily disable it */
    fun FragmentActivity.popCurrentPage(fromBackPressedCallback: String? = null) {
        // Use the main looper handler to post actions on the main thread
        main {
            // Post the back press action to the main thread handler to ensure it executes
            // after any currently pending UI updates or fragment transactions.
            if (fromBackPressedCallback != null) {
                disableBackPressedCallback(fromBackPressedCallback)
            }
            if (!supportFragmentManager.isStateSaved) {
                // Get the top fragment from the back stack
                Log.d("popFragment", "Destroying Fragment")
                // If the state is not saved, it's safe to perform the back press action.
                onBackPressedDispatcher.onBackPressed()
            } else {
                // If the state is saved, retry the back press action after a slight delay.
                // This gives the FragmentManager time to complete any ongoing state-saving
                // operations or transactions, ensuring that we do not encounter an IllegalStateException.
                delay(100)
                if (!supportFragmentManager.isStateSaved) {
                    Log.d("popFragment", "Destroying after delay")
                    onBackPressedDispatcher.onBackPressed()
                }
            }
            if (fromBackPressedCallback != null) {
                enableBackPressedCallback(fromBackPressedCallback)
            }
        }
    }

    @ColorInt
    fun Context.getResourceColor(@AttrRes resource: Int, alphaFactor: Float = 1f): Int {
        val typedArray = obtainStyledAttributes(intArrayOf(resource))
        val color = typedArray.getColor(0, 0)
        typedArray.recycle()

        if (alphaFactor < 1f) {
            val alpha = (color.alpha * alphaFactor).roundToInt()
            return Color.argb(alpha, color.red, color.green, color.blue)
        }

        return color
    }

    var createPaletteAsyncCache: HashMap<String, Palette> = hashMapOf()
    fun createPaletteAsync(url: String, bitmap: Bitmap, callback: (Palette) -> Unit) {
        createPaletteAsyncCache[url]?.let { palette ->
            callback.invoke(palette)
            return
        }
        Palette.from(bitmap).generate { paletteNull ->
            paletteNull?.let { palette ->
                createPaletteAsyncCache[url] = palette
                callback(palette)
            }
        }
    }

    fun adjustAlpha(@ColorInt color: Int, factor: Float): Int {
        val alpha = (Color.alpha(color) * factor).roundToInt()
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        return Color.argb(alpha, red, green, blue)
    }

    fun Context.colorFromAttribute(attribute: Int): Int {
        val attributes = obtainStyledAttributes(intArrayOf(attribute))
        val color = attributes.getColor(0, 0)
        attributes.recycle()
        return color
    }

    fun Activity.hideSystemUI() {
        // Enables regular immersive mode.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            return
        }

        // For "lean back" mode, remove SYSTEM_UI_FLAG_IMMERSIVE.
        // Or for "sticky immersive," replace it with SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        // Set the content to appear under the system bars so that the
                        // content doesn't resize when the system bars hide and show.
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        // Hide the nav bar and status bar
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                )
    }

    fun Activity.enableEdgeToEdgeCompat() {
        // edge-to-edge is very buggy on earlier versions
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        WindowCompat.enableEdgeToEdge(window)
    }

    fun Activity.setNavigationBarColorCompat(@AttrRes resourceId: Int) {
        // edge-to-edge handles this
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return

        @Suppress("DEPRECATION")
        window?.navigationBarColor = colorFromAttribute(resourceId)
    }

    fun Context.getStatusBarHeight(): Int {
        if (isLayout(TV or EMULATOR)) {
            return 0
        }

        var result = 0
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = resources.getDimensionPixelSize(resourceId)
        }
        return result
    }

    fun fixPaddingStatusbarMargin(v: View?) {
        if (v == null) return
        val ctx = v.context ?: return

        v.layoutParams = v.layoutParams.apply {
            if (this is MarginLayoutParams) {
                setMargins(
                    v.marginLeft,
                    v.marginTop + ctx.getStatusBarHeight(),
                    v.marginRight,
                    v.marginBottom
                )
            }
        }
    }

    fun fixPaddingStatusbarView(v: View?) {
        if (v == null) return
        val ctx = v.context ?: return
        val params = v.layoutParams
        params.height = ctx.getStatusBarHeight()
        v.layoutParams = params
    }

    fun fixSystemBarsPadding(
        v: View,
        @DimenRes heightResId: Int? = null,
        @DimenRes widthResId: Int? = null,
        padTop: Boolean = true,
        padBottom: Boolean = true,
        padLeft: Boolean = true,
        padRight: Boolean = true,
        overlayCutout: Boolean = true,
        fixIme: Boolean = false
    ) {
        // edge-to-edge is very buggy on earlier versions so we just
        // handle the status bar here instead.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if (padTop) {
                val ctx = v.context ?: return
                v.updatePadding(top = ctx.getStatusBarHeight())
            }
            return
        }

        ViewCompat.setOnApplyWindowInsetsListener(v) { view, windowInsets ->
            val leftCheck = if (view.isRtl()) padRight else padLeft
            val rightCheck = if (view.isRtl()) padLeft else padRight

            val insetTypes = WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout() or
                if (fixIme) WindowInsetsCompat.Type.ime() else 0

            val insets = windowInsets.getInsets(insetTypes)

            view.updatePadding(
                left = if (leftCheck) insets.left else view.paddingLeft,
                right = if (rightCheck) insets.right else view.paddingRight,
                bottom = if (padBottom) insets.bottom else view.paddingBottom,
                top = if (padTop) insets.top else view.paddingTop
            )

            heightResId?.let {
                val heightPx = view.resources.getDimensionPixelSize(it)
                view.updateLayoutParams {
                    height = heightPx + insets.bottom
                }
            }

            widthResId?.let {
                val widthPx = view.resources.getDimensionPixelSize(it)
                view.updateLayoutParams {
                    val startInset = if (view.isRtl()) insets.right else insets.left
                    width = if (startInset > 0) widthPx + startInset else widthPx
                }
            }

            if (overlayCutout && isLayout(PHONE)) {
                // Draw a black overlay over the cutout. We do this so that
                // it doesn't use the fragment background. We want it to
                // appear as if the screen actually ends at cutout.
                val cutout = windowInsets.displayCutout
                if (cutout != null) {
                    val left = if (!leftCheck) 0 else cutout.safeInsetLeft
                    val right = if (!rightCheck) 0 else cutout.safeInsetRight
                    view.overlay.clear()
                    if (left > 0 || right > 0) {
                        view.overlay.add(
                            CutoutOverlayDrawable(
                                view,
                                leftCutout = left,
                                rightCutout = right
                            )
                        )
                    }
                }
            }

            WindowInsetsCompat.CONSUMED
        }
    }

    fun Context.getNavigationBarHeight(): Int {
        var result = 0
        val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = resources.getDimensionPixelSize(resourceId)
        }
        return result
    }

    fun Context?.isBottomLayout(): Boolean {
        if (this == null) return true
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
        return settingsManager.getBoolean(getString(R.string.bottom_title_key), true)
    }

    fun Activity.changeStatusBarState(hide: Boolean) {
        try {
            if (hide) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    controller.hide(WindowInsetsCompat.Type.statusBars())
                } else {
                    @Suppress("DEPRECATION")
                    window.setFlags(
                        WindowManager.LayoutParams.FLAG_FULLSCREEN,
                        WindowManager.LayoutParams.FLAG_FULLSCREEN
                    )
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    controller.show(WindowInsetsCompat.Type.statusBars())
                } else {
                    @Suppress("DEPRECATION")
                    window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                }
            }
        } catch (t: Throwable) {
            logError(t)
        }
    }

    // Shows the system bars by removing all the flags
    // except for the ones that make the content appear under the system bars.
    fun Activity.showSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            if (isLayout(EMULATOR)) {
                controller.show(WindowInsetsCompat.Type.navigationBars())
                controller.hide(WindowInsetsCompat.Type.statusBars())
            } else controller.show(WindowInsetsCompat.Type.systemBars())
            return
        }

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            (View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)

        changeStatusBarState(isLayout(EMULATOR))
    }

    fun hideKeyboard(view: View?) {
        if (view == null) return

        val inputMethodManager =
            view.context.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager?
        inputMethodManager?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    fun showInputMethod(view: View?) {
        if (view == null) return
        val inputMethodManager =
            view.context.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager?
        inputMethodManager?.showSoftInput(view, 0)
    }

    fun Dialog?.dismissSafe(activity: Activity?) {
        if (this?.isShowing == true && activity?.isFinishing == false) {
            this.dismiss()
        }
    }

    fun Dialog?.dismissSafe() {
        if (this?.isShowing == true && activity?.isFinishing != true) {
            this.dismiss()
        }
    }

    /**id, stringRes */
    @SuppressLint("RestrictedApi")
    fun View.popupMenuNoIcons(
        items: List<Pair<Int, Int>>,
        onMenuItemClick: MenuItem.() -> Unit,
    ): PopupMenu {
        val ctw = ContextThemeWrapper(context, R.style.PopupMenu)
        val popup = PopupMenu(
            ctw,
            this,
            Gravity.NO_GRAVITY,
            androidx.appcompat.R.attr.actionOverflowMenuStyle,
            0
        )

        items.forEach { (id, stringRes) ->
            popup.menu.add(0, id, 0, stringRes)
        }

        (popup.menu as? MenuBuilder)?.setOptionalIconsVisible(true)

        popup.setOnMenuItemClickListener {
            it.onMenuItemClick()
            true
        }

        popup.show()
        return popup
    }

    /**id, string */
    @SuppressLint("RestrictedApi")
    fun View.popupMenuNoIconsAndNoStringRes(
        items: List<Pair<Int, String>>,
        onMenuItemClick: MenuItem.() -> Unit,
    ): PopupMenu {
        val ctw = ContextThemeWrapper(context, R.style.PopupMenu)
        val popup = PopupMenu(
            ctw,
            this,
            Gravity.NO_GRAVITY,
            androidx.appcompat.R.attr.actionOverflowMenuStyle,
            0
        )

        items.forEach { (id, string) ->
            popup.menu.add(0, id, 0, string)
        }

        (popup.menu as? MenuBuilder)?.setOptionalIconsVisible(true)

        popup.setOnMenuItemClickListener {
            it.onMenuItemClick()
            true
        }

        popup.show()
        return popup
    }
}

private class CutoutOverlayDrawable(
    private val view: View,
    private val leftCutout: Int,
    private val rightCutout: Int,
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }

    override fun draw(canvas: Canvas) {
        if (leftCutout > 0) canvas.drawRect(
            0f,
            0f,
            leftCutout.toFloat(),
            view.height.toFloat(),
            paint
        )
        if (rightCutout > 0) {
            canvas.drawRect(
                view.width - rightCutout.toFloat(),
                0f, view.width.toFloat(),
                view.height.toFloat(),
                paint
            )
        }
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity() = PixelFormat.OPAQUE
}