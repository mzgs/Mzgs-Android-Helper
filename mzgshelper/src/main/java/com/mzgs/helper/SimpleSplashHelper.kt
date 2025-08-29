package com.mzgs.helper

import android.animation.ValueAnimator
import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity

class SimpleSplashHelper(private val activity: ComponentActivity) {
    
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
    
    class Builder(private val activity: ComponentActivity) {
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
        
        fun build(): SimpleSplashHelper = helper
    }
    
    fun show() {
        createAndShowSplash()
        
        remainingTime = splashDuration
        
        // Only start animation and timer if not paused
        if (!isPaused) {
            if (showProgress) {
                startProgressAnimation()
            }
            
            // Dismiss after duration
            handler = Handler(Looper.getMainLooper())
            dismissRunnable = Runnable {
                dismiss()
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
                addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
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
            val icon = ImageView(context).apply {
                setImageResource(activity.applicationInfo.icon)
                layoutParams = LinearLayout.LayoutParams(180, 180).apply {
                    bottomMargin = 40
                }
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            addView(icon)
            
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
                // Progress Bar with blue color for white background
                progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        600,
                        12
                    ).apply {
                        bottomMargin = 20
                    }
                    max = 100
                    progress = 0
                    // Blue progress bar
                    progressDrawable.setColorFilter(
                        Color.parseColor("#2196F3"),
                        android.graphics.PorterDuff.Mode.SRC_IN
                    )
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
                    // Blue circular progress
                    indeterminateDrawable?.setColorFilter(
                        Color.parseColor("#2196F3"),
                        android.graphics.PorterDuff.Mode.SRC_IN
                    )
                }
                addView(circularProgressBar)
                
                // Progress Text with dark color
                progressText = TextView(context).apply {
                    text = "Loading... 0%"
                    setTextColor(Color.parseColor("#757575")) // Medium gray
                    textSize = 14f
                    gravity = Gravity.CENTER
                    letterSpacing = 0.05f
                }
                addView(progressText)
            }
        }
    }
    
    private fun startProgressAnimation() {
        progressAnimator = ValueAnimator.ofInt(0, 100).apply {
            duration = splashDuration
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Int
                progressBar?.progress = progress
                progressText?.text = "Loading... $progress%"
            }
            start()
        }
    }
    
    private fun dismiss() {
        progressAnimator?.cancel()
        splashDialog?.dismiss()
        handler?.removeCallbacks(dismissRunnable ?: return)
        onComplete?.invoke()
    }
    
    fun pause() {
        if (!isPaused) {
            isPaused = true
            lastPauseTime = System.currentTimeMillis()
            
            // Only do these if dialog is actually showing
            if (splashDialog?.isShowing == true) {
                // Pause the progress animation
                progressAnimator?.pause()
                
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
            
            // Create handler and runnable if needed
            if (handler == null) {
                handler = Handler(Looper.getMainLooper())
            }
            if (dismissRunnable == null) {
                dismissRunnable = Runnable {
                    dismiss()
                }
            }
            
            // Reschedule the dismiss with remaining time
            if (remainingTime > 0) {
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