package com.zfdang.chess.utils

import android.widget.Toast
import com.zfdang.chess.ChessApp

class ToastUtils {
    companion object {
        fun showToast(stringRes: Int) {
            Toast.makeText(ChessApp.getContext(), stringRes, Toast.LENGTH_LONG)
                .show()
        }

        fun showToast(str: String) {
            Toast.makeText(ChessApp.getContext(), str, Toast.LENGTH_LONG)
                .show()
        }
    }
}