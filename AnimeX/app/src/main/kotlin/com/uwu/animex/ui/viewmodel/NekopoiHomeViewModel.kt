package com.uwu.animex.ui.viewmodel

import com.uwu.animex.data.model.NekopoiSection
import com.uwu.animex.data.nekopoi.Nekopoi

/** Baris kategori Nekopoi di Home; dimuat terpisah supaya lambatnya situs tidak menahan Home utama. */
class NekopoiHomeViewModel :
    LoadViewModel<List<NekopoiSection>>(
        loader = { force -> Nekopoi.homeSections(force) },
    )
