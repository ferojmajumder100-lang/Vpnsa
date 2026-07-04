package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var vpnComposeView: ComposeView? = null
    private var freezeComposeView: ComposeView? = null
    private var teleComposeView: ComposeView? = null
    private var dwComposeView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null

    private var isVpnAdded = false
    private var isFreezeAdded = false
    private var isTeleAdded = false
    private var isDwAdded = false

    private val serviceJob = kotlinx.coroutines.Job()
    private val serviceScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + serviceJob)

    companion object {
        // Shared scale index for adjusting the sizes of all 4 buttons
        val buttonScaleIndex = MutableStateFlow(1) // 0 = Small, 1 = Medium, 2 = Large

        // Visibility of floating buttons
        val isVpnVisible = MutableStateFlow(true)
        val isFreezeVisible = MutableStateFlow(true)
        val isTeleVisible = MutableStateFlow(true)
        val isDwVisible = MutableStateFlow(true)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        showNotification()
        showFloatingButtons()
    }

    private fun showNotification() {
        val channelId = "overlay_service_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Overlay Controller",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Overlay Buttons Active")
            .setContentText("Traffic Controller overlay is running on your screen.")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(101, notification)
    }

    private fun showFloatingButtons() {
        Toast.makeText(
            this,
            "বাটন বড়/ছোট করার সিস্টেম অ্যাপস এর ভিতর পাবেন!",
            Toast.LENGTH_LONG
        ).show()

        // Common layout parameters helper for floating overlays
        val createLayoutParams = { defaultX: Int, defaultY: Int ->
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = defaultX
                y = defaultY
            }
        }

        val vpnParams = createLayoutParams(100, 300)
        val freezeParams = createLayoutParams(300, 300)
        val teleParams = createLayoutParams(100, 500)
        val dwParams = createLayoutParams(300, 500)

        lifecycleOwner = OverlayLifecycleOwner().apply {
            handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            handleLifecycleEvent(Lifecycle.Event.ON_START)
            handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        // 1. VPN Button ComposeView
        vpnComposeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)

            setContent {
                MaterialTheme {
                    val isVpnOn by MyVpnService.isVpnActive.collectAsState()
                    val countdownSec by MyVpnService.countdownSeconds.collectAsState()
                    val scaleIndex by buttonScaleIndex.collectAsState()

                    val buttonSize = when (scaleIndex) {
                        0 -> 64.dp
                        1 -> 90.dp
                        else -> 120.dp
                    }

                    val buttonTextSize = when (scaleIndex) {
                        0 -> 12.sp
                        1 -> 16.sp
                        else -> 20.sp
                    }

                    val animatedButtonSize by animateDpAsState(targetValue = buttonSize, label = "vpnButtonSize")

                    val vpnColor by animateColorAsState(
                        targetValue = if (isVpnOn) Color(0xFF4CAF50) else Color(0xFFF44336),
                        label = "vpnColor"
                    )

                    Box(
                        modifier = Modifier
                            .size(animatedButtonSize)
                            .clip(CircleShape)
                            .background(vpnColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Vpn",
                                fontSize = buttonTextSize,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            if (isVpnOn && countdownSec > 0) {
                                Text(
                                    text = "${countdownSec}s",
                                    fontSize = (buttonTextSize.value * 0.75).sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Freeze Button ComposeView
        freezeComposeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)

            setContent {
                MaterialTheme {
                    val isFreezeOn by MyVpnService.isFreezeActive.collectAsState()
                    val scaleIndex by buttonScaleIndex.collectAsState()

                    val buttonSize = when (scaleIndex) {
                        0 -> 64.dp
                        1 -> 90.dp
                        else -> 120.dp
                    }

                    val buttonTextSize = when (scaleIndex) {
                        0 -> 12.sp
                        1 -> 16.sp
                        else -> 20.sp
                    }

                    val animatedButtonSize by animateDpAsState(targetValue = buttonSize, label = "freezeButtonSize")

                    val freezeColor by animateColorAsState(
                        targetValue = if (isFreezeOn) Color(0xFF4CAF50) else Color(0xFFF44336),
                        label = "freezeColor"
                    )

                    Box(
                        modifier = Modifier
                            .size(animatedButtonSize)
                            .clip(CircleShape)
                            .background(freezeColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Frezz",
                            fontSize = buttonTextSize,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // 3. Tele Button ComposeView
        teleComposeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)

            setContent {
                MaterialTheme {
                    val isTeleOn by MyVpnService.isTeleActive.collectAsState()
                    val scaleIndex by buttonScaleIndex.collectAsState()

                    val buttonSize = when (scaleIndex) {
                        0 -> 64.dp
                        1 -> 90.dp
                        else -> 120.dp
                    }

                    val buttonTextSize = when (scaleIndex) {
                        0 -> 12.sp
                        1 -> 16.sp
                        else -> 20.sp
                    }

                    val animatedButtonSize by animateDpAsState(targetValue = buttonSize, label = "teleButtonSize")

                    val teleColor by animateColorAsState(
                        targetValue = if (isTeleOn) Color(0xFF4CAF50) else Color(0xFFF44336),
                        label = "teleColor"
                    )

                    Box(
                        modifier = Modifier
                            .size(animatedButtonSize)
                            .clip(CircleShape)
                            .background(teleColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Tele",
                            fontSize = buttonTextSize,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // 4. Dw Button ComposeView
        dwComposeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)

            setContent {
                MaterialTheme {
                    val isDwOn by MyVpnService.isDwActive.collectAsState()
                    val scaleIndex by buttonScaleIndex.collectAsState()

                    val buttonSize = when (scaleIndex) {
                        0 -> 64.dp
                        1 -> 90.dp
                        else -> 120.dp
                    }

                    val buttonTextSize = when (scaleIndex) {
                        0 -> 12.sp
                        1 -> 16.sp
                        else -> 20.sp
                    }

                    val animatedButtonSize by animateDpAsState(targetValue = buttonSize, label = "dwButtonSize")

                    val dwColor by animateColorAsState(
                        targetValue = if (isDwOn) Color(0xFF4CAF50) else Color(0xFFF44336),
                        label = "dwColor"
                    )

                    Box(
                        modifier = Modifier
                            .size(animatedButtonSize)
                            .clip(CircleShape)
                            .background(dwColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Dw",
                            fontSize = buttonTextSize,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // Set up independent drag/click listeners for each button
        setupTouchListener(vpnComposeView!!, vpnParams, buttonType = 0)
        setupTouchListener(freezeComposeView!!, freezeParams, buttonType = 1)
        setupTouchListener(teleComposeView!!, teleParams, buttonType = 2)
        setupTouchListener(dwComposeView!!, dwParams, buttonType = 3)

        // Dynamically show/hide buttons based on user selection in the app
        serviceScope.launch {
            isVpnVisible.collect { visible ->
                updateViewVisibility(vpnComposeView, vpnParams, visible, 0)
            }
        }
        serviceScope.launch {
            isFreezeVisible.collect { visible ->
                updateViewVisibility(freezeComposeView, freezeParams, visible, 1)
            }
        }
        serviceScope.launch {
            isTeleVisible.collect { visible ->
                updateViewVisibility(teleComposeView, teleParams, visible, 2)
            }
        }
        serviceScope.launch {
            isDwVisible.collect { visible ->
                updateViewVisibility(dwComposeView, dwParams, visible, 3)
            }
        }
    }

    private fun updateViewVisibility(view: ComposeView?, params: WindowManager.LayoutParams, visible: Boolean, buttonType: Int) {
        if (view == null) return
        val isAdded = when (buttonType) {
            0 -> isVpnAdded
            1 -> isFreezeAdded
            2 -> isTeleAdded
            3 -> isDwAdded
            else -> false
        }
        
        if (visible && !isAdded) {
            try {
                windowManager.addView(view, params)
                when (buttonType) {
                    0 -> isVpnAdded = true
                    1 -> isFreezeAdded = true
                    2 -> isTeleAdded = true
                    3 -> isDwAdded = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else if (!visible && isAdded) {
            try {
                windowManager.removeView(view)
                when (buttonType) {
                    0 -> isVpnAdded = false
                    1 -> isFreezeAdded = false
                    2 -> isTeleAdded = false
                    3 -> isDwAdded = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun setupTouchListener(
        view: ComposeView,
        params: WindowManager.LayoutParams,
        buttonType: Int // 0 = Vpn, 1 = Freeze, 2 = Tele, 3 = Dw
    ) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (Math.abs(dx) > 15 || Math.abs(dy) > 15) {
                        isDragging = true
                    }
                    params.x = initialX + dx.toInt()
                    params.y = initialY + dy.toInt()
                    try {
                        windowManager.updateViewLayout(view, params)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        // Handle click/toggle action
                        when (buttonType) {
                            0 -> {
                                val isVpnOn = MyVpnService.isVpnActive.value
                                if (isVpnOn) {
                                    val intent = Intent(this, MyVpnService::class.java).apply {
                                        action = MyVpnService.ACTION_STOP
                                    }
                                    startService(intent)
                                } else {
                                    val intent = Intent(this, MyVpnService::class.java).apply {
                                        action = MyVpnService.ACTION_START
                                    }
                                    startService(intent)
                                }
                            }
                            1 -> {
                                val nextFreezeState = !MyVpnService.isFreezeActive.value
                                MyVpnService.isFreezeActive.value = nextFreezeState
                                if (MyVpnService.isVpnActive.value) {
                                    val intent = Intent(this, MyVpnService::class.java).apply {
                                        action = MyVpnService.ACTION_SET_FREEZE
                                        putExtra("freeze", nextFreezeState)
                                    }
                                    startService(intent)
                                }
                            }
                            2 -> {
                                val nextTeleState = !MyVpnService.isTeleActive.value
                                MyVpnService.isTeleActive.value = nextTeleState
                                if (MyVpnService.isVpnActive.value) {
                                    val intent = Intent(this, MyVpnService::class.java).apply {
                                        action = MyVpnService.ACTION_SET_TELE
                                        putExtra("tele", nextTeleState)
                                    }
                                    startService(intent)
                                }
                            }
                            3 -> {
                                val nextDwState = !MyVpnService.isDwActive.value
                                MyVpnService.isDwActive.value = nextDwState
                                if (MyVpnService.isVpnActive.value) {
                                    val intent = Intent(this, MyVpnService::class.java).apply {
                                        action = MyVpnService.ACTION_SET_DW
                                        putExtra("dw", nextDwState)
                                    }
                                    startService(intent)
                                }
                            }
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    override fun onDestroy() {
        serviceJob.cancel()
        lifecycleOwner?.apply {
            handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
        
        val removeViewSafe = { view: ComposeView?, buttonType: Int ->
            view?.let {
                val isAdded = when (buttonType) {
                    0 -> isVpnAdded
                    1 -> isFreezeAdded
                    2 -> isTeleAdded
                    3 -> isDwAdded
                    else -> false
                }
                if (isAdded) {
                    try {
                        windowManager.removeView(it)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        removeViewSafe(vpnComposeView, 0)
        removeViewSafe(freezeComposeView, 1)
        removeViewSafe(teleComposeView, 2)
        removeViewSafe(dwComposeView, 3)
        
        isVpnAdded = false
        isFreezeAdded = false
        isTeleAdded = false
        isDwAdded = false
        
        super.onDestroy()
    }
}

// Custom simple implementation of lifecycle objects for floating service
class OverlayLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val _viewModelStore = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    init {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.INITIALIZED
    }

    fun handleLifecycleEvent(event: Lifecycle.Event) {
        lifecycleRegistry.handleLifecycleEvent(event)
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val viewModelStore: ViewModelStore
        get() = _viewModelStore

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry
}
