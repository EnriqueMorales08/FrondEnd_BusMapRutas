package com.example.app_rutas.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.app_rutas.domain.repositories.InformacionRepository
import com.example.app_rutas.domain.usecases.ObtenerInformacionUseCase

class InformacionViewModelFactory(
    private val repository: InformacionRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(InformacionViewModel::class.java)) {
            val useCase = ObtenerInformacionUseCase(repository)
            return InformacionViewModel(useCase) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
