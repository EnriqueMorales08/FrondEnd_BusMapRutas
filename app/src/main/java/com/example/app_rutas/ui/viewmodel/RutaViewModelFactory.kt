package com.example.app_rutas.ui.viewmodel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.app_rutas.domain.usecases.ObtenerRutaUseCase
import com.example.app_rutas.infrastructure.repositories.RutaRepositoryImpl

class RutaViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val useCase = ObtenerRutaUseCase(RutaRepositoryImpl())
        return RutaViewModel(useCase) as T
    }
}