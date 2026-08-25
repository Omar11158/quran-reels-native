package com.quranreels.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.quranreels.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var selectedReciter = "مشاري راشد العفاسي"
    private var selectedSurah = "سورة الشرح · الآيات 1–8"
    private var selectedBackground = "حديقة الفجر"
    private var overlayText = ""

    private val pickBackground = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedBackground = "خلفية مخصصة"
            binding.btnBackground.text = "٢  ·  الخلفية: $selectedBackground"
            binding.tvStatus.text = "تم اختيار خلفيتك الخاصة"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnRecitation.setOnClickListener { showRecitationDialog() }
        binding.btnBackground.setOnClickListener { showBackgroundDialog() }
        binding.btnCustomize.setOnClickListener { showCustomizeDialog() }
        binding.btnExport.setOnClickListener { prepareAndShare() }
    }

    private fun showRecitationDialog() {
        val items = arrayOf(
            "مشاري راشد العفاسي — سورة الشرح",
            "ماهر المعيقلي — سورة الملك",
            "عبدالرحمن السديس — سورة الرحمن",
            "ياسر الدوسري — سورة النبأ"
        )
        MaterialAlertDialogBuilder(this)
            .setTitle("اختر التلاوة")
            .setSingleChoiceItems(items, 0) { dialog, which ->
                selectedReciter = items[which].substringBefore(" —")
                selectedSurah = items[which].substringAfter("— ") + " · الآيات المختارة"
                binding.btnRecitation.text = "١  ·  $selectedReciter"
                binding.tvPreviewMeta.text = "$selectedSurah"
                binding.tvStatus.text = "تم اختيار $selectedReciter"
                dialog.dismiss()
            }.show()
    }

    private fun showBackgroundDialog() {
        val items = arrayOf("حديقة الفجر", "سماء ليلية", "مسجد هادئ", "ألوان تجريدية", "رفع من الجهاز")
        MaterialAlertDialogBuilder(this)
            .setTitle("اختر الخلفية")
            .setItems(items) { _, which ->
                if (which == items.lastIndex) {
                    pickBackground.launch("image/*")
                } else {
                    selectedBackground = items[which]
                    binding.btnBackground.text = "٢  ·  الخلفية: $selectedBackground"
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
                binding.btnCustomize.text = if (overlayText.isBlank()) "٣  ·  خصص النص والقالب" else "٣  ·  تم تخصيص العلامة"
                binding.tvStatus.text = "تم حفظ التخصيص"
            }
            .setNegativeButton("إلغاء", null).show()
    }

    private fun prepareAndShare() {
        binding.tvPreviewBadge.text = "تم إعداد المعاينة · 9:16 · Full HD"
        binding.tvStatus.text = "المعاينة جاهزة للمشاركة"
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "ريل قرآني — $selectedSurah\nالقارئ: $selectedReciter\nالخلفية: $selectedBackground\n$overlayText")
        }
        startActivity(Intent.createChooser(share, "مشاركة الريل"))
        Toast.makeText(this, "تم تجهيز الريل للمشاركة", Toast.LENGTH_SHORT).show()
    }
}
