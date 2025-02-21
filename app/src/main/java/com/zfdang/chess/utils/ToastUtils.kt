package com.zfdang.chess.utils

import androidx.coordinatorlayout.widget.CoordinatorLayout
import android.widget.Toast
import com.google.android.material.snackbar.Snackbar
import com.zfdang.chess.ChessApp

class ToastUtils {
    companion object {
        var coordinatorLayout: CoordinatorLayout? = null

        fun showToast(stringRes: Int) {
            Toast.makeText(ChessApp.getContext(), stringRes, Toast.LENGTH_LONG)
                .show()
        }

        fun showToast(str: String) {
            Toast.makeText(ChessApp.getContext(), str, Toast.LENGTH_LONG)
                .show()
        }

        fun showSnackbar(message: String) {
            Snackbar.make(coordinatorLayout!!, message, Snackbar.LENGTH_LONG).show()
        }
    }
}