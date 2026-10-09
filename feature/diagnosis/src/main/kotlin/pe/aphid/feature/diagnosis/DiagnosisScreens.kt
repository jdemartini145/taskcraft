package pe.aphid.feature.diagnosis

import android.Manifest
import android.content.pm.PackageManager
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import pe.aphid.core.designsystem.R
import pe.aphid.core.designsystem.component.AphidScaffold
import pe.aphid.core.designsystem.component.AphidTopBar
import pe.aphid.core.designsystem.component.BannerKind
import pe.aphid.core.designsystem.component.EmptyState
import pe.aphid.core.designsystem.component.InfoBanner
import pe.aphid.core.designsystem.component.SectionCard
import pe.aphid.core.designsystem.component.fmt
import pe.aphid.core.designsystem.component.formatMillis
import pe.aphid.core.designsystem.icon.AphidIcons
import pe.aphid.core.model.DiagnosisOutput

@Composable
fun DiagnosisScreen(onCapture: () -> Unit, viewModel: DiagnosisViewModel = hiltViewModel()) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    AphidScaffold(
        topBar = { AphidTopBar(stringResource(R.string.nav_diagnosis)) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCapture,
                icon = { Icon(AphidIcons.Camera, contentDescription = null) },
                text = { Text(stringResource(R.string.diag_take_photo)) },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item { InfoBanner(stringResource(R.string.diag_disclaimer), BannerKind.WARNING, Modifier.padding(16.dp)) }
            if (history.isEmpty()) item { EmptyState(stringResource(R.string.diag_empty)) }
            items(history, key = { it.id }) { d ->
                ListItem(
                    headlineContent = { Text(d.output.problema) },
                    supportingContent = {
                        Text("${formatMillis(d.createdMillis)} · ${(d.output.confianza * 100).fmt(0)} % · ${d.provider}")
                    },
                )
            }
        }
    }
}

@Composable
fun CaptureScreen(onBack: () -> Unit, viewModel: DiagnosisViewModel = hiltViewModel()) {
    val state by viewModel.capture.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }
    AphidScaffold(topBar = { AphidTopBar(stringResource(R.string.diag_take_photo), onBack = onBack) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.photo != null -> AnalysisContent(state, viewModel)
                !hasPermission -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.diag_camera_why))
                    Button(onClick = { permission.launch(Manifest.permission.CAMERA) }) { Text(stringResource(R.string.diag_camera_allow)) }
                }
                else -> CameraCapture(onCaptured = viewModel::onPhoto, newFile = viewModel::newPhotoFile)
            }
        }
    }
    if (state.askCloudConsent) {
        AlertDialog(
            onDismissRequest = { viewModel.cloudConsent(false) },
            icon = { Icon(AphidIcons.Lock, contentDescription = null) },
            title = { Text(stringResource(R.string.consent_title)) },
            text = { Text(stringResource(R.string.consent_photo_text)) },
            confirmButton = { TextButton(onClick = { viewModel.cloudConsent(true) }) { Text(stringResource(R.string.consent_accept)) } },
            dismissButton = { TextButton(onClick = { viewModel.cloudConsent(false) }) { Text(stringResource(R.string.consent_reject)) } },
        )
    }
}

/** Vista previa CameraX + ImageCapture a archivo (no usa TakePicturePreview). */
@Composable
private fun CameraCapture(onCaptured: (java.io.File) -> Unit, newFile: () -> java.io.File) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            }.onFailure { error = it.message }
        }, ContextCompat.getMainExecutor(context))
        onDispose { runCatching { future.get().unbindAll() } }
    }
    val takeLabel = stringResource(R.string.diag_shutter)
    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        error?.let { InfoBanner(it, BannerKind.ERROR, Modifier.align(Alignment.TopCenter).padding(16.dp)) }
        LargeFloatingActionButton(
            onClick = {
                if (busy) return@LargeFloatingActionButton
                busy = true
                val file = newFile()
                val options = ImageCapture.OutputFileOptions.Builder(file).build()
                imageCapture.takePicture(
                    options,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            busy = false
                            onCaptured(file)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            busy = false
                            error = exception.message
                        }
                    },
                )
            },
            modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
        ) { Icon(AphidIcons.Camera, contentDescription = takeLabel) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnalysisContent(state: CaptureUiState, vm: DiagnosisViewModel) {
    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model = state.photo,
            contentDescription = stringResource(R.string.diag_photo_cd),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp)),
        )
        OutlinedTextField(
            value = state.notes,
            onValueChange = vm::setNotes,
            label = { Text(stringResource(R.string.diag_notes)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = vm::analyzeLocal, enabled = !state.analyzing) { Text(stringResource(R.string.diag_analyze_local)) }
            OutlinedButton(onClick = vm::requestCloud, enabled = !state.analyzing && state.cloudAllowed) { Text(stringResource(R.string.diag_analyze_cloud)) }
            TextButton(onClick = vm::reset) { Text(stringResource(R.string.diag_retake)) }
        }
        if (!state.cloudAllowed) Text(stringResource(R.string.diag_cloud_pro), style = MaterialTheme.typography.bodySmall)
        if (state.analyzing) CircularProgressIndicator()
        state.error?.let { InfoBanner(it, BannerKind.ERROR) }
        state.output?.let { out ->
            DiagnosisCard(out, state.provider ?: "")
            Button(onClick = vm::scheduleRecheck, enabled = !state.reminderSet) {
                Text(stringResource(if (state.reminderSet) R.string.saved_ok else R.string.diag_recheck, out.reconsultar_en_dias))
            }
        }
        InfoBanner(stringResource(R.string.diag_disclaimer), BannerKind.WARNING)
    }
}

@Composable
private fun DiagnosisCard(out: DiagnosisOutput, provider: String) {
    SectionCard(out.problema) {
        Text(stringResource(R.string.diag_confidence, (out.confianza * 100).fmt(0), provider))
        Text(stringResource(R.string.diag_urgency, out.urgencia.name))
        if (out.evidencias.isNotEmpty()) {
            Text(stringResource(R.string.diag_evidence), style = MaterialTheme.typography.titleSmall)
            out.evidencias.forEach { Text("• $it") }
        }
        Text(stringResource(R.string.diag_actions), style = MaterialTheme.typography.titleSmall)
        out.acciones.forEach { Text("• $it") }
    }
}
