package com.quranreels.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.quranreels.app.databinding.ActivityMainBinding
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val fileProviderAuthority = "com.quranreels.app.fileprovider"
    private val exportExecutor = Executors.newSingleThreadExecutor()
    private var selectedReciter = "مشاري راشد العفاسي"
    private var selectedSurah = "سورة الشرح · الآيات 1–8"
    private var selectedVerse = "إِنَّ مَعَ الْعُسْرِ يُسْرًا"
    private var selectedBackground = "تدرج زمردي"
    private var selectedBackgroundUri: Uri? = null
    private var selectedAudioUri: Uri? = null
    private var overlayText = ""

    private val pickBackground = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedBackgroundUri = uri
            selectedBackground = "خلفية مخصصة"
            binding.btnBackground.text = "٣  ·  الخلفية: $selectedBackground"
            binding.tvStatus.text = "تم اختيار خلفيتك الخاصة"
        }
    }

    private val pickAudio = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedAudioUri = uri
            binding.btnAudio.text = "٢  ·  تم اختيار ملف التلاوة الصوتي"
            binding.tvStatus.text = "سيتم دمج التلاوة داخل فيديو MP4"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnRecitation.setOnClickListener { showRecitationDialog() }
        binding.btnAudio.setOnClickListener { pickAudio.launch("audio/*") }
        binding.btnBackground.setOnClickListener { showBackgroundDialog() }
        binding.btnCustomize.setOnClickListener { showCustomizeDialog() }
        binding.btnExport.setOnClickListener { exportAndShareReel() }
        binding.navHome.setOnClickListener { binding.tvStatus.text = "الرئيسية · مشروع ريلز جديد" }
        binding.navProjects.setOnClickListener { binding.tvStatus.text = "المشاريع · ستظهر هنا الريلز المحفوظة" }
        binding.navBackgrounds.setOnClickListener { showBackgroundDialog() }
        binding.navSettings.setOnClickListener {
            Toast.makeText(this, "الإعدادات: جودة 720×1280 وتصدير MP4", Toast.LENGTH_SHORT).show()
        }
        binding.videoPreview.setOnPreparedListener { player ->
            player.isLooping = true
            binding.videoPreview.start()
        }
    }

    private fun showRecitationDialog() {
        val items = arrayOf(
            "مشاري راشد العفاسي — سورة الشرح",
            "ماهر المعيقلي — سورة الملك",
            "عبدالرحمن السديس — سورة الرحمن",
            "ياسر الدوسري — سورة النبأ"
        )
        MaterialAlertDialogBuilder(this)
            .setTitle("اختر القارئ والسورة")
            .setSingleChoiceItems(items, 0) { dialog, which ->
                selectedReciter = items[which].substringBefore(" —")
                selectedSurah = items[which].substringAfter("— ") + " · الآيات المختارة"
                selectedVerse = when (which) {
                    1 -> "تَبَارَكَ الَّذِي بِيَدِهِ الْمُلْكُ"
                    2 -> "الرَّحْمَنُ عَلَّمَ الْقُرْآنَ"
                    3 -> "عَمَّ يَتَسَاءَلُونَ"
                    else -> "إِنَّ مَعَ الْعُسْرِ يُسْرًا"
                }
                binding.btnRecitation.text = "١  ·  $selectedReciter"
                binding.tvPreviewVerse.text = selectedVerse
                binding.tvPreviewMeta.text = selectedSurah
                binding.tvStatus.text = "تم اختيار $selectedReciter"
                dialog.dismiss()
            }.show()
    }

    private fun showBackgroundDialog() {
        val items = arrayOf("تدرج زمردي", "سماء ليلية", "مسجد هادئ", "ألوان تجريدية", "رفع من الجهاز")
        MaterialAlertDialogBuilder(this)
            .setTitle("اختر الخلفية")
            .setItems(items) { _, which ->
                if (which == items.lastIndex) {
                    pickBackground.launch("image/*")
                } else {
                    selectedBackgroundUri = null
                    selectedBackground = items[which]
                    binding.btnBackground.text = "٣  ·  الخلفية: $selectedBackground"
                    binding.tvStatus.text = "الخلفية: $selectedBackground"
                }
            }.show()
    }

    private fun showCustomizeDialog() {
        val input = EditText(this).apply {
            hint = "مثال: تابعونا @quran.reels"
            setText(overlayText)
            setPadding(32, 16, 32, 16)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("نص التعريف أو العلامة")
            .setMessage("سيظهر النص بشكل بسيط أسفل الريل مع احترام وضوح الآية.")
            .setView(input)
            .setPositiveButton("حفظ") { _, _ ->
                overlayText = input.text.toString()
                binding.btnCustomize.text = if (overlayText.isBlank()) "٤  ·  خصص النص والقالب" else "٤  ·  تم تخصيص العلامة"
                binding.tvStatus.text = "تم حفظ التخصيص"
            }
            .setNegativeButton("إلغاء", null).show()
    }

    private fun exportAndShareReel() {
        binding.btnExport.isEnabled = false
        binding.tvStatus.text = "جارٍ إنتاج فيديو MP4 عمودي… 0%"
        binding.videoPreview.visibility = View.GONE
        exportExecutor.execute {
            try {
                val output = ReelExporter.export(
                    context = this,
                    verse = selectedVerse,
                    meta = "$selectedSurah · $selectedReciter",
                    overlay = overlayText,
                    backgroundUri = selectedBackgroundUri,
                    audioUri = selectedAudioUri,
                    onProgress = { progress ->
                        runOnUiThread { binding.tvStatus.text = "جارٍ إنتاج فيديو MP4 عمودي… $progress%" }
                    }
                )
                val uri = FileProvider.getUriForFile(this, fileProviderAuthority, output)
                runOnUiThread {
                    binding.btnExport.isEnabled = true
                    binding.videoPreview.visibility = View.VISIBLE
                    binding.videoPreview.setVideoURI(uri)
                    binding.tvPreviewBadge.text = "فيديو MP4 جاهز · 9:16"
                    binding.tvStatus.text = "تم إنشاء الريل: ${output.name}"
                    shareVideo(uri)
                }
            } catch (error: Throwable) {
                runOnUiThread {
                    binding.btnExport.isEnabled = true
                    binding.tvStatus.text = "تعذر إنتاج الفيديو: ${error.localizedMessage ?: "خطأ غير معروف"}"
                    Toast.makeText(this, "تحقق من ملف التلاوة الصوتي أو أعد المحاولة", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun shareVideo(uri: Uri) {
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_TEXT, "ريل قرآني — $selectedSurah")
        }
        startActivity(Intent.createChooser(share, "مشاركة الريل"))
    }

    override fun onDestroy() {
        exportExecutor.shutdownNow()
        super.onDestroy()
    }
}
