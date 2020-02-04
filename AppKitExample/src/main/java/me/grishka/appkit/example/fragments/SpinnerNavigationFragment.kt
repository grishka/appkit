package me.grishka.appkit.example.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import me.grishka.appkit.example.R
import me.grishka.appkit.fragments.ToolbarFragment
import java.util.*

class SpinnerNavigationFragment : ToolbarFragment() {

    private var text: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateContentView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View {
        val view = inflater.inflate(R.layout.big_text_view, container, false)
        text = view.findViewById(R.id.text)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setSpinnerItems(Arrays.asList("Spinner", "Navigation", "Items", "Wow"))
    }

    override fun onSpinnerItemSelected(position: Int): Boolean {
        text!!.text = "Item: $position"
        return true
    }
}
