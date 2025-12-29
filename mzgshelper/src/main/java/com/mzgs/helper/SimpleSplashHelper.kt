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
import androidx.lifecycle.whenStateAtLeast
import kotlinx.coroutines.launch

object SimpleSplashHelper {

    private var activity: Activity? = null
    private var splashDuration: Long = 3000L
    private var onComplete: (() -> Unit)? = null
    private var shouldShowProgress: Boolean = true
    private var splashDialog: Dialog? = null
    private var progressBar: ProgressBar? = null
    private var progressText: TextView? = null
    private var circularProgressBar: ProgressBar? = null
    private var progressAnimator: ValueAnimator? = null
    private var handler: Handler? = null
    private var dismissRunnable: Runnable? = null
    private var onCompleteInvoked: Boolean = false
    private var rotateLogo: Boolean = true
    private var logoAnimatorSet: AnimatorSet? = null
    private var logoImageView: ImageView? = null
    private var progressStarted: Boolean = false

    @JvmStatic
    fun showSplash(activity: Activity, duration: Long = 3000L) {
        dismiss()
        resetState()
        this.activity = activity
        this.splashDuration = duration
        createAndShowSplash(activity)
        startLogoRotationAnimation()
        showCircularProgress()
    }

    fun setDuration(millis: Long): SimpleSplashHelper = apply { splashDuration = millis }

    fun setShowProgress(show: Boolean): SimpleSplashHelper = apply { shouldShowProgress = show }

    fun setOnComplete(callback: () -> Unit): SimpleSplashHelper = apply { onComplete = callback }

    fun setRotateLogo(rotate: Boolean): SimpleSplashHelper = apply { rotateLogo = rotate }

    @JvmStatic
    fun startProgress() {
        if (progressStarted) return
        progressStarted = true

        handler = handler ?: Handler(Looper.getMainLooper())

        if (shouldShowProgress) {
            showProgressIndicators()
            progressAnimator = ValueAnimator.ofInt(0, 100).apply {
                duration = splashDuration
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { animator ->
                    val progress = animator.animatedValue as Int
                    progressBar?.progress = progress
                    progressText?.text = "$progress%"
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        invokeCompleteAndDismiss()
                    }
                })
                start()
            }
        } else {
            dismissRunnable = Runnable { invokeCompleteAndDismiss() }
            handler?.postDelayed(dismissRunnable!!, splashDuration)
        }
    }

    @JvmStatic
    fun hideSplash() {
        invokeCompleteAndDismiss()
    }

    private fun createAndShowSplash(currentActivity: Activity) {
        splashDialog = Dialog(currentActivity, android.R.style.Theme_Black_NoTitleBar_Fullscreen).apply {
            setContentView(createSplashView(currentActivity))
            window?.apply {
                val gradientDrawable = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(
                        Color.parseColor("#FFFFFF"),
                        Color.parseColor("#F5F5F5")
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

            logoImageView = ImageView(context).apply {
                setImageResource(currentActivity.applicationInfo.icon)
                layoutParams = LinearLayout.LayoutParams(240, 240).apply {
                    bottomMargin = 40
                }
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            addView(logoImageView)

            val appName = TextView(context).apply {
                text = currentActivity.applicationInfo.loadLabel(currentActivity.packageManager)
                setTextColor(Color.parseColor("#212121"))
                textSize = 26f
                gravity = Gravity.CENTER
                setShadowLayer(2f, 0f, 1f, Color.parseColor("#20000000"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 60
                }
            }
            addView(appName)

            if (shouldShowProgress) {
                progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
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
                                setColor(Color.parseColor("#E0E0E0"))
                            },
                            GradientDrawable().apply {
                                cornerRadius = 18f
                                setColor(Color.TRANSPARENT)
                            },
                            android.graphics.drawable.ClipDrawable(
                                GradientDrawable().apply {
                                    cornerRadius = 18f
                                    setColor(Color.parseColor("#2196F3"))
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
                addView(progressBar)

                circularProgressBar = ProgressBar(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        120,
                        120
                    ).apply {
                        bottomMargin = 20
                    }
                    visibility = View.VISIBLE
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        indeterminateDrawable?.colorFilter = android.graphics.BlendModeColorFilter(
                            Color.parseColor("#2196F3"),
                            android.graphics.BlendMode.SRC_IN
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        indeterminateDrawable?.setColorFilter(
                            Color.parseColor("#2196F3"),
                            android.graphics.PorterDuff.Mode.SRC_IN
                        )
                    }
                }
                addView(circularProgressBar)

                progressText = TextView(context).apply {
                    text = "0%"
                    setTextColor(Color.parseColor("#424242"))
                    textSize = 20f
                    gravity = Gravity.CENTER
                    letterSpacing = 0.1f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setPadding(0, 8, 0, 0)
                    visibility = View.GONE
                }
                addView(progressText)
            }
        }
    }

    private fun startLogoRotationAnimation() {
        if (!rotateLogo) return
        logoImageView?.let { logo ->
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
            if (splashDialog?.isShowing == true) {
                splashDialog?.dismiss()
            }
        } catch (e: IllegalArgumentException) {
            // Dialog was already dismissed or window was detached
        }
        dismissRunnable?.let { handler?.removeCallbacks(it) }
        progressStarted = false
    }

    private fun resetState() {
        activity = null
        splashDuration = 3000L
        onComplete = null
        shouldShowProgress = true
        splashDialog = null
        progressBar = null
        progressText = null
        circularProgressBar = null
        progressAnimator = null
        handler = null
        dismissRunnable = null
        onCompleteInvoked = false
        rotateLogo = true
        logoAnimatorSet = null
        logoImageView = null
        progressStarted = false
    }

    fun hideProgress() {
        progressBar?.visibility = View.INVISIBLE
        progressText?.visibility = View.INVISIBLE
        circularProgressBar?.visibility = View.GONE
    }

    private fun showProgressIndicators() {
        if (shouldShowProgress) {
            progressBar?.visibility = View.VISIBLE
            progressText?.visibility = View.VISIBLE
            circularProgressBar?.visibility = View.GONE
        }
    }

    private fun showCircularProgress() {
        if (shouldShowProgress) {
            progressBar?.visibility = View.INVISIBLE
            progressText?.visibility = View.INVISIBLE
            circularProgressBar?.visibility = View.VISIBLE
        }
    }

    private fun invokeCompleteAndDismiss() {
        if (!onCompleteInvoked) {
            onCompleteInvoked = true
            runOnCompleteWhenResumed()
        }
        dismiss()
        resetState()
    }

    private fun runOnCompleteWhenResumed() {
        val callback = onComplete ?: return
        val currentActivity = activity ?: return

        // If we don't have a lifecycle owner, run immediately
        val componentActivity = currentActivity as? ComponentActivity ?: run {
            callback.invoke()
            return
        }

        // Run callback once the activity is safely RESUMED to avoid ActivityResultRegistry crashes
        componentActivity.lifecycleScope.launch {
            componentActivity.lifecycle.whenStateAtLeast(Lifecycle.State.RESUMED) {
                if (!componentActivity.isFinishing && !componentActivity.isDestroyed) {
                    callback.invoke()
                }
            }
        }
    }
}
