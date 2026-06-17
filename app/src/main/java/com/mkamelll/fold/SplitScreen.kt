package com.mkamelll.fold

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds


@OptIn(FlowPreview::class, ExperimentalMaterial3Api::class)
@Composable
fun SplitScreen(modifier: Modifier = Modifier, vm: SplitViewModel = viewModel()) {

    val context = LocalContext.current
    val listState = rememberLazyListState()
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            vm.file = uri
        }
    }
    var expanded by remember { mutableStateOf(false) }
    val options = listOf("even", "odd", "range")

    LaunchedEffect(vm.file, vm.selectedOption, vm.input, vm.pages.size) {
        vm.setPagesToSplit()
    }

    val scope = rememberCoroutineScope()
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri?.let { outputUri ->
            vm.outputUri = outputUri
            vm.split(context.contentResolver)
            scope.launch {
                withContext(Dispatchers.Main) {
                    showCompletedNotification(context, outputUri, "Splitted file is ready!")
                }
            }
        }
    }

    LaunchedEffect(vm.file) {
        vm.initPages(context.contentResolver)
    }

    LaunchedEffect(vm.file) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .debounce(150.milliseconds)
            .collectLatest { first ->
                val last =
                    (first + listState.layoutInfo.visibleItemsInfo.size + 1).coerceAtMost(
                        vm.pages.size - 1
                    )
                vm.renderPages(context.contentResolver, first, last)
            }
    }

    LaunchedEffect(vm.fullScreenPageIndex) {
        vm.renderFullScreenPage(context.contentResolver)
    }

    BackHandler(
        enabled = vm.fullScreenPageIndex != null
    ) {
        vm.fullScreenPageIndex = null
        vm.fullScreenPageBitmap = null
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Scaffold(
            floatingActionButton = {
                AnimatedVisibility(visible = !vm.isSplitting) {
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
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = {
                        expanded = it
                    },
                ) {
                    OutlinedTextField(
                        value = vm.selectedOption,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded)
                        },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = {
                            expanded = false
                        }
                    ) {
                        options.forEach {
                            DropdownMenuItem(
                                text = { Text(it) },
                                onClick = {
                                    vm.selectedOption = it
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = vm.selectedOption == "range",
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                    modifier = Modifier.padding(4.dp)
                ) {
                    OutlinedTextField(
                        value = vm.input,
                        onValueChange = { vm.input = it },
                        placeholder = { Text("pages(i.e 1,1-5,4)") }
                    )
                }

                vm.file?.let {
                    Button(
                        enabled = !vm.isSplitting,
                        onClick = {
                            saveLauncher.launch("splitted.pdf")
                        }
                    ) {
                        if (vm.isSplitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Split")
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                LazyRow(
                    state = listState
                ) {
                    items(vm.pagesToSplit) {
                        val bitmap = vm.pages.getOrNull(it)
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
                                            vm.fullScreenPageIndex = it
                                        },
                                    contentScale = ContentScale.Fit,
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "thumbnail for page $it"
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
                                text = "${it + 1}",
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
    }

    vm.fullScreenPageIndex?.let { index ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            vm.fullScreenPageBitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "fullscreen of page $index",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}
