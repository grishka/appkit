package me.grishka.appkit.example.fragments

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView

import me.grishka.appkit.Nav
import me.grishka.appkit.example.R
import me.grishka.appkit.fragments.ToolbarFragment

class StackTestFragment : ToolbarFragment() {

    private var index: Int = 0

    override fun onAttach(activity: Activity) {
        super.onAttach(activity)
        setTitle("Fragment stack")
        index = arguments.getInt("index")
    }

    override fun onCreateContentView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View {
        val view = inflater.inflate(R.layout.stack_test_fragment, container, false)

        val text = view.findViewById<TextView>(R.id.text)
        val btn = view.findViewById<Button>(R.id.start_fragment)

        text.text = "Fragment $index"
        btn.setOnClickListener {
            val args = Bundle()
            args.putInt("index", index + 1)
            Nav.go(activity, StackTestFragment::class.java, args)
        }

        return view
    }
}
