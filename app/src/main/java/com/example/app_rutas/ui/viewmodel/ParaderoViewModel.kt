package com.example.app_rutas.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.app_rutas.model.Paradero
import com.example.app_rutas.domain.usecases.ObtenerParaderoCercanoUseCase
import kotlinx.coroutines.launch

class ParaderoViewModel(
    private val obtenerParaderoCercanoUseCase: ObtenerParaderoCercanoUseCase
) : ViewModel() {

    private val _paraderoCercano = MutableLiveData<Paradero?>()
    val paraderoCercano: LiveData<Paradero?> = _paraderoCercano

    fun obtenerParaderoMasCercano(lat: Double, lng: Double, rutaId: Long) {
        viewModelScope.launch {
            try {
                val paradero = obtenerParaderoCercanoUseCase(lat, lng, rutaId)
                _paraderoCercano.postValue(paradero)
            } catch (e: Exception) {
                _paraderoCercano.postValue(null)
            }
        }
    }

}
