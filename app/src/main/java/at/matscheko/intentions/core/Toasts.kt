package at.matscheko.intentions.core

import android.content.Context
import android.widget.Toast

/** Convenience for the brief confirmation toasts used by the list screens. */
fun toast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
