package com.mzgs.helper

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withStateAtLeast
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import androidx.core.graphics.toColorInt

object SimpleSplashHelper {

    private var splashDuration: Long = 3000L
    private var onComplete: (() -> Unit)? = null
    private var shouldShowProgress: Boolean = true
    private var splashDialogRef: WeakReference<Dialog>? = null
    private var progressBarId: Int = View.NO_ID
    private var progressTextId: Int = View.NO_ID
    private var circularProgressBarId: Int = View.NO_ID
    private var logoImageViewId: Int = View.NO_ID
    private var progressAnimator: ValueAnimator? = null
    private var handler: Handler? = null
    private var dismissRunnable: Runnable? = null
    private var onCompleteInvoked: Boolean = false
    private var rotateLogo: Boolean = true
    private var logoAnimatorSet: AnimatorSet? = null
    private var progressStarted: Boolean = false

    fun showSplash(activity: Activity, duration: Long = 3000L) {
        dismiss()
        resetState()
        this.splashDuration = duration
        createAndShowSplash(activity)
        startLogoRotationAnimation()
        showCircularProgress()
    }

    fun setDuration(millis: Long): SimpleSplashHelper = apply { splashDuration = millis }

    fun setShowProgress(show: Boolean): SimpleSplashHelper = apply { shouldShowProgress = show }

    fun setOnComplete(callback: () -> Unit): SimpleSplashHelper = apply { onComplete = callback }

    fun setRotateLogo(rotate: Boolean): SimpleSplashHelper = apply { rotateLogo = rotate }

