package ru.valldun.ruslan

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class WizardPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> WizardStep1Fragment()
            1 -> WizardStep2Fragment()
            2 -> WizardStep3Fragment()
            3 -> WizardStep4Fragment()
            else -> throw IllegalArgumentException("Invalid position: $position")
        }
    }
}