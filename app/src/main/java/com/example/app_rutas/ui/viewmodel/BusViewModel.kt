package com.example.app_rutas.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.app_rutas.infrastructure.firebase.BusFirebaseRepository
import com.example.app_rutas.model.BusPosFirebase

class BusViewModel(
    private val repo: BusFirebaseRepository
) : ViewModel() {

    private val _posicion = MutableLiveData<BusPosFirebase?>()
    val posicion: LiveData<BusPosFirebase?> = _posicion

    fun start(path: String? = null, onError: ((String) -> Unit)? = null) =
        repo.startListening({ pos -> _posicion.postValue(pos) }, onError, pathOverride = path)

    fun stop() = repo.stopListening()

    override fun onCleared() {
        stop()
        super.onCleared()
    }
}

