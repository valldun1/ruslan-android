package ru.valldun.ruslan

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class WizardPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    private val fragments = mutableListOf<Fragment?>()

    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        val fragment = when (position) {
            0 -> WizardStep1Fragment()
            1 -> WizardStep2Fragment()
            2 -> WizardStep3Fragment()
            else -> throw IllegalArgumentException("Invalid position: $position")
        }
        // Store for later access
        while (fragments.size <= position) {
            fragments.add(null)
        }
        fragments[position] = fragment
        return fragment
    }

    fun getFragmentAt(position: Int): Fragment? {
        return if (position < fragments.size) fragments[position] else null
    }
}
