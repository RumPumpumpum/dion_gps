package com.dion.gpsmock

import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.dion.gpsmock.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var appPreferences: AppPreferences

    private val presetViewIds = mapOf(
        AppPreferences.AUTO_OFF_DISABLED to R.id.auto_off_disabled,
        5 to R.id.auto_off_5,
        10 to R.id.auto_off_10,
        15 to R.id.auto_off_15,
        30 to R.id.auto_off_30,
        60 to R.id.auto_off_60
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appPreferences = AppPreferences(this)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings_title)

        setupAutoOffOptions()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupAutoOffOptions() {
        val currentMinutes = appPreferences.autoOffMinutes
        val isPreset = presetViewIds.containsKey(currentMinutes)

        AppPreferences.AUTO_OFF_OPTIONS.forEach { (minutes, label) ->
            addRadioButton(presetViewIds.getValue(minutes), label)
        }
        addRadioButton(R.id.auto_off_custom, getString(R.string.auto_off_custom))

        // 초기 선택 상태 복원
        if (isPreset) {
            binding.radioGroupAutoOff.check(presetViewIds.getValue(currentMinutes))
            binding.customInputRow.visibility = View.GONE
        } else {
            // 프리셋에 없는 값이면 사용자 정의로 간주
            binding.radioGroupAutoOff.check(R.id.auto_off_custom)
            binding.customInputRow.visibility = View.VISIBLE
            binding.editCustomMinutes.setText(currentMinutes.toString())
        }

        binding.radioGroupAutoOff.setOnCheckedChangeListener { _: RadioGroup, checkedId: Int ->
            if (checkedId == R.id.auto_off_custom) {
                binding.customInputRow.visibility = View.VISIBLE
                binding.editCustomMinutes.requestFocus()
            } else {
                binding.customInputRow.visibility = View.GONE
                val minutes = presetViewIds.entries.firstOrNull { it.value == checkedId }?.key
                    ?: AppPreferences.AUTO_OFF_DISABLED
                saveMinutes(minutes)
            }
        }

        binding.btnApplyCustom.setOnClickListener { applyCustomMinutes() }
    }

    private fun addRadioButton(viewId: Int, label: String) {
        val radioButton = RadioButton(this).apply {
            id = viewId
            text = label
            textSize = 16f
            setPadding(0, 24, 0, 24)
        }
        binding.radioGroupAutoOff.addView(radioButton)
    }

    private fun applyCustomMinutes() {
        val minutes = binding.editCustomMinutes.text?.toString()?.trim()?.toIntOrNull() ?: 0
        if (minutes < 1) {
            Toast.makeText(this, R.string.auto_off_custom_invalid, Toast.LENGTH_SHORT).show()
            return
        }
        saveMinutes(minutes)
        hideKeyboard()
    }

    private fun saveMinutes(minutes: Int) {
        appPreferences.autoOffMinutes = minutes
        Toast.makeText(
            this,
            getString(R.string.auto_off_saved, AppPreferences.labelForMinutes(minutes)),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(binding.editCustomMinutes.windowToken, 0)
    }
}
