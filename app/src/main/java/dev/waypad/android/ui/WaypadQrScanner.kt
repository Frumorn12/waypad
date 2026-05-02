package dev.waypad.android.ui

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "WaypadQrScanner"

@Composable
fun WaypadQrScannerOverlay(
    onInviteScanned: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var error by remember { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        Log.i(TAG, "camera_permission_result granted=$granted")
        if (!granted) error = "Camera permission denied. Allow camera access to scan Waypad terminal invites."
    }

    LaunchedEffect(Unit) {
        Log.i(TAG, "scanner_open")
        if (!hasPermission) {
            Log.i(TAG, "camera_permission_request")
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    DisposableEffect(Unit) {
        onDispose { Log.i(TAG, "scanner_close") }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (hasPermission) {
            CameraQrPreview(
                onInviteScanned = { raw ->
                    Log.i(TAG, "qr_decode_success length=${raw.length}")
                    onInviteScanned(raw)
                },
                onError = { message ->
                    Log.w(TAG, "camera_start_failure reason=$message")
                    error = message
                },
            )
        } else {
            PermissionBody(
                error = error,
                onRetry = {
                    Log.i(TAG, "camera_permission_request retry=true")
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                },
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = Graphite.copy(alpha = 0.78f),
                contentColor = Mist,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, tint = Acid)
                    Spacer(Modifier.height(1.dp))
                    Text(
                        "Scan Waypad invite",
                        modifier = Modifier.padding(start = 10.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, contentDescription = "Close scanner", tint = Mist)
            }
        }

        error?.let { message ->
            Surface(
                color = Color(0xFF371A1A),
                contentColor = Color(0xFFFFB4B4),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(18.dp),
            ) {
                Text(
                    message,
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun PermissionBody(error: String?, onRetry: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, tint = Acid)
        Spacer(Modifier.height(14.dp))
        Text("Camera access is required for QR invites", color = Mist, fontWeight = FontWeight.Bold)
        Text(
            error ?: "Waypad uses the camera only to decode the terminal invite QR.",
            color = Muted,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(18.dp))
        Button(onClick = onRetry) {
            Text("Allow camera")
        }
    }
}

@Composable
private fun CameraQrPreview(
    onInviteScanned: (String) -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val bound = remember { AtomicBoolean(false) }
    val completed = remember { AtomicBoolean(false) }
    val processing = remember { AtomicBoolean(false) }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build(),
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            if (cameraProviderFuture.isDone) {
                runCatching { cameraProviderFuture.get().unbindAll() }
                    .onFailure { Log.w(TAG, "camera_unbind_failure", it) }
            }
            scanner.close()
            executor.shutdown()
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { viewContext ->
            PreviewView(viewContext).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        },
        update = { previewView ->
            if (!bound.compareAndSet(false, true)) return@AndroidView
            cameraProviderFuture.addListener(
                {
                    runCatching {
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder()
                            .build()
                            .also { it.setSurfaceProvider(previewView.surfaceProvider) }
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { imageAnalysis ->
                                imageAnalysis.setAnalyzer(executor) { proxy ->
                                    analyzeQrFrame(
                                        proxy = proxy,
                                        scanner = scanner,
                                        processing = processing,
                                        completed = completed,
                                        onInviteScanned = onInviteScanned,
                                    )
                                }
                            }
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis,
                        )
                        Log.i(TAG, "camera_start_success")
                    }.onFailure { throwable ->
                        bound.set(false)
                        Log.e(TAG, "camera_start_failure", throwable)
                        onError(throwable.message ?: throwable::class.java.simpleName)
                    }
                },
                ContextCompat.getMainExecutor(context),
            )
        },
    )
}

@OptIn(ExperimentalGetImage::class)
private fun analyzeQrFrame(
    proxy: ImageProxy,
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    processing: AtomicBoolean,
    completed: AtomicBoolean,
    onInviteScanned: (String) -> Unit,
) {
    if (completed.get() || !processing.compareAndSet(false, true)) {
        proxy.close()
        return
    }
    val mediaImage = proxy.image
    if (mediaImage == null) {
        processing.set(false)
        proxy.close()
        return
    }
    val input = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
    scanner.process(input)
        .addOnSuccessListener { barcodes ->
            val raw = barcodes.firstNotNullOfOrNull { it.rawValue }
            if (raw != null && raw.startsWith("waypad://invite") && completed.compareAndSet(false, true)) {
                onInviteScanned(raw)
            } else if (raw != null) {
                Log.d(TAG, "qr_decode_ignored unsupported_payload=true")
            }
        }
        .addOnFailureListener { throwable ->
            Log.w(TAG, "qr_decode_failure", throwable)
        }
        .addOnCompleteListener {
            processing.set(false)
            proxy.close()
        }
}
