package com.david.llegeix.ui.platform

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.david.llegeix.util.allFilesAccessIntents
import com.david.llegeix.platform.ContentRef

@Composable
actual fun rememberPicturePicker(onPicked: (ContentRef) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { onPicked(ContentRef(it)) }
    }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

@Composable
actual fun rememberDocumentCreator(mimeType: String, onCreated: (ContentRef) -> Unit): (String) -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mimeType)) { uri ->
        uri?.let { onCreated(ContentRef(it)) }
    }
    return { name -> launcher.launch(name) }
}

@Composable
actual fun rememberDocumentOpener(onOpened: (ContentRef) -> Unit): (Array<String>) -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onOpened(ContentRef(it)) }
    }
    return { types -> launcher.launch(types) }
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled, onBack)

@Composable
actual fun rememberFolderPicker(onPicked: (ContentRef) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
        treeUri?.let { onPicked(ContentRef(it)) }
    }
    return { launcher.launch(null) }
}

@Composable
actual fun rememberPdfFilesPicker(onPicked: (List<ContentRef>) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        onPicked(uris.map(::ContentRef))
    }
    return { launcher.launch(arrayOf("application/pdf")) }
}

/** All Files Access is granted in Settings; the result code means nothing, coming back does. */
@Composable
actual fun rememberDeviceScanRequest(onReturn: () -> Unit): () -> Boolean {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { onReturn() }
    return {
        allFilesAccessIntents(context).any { intent -> runCatching { launcher.launch(intent) }.isSuccess }
    }
}

actual val usesMouse: Boolean = false
