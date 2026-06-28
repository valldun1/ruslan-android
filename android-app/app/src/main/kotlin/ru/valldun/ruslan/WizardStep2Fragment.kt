package ru.valldun.ruslan

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import androidx.fragment.app.Fragment
import ru.valldun.ruslan.databinding.FragmentWizardStep2Binding

class WizardStep2Fragment : Fragment() {

    private var _binding: FragmentWizardStep2Binding? = null
    private val binding get() = _binding!!

    private var selectedProvider = ""

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
        populateProviders()
    }

    private fun populateProviders() {
        val group = binding.rgProviders
        group.removeAllViews()
        val ctx = group.context

        for (cfg in ProviderConfig.BUILT_IN) {
            // Skip "custom" — wizard should use real providers
            if (cfg.id == "custom") continue

            val rb = RadioButton(ctx).apply {
                id = View.generateViewId()
                tag = cfg.id
                text = cfg.name
                textSize = 16f
                setTextColor(ctx.getColor(R.color.text_primary))
                layoutParams = RadioGroup.LayoutParams(
                    RadioGroup.LayoutParams.MATCH_PARENT,
                    RadioGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 12)
                }
                setOnClickListener {
                    selectProvider(cfg.id)
                }
            }
            group.addView(rb)
        }

        // Select first by default
        val first = ProviderConfig.BUILT_IN.firstOrNull { it.id != "custom" }
        if (first != null) selectProvider(first.id)
    }

    private fun selectProvider(providerId: String) {
        selectedProvider = providerId
        for (i in 0 until binding.rgProviders.childCount) {
            val child = binding.rgProviders.getChildAt(i)
            if (child is RadioButton) {
                child.isChecked = child.tag == providerId
            }
        }
    }

    fun getSelectedProvider(): String = selectedProvider

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
