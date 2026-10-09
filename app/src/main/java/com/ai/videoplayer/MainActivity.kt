package com.ai.videoplayer   // ← 改成你仓库里的包名

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.effect.Effects
import androidx.media3.effect.GlEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import com.ai.videoplayer.databinding.ActivityMainBinding   // ← 改成对应包名的 databinding
import android.media.MediaMetadataRetriever
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(UnstableApi::class)
class MainActivity : androidx.appcompat.app.AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var player: ExoPlayer? = null
    private var liveProgram: EnhancementShaderProgram? = null
    private var currentUri: Uri? = null
    private var pendingPositionMs = 0L
    private var currentMode: FilterMode = FilterMode.OFF

    private var transformer: Transformer? = null
    private var exportDialog: AlertDialog? = null
    private val handler = Handler(Looper.getMainLooper())

    private val openVideoLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                runCatching {
                    contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                playUri(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding.btnOpen.setOnClickListener {
            openVideoLauncher.launch(arrayOf("video/*"))
        }
        binding.btnFilter.setOnClickListener { showFilterSheet() }
        binding.btnExport.setOnClickListener {
            if (currentUri == null) {
                Toast.makeText(this, "请先打开视频", Toast.LENGTH_SHORT).show()
            } else {
                showExportDialog()
            }
        }

        intent?.let { runCatching { handleExternalIntent(it) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        runCatching { handleExternalIntent(intent) }
    }

    private fun handleExternalIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_VIEW) {
            intent.data?.let { playUri(it) }
        }
    }

    // ---------------- 播放器 ----------------

    private fun ensurePlayer() {
        if (player != null) return
        runCatching {
            val program = EnhancementShaderProgram(this).apply { mode = currentMode }
            val p = ExoPlayer.Builder(this)
                .setVideoEffects(Effects(emptyList(), listOf<GlEffect>(program)))
                .build()
            binding.playerView.player = p
            player = p
            liveProgram = program
        }.onFailure { e ->
            // 增强引擎（GL Shader）初始化失败时不闪退，仅提示；播放器降级为无增强模式
            Toast.makeText(this, "增强引擎初始化失败：${e.message}", Toast.LENGTH_LONG).show()
            runCatching {
                val p = ExoPlayer.Builder(this).build()
                binding.playerView.player = p
                player = p
            }
        }
    }

    private fun playUri(uri: Uri) {
        runCatching {
            currentUri = uri
            pendingPositionMs = 0L
            ensurePlayer()
            player?.setMediaItem(MediaItem.fromUri(uri))
            player?.prepare()
            player?.playWhenReady = true
            binding.emptyState.isVisible = false
            binding.playerView.isVisible = true
            binding.topBar.isVisible = true
        }.onFailure { e ->
            Toast.makeText(this, "打开视频失败：${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onStart() {
        super.onStart()
        runCatching {
            ensurePlayer()
            currentUri?.let {
                player?.setMediaItem(MediaItem.fromUri(it), pendingPositionMs)
                player?.prepare()
                player?.playWhenReady = false
            }
        }
    }

    override fun onStop() {
        pendingPositionMs = player?.currentPosition ?: 0L
        binding.playerView.player = null
        player?.release()
        player = null
        liveProgram = null
        super.onStop()
    }

    // ---------------- 滤镜选择 ----------------

    private fun applyFilter(mode: FilterMode) {
        currentMode = mode
        liveProgram?.mode = mode
        binding.btnFilter.text = if (mode == FilterMode.OFF) "滤镜" else "滤镜·${mode.shortName}"
        Snackbar.make(
            binding.root,
            if (mode == FilterMode.OFF) "已关闭增强" else "已切换：${mode.title}",
            Snackbar.LENGTH_SHORT
        ).show()
    }

    @SuppressLint("InflateParams")
    private fun showFilterSheet() {
        runCatching {
            val dialog = BottomSheetDialog(this)
            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }
            val title = TextView(this).apply {
                text = getString(R.string.filter_title)
                textSize = 16f
                setPadding(dp(20), dp(18), dp(20), dp(8))
                setTextColor(Color.WHITE)
            }
            container.addView(title)

            FilterMode.values().forEach { mode ->
                val row = LayoutInflater.from(this).inflate(R.layout.item_filter, container, false)
                row.findViewById<RadioButton>(R.id.radio).isChecked = mode == currentMode
                row.findViewById<TextView>(R.id.name).text = mode.title
                row.findViewById<TextView>(R.id.desc).text = mode.desc
                row.setOnClickListener {
                    applyFilter(mode)
                    dialog.dismiss()
                }
                container.addView(row)
            }

            val scroll = ScrollView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                addView(container)
            }
            dialog.setContentView(scroll)
            dialog.show()
        }.onFailure { e ->
            Toast.makeText(this, "滤镜面板打开失败：${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // ---------------- 离线增强导出 ----------------

    @SuppressLint("InflateParams")
    private fun showExportDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_export, null)
        AlertDialog.Builder(this)
            .setTitle(R.string.export_title)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton("开始增强") { _, _ ->
                val scale2x = view.findViewById<RadioButton>(R.id.radio2x).isChecked
                currentUri?.let { startExport(it, scale2x) }
            }
            .show()
    }

    private fun obtainVideoHeight(uri: Uri): Int {
        player?.videoSize?.let { if (it.height > 0) return it.height }
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(this, uri)
            var w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            var h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            val rot = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
            if (rot == 90 || rot == 270) {
                val tmp = w; w = h; h = tmp
            }
            h
        } catch (_: Exception) {
            0
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun startExport(uri: Uri, scale2x: Boolean) {
        runCatching {
            val videoEffects = mutableListOf<GlEffect>()
            if (scale2x) {
                val srcH = obtainVideoHeight(uri)
                if (srcH > 0) {
                    videoEffects += Presentation.createForHeight((srcH * 2).coerceAtMost(4320))
                }
            }
            val program = EnhancementShaderProgram(this).apply { mode = currentMode }
            videoEffects += program
            val effects = Effects(emptyList(), videoEffects)

            val editedItem = EditedMediaItem.Builder(MediaItem.fromUri(uri))
                .setEffects(effects)
                .build()

            val dir = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "NeuralFX").apply { mkdirs() }
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val tag = if (currentMode == FilterMode.OFF) "原始" else currentMode.shortName
            val suffix = if (scale2x) "_2x" else ""
            val outFile = File(dir, "NeuralFX_${tag}${suffix}_$ts.mp4")

            val progressView = LayoutInflater.from(this).inflate(R.layout.dialog_progress, null)
            val dialog = AlertDialog.Builder(this)
                .setView(progressView)
                .setCancelable(false)
                .create()
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            exportDialog = dialog
            dialog.show()

            val t = Transformer.Builder(this)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        onExportFinished(outFile, null)
                    }

                    override fun onError(
                        composition: Composition,
                        exportException: ExportException,
                        exportResult: ExportResult
                    ): Boolean {
                        onExportFinished(outFile, exportException)
                        return true
                    }
                })
                .build()
            transformer = t
            pollProgress(t, progressView)

            runCatching { t.start(editedItem, outFile.absolutePath) }
                .onFailure { e ->
                    onExportFinished(
                        outFile,
                        if (e is ExportException) e else RuntimeException(e.message, e)
                    )
                }
        }.onFailure { e ->
            Toast.makeText(this, "导出启动失败：${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun pollProgress(t: Transformer, progressView: View) {
        val bar = progressView.findViewById<ProgressBar>(R.id.progressBar)
        val txt = progressView.findViewById<TextView>(R.id.progressText)
        val tick = object : Runnable {
            override fun run() {
                if (transformer !== t) return
                val holder = ProgressHolder()
                when (t.getProgress(holder)) {
                    Transformer.PROGRESS_STATE_AVAILABLE -> {
                        bar.isIndeterminate = false
                        bar.progress = holder.progress
                        txt.text = "正在增强导出… ${holder.progress}%"
                    }
                    else -> {
                        bar.isIndeterminate = true
                        txt.text = "正在增强导出…"
                    }
                }
                handler.postDelayed(this, 500)
            }
        }
        handler.post(tick)
    }

    private fun onExportFinished(outFile: File, error: Exception?) {
        transformer = null
        exportDialog?.dismiss()
        exportDialog = null
        if (error == null && outFile.exists()) {
            AlertDialog.Builder(this)
                .setTitle("增强导出完成")
                .setMessage("已保存到应用私有目录：\nMovies/NeuralFX/${outFile.name}")
                .setPositiveButton("播放") { _, _ -> playUri(Uri.fromFile(outFile)) }
                .setNeutralButton("分享/另存") { _, _ -> shareFile(outFile) }
                .setNegativeButton("关闭", null)
                .show()
        } else {
            AlertDialog.Builder(this)
                .setTitle("导出失败")
                .setMessage(
                    error?.message
                        ?: "编码器可能不支持该分辨率，4K 片源请选择原分辨率导出。"
                )
                .setPositiveButton("知道了", null)
                .show()
        }
    }

    private fun shareFile(file: File) {
        runCatching {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this, "$packageName.fileprovider", file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "分享增强视频"))
        }.onFailure {
            Toast.makeText(this, "分享失败：${it.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
