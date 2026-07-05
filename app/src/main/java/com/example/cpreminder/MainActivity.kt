package com.example.cpreminder

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * Main (home) screen. Hosts the "Streak", "Contests", "Friends", and
 * "Profile" tabs, switchable via the bottom navigation bar or by swiping
 * between pages. A top title bar shows the current tab's name.
 */
class MainActivity : AppCompatActivity() {

    private val tabOrder = listOf(R.id.tab_streak, R.id.tab_contests, R.id.tab_friends, R.id.tab_profile)
    private val tabTitles = listOf(R.string.tab_streak, R.string.tab_contests, R.string.tab_friends, R.string.tab_profile)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))

        val viewPager = findViewById<ViewPager2>(R.id.viewPager)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)

        viewPager.adapter = MainPagerAdapter(this)

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val itemId = tabOrder[position]
                if (bottomNav.selectedItemId != itemId) {
                    bottomNav.selectedItemId = itemId
                }
                supportActionBar?.title = getString(tabTitles[position])
            }
        })

        bottomNav.setOnItemSelectedListener { item ->
            val position = tabOrder.indexOf(item.itemId)
            if (position != -1 && viewPager.currentItem != position) {
                viewPager.currentItem = position
            }
            true
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (viewPager.currentItem != 0) {
                    viewPager.currentItem = 0
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        supportActionBar?.title = getString(tabTitles[0])
    }
}
