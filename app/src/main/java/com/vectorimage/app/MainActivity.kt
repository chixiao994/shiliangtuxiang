package com.vectorimage.app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.vectorimage.app.potrace.Potrace
import com.vectorimage.app.potrace.PotraceParams
import com.vectorimage.app.potrace.SvgWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var inputBtn: Button
    private lateinit var outputBtn: Button
    private lateinit var saveBtn: Button
    private lateinit var prevBtn: Button
    private lateinit var skipBtn: Button
    private lateinit var nextBtn: Button
    private lateinit var statusText: TextView
    private lateinit var previewView: PotracePreviewView

    private var inputTreeUri: Uri? = null
    private var outputTreeUri: Uri? = null
    private val imageUris = mutableListOf<Uri>()
    private val imageNames = mutableListOf<String>()
    private var currentIndex = 0
    private val skipped = mutableSetOf<Int>()
    private var busy = false

    private val pickInput =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri == null) return@registerForActivityResult
            try {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            inputTreeUri = uri
            loadImages()
        }

    private val pickOutput =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri == null) return@registerForActivityResult
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {}
            outputTreeUri = uri
            statusText.text = "输出文件夹已选择"
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        inputBtn = findViewById(R.id.btn_input)
        outputBtn = findViewById(R.id.btn_output)
        saveBtn = findViewById(R.id.btn_save)
        prevBtn = findViewById(R.id.btn_prev)
        skipBtn = findViewById(R.id.btn_skip)
        nextBtn = findViewById(R.id.btn_next)
        statusText = findViewById(R.id.status_text)
        previewView = findViewById(R.id.preview_view)

        inputBtn.setOnClickListener { if (!busy) pickInput.launch(null) }
        outputBtn.setOnClickListener { if (!busy) pickOutput.launch(null) }
        saveBtn.setOnClickListener { if (!busy) saveAll() }
        prevBtn.setOnClickListener { if (!busy) goPrev() }
        nextBtn.setOnClickListener { if (!busy) goNext() }
        skipBtn.setOnClickListener { if (!busy) skipCurrent() }

        updateNav()
    }

    private fun loadImages() {
        val uri = inputTreeUri ?: return
        busy = true; updateNav()
        statusText.text = "正在读取文件夹…"

        lifecycleScope.launch {
            val pairs = withContext(Dispatchers.IO) { listImages(uri) }
            imageUris.clear(); imageNames.clear()
            pairs.forEach { (u, n) -> imageUris.add(u); imageNames.add(n) }
            currentIndex = 0; skipped.clear(); busy = false

            if (imageUris.isEmpty()) {
                statusText.text = "文件夹里没有图片（含子文件夹）"
                previewView.clear()
            } else {
                statusText.text = "已加载 ${imageUris.size} 张图片"
                renderCurrent()
            }
            updateNav()
        }
    }

    private fun listImages(treeUri: Uri): List<Pair<Uri, String>> {
        val result = mutableListOf<Pair<Uri, String>>()
        try {
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            scanFolder(treeUri, rootDocId, result)
        } catch (e: Exception) { e.printStackTrace() }
        return result.sortedBy { it.second }
    }

    private fun scanFolder(treeUri: Uri, parentDocId: String, out: MutableList<Pair<Uri, String>>) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            ), null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0)
                val mime = c.getString(1) ?: ""
                val name = c.getString(2) ?: "image"
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    scanFolder(treeUri, id, out)
                } else if (mime.startsWith("image/")) {
                    out.add(DocumentsContract.buildDocumentUriUsingTree(treeUri, id) to name)
                }
            }
        }
    }

    private fun renderCurrent() {
        if (imageUris.isEmpty()) return
        val idx = currentIndex
        val uri = imageUris[idx]
        val name = imageNames.getOrNull(idx) ?: "image"

        if (skipped.contains(idx)) {
            previewView.clear()
            statusText.text = "已跳过：$name（${idx + 1}/${imageUris.size}）"
            return
        }

        statusText.text = "处理中：$name（${idx + 1}/${imageUris.size}）"

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    val bmp = loadBitmap(uri)
                    val maxDim = 512
                    val scale = minOf(
                        maxDim.toFloat() / bmp.width,
                        maxDim.toFloat() / bmp.height,
                        1f
                    )
                    val scaled = Bitmap.createScaledBitmap(
                        bmp,
                        (bmp.width * scale).toInt().coerceAtLeast(1),
                        (bmp.height * scale).toInt().coerceAtLeast(1),
                        true
                    )
                    val paths = Potrace.trace(scaled, PotraceParams())
                    bmp.recycle()
                    scaled.recycle()
                    Triple(paths, scaled.width, scaled.height)
                }
                val (paths, w, h) = result
                previewView.setPaths(paths, w, h)
                statusText.text = "预览：$name（${idx + 1}/${imageUris.size}）"
            } catch (e: Exception) {
                previewView.clear()
                statusText.text = "处理失败：${e.message}"
            }
        }
    }

    private fun goPrev() {
        if (currentIndex > 0) { currentIndex--; renderCurrent(); updateNav() }
    }
    private fun goNext() {
        if (currentIndex < imageUris.size - 1) { currentIndex++; renderCurrent(); updateNav() }
    }
    private fun skipCurrent() {
        if (imageUris.isEmpty()) return
        skipped.add(currentIndex)
        if (currentIndex < imageUris.size - 1) {
            currentIndex++; renderCurrent()
        } else {
            previewView.clear()
            statusText.text = "已跳过最后一张"
        }
        updateNav()
    }

    private fun saveAll() {
        if (inputTreeUri == null) { toast("请先选择输入文件夹"); return }
        if (outputTreeUri == null) { toast("请先选择输出文件夹"); return }
        if (imageUris.isEmpty()) { toast("没有可处理的图片"); return }

        busy = true; updateNav()

        lifecycleScope.launch {
            var success = 0; var fail = 0
            for (i in imageUris.indices) {
                if (skipped.contains(i)) continue
                val name = imageNames.getOrNull(i) ?: "image"
                statusText.text = "保存中 ${i + 1}/${imageUris.size}：$name"
                try {
                    val svg = withContext(Dispatchers.Default) {
                        val bmp = loadBitmap(imageUris[i])
                        val maxDim = 512
                        val scale = minOf(
                            maxDim.toFloat() / bmp.width,
                            maxDim.toFloat() / bmp.height,
                            1f
                        )
                        val scaled = Bitmap.createScaledBitmap(
                            bmp,
                            (bmp.width * scale).toInt().coerceAtLeast(1),
                            (bmp.height * scale).toInt().coerceAtLeast(1),
                            true
                        )
                        val paths = Potrace.trace(scaled, PotraceParams())
                        val out = SvgWriter.toSvg(paths, scaled.width, scaled.height)
                        bmp.recycle()
                        scaled.recycle()
                        out
                    }
                    val outName = name.substringBeforeLast('.', name) + ".svg"
                    withContext(Dispatchers.IO) { writeToOutputTree(outName, svg) }
                    success++
                } catch (e: Exception) { e.printStackTrace(); fail++ }
            }
            busy = false; updateNav()
            statusText.text = "完成：成功 $success，失败 $fail"
            toast("保存完成")
        }
    }

    private fun writeToOutputTree(fileName: String, content: String) {
        val treeUri = outputTreeUri ?: return
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
        val newUri = DocumentsContract.createDocument(
            contentResolver, parentUri, "image/svg+xml", fileName
        ) ?: throw IllegalStateException("无法创建文件：$fileName")
        contentResolver.openOutputStream(newUri, "w")?.use { os ->
            os.write(content.toByteArray(Charsets.UTF_8)); os.flush()
        } ?: throw IllegalStateException("无法写入文件：$fileName")
    }

    private fun loadBitmap(uri: Uri): Bitmap {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        val maxDim = 1024
        var sample = 1
        while (opts.outWidth / sample > maxDim || opts.outHeight / sample > maxDim) sample *= 2
        val opts2 = BitmapFactory.Options().apply { inSampleSize = sample }
        return contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts2)
        } ?: throw IllegalStateException("无法解码图片")
    }

    private fun updateNav() {
        val has = imageUris.isNotEmpty()
        prevBtn.isEnabled = !busy && has && currentIndex > 0
        nextBtn.isEnabled = !busy && has && currentIndex < imageUris.size - 1
        skipBtn.isEnabled = !busy && has
        inputBtn.isEnabled = !busy
        outputBtn.isEnabled = !busy
        saveBtn.isEnabled = !busy && has
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
