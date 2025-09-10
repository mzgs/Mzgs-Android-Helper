package com.mzgs.helper

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

class SimpleSplashHelper(private val activity: Activity) {
    
    private var splashDuration: Long = 3000L
    private var onComplete: (() -> Unit)? = null
    private var showProgress: Boolean = true
    private var splashDialog: Dialog? = null
    private var progressBar: ProgressBar? = null
    private var progressText: TextView? = null
    private var circularProgressBar: ProgressBar? = null
    private var progressAnimator: ValueAnimator? = null
    private var handler: Handler? = null
    private var dismissRunnable: Runnable? = null
    private var remainingTime: Long = 0L
    private var lastPauseTime: Long = 0L
    private var isPaused: Boolean = false
    private var onCompleteInvoked: Boolean = false
    private var rotateLogo: Boolean = false
    private var logoAnimatorSet: AnimatorSet? = null
    private var logoImageView: ImageView? = null
    
    class Builder(private val activity: Activity) {
        private val helper = SimpleSplashHelper(activity)
        
        fun setDuration(millis: Long): Builder {
            helper.splashDuration = millis
            return this
        }
        
        fun showProgress(show: Boolean): Builder {
            helper.showProgress = show
            return this
        }
        
        fun onComplete(callback: () -> Unit): Builder {
            helper.onComplete = callback
            return this
        }
        
        fun setRotateLogo(rotate: Boolean): Builder {
            helper.rotateLogo = rotate
            return this
        }
        
        fun build(): SimpleSplashHelper = helper
    }
    
    fun show() {
        createAndShowSplash()
        
        remainingTime = splashDuration
        
        // Start logo rotation if enabled
        if (rotateLogo && logoImageView != null) {
            startLogoRotationAnimation()
        }
        
        // Only start animation and timer if not paused
        if (!isPaused) {
            if (showProgress) {
                startProgressAnimation()
            }
            
            handler = Handler(Looper.getMainLooper())
            
            // Trigger onComplete 1 second before dismissal
            if (splashDuration > 1000L) {
                handler?.postDelayed({
                    if (!onCompleteInvoked) {
                        onComplete?.invoke()
                        onCompleteInvoked = true
                    }
                }, splashDuration - 1000L)
            } else {
                // If duration is 1 second or less, trigger immediately
                if (!onCompleteInvoked) {
                    onComplete?.invoke()
                    onCompleteInvoked = true
                }
            }
            
            // Dismiss after full duration
            dismissRunnable = Runnable {
                splashDialog?.dismiss()
                progressAnimator?.cancel()
                handler?.removeCallbacks(dismissRunnable ?: return@Runnable)
            }
            handler?.postDelayed(dismissRunnable!!, splashDuration)
        } else {
            // If starting paused, show circular progress
            showCircularProgress()
        }
    }
    
