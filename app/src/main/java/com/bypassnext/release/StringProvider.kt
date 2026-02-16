package com.bypassnext.release

import android.content.Context
import androidx.annotation.StringRes

interface StringProvider {
    fun getString(@StringRes resId: Int): String
    fun getString(@StringRes resId: Int, vararg args: Any): String
}

class AndroidStringProvider(private val context: Context) : StringProvider {
    override fun getString(@StringRes resId: Int): String = context.getString(resId)
    override fun getString(@StringRes resId: Int, vararg args: Any): String = context.getString(resId, *args)
}
