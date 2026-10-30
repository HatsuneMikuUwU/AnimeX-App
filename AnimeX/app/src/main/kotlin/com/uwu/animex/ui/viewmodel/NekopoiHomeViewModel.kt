package com.uwu.animex.ui.viewmodel

import com.uwu.animex.data.model.NekopoiSection
import com.uwu.animex.data.nekopoi.Nekopoi
import com.uwu.animex.data.nekopoi.NekopoiPrefs

/** Baris kategori Nekopoi di Home; dimuat terpisah supaya lambatnya situs tidak menahan Home utama. */
class NekopoiHomeViewModel :
    LoadViewModel<List<NekopoiSection>>(
        loader = { force ->
            if (NekopoiPrefs.showCategories.value) Nekopoi.homeSections(force) else emptyList()
        },
    )
