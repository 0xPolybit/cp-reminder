package com.example.cpreminder

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

/** Backs the main tab bar: Streak, Contests, Friends, Profile. */
class MainPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment = when (position) {
        0 -> StreakFragment()
        1 -> ContestsFragment()
        2 -> FriendsFragment()
        else -> ProfileFragment()
    }
}
