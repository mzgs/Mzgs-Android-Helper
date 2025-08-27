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
    private var progressAnimator: ValueAnimator? = null
    
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
        if (showProgress) {
            startProgressAnimation()
        }
        
        // Dismiss after duration
        Handler(Looper.getMainLooper()).postDelayed({
            dismiss()
        }, splashDuration)
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
        onComplete?.invoke()
    }
}