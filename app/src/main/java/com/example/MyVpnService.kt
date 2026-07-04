package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.Random

class MyVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.example.ACTION_START"
        const val ACTION_STOP = "com.example.ACTION_STOP"
        const val ACTION_SET_FREEZE = "com.example.ACTION_SET_FREEZE"
        const val ACTION_SET_TELE = "com.example.ACTION_SET_TELE"
        const val ACTION_SET_DW = "com.example.ACTION_SET_DW"
        
        val isVpnActive = MutableStateFlow(false)
        val isFreezeActive = MutableStateFlow(false)
        val isTeleActive = MutableStateFlow(false)
        val isDwActive = MutableStateFlow(false)
        val isTrafficFrozen = MutableStateFlow(false)
        val countdownSeconds = MutableStateFlow(0)
        val downloadSpeed = MutableStateFlow("0.00 B/s")
        val uploadSpeed = MutableStateFlow("0.00 B/s")
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var isCurrentlyFrozen = false
    private var speedJob: Job? = null
    private var countdownJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    
    // Bytes counters for notification like in professional apps
    private var totalBytesIn = 0L
    private var totalBytesOut = 0L

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }

        if (action == ACTION_SET_FREEZE) {
            val freezeParam = intent.getBooleanExtra("freeze", false)
            isFreezeActive.value = freezeParam
            countdownJob?.cancel()
            countdownSeconds.value = 0
            updateVpnState()
            return START_STICKY
        }

        if (action == ACTION_SET_TELE) {
            val teleParam = intent.getBooleanExtra("tele", false)
            isTeleActive.value = teleParam
            countdownJob?.cancel()
            countdownSeconds.value = 0
            updateVpnState()
            return START_STICKY
        }

        if (action == ACTION_SET_DW) {
            val dwParam = intent.getBooleanExtra("dw", false)
            isDwActive.value = dwParam
            countdownJob?.cancel()
            countdownSeconds.value = 0
            updateVpnState()
            return START_STICKY
        }

        // Action is ACTION_START or default initial start
        if (!isVpnActive.value) {
            isVpnActive.value = true
            isFreezeActive.value = false
            isTeleActive.value = false
            isDwActive.value = false
            
            // Start 10 seconds auto countdown with frozen state
            countdownSeconds.value = 10
            updateVpnState(forceFreeze = true)
            
            countdownJob?.cancel()
            countdownJob = scope.launch {
                while (countdownSeconds.value > 0) {
                    delay(1000)
                    countdownSeconds.value -= 1
                }
                if (isVpnActive.value) {
                    updateVpnState()
                }
            }
        } else {
            updateVpnState()
        }

        startSpeedSimulator()
        return START_STICKY
    }

    private var lastAnyBlockActive: Boolean? = null
    private var lastUpdateJob: Job? = null

    private fun updateVpnState(forceFreeze: Boolean = false) {
        val freeze = isFreezeActive.value
        val tele = isTeleActive.value
        val dw = isDwActive.value
        val anyBlockActive = forceFreeze || freeze || tele || dw
        
        if (vpnInterface != null && lastAnyBlockActive == anyBlockActive) {
            return
        }
        
        lastUpdateJob?.cancel()
        lastUpdateJob = scope.launch {
            delay(100) // Much faster debounce
            performVpnTransition(anyBlockActive)
        }
    }

    private fun performVpnTransition(anyBlockActive: Boolean) {
        try {
            val builder = Builder()
            builder.setSession("TrafficControllerVpn")
                .addAddress("10.0.0.1", 24)
                .addDnsServer("8.8.8.8")
                .setMtu(1500)

            if (anyBlockActive) {
                // Freeze Mode: Packets are sent to our virtual sink
                builder.addRoute("0.0.0.0", 0)
            } else {
                // Active Mode: Dummy route to keep VPN icon visible
                builder.addRoute("192.0.2.1", 32)
            }

            val oldInterface = vpnInterface
            vpnInterface = builder.establish()
            
            try {
                oldInterface?.close()
            } catch (e: Exception) { }
            
            isCurrentlyFrozen = anyBlockActive
            lastAnyBlockActive = anyBlockActive
            isTrafficFrozen.value = anyBlockActive
            showNotification(anyBlockActive)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startSpeedSimulator() {
        speedJob?.cancel()
        speedJob = scope.launch {
            val random = Random()
            while (true) {
                if (!isVpnActive.value) {
                    downloadSpeed.value = "0.00 B/s"
                    uploadSpeed.value = "0.00 B/s"
                } else {
                    val dlFrozen = isFreezeActive.value || isTeleActive.value || (countdownSeconds.value > 0)
                    val ulFrozen = isFreezeActive.value || isDwActive.value || (countdownSeconds.value > 0)
                    
                    if (dlFrozen) {
                        downloadSpeed.value = "0.00 B/s"
                    } else {
                        val dlBytes = 50 * 1024 + random.nextInt(450 * 1024)
                        totalBytesIn += dlBytes
                        downloadSpeed.value = formatSpeed(dlBytes.toLong())
                    }

                    if (ulFrozen) {
                        uploadSpeed.value = "0.00 B/s"
                    } else {
                        val ulBytes = 10 * 1024 + random.nextInt(90 * 1024)
                        totalBytesOut += ulBytes
                        uploadSpeed.value = formatSpeed(ulBytes.toLong())
                    }
                    showNotification(dlFrozen || ulFrozen)
                }
                delay(1000)
            }
        }
    }


    private fun formatSpeed(bytes: Long): String {
        return if (bytes < 1024) {
            "$bytes B/s"
        } else if (bytes < 1024 * 1024) {
            String.format("%.2f KB/s", bytes / 1024.0)
        } else {
            String.format("%.2f MB/s", bytes / (1024.0 * 1024.0))
        }
    }

    private fun stopVpn() {
        speedJob?.cancel()
        countdownJob?.cancel()
        countdownSeconds.value = 0
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        lastUpdateJob?.cancel()
        lastUpdateJob = null
        lastAnyBlockActive = null
        isCurrentlyFrozen = false
        vpnInterface = null
        isVpnActive.value = false
        isFreezeActive.value = false
        isTeleActive.value = false
        isDwActive.value = false
        isTrafficFrozen.value = false
        downloadSpeed.value = "0.00 B/s"
        uploadSpeed.value = "0.00 B/s"
        
        stopForeground(true)
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(1)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        stopSelf()
    }

    private fun showNotification(isFrozen: Boolean) {
        val channelId = "vpn_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "VPN Status",
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

        val statusText = if (isFrozen) "FROZEN" else "ACTIVE"
        val dataText = "Download: ${formatDataSize(totalBytesIn)} | Upload: ${formatDataSize(totalBytesOut)}"

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Traffic Controller: $statusText")
            .setContentText(dataText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(1, notification)
    }

    private fun formatDataSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
        val pre = "KMGTPE"[exp - 1]
        return String.format("%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
    }


    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
    }
}
