package com.mkamelll.fold

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.graphics.createBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SplitViewModel : ViewModel() {
    var file by mutableStateOf<Uri?>(null)
    var isSplitting by mutableStateOf(false)
    var input by mutableStateOf("")
    var fullScreenPageIndex by mutableStateOf<Int?>(null)
    var fullScreenPageBitmap by mutableStateOf<Bitmap?>(null)
    val pages = mutableStateListOf<Bitmap?>()
    var selectedOption by mutableStateOf("range")
    var outputUri by mutableStateOf<Uri?>(null)
    var pagesToSplit by mutableStateOf(pages.indices.toList())

    fun setPagesToSplit() {
        when (selectedOption) {
            "even" -> {
                pagesToSplit = pages.indices.filter { (it + 1) % 2 == 0 }.toList()
            }

            "odd" -> {
                pagesToSplit = pages.indices.filter { (it + 1) % 2 != 0 }.toList()
            }

            "range" if input.isNotEmpty() -> {
                pagesToSplit = input.toPageIndices()
                    .map { it.coerceAtLeast(0).coerceAtMost(pages.size - 1) }
                    .toList()
            }

            else -> pagesToSplit = pages.indices.toList()
        }
    }

    fun initPages(resolver: ContentResolver) {
        file?.let {
            viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    val fd = resolver.openFileDescriptor(it, "r") ?: return@withContext
                    val renderer = PdfRenderer(fd)
                    pages.clear()
                    repeat(renderer.pageCount) { pages.add(null) }
                    renderer.close()
                }
            }
        }
    }

    fun renderPages(resolver: ContentResolver, first: Int, last: Int) {
        file?.let {
            viewModelScope.launch {
                val fd = resolver.openFileDescriptor(it, "r") ?: return@launch
                val renderer = PdfRenderer(fd)
                try {
                    val buffer = 10
                    withContext(Dispatchers.IO) {
                        pages.indices.forEach { index ->
                            when {
                                index in (first - buffer).coerceAtLeast(0)..(last + buffer).coerceAtMost(
                                    pages.size - 1
                                ) -> {
                                    if (pages[index] == null) {
                                        val page = renderer.openPage(index)
                                        val scale = 0.5f
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

                } finally {
                    renderer.close()
                    fd.close()
                }
            }
        }

    }

    fun renderFullScreenPage(resolver: ContentResolver) {
        fullScreenPageIndex?.let { i ->
            file?.let {
                viewModelScope.launch {
                    withContext(Dispatchers.IO) {
                        val fd =
                            resolver.openFileDescriptor(it, "r") ?: return@withContext
                        val renderer = PdfRenderer(fd)
                        val page = renderer.openPage(i)
                        val bitmap = createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                        renderer.close()
                        fd.close()
                        fullScreenPageBitmap = bitmap
                    }
                }
            }
        }
    }

    fun split(resolver: ContentResolver) {
        file?.let {
            outputUri?.let { outputUri ->
                viewModelScope.launch {
                    withContext(Dispatchers.IO) {
                        isSplitting = true
                        val input = resolver.openInputStream(it) ?: return@withContext
                        val output = resolver.openOutputStream(outputUri) ?: return@withContext

                        val original = PDDocument.load(input)
                        val newDoc = PDDocument()

                        pagesToSplit.forEach { i ->
                            val page = original.getPage(i)
                            newDoc.addPage(page)
                        }

                        newDoc.save(output)
                        newDoc.close()
                        original.close()
                        output.close()
                        input.close()
                        isSplitting = false
                    }
                }
            }
        }
    }
}