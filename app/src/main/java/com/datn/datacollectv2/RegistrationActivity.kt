package com.datn.datacollectv2

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.datn.datacollectv2.databinding.ActivityRegistrationBinding
import java.io.File

class RegistrationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegistrationBinding

    // ── Lifecycle ──────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegistrationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (UserSession.isLoggedIn(this)) {
            navigateToSensor(UserSession.getProfile(this)!!.userId)
            return
        }

        setupHideKeyboard()
        setupSpinners()
        setupStartButton()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("name",   binding.etName.text.toString())
        outState.putString("age",    binding.etAge.text.toString())
        outState.putString("device", binding.etDevice.text.toString())
        outState.putInt("gender",    binding.spinnerGender.selectedItemPosition)
        outState.putInt("hand",      binding.spinnerDominantHand.selectedItemPosition)
        outState.putBoolean("consent", binding.cbConsent.isChecked)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        binding.etName.setText(savedInstanceState.getString("name"))
        binding.etAge.setText(savedInstanceState.getString("age"))
        binding.etDevice.setText(savedInstanceState.getString("device"))
        binding.spinnerGender.setSelection(savedInstanceState.getInt("gender"))
        binding.spinnerDominantHand.setSelection(savedInstanceState.getInt("hand"))
        binding.cbConsent.isChecked = savedInstanceState.getBoolean("consent")
    }

    // ── UI setup ───────────────────────────────────────────────────────

    private fun setupHideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        binding.root.setOnTouchListener { v, event ->
            if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                val focused = currentFocus
                if (focused != null) {
                    imm.hideSoftInputFromWindow(focused.windowToken, 0)
                    focused.clearFocus()
                }
            }
            v.performClick()
            false
        }
    }

    private fun setupSpinners() {
        setupGenderSpinner()
        setupHandSpinner()
    }

    private fun setupGenderSpinner() {
        val genderItems = listOf(getString(R.string.spinner_hint_gender)) +
                resources.getStringArray(R.array.gender_options).toList()

        val adapter = object : ArrayAdapter<String>(
            this, android.R.layout.simple_spinner_item, genderItems
        ) {
            override fun isEnabled(position: Int) = position != 0
            override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent)
                (view as? TextView)?.setTextColor(
                    if (position == 0) ContextCompat.getColor(context, R.color.hint_text)
                    else ContextCompat.getColor(context, R.color.on_surface)
                )
                return view
            }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerGender.adapter = adapter

        binding.spinnerGender.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                (view as? TextView)?.setTextColor(
                    if (position == 0) ContextCompat.getColor(this@RegistrationActivity, R.color.hint_text)
                    else ContextCompat.getColor(this@RegistrationActivity, R.color.on_surface)
                )
                if (position != 0) {
                    binding.tvGenderError.visibility = View.GONE
                    binding.cardGender.strokeWidth = 0
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun setupHandSpinner() {
        val handItems = listOf(getString(R.string.spinner_hint_hand)) +
                resources.getStringArray(R.array.hand_options).toList()

        val adapter = object : ArrayAdapter<String>(
            this, android.R.layout.simple_spinner_item, handItems
        ) {
            override fun isEnabled(position: Int) = position != 0
            override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent)
                (view as? TextView)?.setTextColor(
                    if (position == 0) ContextCompat.getColor(context, R.color.hint_text)
                    else ContextCompat.getColor(context, R.color.on_surface)
                )
                return view
            }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerDominantHand.adapter = adapter

        binding.spinnerDominantHand.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                (view as? TextView)?.setTextColor(
                    if (position == 0) ContextCompat.getColor(this@RegistrationActivity, R.color.hint_text)
                    else ContextCompat.getColor(this@RegistrationActivity, R.color.on_surface)
                )
                if (position != 0) {
                    binding.tvHandError.visibility = View.GONE
                    binding.cardDominantHand.strokeWidth = 0
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun setupStartButton() {
        binding.btnStart.setOnClickListener {
            if (!validateForm()) return@setOnClickListener

            val userId = "user_" + System.currentTimeMillis()

            val deviceInfo = "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"
            UserSession.saveAndLogin(
                this,
                UserSession.Profile(
                    userId = userId,
                    name   = binding.etName.text.toString(),
                    age    = binding.etAge.text.toString(),
                    gender = binding.spinnerGender.selectedItem.toString(),
                    hand   = binding.spinnerDominantHand.selectedItem.toString(),
                    device = deviceInfo
                )
            )

            saveMetadata(userId)
            navigateToSensor(userId)
        }
    }

    // ── Navigation ─────────────────────────────────────────────────────

    private fun navigateToSensor(userId: String) {
        getSharedPreferences("session_prefs", MODE_PRIVATE).edit()
            .putString("current_session_id", "session_1")
            .apply()

        Intent(this, SensorCollectionActivity::class.java).also {
            it.putExtra("USER_ID", userId)
            it.putExtra("SESSION_ID", "session_1")
            startActivity(it)
            finish()
        }
    }

    // ── Validation ─────────────────────────────────────────────────────

    private fun validateForm(): Boolean {
        var isValid = true
        with(binding) {
            if (etName.text.toString().isBlank()) {
                etName.error = "Vui lòng nhập họ tên"; isValid = false
            }
            if (etAge.text.toString().isBlank()) {
                etAge.error = "Vui lòng nhập tuổi"; isValid = false
            }
            if (spinnerGender.selectedItemPosition == 0) {
                tvGenderError.visibility = View.VISIBLE
                cardGender.strokeWidth = 2
                cardGender.strokeColor = ContextCompat.getColor(this@RegistrationActivity, R.color.error)
                isValid = false
            } else {
                tvGenderError.visibility = View.GONE
                cardGender.strokeWidth = 0
            }
            if (spinnerDominantHand.selectedItemPosition == 0) {
                tvHandError.visibility = View.VISIBLE
                cardDominantHand.strokeWidth = 2
                cardDominantHand.strokeColor = ContextCompat.getColor(this@RegistrationActivity, R.color.error)
                isValid = false
            } else {
                tvHandError.visibility = View.GONE
                cardDominantHand.strokeWidth = 0
            }
            if (etDevice.text.toString().isBlank()) {
                etDevice.error = "Vui lòng nhập loại thiết bị"; isValid = false
            }
            if (!cbConsent.isChecked) {
                tvConsentError.visibility = View.VISIBLE; isValid = false
            } else {
                tvConsentError.visibility = View.GONE
            }
        }
        return isValid
    }

    // ── Persist metadata file ──────────────────────────────────────────

    private fun saveMetadata(userId: String) {
        val dir = File(getExternalFilesDir(null), userId)
        dir.mkdirs()
        val deviceInfo = "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"
        File(dir, "metadata.csv").writeText(
            "field,value\n" +
                    "user_id,$userId\n" +
                    "name,${binding.etName.text}\n" +
                    "age,${binding.etAge.text}\n" +
                    "gender,${binding.spinnerGender.selectedItem}\n" +
                    "dominant_hand,${binding.spinnerDominantHand.selectedItem}\n" +
                    "device,$deviceInfo\n" +
                    "timestamp,${System.currentTimeMillis()}\n"
        )
    }
}