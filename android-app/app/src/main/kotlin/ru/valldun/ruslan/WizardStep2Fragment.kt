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

    private var selectedProvider = "deepseek"

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

        binding.providerDeepseek.setOnClickListener {
            selectProvider("deepseek")
        }
        binding.providerOpenai.setOnClickListener {
            selectProvider("openai")
        }
        binding.providerAnthropic.setOnClickListener {
            selectProvider("anthropic")
        }

        selectProvider("deepseek")
    }

    private fun selectProvider(provider: String) {
        binding.providerDeepseek.isChecked = provider == "deepseek"
        binding.providerOpenai.isChecked = provider == "openai"
        binding.providerAnthropic.isChecked = provider == "anthropic"
        selectedProvider = provider
    }

    fun getSelectedProvider(): String = selectedProvider

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
