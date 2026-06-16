package com.mkamelll.fold

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

@OptIn(FlowPreview::class)
@Composable
fun SplitScreen(modifier: Modifier = Modifier) {
    var file by remember { mutableStateOf<Uri?>(null) }
    var isSplitting by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    val context = LocalContext.current
    var fullScreenPage by remember { mutableStateOf<Pair<Bitmap, Int>?>(null) }
    val pages = remember { mutableStateListOf<Bitmap?>() }
    val listState = rememberLazyListState()
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { file = uri }
    }

    LaunchedEffect(file) {
        file?.let {
            withContext(Dispatchers.IO) {
                val fd = context.contentResolver.openFileDescriptor(it, "r") ?: return@withContext
                val renderer = PdfRenderer(fd)
                repeat(renderer.pageCount) { pages.add(null) }
                renderer.close()
            }
        }
    }

    LaunchedEffect(file) {
        file?.let {
            val fd = context.contentResolver.openFileDescriptor(it, "r") ?: return@LaunchedEffect
            val renderer = PdfRenderer(fd)
            try {
                snapshotFlow { listState.firstVisibleItemIndex }
                    .debounce(150.milliseconds)
                    .collectLatest { first ->
                        val last =
                            (first + listState.layoutInfo.visibleItemsInfo.size + 1).coerceAtMost(
                                pages.size - 1
                            )
                        val buffer = 10
                        withContext(Dispatchers.IO) {
                            pages.indices.forEach { index ->
                                when {
                                    index in (first - buffer).coerceAtLeast(0)..(last + buffer).coerceAtMost(
                                        pages.size - 1
                                    ) -> {
                                        if (pages[index] == null) {
                                            val page = renderer.openPage(index)
                                            val scale = 0.7f
                                            val bitmap = createBitmap(
                                                (page.width * scale).toInt(),
                                                (page.height * scale).toInt()
                                            )
                                            bitmap.eraseColor(android.graphics.Color.WHITE)
                                            page.render(
                                                bitmap,
                                                null,
                                                null,
                                                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                                            )
                                            page.close()
                                            pages[index] = bitmap
                                        }
                                    }

                                    else -> {
                                        pages[index]?.recycle()
                                        pages[index] = null
                                    }
                                }
                            }
                        }
                    }
            } finally {
                renderer.close()
                fd.close()
            }
        }

    }

    BackHandler(
        enabled = fullScreenPage != null
    ) {
        fullScreenPage = null
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Scaffold(
            floatingActionButton = {
                AnimatedVisibility(visible = (file == null && !isSplitting)) {
                    FloatingActionButton(
                        onClick = {
                            launcher.launch(arrayOf("application/pdf"))
                        }
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "floating add file button")
                    }
                }
            },
            floatingActionButtonPosition = FabPosition.End
        ) { innerpadding ->
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerpadding),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OutlinedTextField(
                    enabled = (file != null && !isSplitting),
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("pages(i.e 1,1-5,4)") }
                )
                Spacer(Modifier.height(12.dp))
                LazyRow(
                    state = listState
                ) {
                    itemsIndexed(pages) { index, bitmap ->
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(300.dp)
                                .padding(8.dp)
                        ) {
                            if (bitmap != null) {
                                Image(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable {
                                            fullScreenPage = Pair(bitmap, index)
                                        },
                                    contentScale = ContentScale.Fit,
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "thumbnail for page $index"
                                )
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                            Text(
                                text = "${index + 1}",
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(8.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
        fullScreenPage?.let { (bitmap, index) ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { fullScreenPage = null },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "fullscreen of page $index",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}
