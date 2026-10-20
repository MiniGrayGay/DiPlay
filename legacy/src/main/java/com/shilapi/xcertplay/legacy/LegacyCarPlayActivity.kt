package com.shilapi.xcertplay.legacy

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import com.shilapi.xcertplay.airplay.AirPlayConfig
import com.shilapi.xcertplay.airplay.AirPlayDisplayConfig
import com.shilapi.xcertplay.airplay.AirPlayIdentity
import com.shilapi.xcertplay.airplay.AirPlaySession
import com.shilapi.xcertplay.airplay.AirPlaySessionListener
import com.shilapi.xcertplay.airplay.CarPlayMediaEngine
import com.shilapi.xcertplay.airplay.PairingStore
import com.shilapi.xcertplay.media.AndroidMediaSink
import com.shilapi.xcertplay.media.CarPlayTouchMapper
import com.shilapi.xcertplay.mfi.LocalMfiAuthenticationClient
import com.shilapi.xcertplay.orchestration.CarPlayController
import com.shilapi.xcertplay.orchestration.CarPlayRuntimeConfig
import com.shilapi.xcertplay.orchestration.CarPlayStatus
import com.shilapi.xcertplay.orchestration.CarPlayTransport
import com.shilapi.xcertplay.orchestration.MfiTarget
import com.shilapi.xcertplay.transport.Iap2IdentificationConfig
import java.io.File
import java.security.MessageDigest

/**
 * Minimal CarPlay host activity for Android 4.4+ (API 19) test builds.
 * Wired USB only. No Compose, no AndroidX Activity Result, no settings UI.
 */
