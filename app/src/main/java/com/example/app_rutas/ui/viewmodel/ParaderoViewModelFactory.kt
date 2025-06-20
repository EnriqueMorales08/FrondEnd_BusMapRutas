package com.example.app_rutas.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.app_rutas.domain.usecases.ObtenerParaderoCercanoUseCase

class ParaderoViewModelFactory(
    private val useCase: ObtenerParaderoCercanoUseCase
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ParaderoViewModel::class.java)) {
            return ParaderoViewModel(useCase) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}