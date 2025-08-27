package com.mzgs.helper

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.PorterDuff
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.animation.BounceInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.AnimRes
import androidx.annotation.DrawableRes
import androidx.activity.ComponentActivity

class SplashScreenHelper(private val activity: ComponentActivity) {

    private var splashDuration: Long = DEFAULT_SPLASH_DURATION
    private var targetActivity: Class<*>? = null
    private var fadeInAnimation: Int? = null
    private var fadeOutAnimation: Int? = null
    private var onSplashComplete: (() -> Unit)? = null
    private var keepOnScreenCondition: () -> Boolean = { false }
    private var customTransition: Pair<Int, Int>? = null
    private var isKeepingOnScreen = true
    private var splashStartTime: Long = 0
    private var showProgressBar: Boolean = false
    private var progressBarStyle: ProgressBarStyle = ProgressBarStyle.HORIZONTAL
    private var progressUpdateInterval: Long = 50L
    private var progressDialog: Dialog? = null
    private var progressAnimator: ValueAnimator? = null
    private var onProgressUpdate: ((Int) -> Unit)? = null
    private var logoResourceId: Int? = null
    private var splashBackgroundColor: Int = Color.BLACK
    private var progressTextColor: Int = Color.WHITE
    private var useGradientBackground: Boolean = true
    private var gradientStartColor: Int = Color.parseColor("#1a1a2e")
    private var gradientEndColor: Int = Color.parseColor("#0f0f1e")
    private var progressBarColor: Int = Color.parseColor("#00D4FF")
    private var secondaryProgressBarColor: Int = Color.parseColor("#FF006E")
    
    enum class ProgressBarStyle {
        HORIZONTAL,
        CIRCULAR,
        BOTH
    }

    companion object {
        private const val DEFAULT_SPLASH_DURATION = 2000L
        private const val MIN_SPLASH_DURATION = 500L
        private const val MAX_SPLASH_DURATION = 10000L
    }

    class Builder(private val activity: ComponentActivity) {
        private val helper = SplashScreenHelper(activity)

        fun setDuration(duration: Long): Builder {
            helper.splashDuration = duration.coerceIn(MIN_SPLASH_DURATION, MAX_SPLASH_DURATION)
            return this
        }

        fun setTargetActivity(targetClass: Class<*>): Builder {
            helper.targetActivity = targetClass
            return this
        }

        fun setFadeInAnimation(@AnimRes animationRes: Int): Builder {
            helper.fadeInAnimation = animationRes
            return this
        }

        fun setFadeOutAnimation(@AnimRes animationRes: Int): Builder {
            helper.fadeOutAnimation = animationRes
            return this
        }

        fun setOnSplashComplete(callback: () -> Unit): Builder {
            helper.onSplashComplete = callback
            return this
        }

        fun setKeepOnScreenCondition(condition: () -> Boolean): Builder {
            helper.keepOnScreenCondition = condition
            return this
        }

        fun setCustomTransition(enterAnim: Int, exitAnim: Int): Builder {
            helper.customTransition = Pair(enterAnim, exitAnim)
            return this
        }
        
        fun showProgressBar(show: Boolean): Builder {
            helper.showProgressBar = show
            return this
        }
        
        fun setProgressBarStyle(style: ProgressBarStyle): Builder {
            helper.progressBarStyle = style
            return this
        }
        
        fun setOnProgressUpdate(callback: (Int) -> Unit): Builder {
            helper.onProgressUpdate = callback
            return this
        }
        
        fun setLogoResource(@DrawableRes logoResId: Int): Builder {
            helper.logoResourceId = logoResId
            return this
        }
        
        fun setSplashBackgroundColor(color: Int): Builder {
            helper.splashBackgroundColor = color
            return this
        }
        
        fun setProgressTextColor(color: Int): Builder {
            helper.progressTextColor = color
            return this
        }

        fun build(): SplashScreenHelper = helper
    }

    fun show() {
        // Always use custom splash implementation for full control
        showCustomSplash()
    }

    private fun showCustomSplash() {
        activity.window.apply {
            addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }

        if (showProgressBar) {
            showProgressDialog()
        }

        fadeInAnimation?.let { animRes ->
            val animation = AnimationUtils.loadAnimation(activity, animRes)
            activity.window.decorView.startAnimation(animation)
        }

        Handler(Looper.getMainLooper()).postDelayed({
            progressAnimator?.cancel()
            progressDialog?.dismiss()
            
            fadeOutAnimation?.let { animRes ->
                val animation = AnimationUtils.loadAnimation(activity, animRes)
                animation.setAnimationListener(object : Animation.AnimationListener {
                    override fun onAnimationStart(animation: Animation?) {}
                    override fun onAnimationRepeat(animation: Animation?) {}
                    override fun onAnimationEnd(animation: Animation?) {
                        navigateToNextScreen()
                    }
                })
                activity.window.decorView.startAnimation(animation)
            } ?: run {
                navigateToNextScreen()
            }
        }, splashDuration)
    }
    
