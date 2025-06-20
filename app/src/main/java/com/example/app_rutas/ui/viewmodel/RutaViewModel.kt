package com.example.app_rutas.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

import com.example.app_rutas.domain.usecases.ObtenerRutaUseCase
import com.example.app_rutas.model.Coordenada

class RutaViewModel(private val useCase: ObtenerRutaUseCase) : ViewModel() {

    private val _coordenadasRuta = MutableLiveData<List<Coordenada>>()
    val coordenadasRuta: LiveData<List<Coordenada>> = _coordenadasRuta

    fun obtenerRuta(rutaId: Long) {
        viewModelScope.launch {
            val coordenadas = useCase.ejecutar(rutaId)
            _coordenadasRuta.postValue(coordenadas)
        }
    }
}