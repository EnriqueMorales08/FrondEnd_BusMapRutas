package com.example.app_rutas.ui.common

import android.os.Bundle
import android.view.*
import androidx.fragment.app.DialogFragment
import com.example.app_rutas.R

class LoadingDialogFragment : DialogFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.FullScreenLoadingDialog)
        isCancelable = false
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
        inflater.inflate(R.layout.dialog_loading_fullscreen, container, false)

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    companion object {
        fun newInstance() = LoadingDialogFragment()
    }
}
