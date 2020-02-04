package me.grishka.appkit.example

import android.os.Bundle

import me.grishka.appkit.FragmentStackActivity
import me.grishka.appkit.example.api.PlaceholderAPIController
import me.grishka.appkit.example.fragments.TestSimpleFragment

class MainActivity : FragmentStackActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            showFragmentClearingBackStack(TestSimpleFragment())
        }
    }

    companion object {

        init {
            PlaceholderAPIController.getInstance()
        }
    }
}