    @JvmOverloads
    fun startProgress(activity: Activity? = null) {
        if (progressStarted) return
        progressStarted = true
        val callbackActivity = activity

        handler = handler ?: Handler(Looper.getMainLooper())

        if (shouldShowProgress) {
            showProgressIndicators()
            progressAnimator = ValueAnimator.ofInt(0, 100).apply {
                duration = splashDuration
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { animator ->
                    val progress = animator.animatedValue as Int
                    findProgressBar()?.progress = progress
                    findProgressText()?.text = "$progress%"
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        invokeCompleteAndDismiss(callbackActivity)
                    }
                })
                start()
            }
        } else {
            dismissRunnable = Runnable { invokeCompleteAndDismiss(callbackActivity) }
            handler?.postDelayed(dismissRunnable!!, splashDuration)
        }
    }

    @JvmOverloads
    fun hideSplash(activity: Activity? = null) {
        invokeCompleteAndDismiss(activity)
    }

    private fun createAndShowSplash(currentActivity: Activity) {
        val dialog = Dialog(currentActivity, android.R.style.Theme_Black_NoTitleBar_Fullscreen).apply {
            setContentView(createSplashView(currentActivity))
            window?.apply {
                val gradientDrawable = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(
                        "#FFFFFF".toColorInt(),
                        "#F5F5F5".toColorInt()
                    )
                )
                setBackgroundDrawable(gradientDrawable)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    window?.insetsController?.hide(WindowInsets.Type.statusBars())
                } else {
                    @Suppress("DEPRECATION")
                    addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                }
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
            setCancelable(false)
            show()
        }
        splashDialogRef = WeakReference(dialog)
    }

    private fun createSplashView(currentActivity: Activity): LinearLayout {
        return LinearLayout(currentActivity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            setPadding(40, 0, 40, 0)

            val logo = ImageView(context).apply {
                logoImageViewId = View.generateViewId()
                id = logoImageViewId
                setImageResource(currentActivity.applicationInfo.icon)
                layoutParams = LinearLayout.LayoutParams(240, 240).apply {
                    bottomMargin = 40
                }
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            addView(logo)

            val appName = TextView(context).apply {
                text = currentActivity.applicationInfo.loadLabel(currentActivity.packageManager)
                setTextColor("#212121".toColorInt())
                textSize = 26f
                gravity = Gravity.CENTER
                setShadowLayer(2f, 0f, 1f, "#20000000".toColorInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 60
                }
            }
            addView(appName)

            if (shouldShowProgress) {
                val horizontalProgressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                    progressBarId = View.generateViewId()
                    id = progressBarId
                    layoutParams = LinearLayout.LayoutParams(
                        700,
                        36
                    ).apply {
                        bottomMargin = 28
                    }
                    max = 100
                    progress = 0

                    val progressDrawable = android.graphics.drawable.LayerDrawable(
                        arrayOf(
                            GradientDrawable().apply {
                                cornerRadius = 18f
                                setColor("#E0E0E0".toColorInt())
                            },
                            GradientDrawable().apply {
                                cornerRadius = 18f
                                setColor(Color.TRANSPARENT)
                            },
                            android.graphics.drawable.ClipDrawable(
                                GradientDrawable().apply {
                                    cornerRadius = 18f
                                    setColor("#2196F3".toColorInt())
                                },
                                Gravity.START,
                                android.graphics.drawable.ClipDrawable.HORIZONTAL
                            )
                        )
                    )

                    progressDrawable.setId(0, android.R.id.background)
                    progressDrawable.setId(1, android.R.id.secondaryProgress)
                    progressDrawable.setId(2, android.R.id.progress)

                    this.progressDrawable = progressDrawable
                    visibility = View.GONE
                }
                addView(horizontalProgressBar)

                val circularProgress = ProgressBar(context).apply {
                    circularProgressBarId = View.generateViewId()
                    id = circularProgressBarId
                    layoutParams = LinearLayout.LayoutParams(
                        120,
                        120
                    ).apply {
                        bottomMargin = 20
                    }
                    visibility = View.VISIBLE
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        indeterminateDrawable?.colorFilter = android.graphics.BlendModeColorFilter(
                            "#2196F3".toColorInt(),
                            android.graphics.BlendMode.SRC_IN
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        indeterminateDrawable?.setColorFilter(
                            "#2196F3".toColorInt(),
                            android.graphics.PorterDuff.Mode.SRC_IN
                        )
                    }
                }
                addView(circularProgress)

                val progressLabel = TextView(context).apply {
                    progressTextId = View.generateViewId()
                    id = progressTextId
                    text = "0%"
                    setTextColor("#424242".toColorInt())
                    textSize = 20f
                    gravity = Gravity.CENTER
                    letterSpacing = 0.1f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setPadding(0, 8, 0, 0)
                    visibility = View.GONE
                }
                addView(progressLabel)
            }
        }
    }

    private fun startLogoRotationAnimation() {
        if (!rotateLogo) return
        findLogoImageView()?.let { logo ->
            val rotationAnimator = ObjectAnimator.ofFloat(logo, "rotation", 0f, 360f).apply {
                duration = 1500
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                interpolator = AccelerateDecelerateInterpolator()
            }

            val pulseScaleX = ObjectAnimator.ofFloat(logo, "scaleX", 1f, 1.15f, 1f).apply {
                duration = 1500
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                interpolator = AccelerateDecelerateInterpolator()
            }

            val pulseScaleY = ObjectAnimator.ofFloat(logo, "scaleY", 1f, 1.15f, 1f).apply {
                duration = 1500
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                interpolator = AccelerateDecelerateInterpolator()
            }

            logoAnimatorSet = AnimatorSet().apply {
                playTogether(rotationAnimator, pulseScaleX, pulseScaleY)
                start()
            }
        }
    }

    private fun dismiss() {
        progressAnimator?.cancel()
        logoAnimatorSet?.cancel()
        try {
            if (splashDialogRef?.get()?.isShowing == true) {
                splashDialogRef?.get()?.dismiss()
            }
        } catch (e: IllegalArgumentException) {
            // Dialog was already dismissed or window was detached
        }
        dismissRunnable?.let { handler?.removeCallbacks(it) }
        progressStarted = false
    }

    private fun resetState() {
        splashDuration = 3000L
        onComplete = null
        shouldShowProgress = true
        splashDialogRef = null
        progressBarId = View.NO_ID
        progressTextId = View.NO_ID
        circularProgressBarId = View.NO_ID
        logoImageViewId = View.NO_ID
        progressAnimator = null
        handler = null
        dismissRunnable = null
        onCompleteInvoked = false
        rotateLogo = true
        logoAnimatorSet = null
        progressStarted = false
    }

    fun hideProgress() {
        findProgressBar()?.visibility = View.INVISIBLE
        findProgressText()?.visibility = View.INVISIBLE
        findCircularProgressBar()?.visibility = View.GONE
    }

    private fun showProgressIndicators() {
        if (shouldShowProgress) {
            findProgressBar()?.visibility = View.VISIBLE
            findProgressText()?.visibility = View.VISIBLE
            findCircularProgressBar()?.visibility = View.GONE
        }
    }

    private fun showCircularProgress() {
        if (shouldShowProgress) {
            findProgressBar()?.visibility = View.INVISIBLE
            findProgressText()?.visibility = View.INVISIBLE
            findCircularProgressBar()?.visibility = View.VISIBLE
        }
    }

    private fun invokeCompleteAndDismiss(currentActivity: Activity?) {
        if (!onCompleteInvoked) {
            onCompleteInvoked = true
            runOnCompleteWhenResumed(currentActivity)
        }
        dismiss()
        resetState()
    }

    private fun runOnCompleteWhenResumed(currentActivity: Activity?) {
        val callback = onComplete ?: return

        // If we don't have a lifecycle owner, run immediately
        val componentActivity = currentActivity as? ComponentActivity ?: run {
            callback.invoke()
            return
        }

        // Run callback once the activity is safely RESUMED to avoid ActivityResultRegistry crashes
        componentActivity.lifecycleScope.launch {
            componentActivity.lifecycle.withStateAtLeast(Lifecycle.State.RESUMED) {
                if (!componentActivity.isFinishing && !componentActivity.isDestroyed) {
                    callback.invoke()
                }
            }
        }
    }

    private fun findProgressBar(): ProgressBar? {
        val dialog = splashDialogRef?.get() ?: return null
        if (progressBarId == View.NO_ID) return null
        return dialog.findViewById(progressBarId)
    }

    private fun findProgressText(): TextView? {
        val dialog = splashDialogRef?.get() ?: return null
        if (progressTextId == View.NO_ID) return null
        return dialog.findViewById(progressTextId)
    }

    private fun findCircularProgressBar(): ProgressBar? {
        val dialog = splashDialogRef?.get() ?: return null
        if (circularProgressBarId == View.NO_ID) return null
        return dialog.findViewById(circularProgressBarId)
    }

    private fun findLogoImageView(): ImageView? {
        val dialog = splashDialogRef?.get() ?: return null
        if (logoImageViewId == View.NO_ID) return null
        return dialog.findViewById(logoImageViewId)
    }
}
