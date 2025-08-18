package com.example.app_rutas.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.app_rutas.infrastructure.repositories.UsuarioRepositoryImpl

class RegisterViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val repository = UsuarioRepositoryImpl() // Aquí podrías pasar dependencias si usas DI
        return RegisterViewModel(repository) as T
    }
}