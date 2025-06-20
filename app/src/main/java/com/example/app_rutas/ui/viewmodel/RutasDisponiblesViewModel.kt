// RutasDisponiblesViewModel.kt
package com.example.app_rutas.ui.viewmodel

import androidx.lifecycle.*
import com.example.app_rutas.domain.usecases.ObtenerRutasUseCase
import com.example.app_rutas.infrastructure.repositories.RutaRepositoryImpl
import com.example.app_rutas.model.Ruta
import kotlinx.coroutines.launch

class RutasDisponiblesViewModel : ViewModel() {

    private val useCase = ObtenerRutasUseCase(RutaRepositoryImpl())

    private val _rutasDisponibles = MutableLiveData<List<Ruta>>()
    val rutasDisponibles: LiveData<List<Ruta>> = _rutasDisponibles

    fun obtenerRutas() {
        viewModelScope.launch {
            val rutas = useCase.ejecutar()
            _rutasDisponibles.postValue(rutas)
        }
    }
}
