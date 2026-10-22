package com.example.nutrilog

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Set by notifications ("Log now") to jump to a tab and pre-fill a template. */
object NavRequest {
    var tab by mutableIntStateOf(-1)
    var prefill by mutableStateOf<String?>(null)
}
