package com.example.app_rutas.ui.viewmodel

import androidx.lifecycle.*
import com.example.app_rutas.domain.usecases.ObtenerInformacionUseCase
import com.example.app_rutas.model.Informacion
import kotlinx.coroutines.launch

class InformacionViewModel(private val useCase: ObtenerInformacionUseCase) : ViewModel() {

    private val _informacion = MutableLiveData<Informacion?>()
    val informacion: LiveData<Informacion?> = _informacion

    fun obtenerInformacion(empresaId: Long) {
        viewModelScope.launch {
            val data = useCase.ejecutar(empresaId)
            _informacion.postValue(data)
        }
    }
}
