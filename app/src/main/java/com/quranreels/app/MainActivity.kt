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
import androidx.core.widget.addTextChangedListener
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.quranreels.app.databinding.ActivityMainBinding
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val fileProviderAuthority = "com.quranreels.app.fileprovider"
    private val exportExecutor = Executors.newSingleThreadExecutor()
    private var currentStep = 1
    private var selectedType = "تلاوة"
    private var selectedReciter = "مشاري العفاسي"
    private var selectedSurah = "سورة الشرح · الآيات 1–8"
    private var selectedVerse = "إِنَّ مَعَ الْعُسْرِ يُسْرًا"
    private var selectedBackground = "نسيم الفجر"
    private var selectedBackgroundUri: Uri? = null
    private var selectedAudioUri: Uri? = null
    private var overlayText = ""

    private val stepNames = arrayOf("القارئ", "السورة والآيات", "الخلفية", "النغمة الدينية", "النص والحركة", "المؤثرات", "العلامة", "المراجعة", "التصدير")
    private val stepTitles = arrayOf("اختر صوتاً يحمل الآية", "حدّد السورة والآيات", "اختر مشهداً يليق بالمعنى", "أضف النغمة الدينية", "نسّق النص والحركة", "أضف لمستك البصرية", "ثبّت هوية المقطع", "راجع كل التفاصيل", "صدّر النسخة النهائية")
    private val stepDescriptions = arrayOf(
        "حدّد القارئ الذي تريد أن يقود إيقاع المقطع، ويمكنك البحث بالاسم أو الرواية.",
        "اختر السورة ونطاق الآيات التي ستظهر في الريل قبل الانتقال إلى المشهد.",
        "اختر خلفية من القوالب الجاهزة أو ارفع صورة من جهازك لتخصيص المشهد.",
        "أضف ملف التلاوة الأساسي أو نغمة خلفية مرخصة من جهازك.",
        "تظهر الآية بخط واضح مع حركة زمنية ومؤشر يواكب مدة الصوت.",
        "اضبط الطبقات اللونية والتعتيم للحصول على قراءة مريحة للنص.",
        "أضف اسمك أو حسابك التعريفي في أسفل الفيديو.",
        "راجع القارئ والسورة والخلفية والجودة قبل إنشاء ملف MP4.",
        "كل شيء جاهز. أنشئ الفيديو العمودي وشاركه مباشرة."
    )

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
        binding.startScreen.visibility = View.VISIBLE
        binding.workspaceScreen.visibility = View.GONE

        binding.cardTilawa.setOnClickListener { openWorkspace("تلاوة") }
        binding.cardHadith.setOnClickListener { openWorkspace("حديث") }
        binding.cardDuaa.setOnClickListener { openWorkspace("دعاء") }
        binding.cardMessage.setOnClickListener { openWorkspace("رسالة") }
        binding.btnBackToTypes.setOnClickListener { showTypeChooser() }
        binding.btnRecitation.setOnClickListener { showRecitationDialog() }
        binding.btnAudio.setOnClickListener { pickAudio.launch("audio/*") }
        binding.btnBackground.setOnClickListener { showBackgroundDialog() }
        binding.btnCustomize.setOnClickListener { showCustomizeDialog() }
        binding.btnExport.setOnClickListener { exportAndShareReel() }
        binding.btnPrevStep.setOnClickListener { if (currentStep > 1) setStep(currentStep - 1) }
        binding.btnNextStep.setOnClickListener { if (currentStep < stepNames.size) setStep(currentStep + 1) else exportAndShareReel() }
        bindStepSelectors()
        bindReciterButtons()
        binding.etReciterSearch.addTextChangedListener { text -> filterReciters(text?.toString().orEmpty()) }
        binding.videoPreview.setOnPreparedListener { player ->
            player.isLooping = true
            binding.videoPreview.start()
        }
        setStep(1)
    }

    private fun openWorkspace(type: String) {
        selectedType = type
        binding.startScreen.visibility = View.GONE
        binding.workspaceScreen.visibility = View.VISIBLE
        binding.tvWorkspaceType.text = "مسار $type"
        currentStep = 1
        setStep(1)
        binding.tvStatus.text = "بدأنا مسار إنشاء مقطع ريلز $type"
    }

    private fun showTypeChooser() {
        binding.workspaceScreen.visibility = View.GONE
        binding.startScreen.visibility = View.VISIBLE
    }

    private fun bindStepSelectors() {
        val selectors = listOf(binding.step1, binding.step2, binding.step3, binding.step4, binding.step5, binding.step6, binding.step7, binding.step8, binding.step9)
        selectors.forEachIndexed { index, view -> view.setOnClickListener { setStep(index + 1) } }
    }

    private fun setStep(step: Int) {
        currentStep = step.coerceIn(1, stepNames.size)
        val index = currentStep - 1
        binding.progressBar.progress = (currentStep * 100) / stepNames.size
        binding.tvStepProgress.text = "المرحلة $currentStep من ${stepNames.size}"
        binding.tvStepKicker.text = "%02d / %02d · %s".format(currentStep, stepNames.size, stepNames[index])
        binding.tvEditorTitle.text = stepTitles[index]
        binding.tvEditorDescription.text = stepDescriptions[index]
        binding.reciterPanel.visibility = if (currentStep == 1 && selectedType == "تلاوة") View.VISIBLE else View.GONE
        binding.etReciterSearch.visibility = binding.reciterPanel.visibility
        binding.tvReciterCount.visibility = binding.reciterPanel.visibility
        binding.btnPrevStep.isEnabled = currentStep > 1
        binding.btnNextStep.text = if (currentStep == stepNames.size) "إنتاج الريل ›" else "التالي ›"
        binding.tvStatus.text = "${stepNames[index]} · $selectedType"
    }

    private fun bindReciterButtons() {
        binding.btnReciterMishary.setOnClickListener { selectReciter("مشاري العفاسي") }
        binding.btnReciterBasit.setOnClickListener { selectReciter("عبدالباسط عبدالصمد") }
        binding.btnReciterMinshawy.setOnClickListener { selectReciter("محمد صديق المنشاوي") }
        binding.btnReciterHusary.setOnClickListener { selectReciter("محمود خليل الحصري") }
        binding.btnReciterDosari.setOnClickListener { selectReciter("ياسر الدوسري") }
        binding.btnReciterMuaiqly.setOnClickListener { selectReciter("ماهر المعيقلي") }
    }

    private fun selectReciter(name: String) {
        selectedReciter = name
        binding.tvPreviewMeta.text = "$selectedReciter · $selectedSurah · 1080p"
        binding.tvStatus.text = "تم اختيار $selectedReciter"
    }

    private fun filterReciters(query: String) {
        val buttons = listOf(binding.btnReciterMishary, binding.btnReciterBasit, binding.btnReciterMinshawy, binding.btnReciterHusary, binding.btnReciterDosari, binding.btnReciterMuaiqly)
        buttons.forEach { button -> button.visibility = if (query.isBlank() || button.text.contains(query, ignoreCase = true)) View.VISIBLE else View.GONE }
    }

    private fun showRecitationDialog() {
        val items = arrayOf("سورة الشرح · الآيات 1–8", "سورة الملك · الآيات 1–5", "سورة الرحمن · الآيات 1–13", "سورة النبأ · الآيات 1–10")
        MaterialAlertDialogBuilder(this)
            .setTitle("اختر السورة والآيات")
            .setSingleChoiceItems(items, 0) { dialog, which ->
                selectedSurah = items[which]
                selectedVerse = when (which) {
                    1 -> "تَبَارَكَ الَّذِي بِيَدِهِ الْمُلْكُ"
                    2 -> "الرَّحْمَنُ عَلَّمَ الْقُرْآنَ"
                    3 -> "عَمَّ يَتَسَاءَلُونَ"
                    else -> "إِنَّ مَعَ الْعُسْرِ يُسْرًا"
                }
                binding.btnRecitation.text = "١  ·  $selectedSurah"
                binding.tvPreviewVerse.text = selectedVerse
                binding.tvPreviewMeta.text = "$selectedReciter · $selectedSurah · 1080p"
                dialog.dismiss()
            }.show()
    }

    private fun showBackgroundDialog() {
        val items = arrayOf("نسيم الفجر", "سماء ليلية", "مسجد هادئ", "ألوان تجريدية", "رفع من الجهاز")
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
            .setTitle("العلامة والنص التعريفي")
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
                val output = ReelExporter.export(this, selectedVerse, "$selectedSurah · $selectedReciter", overlayText, selectedBackgroundUri, selectedAudioUri) { progress ->
                    runOnUiThread { binding.tvStatus.text = "جارٍ إنتاج فيديو MP4 عمودي… $progress%" }
                }
                val uri = FileProvider.getUriForFile(this, fileProviderAuthority, output)
                runOnUiThread {
                    binding.btnExport.isEnabled = true
                    binding.videoPreview.visibility = View.VISIBLE
                    binding.videoPreview.setVideoURI(uri)
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
            putExtra(Intent.EXTRA_TEXT, "ريل $selectedType — $selectedSurah")
        }
        startActivity(Intent.createChooser(share, "مشاركة الريل"))
    }

    override fun onDestroy() {
        exportExecutor.shutdownNow()
        super.onDestroy()
    }
}
