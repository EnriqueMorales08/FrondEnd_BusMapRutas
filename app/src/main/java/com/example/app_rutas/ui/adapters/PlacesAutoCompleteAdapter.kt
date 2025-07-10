package com.example.app_rutas.ui.adapters

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.Filterable
import android.widget.TextView
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.TimeUnit

class PlacesAutoCompleteAdapter(
    context: Context,
    private val placesClient: PlacesClient
) : ArrayAdapter<AutocompletePrediction>(context, android.R.layout.simple_dropdown_item_1line), Filterable {

    private var predictions: List<AutocompletePrediction> = emptyList()

    override fun getCount(): Int = predictions.size

    override fun getItem(position: Int): AutocompletePrediction? = predictions[position]

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent) as TextView
        view.text = getItem(position)?.getFullText(null)
        return view
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val results = FilterResults()
                if (constraint != null && constraint.length >= 2) {
                    val request = FindAutocompletePredictionsRequest.builder()
                        .setQuery(constraint.toString())
                        .setCountries("PE")
                        .setLocationBias(
                            RectangularBounds.newInstance(
                                LatLng(-5.3000, -80.7000), // Suroeste de Piura
                                LatLng(-5.1000, -80.6000)  // Noreste de Piura
                            )
                        )
                        .build()

                    try {
                        val response = Tasks.await(placesClient.findAutocompletePredictions(request), 60, TimeUnit.SECONDS)
                        predictions = response.autocompletePredictions
                        results.values = predictions
                        results.count = predictions.size
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                return results
            }

            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                notifyDataSetChanged()
            }

            override fun convertResultToString(resultValue: Any?): CharSequence {
                return (resultValue as? AutocompletePrediction)?.getFullText(null) ?: ""
            }
        }
    }
}
