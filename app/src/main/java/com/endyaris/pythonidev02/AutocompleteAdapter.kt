package com.endyaris.pythonidev02

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView

class AutocompleteAdapter(
    context: Context,
    private val items: MutableList<String> // ✅ use MutableList
) : ArrayAdapter<String>(context, 0, items) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_autocomplete, parent, false)

        val textView = view.findViewById<TextView>(R.id.itemText)
        textView.text = getItem(position)
        textView.setTextColor(Color.BLACK)
        textView.textSize = 12f
        return view
    }

    fun updateData(newItems: List<String>) {
        items.clear()              // ✅ now works
        items.addAll(newItems)     // ✅ now works
        notifyDataSetChanged()
    }
}