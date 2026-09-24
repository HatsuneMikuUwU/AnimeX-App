package com.uwu.animex.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.uwu.animex.data.Api

@Composable
fun SearchScreen(onOpen: (String) -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    val state by rememberLoad(query) {
        if (query.isBlank()) Api.homeMovies("popular") else Api.search(query)
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it; if (it.isBlank()) query = "" },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            placeholder = { Text("Cari anime…") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { query = input.trim() }),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
        when (val s = state) {
            UiState.Loading -> CenterLoading()
            is UiState.Error -> CenterText("Gagal memuat: ${s.msg}")
            is UiState.Ready ->
                if (s.value.isEmpty()) CenterText("Tidak ada hasil")
                else MovieGrid(s.value, onOpen, bottomPad = 16.dp)
        }
    }
}
