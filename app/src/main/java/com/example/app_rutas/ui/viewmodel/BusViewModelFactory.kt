package com.example.app_rutas.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.app_rutas.infrastructure.firebase.BusFirebaseRepository

class BusViewModelFactory(
    private val path: String = "ubicacion"
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BusViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BusViewModel(BusFirebaseRepository(path)) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

