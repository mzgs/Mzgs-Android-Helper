package com.mzgs.helper

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.annotation.DrawableRes
import androidx.annotation.LayoutRes

open class SplashActivity : ComponentActivity() {
    
    private var progressBar: ProgressBar? = null
    private var loadingText: TextView? = null
    private var logoImage: ImageView? = null
    private var progressAnimator: ValueAnimator? = null
    
    protected open val splashDuration: Long = 3000L
    protected open val showProgressBar: Boolean = true
    protected open val progressBarStyle: ProgressBarStyle = ProgressBarStyle.HORIZONTAL
    protected open val customLayoutId: Int? = null
    protected open val logoResourceId: Int? = null
    protected open val loadingMessage: String = "Loading..."
    
    enum class ProgressBarStyle {
        HORIZONTAL,
        CIRCULAR,
        BOTH,
        NONE
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Use custom layout or default
        val layoutId = customLayoutId ?: R.layout.splash_screen_layout
        setContentView(layoutId)
        
        setupViews()
        configureProgressBar()
        startSplashAnimation()
    }
    
    private fun setupViews() {
        progressBar = findViewById(R.id.splash_progress_horizontal)
        loadingText = findViewById(R.id.splash_loading_text)
        logoImage = findViewById(R.id.splash_logo)
        
        // Set custom logo if provided
        logoResourceId?.let { resId ->
            logoImage?.setImageResource(resId)
        }
        
        // Set loading message
        loadingText?.text = loadingMessage
        
        // Configure progress bar visibility based on style
        when (progressBarStyle) {
            ProgressBarStyle.HORIZONTAL -> {
                findViewById<View>(R.id.splash_progress_horizontal)?.visibility = View.VISIBLE
                findViewById<View>(R.id.splash_progress_circular)?.visibility = View.GONE
            }
            ProgressBarStyle.CIRCULAR -> {
                findViewById<View>(R.id.splash_progress_horizontal)?.visibility = View.GONE
                findViewById<View>(R.id.splash_progress_circular)?.visibility = View.VISIBLE
                progressBar = findViewById(R.id.splash_progress_circular)
            }
            ProgressBarStyle.BOTH -> {
                findViewById<View>(R.id.splash_progress_horizontal)?.visibility = View.VISIBLE
                findViewById<View>(R.id.splash_progress_circular)?.visibility = View.VISIBLE
            }
            ProgressBarStyle.NONE -> {
                findViewById<View>(R.id.splash_progress_horizontal)?.visibility = View.GONE
                findViewById<View>(R.id.splash_progress_circular)?.visibility = View.GONE
                loadingText?.visibility = View.GONE
            }
        }
    }
    
    private fun configureProgressBar() {
        if (!showProgressBar || progressBarStyle == ProgressBarStyle.NONE) {
            return
        }
        
        // For horizontal progress bar, animate the progress
        if (progressBarStyle == ProgressBarStyle.HORIZONTAL || progressBarStyle == ProgressBarStyle.BOTH) {
            progressBar?.max = 100
            progressBar?.progress = 0
            
            progressAnimator = ValueAnimator.ofInt(0, 100).apply {
                duration = splashDuration
                addUpdateListener { animator ->
                    val progress = animator.animatedValue as Int
                    progressBar?.progress = progress
                    
                    // Update loading text with percentage
                    loadingText?.text = "$loadingMessage ${progress}%"
                }
            }
            progressAnimator?.start()
        }
    }
    
    private fun startSplashAnimation() {
        Handler(Looper.getMainLooper()).postDelayed({
            onSplashComplete()
        }, splashDuration)
    }
    
    protected open fun onSplashComplete() {
        progressAnimator?.cancel()
        
        // Override this method to navigate to your main activity
        val targetActivity = getTargetActivity()
        if (targetActivity != null) {
            val intent = Intent(this, targetActivity)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
        
        finish()
    }
    
    protected open fun getTargetActivity(): Class<*>? {
        // Override this to return your main activity class
        return null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        progressAnimator?.cancel()
    }
}