    private fun showProgressDialog() {
        progressDialog = Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen).apply {
            val contentView = createProgressView()
            setContentView(contentView)
            window?.apply {
                // Set gradient or solid color background
                if (useGradientBackground) {
                    setBackgroundDrawable(createGradientBackground())
                } else {
                    setBackgroundDrawable(ColorDrawable(splashBackgroundColor))
                }
                addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                
                // Add fade-in animation to the window
                attributes?.windowAnimations = android.R.style.Animation_Dialog
            }
            setCancelable(false)
            show()
            
            // Start entrance animations after showing
            animateEntrance(contentView)
        }
        
        startProgressAnimation()
    }
    
    private fun createGradientBackground(): GradientDrawable {
        return GradientDrawable().apply {
            orientation = GradientDrawable.Orientation.TOP_BOTTOM
            colors = intArrayOf(gradientStartColor, gradientEndColor)
        }
    }
    
    private fun animateEntrance(contentView: View) {
        if (contentView !is ViewGroup) return
        
        val viewGroup = contentView as ViewGroup
        
        // Animate logo with bounce effect
        viewGroup.getChildAt(0)?.let { logo ->
            logo.scaleX = 0f
            logo.scaleY = 0f
            logo.alpha = 0f
            
            val scaleXAnim = ObjectAnimator.ofFloat(logo, "scaleX", 0f, 1.1f, 1f)
            val scaleYAnim = ObjectAnimator.ofFloat(logo, "scaleY", 0f, 1.1f, 1f)
            val alphaAnim = ObjectAnimator.ofFloat(logo, "alpha", 0f, 1f)
            
            AnimatorSet().apply {
                playTogether(scaleXAnim, scaleYAnim, alphaAnim)
                duration = 800
                interpolator = BounceInterpolator()
                startDelay = 100
                start()
            }
        }
        
        // Animate app name with fade and slide
        viewGroup.getChildAt(1)?.let { appName ->
            appName.alpha = 0f
            appName.translationY = 50f
            
            appName.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(600)
                .setInterpolator(DecelerateInterpolator())
                .setStartDelay(400)
                .start()
        }
        
        // Animate progress bar with fade in
        for (i in 2 until viewGroup.childCount) {
            viewGroup.getChildAt(i)?.let { view ->
                view.alpha = 0f
                view.animate()
                    .alpha(1f)
                    .setDuration(500)
                    .setStartDelay(600L + (i * 100L))
                    .start()
            }
        }
    }
    
    private var currentProgressBar: ProgressBar? = null
    private var currentLoadingText: TextView? = null
    
    private fun createProgressView(): View {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setPadding(40, 0, 40, 0)
            
            // Add app logo with shadow effect
            val logoContainer = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(250, 250).apply {
                    bottomMargin = 40
                }
            }
            
            val logoImageView = ImageView(context).apply {
                // Use provided logo or default app icon
                val logoRes = logoResourceId ?: activity.applicationInfo.icon
                setImageResource(logoRes)
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ).apply {
                    setMargins(25, 25, 25, 25)
                }
                scaleType = ImageView.ScaleType.FIT_CENTER
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    elevation = 8f
                }
            }
            logoContainer.addView(logoImageView)
            addView(logoContainer)
            
            // Add app name with custom styling
            val appNameText = TextView(context).apply {
                text = activity.applicationInfo.loadLabel(activity.packageManager)
                setTextColor(progressTextColor)
                textSize = 28f
                gravity = Gravity.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                setShadowLayer(4f, 0f, 2f, Color.parseColor("#40000000"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 60
                }
            }
            addView(appNameText)
            
            when (progressBarStyle) {
                ProgressBarStyle.HORIZONTAL -> {
                    val progressBar = createHorizontalProgressBar()
                    currentProgressBar = progressBar
                    addView(progressBar)
                }
                ProgressBarStyle.CIRCULAR -> {
                    addView(createCircularProgressBar())
                }
                ProgressBarStyle.BOTH -> {
                    addView(createCircularProgressBar())
                    addView(View(context).apply { 
                        layoutParams = LinearLayout.LayoutParams(1, 40)
                    })
                    val progressBar = createHorizontalProgressBar()
                    currentProgressBar = progressBar
                    addView(progressBar)
                }
            }
            
            val loadingText = TextView(context).apply {
                text = "Loading..."
                setTextColor(progressTextColor)
                textSize = 14f
                gravity = Gravity.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                alpha = 0.9f
                letterSpacing = 0.1f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 24
                }
            }
            currentLoadingText = loadingText
            addView(loadingText)
        }
    }
    
    private fun createHorizontalProgressBar(): ProgressBar {
        return ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                20
            ).apply {
                gravity = Gravity.CENTER
                leftMargin = 20
                rightMargin = 20
            }
            max = 100
            progress = 0
            isIndeterminate = false
            
            // Create custom gradient progress drawable
            progressDrawable = createCustomProgressDrawable()
            
            // Set progress tint for API 21+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                progressTintList = ColorStateList.valueOf(progressBarColor)
                progressBackgroundTintList = ColorStateList.valueOf(Color.parseColor("#33FFFFFF"))
            }
        }
    }
    
    private fun createCustomProgressDrawable(): GradientDrawable {
        return GradientDrawable().apply {
            orientation = GradientDrawable.Orientation.LEFT_RIGHT
            colors = intArrayOf(
                progressBarColor,
                secondaryProgressBarColor,
                progressBarColor
            )
            cornerRadius = 10f
            gradientType = GradientDrawable.LINEAR_GRADIENT
        }
    }
    
    private fun createCircularProgressBar(): ProgressBar {
        return ProgressBar(activity).apply {
            layoutParams = LinearLayout.LayoutParams(80, 80)
            isIndeterminate = true
            
            // Set circular progress color
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                indeterminateTintList = ColorStateList.valueOf(progressBarColor)
            }
        }
    }
    
    private fun startProgressAnimation() {
        if (progressBarStyle == ProgressBarStyle.CIRCULAR) {
            // Animate loading text for circular progress
            animateLoadingText()
            return
        }
        
        progressAnimator = ValueAnimator.ofInt(0, 100).apply {
            duration = splashDuration
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Int
                currentProgressBar?.progress = progress
                
                // Update loading text with animated dots
                val dots = when (progress % 4) {
                    0 -> ""
                    1 -> "."
                    2 -> ".."
                    else -> "..."
                }
                currentLoadingText?.text = "Loading$dots $progress%"
                
                // Pulse animation on text
                currentLoadingText?.scaleX = 1f + (0.05f * Math.sin(progress * 0.1).toFloat())
                currentLoadingText?.scaleY = 1f + (0.05f * Math.sin(progress * 0.1).toFloat())
                
                onProgressUpdate?.invoke(progress)
            }
        }
        progressAnimator?.start()
    }
    
    private fun animateLoadingText() {
        val texts = arrayOf("Loading", "Loading.", "Loading..", "Loading...")
        var index = 0
        
        Handler(Looper.getMainLooper()).apply {
            val runnable = object : Runnable {
                override fun run() {
                    currentLoadingText?.text = texts[index % texts.size]
                    index++
                    postDelayed(this, 500)
                }
            }
            post(runnable)
        }
    }

    private fun navigateToNextScreen() {
        onSplashComplete?.invoke()
        
        targetActivity?.let { target ->
            val intent = Intent(activity, target).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            activity.startActivity(intent)
            
            customTransition?.let { (enter, exit) ->
                activity.overridePendingTransition(enter, exit)
            }
            
            activity.finish()
        }
    }

    fun setFullScreen() {
        activity.window.apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setDecorFitsSystemWindows(false)
            } else {
                @Suppress("DEPRECATION")
                decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
            }
        }
    }

    fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.window.insetsController?.apply {
                hide(android.view.WindowInsets.Type.statusBars())
                hide(android.view.WindowInsets.Type.navigationBars())
            }
        } else {
            @Suppress("DEPRECATION")
            activity.window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }
    }
}

abstract class BaseSplashActivity : ComponentActivity() {
    
    protected lateinit var splashHelper: SplashScreenHelper
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        splashHelper = SplashScreenHelper.Builder(this)
            .setDuration(getSplashDuration())
            .setTargetActivity(getTargetActivity())
            .setOnSplashComplete { onSplashComplete() }
            .build()
        
        splashHelper.show()
    }
    
    protected abstract fun getSplashDuration(): Long
    protected abstract fun getTargetActivity(): Class<*>
    protected open fun onSplashComplete() {}
}