class LegacyCarPlayActivity : Activity() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var textureView: TextureView? = null
    private var statusView: TextView? = null
    private var controller: CarPlayController? = null
    private var mediaSink: AndroidMediaSink? = null
    private lateinit var identity: AirPlayIdentity
    private val pairings = PairingStore()
    private var currentSession: AirPlaySession? = null
    private var currentSurface: Surface? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
        )
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )

        identity = loadOrCreateIdentity()
        bootstrapMfi()

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)

        val tv = TextureView(this)
        tv.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        tv.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: android.graphics.SurfaceTexture, w: Int, h: Int) {
                val surface = Surface(st)
                currentSurface = surface
                mediaSink?.setSurface(SCREEN_TYPE_MAIN, surface)
            }
            override fun onSurfaceTextureSizeChanged(st: android.graphics.SurfaceTexture, w: Int, h: Int) {}
            override fun onSurfaceTextureDestroyed(st: android.graphics.SurfaceTexture): Boolean {
                currentSurface?.let { mediaSink?.clearSurface(SCREEN_TYPE_MAIN, it) }
                currentSurface = null
                return true
            }
            override fun onSurfaceTextureUpdated(st: android.graphics.SurfaceTexture) {}
        }
        root.addView(tv)
        textureView = tv

        val status = TextView(this)
        status.setTextColor(Color.WHITE)
        status.textSize = 16f
        status.text = getString(R.string.status_waiting)
        val statusParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER,
        )
        root.addView(status, statusParams)
        statusView = status

        setContentView(root)

        handleUsbIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleUsbIntent(intent)
    }

    private fun handleUsbIntent(intent: Intent?) {
        if (intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            startCarPlay()
        } else if (controller == null) {
            startCarPlay()
        }
    }

    private fun startCarPlay() {
        controller?.close()
        val deviceId = deviceId(identity)

        val config = CarPlayRuntimeConfig(
            mfiTarget = MfiTarget.LOCAL,
            identification = Iap2IdentificationConfig(
                name = "DiPlay",
                modelIdentifier = Build.MODEL,
                manufacturer = Build.MANUFACTURER,
                serialNumber = "DIPLAY-" + deviceId.replace(":", ""),
                firmwareVersion = "0.1.0",
                hardwareVersion = "1.0",
                carPlayUsbInterfaceNumber = 3,
            ),
            label = "DiPlay",
            hostName = "diplay-" + deviceId.replace(":", "").lowercase(),
            hostMac = deviceId.split(":").map { it.toInt(16).toByte() }.toByteArray(),
            transport = CarPlayTransport.WIRED,
        )

        val sink = AndroidMediaSink(
            videoWidth = 1280,
            videoHeight = 720,
            context = this,
        )
        mediaSink = sink

        textureView?.surfaceTexture?.let { st ->
            val surface = Surface(st)
            currentSurface = surface
            sink.setSurface(SCREEN_TYPE_MAIN, surface)
        }

        val media = CarPlayMediaEngine(sink)

        val airPlayConfig = AirPlayConfig(
            deviceName = "DiPlay",
            deviceId = deviceId,
            btMac = deviceId,
            sourceVersion = "950.7.1",
            main = AirPlayDisplayConfig(
                widthPixels = 1280,
                heightPixels = 720,
                fps = 30,
                primaryInputDevice = 1,
            ),
            rightHandDrive = false,
            hevc = false,
            microphone = false,
        )

        val next = CarPlayController(
            context = this,
            config = config,
            airPlayConfig = airPlayConfig,
            identity = identity,
            pairings = pairings,
            listener = object : AirPlaySessionListener {
                override fun onSessionActive(session: AirPlaySession) {
                    currentSession = session
                    mainHandler.post { updateStatus(CarPlayStatus.RunningControl) }
                }
                override fun onSessionEnded(session: AirPlaySession) {
                    currentSession = null
                    mainHandler.post { updateStatus(CarPlayStatus.ControlEnded) }
                }
                override fun onTransportError(message: String) {
                    Log.e(TAG, "Transport error: $message")
                }
            },
            media = media,
            reportStatus = { status -> mainHandler.post { updateStatus(status) } },
        )
        controller = next
        next.start()
    }

    private fun updateStatus(status: CarPlayStatus) {
        val text = when (status) {
            is CarPlayStatus.DiscoveringMfi,
            is CarPlayStatus.WaitingForMfi,
            is CarPlayStatus.RequestingMfiPermission,
            is CarPlayStatus.MfiReady,
            is CarPlayStatus.WaitingForIphone,
            is CarPlayStatus.RequestingIphonePermission,
            is CarPlayStatus.WaitingForReenumeration,
            is CarPlayStatus.SelectingConfiguration,
            is CarPlayStatus.OpeningDataPaths,
            is CarPlayStatus.Pairing,
            is CarPlayStatus.ConnectingControl,
            is CarPlayStatus.AttachingNetwork -> getString(R.string.status_connecting)
            is CarPlayStatus.RunningControl -> {
                statusView?.visibility = View.GONE
                getString(R.string.status_running)
            }
            is CarPlayStatus.ControlEnded -> {
                statusView?.visibility = View.VISIBLE
                getString(R.string.status_waiting)
            }
            is CarPlayStatus.Failed -> {
                statusView?.visibility = View.VISIBLE
                getString(R.string.status_error, status.message)
            }
            else -> {
                statusView?.visibility = View.VISIBLE
                getString(R.string.status_waiting)
            }
        }
        statusView?.text = text
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val ctrl = controller ?: return super.onTouchEvent(event)
        val tv = textureView ?: return super.onTouchEvent(event)
        val contacts = CarPlayTouchMapper.contacts(event, tv.width, tv.height)
        if (contacts.isNotEmpty()) {
            ctrl.sendTouch(contacts)
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun onDestroy() {
        controller?.close()
        controller = null
        mediaSink?.close()
        mediaSink = null
        super.onDestroy()
    }

    private fun loadOrCreateIdentity(): AirPlayIdentity {
        val prefs = getSharedPreferences("legacy_airplay", Context.MODE_PRIVATE)
        val privateHex = prefs.getString("identity_private", null)
        val publicHex = prefs.getString("identity_public", null)
        val pairingId = prefs.getString("pairing_id", null)
        if (privateHex != null && publicHex != null && pairingId != null) {
            return AirPlayIdentity(hexToBytes(privateHex), hexToBytes(publicHex), pairingId)
        }
        return AirPlayIdentity.generate().also { id ->
            prefs.edit()
                .putString("identity_private", bytesToHex(id.privateKey))
                .putString("identity_public", bytesToHex(id.publicKey))
                .putString("pairing_id", id.pairingId)
                .apply()
        }
    }

    private fun mfiBaseDir(): File =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) noBackupFilesDir else filesDir

    private fun bootstrapMfi() {
        val target = File(mfiBaseDir(), LocalMfiAuthenticationClient.DIRECTORY)
        if (target.exists()) return
        val staging = File(mfiBaseDir(), "offline-mfi-staging")
        staging.deleteRecursively()
        if (!staging.mkdirs()) {
            Log.w(TAG, "Could not prepare MFi directory")
            return
        }
        try {
            for (name in listOf("identity.pk8", "certificate.p7b")) {
                val file = File(staging, name)
                assets.open("offline-mfi/$name").use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
            }
            staging.renameTo(target)
        } catch (e: Exception) {
            Log.w(TAG, "MFi bootstrap failed; wired CarPlay requires authentication assets", e)
            staging.deleteRecursively()
        }
    }

    companion object {
        private const val TAG = "LegacyCarPlay"
        private const val SCREEN_TYPE_MAIN = 110

        fun deviceId(identity: AirPlayIdentity): String {
            val bytes = MessageDigest.getInstance("SHA-256")
                .digest(identity.publicKey).take(6).toByteArray()
            val b0 = ((bytes[0].toInt() and 0xfc) or 0x02).toByte()
            bytes[0] = b0
            return bytes.joinToString(":") { "%02X".format(it.toInt() and 0xff) }
        }

        private fun bytesToHex(bytes: ByteArray): String =
            bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }

        private fun hexToBytes(hex: String): ByteArray {
            val len = hex.length
            val data = ByteArray(len / 2)
            for (i in 0 until len step 2) {
                data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            }
            return data
        }
    }
}
