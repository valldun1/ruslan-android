package ru.valldun.ruslan

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.fragment.app.Fragment
import ru.valldun.ruslan.databinding.FragmentWizardStepModelBinding

class WizardStepModelFragment : Fragment() {

    private var _binding: FragmentWizardStepModelBinding? = null
    private val binding get() = _binding!!

    private var selectedModel = ""
    private var providerId = "deepseek"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWizardStepModelBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        providerId = arguments?.getString("providerId", "deepseek") ?: "deepseek"
        populateModels()
    }

    fun setProvider(provider: String) {
        providerId = provider
        if (_binding != null) populateModels()
    }

    private fun populateModels() {
        val group = binding.rgModels
        group.removeAllViews()
        val ctx = group.context

        val models = ProviderConfig.getModelsForProvider(providerId)
        val providerName = ProviderConfig.BUILT_IN.find { it.id == providerId }?.name ?: providerId
        binding.tvProviderName.text = "→ $providerName"

        for (model in models) {
            val rb = RadioButton(ctx).apply {
                id = View.generateViewId()
                tag = model
                text = model
                textSize = 14f
                setTextColor(ctx.getColor(R.color.text_primary))
                val lp = RadioGroup.LayoutParams(
                    RadioGroup.LayoutParams.MATCH_PARENT,
                    RadioGroup.LayoutParams.WRAP_CONTENT
                )
                lp.setMargins(0, 0, 0, 12)
                layoutParams = lp
                setOnClickListener {
                    selectedModel = model
                    for (i in 0 until group.childCount) {
                        val child = group.getChildAt(i)
                        if (child is RadioButton) {
                            child.isChecked = child.tag == model
                        }
                    }
                }
            }
            group.addView(rb)
        }

        // Select first by default
        if (models.isNotEmpty()) {
            selectedModel = models[0]
            val first = group.getChildAt(0) as? RadioButton
            first?.isChecked = true
        }
    }

    fun getSelectedModel(): String = selectedModel

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
