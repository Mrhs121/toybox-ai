package com.toybox.llmchat.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Base64
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

object PdfHelper {

    fun init(context: Context) {
        PDFBoxResourceLoader.init(context)
    }

    data class PdfResult(
        val text: String?,
        val images: List<String>?
    )

    suspend fun parsePdf(context: Context, uri: android.net.Uri): PdfResult = withContext(Dispatchers.IO) {
        // Step 1: Try text extraction via pdfbox
        val text = tryExtractText(context, uri)

        if (!text.isNullOrBlank() && text.length > 50) {
            return@withContext PdfResult(text = text, images = null)
        }

        // Step 2: Fallback - convert pages to images for vision model
        val images = tryConvertToImages(context, uri)
        if (images.isNotEmpty()) {
            return@withContext PdfResult(text = null, images = images)
        }

        // Step 3: Both failed
        PdfResult(text = text ?: "", images = null)
    }

    private fun tryExtractText(context: Context, uri: android.net.Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val doc = PDDocument.load(inputStream)
            val stripper = PDFTextStripper()
            stripper.sortByPosition = true
            val text = stripper.getText(doc)
            doc.close()
            inputStream.close()
            text
        } catch (e: Exception) {
            null
        }
    }

    private fun tryConvertToImages(context: Context, uri: android.net.Uri): List<String> {
        return try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return emptyList()
            val renderer = PdfRenderer(pfd)
            val images = mutableListOf<String>()
            val pageCount = renderer.pageCount.coerceAtMost(10) // Max 10 pages

            for (i in 0 until pageCount) {
                val page = renderer.openPage(i)
                val bitmap = Bitmap.createBitmap(
                    page.width * 2, // 2x for better quality
                    page.height * 2,
                    Bitmap.Config.ARGB_8888
                )
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val baos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
                bitmap.recycle()
                val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                images.add("data:image/jpeg;base64,$base64")
            }

            renderer.close()
            pfd.close()
            images
        } catch (e: Exception) {
            emptyList()
        }
    }
}
