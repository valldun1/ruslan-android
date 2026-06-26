package ru.valldun.ruslan

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import ru.valldun.ruslan.databinding.FragmentWizardStep2Binding

class WizardStep2Fragment : Fragment() {

    private var _binding: FragmentWizardStep2Binding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWizardStep2Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Provider selection
        binding.providerDeepseek.setOnClickListener {
            selectProvider("deepseek")
        }
        binding.providerOpenai.setOnClickListener {
            selectProvider("openai")
        }
        binding.providerAnthropic.setOnClickListener {
            selectProvider("anthropic")
        }

        // Default selection
        selectProvider("deepseek")
    }

    private fun selectProvider(provider: String) {
        // Reset all
        binding.providerDeepseek.isChecked = false
        binding.providerOpenai.isChecked = false
        binding.providerAnthropic.isChecked = false

        // Select one
        when (provider) {
            "deepseek" -> binding.providerDeepseek.isChecked = true
            "openai" -> binding.providerOpenai.isChecked = true
            "anthropic" -> binding.providerAnthropic.isChecked = true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
