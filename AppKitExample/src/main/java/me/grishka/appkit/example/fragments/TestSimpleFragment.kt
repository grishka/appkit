package me.grishka.appkit.example.fragments

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button

import me.grishka.appkit.Nav
import me.grishka.appkit.example.R
import me.grishka.appkit.fragments.ToolbarFragment

class TestSimpleFragment : ToolbarFragment() {

    override fun onAttach(activity: Activity) {
        super.onAttach(activity)
        setTitle("Toolbar fragment")
    }

    override fun onCreateContentView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View {
        val view = inflater.inflate(R.layout.simple_fragment, container, false)

        val stack = view.findViewById<Button>(R.id.stack)
        val loader = view.findViewById<Button>(R.id.loader)
        val spinner = view.findViewById<Button>(R.id.spinner)

        stack.setOnClickListener {
            val args = Bundle()
            args.putInt("index", 1)
            Nav.go(activity, StackTestFragment::class.java, args)
        }

        loader.setOnClickListener { Nav.go(activity, ExampleLoaderFragment::class.java, Bundle()) }

        spinner.setOnClickListener { Nav.go(activity, SpinnerNavigationFragment::class.java, Bundle()) }

        return view
    }
}
