package com.toybox.minipos

import android.app.Application
import com.toybox.minipos.data.local.AppDatabase

class MiniPosApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
}