    private fun createAndShowSplash() {
        splashDialog = Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen).apply {
            setContentView(createSplashView())
            window?.apply {
                // Create white gradient background
                val gradientDrawable = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(
                        Color.parseColor("#FFFFFF"), // Pure white
                        Color.parseColor("#F5F5F5")  // Light gray
                    )
                )
                setBackgroundDrawable(gradientDrawable)
                // FLAG_FULLSCREEN is deprecated, use WindowInsetsController instead
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
    
    private fun createSplashView(): LinearLayout {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            // Transparent background to show gradient behind
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            
            // Add padding to the container
            setPadding(40, 0, 40, 0)
            
            // App Icon with better size
            logoImageView = ImageView(context).apply {
                setImageResource(activity.applicationInfo.icon)
                layoutParams = LinearLayout.LayoutParams(240, 240).apply {
                    bottomMargin = 40
                }
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            addView(logoImageView)
            
            // App Name with dark text for white background
            val appName = TextView(context).apply {
                text = activity.applicationInfo.loadLabel(activity.packageManager)
                setTextColor(Color.parseColor("#212121")) // Dark gray text
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
            
            if (showProgress) {
                // Progress Bar with beautiful green gradient and rounded corners
                progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        700,
                        36  // Much thicker progress bar
                    ).apply {
                        bottomMargin = 28
                    }
                    max = 100
                    progress = 0
                    
                    // Create custom drawable with rounded corners and flat blue
                    val progressDrawable = android.graphics.drawable.LayerDrawable(
                        arrayOf(
                            // Background track
                            GradientDrawable().apply {
                                cornerRadius = 18f
                                setColor(Color.parseColor("#E0E0E0"))
                            },
                            // Secondary progress (not used)
                            GradientDrawable().apply {
                                cornerRadius = 18f
                                setColor(Color.TRANSPARENT)
                            },
                            // Primary progress with flat blue
                            android.graphics.drawable.ClipDrawable(
                                GradientDrawable().apply {
                                    cornerRadius = 18f
                                    setColor(Color.parseColor("#2196F3")) // Flat blue color
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
                }
                addView(progressBar)
                
                // Circular Progress Bar (hidden by default)
                circularProgressBar = ProgressBar(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        120,
                        120
                    ).apply {
                        bottomMargin = 20
                    }
                    visibility = android.view.View.GONE
                    // Blue circular progress to match
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
                
                // Progress Text - just percentage
                progressText = TextView(context).apply {
                    text = "0%"
                    setTextColor(Color.parseColor("#424242")) // Darker gray for better contrast
                    textSize = 20f
                    gravity = Gravity.CENTER
                    letterSpacing = 0.1f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setPadding(0, 8, 0, 0)
                }
                addView(progressText)
            }
        }
    }
    
    private fun startProgressAnimation() {
        progressAnimator = ValueAnimator.ofInt(0, 100).apply {
            duration = splashDuration
            interpolator = AccelerateDecelerateInterpolator() // Smooth acceleration/deceleration
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Int
                progressBar?.progress = progress
                progressText?.text = "$progress%"
            }
            start()
        }
    }
    
    private fun startLogoRotationAnimation() {
        logoImageView?.let { logo ->
            // Create rotation animation
            val rotationAnimator = ObjectAnimator.ofFloat(logo, "rotation", 0f, 360f).apply {
                duration = 1500 // 1.5 seconds for one full rotation (faster)
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                interpolator = AccelerateDecelerateInterpolator()
            }
            
            // Create pulse animation for scaleX
            val pulseScaleX = ObjectAnimator.ofFloat(logo, "scaleX", 1f, 1.15f, 1f).apply {
                duration = 1500 // 1.5 seconds for one pulse
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                interpolator = AccelerateDecelerateInterpolator()
            }
            
            // Create pulse animation for scaleY
            val pulseScaleY = ObjectAnimator.ofFloat(logo, "scaleY", 1f, 1.15f, 1f).apply {
                duration = 1500 // 1.5 seconds for one pulse
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                interpolator = AccelerateDecelerateInterpolator()
            }
            
            // Combine all animations
            logoAnimatorSet = AnimatorSet().apply {
                playTogether(rotationAnimator, pulseScaleX, pulseScaleY)
                start()
            }
        }
    }
    
    private fun dismiss() {
        progressAnimator?.cancel()
        logoAnimatorSet?.cancel()
        splashDialog?.dismiss()
        handler?.removeCallbacks(dismissRunnable ?: return)
        // onComplete is now called 1 second before dismissal in show()
    }
    
    fun pause() {
        if (!isPaused) {
            isPaused = true
            lastPauseTime = System.currentTimeMillis()
            
            // Only do these if dialog is actually showing
            if (splashDialog?.isShowing == true) {
                // Pause the progress animation
                progressAnimator?.pause()
                
                // Logo rotation animation continues even when paused (not affected by pause)
                // logoRotationAnimator?.pause() // REMOVED - logo keeps rotating
                
                // Cancel the dismiss handler
                dismissRunnable?.let {
                    handler?.removeCallbacks(it)
                }
                
                // Calculate remaining time
                val elapsedTime = splashDuration - remainingTime
                remainingTime = splashDuration - elapsedTime - (System.currentTimeMillis() - lastPauseTime)
                
                // Show circular progress when paused
                showCircularProgress()
            }
        }
    }
    
    fun resume() {
        if (isPaused && splashDialog?.isShowing == true) {
            isPaused = false
            
            // Show progress indicators
            showProgress()
            
            // Resume or start the progress animation
            if (progressAnimator == null && showProgress) {
                startProgressAnimation()
            } else {
                progressAnimator?.resume()
            }
            
            // Logo rotation animation continues independently (not affected by resume)
            // logoRotationAnimator?.resume() // REMOVED - logo keeps rotating
            
            // Create handler and runnable if needed
            if (handler == null) {
                handler = Handler(Looper.getMainLooper())
            }
            if (dismissRunnable == null) {
                dismissRunnable = Runnable {
                    splashDialog?.dismiss()
                    progressAnimator?.cancel()
                    handler?.removeCallbacks(dismissRunnable ?: return@Runnable)
                }
            }
            
            // Reschedule onComplete and dismiss with remaining time
            if (remainingTime > 0) {
                // Schedule onComplete 1 second before dismissal if not already called
                if (remainingTime > 1000L && !onCompleteInvoked) {
                    handler?.postDelayed({
                        if (!onCompleteInvoked) {
                            onComplete?.invoke()
                            onCompleteInvoked = true
                        }
                    }, remainingTime - 1000L)
                } else if (remainingTime <= 1000L && !onCompleteInvoked) {
                    // If less than 1 second remains, call immediately
                    onComplete?.invoke()
                    onCompleteInvoked = true
                }
                
                // Schedule dismissal
                dismissRunnable?.let {
                    handler?.postDelayed(it, remainingTime)
                }
            }
        }
    }
    
    fun hideProgress() {
        progressBar?.visibility = android.view.View.INVISIBLE
        progressText?.visibility = android.view.View.INVISIBLE
        circularProgressBar?.visibility = android.view.View.GONE
    }
    
    fun showProgress() {
        if (showProgress) {
            progressBar?.visibility = android.view.View.VISIBLE
            progressText?.visibility = android.view.View.VISIBLE
            circularProgressBar?.visibility = android.view.View.GONE
        }
    }
    
    private fun showCircularProgress() {
        if (showProgress) {
            progressBar?.visibility = android.view.View.INVISIBLE
            progressText?.visibility = android.view.View.INVISIBLE
            circularProgressBar?.visibility = android.view.View.VISIBLE
        }
    }
}