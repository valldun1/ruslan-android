package ru.valldun.ruslan

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import ru.valldun.ruslan.databinding.ActivitySetupWizardBinding

class SetupWizardActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetupWizardBinding
    private lateinit var adapter: WizardPagerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupWizardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWizard()
    }

    private fun setupWizard() {
        adapter = WizardPagerAdapter(this)
        binding.viewPager.adapter = adapter
        binding.viewPager.isUserInputEnabled = false

        // Tab indicators
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { _, _ ->
            // Empty, just for dots
        }.attach()

        // Next/Done button
        binding.btnNext.setOnClickListener {
            val currentItem = binding.viewPager.currentItem
            if (currentItem < adapter.itemCount - 1) {
                binding.viewPager.currentItem = currentItem + 1
                updateButtonText()
            } else {
                finishWizard()
            }
        }

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateButtonText()
            }
        })
    }

    private fun updateButtonText() {
        val isLastPage = binding.viewPager.currentItem == adapter.itemCount - 1
        binding.btnNext.text = if (isLastPage) {
            getString(R.string.done)
        } else {
            getString(R.string.next)
        }
    }

    private fun finishWizard() {
        // Mark first run as completed
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
        prefs.edit().putBoolean(MainActivity.KEY_FIRST_RUN, false).apply()

        // Start main activity
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}