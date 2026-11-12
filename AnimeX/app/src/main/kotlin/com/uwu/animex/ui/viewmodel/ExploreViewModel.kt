package com.uwu.animex.ui.viewmodel

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.uwu.animex.data.api.Api
import com.uwu.animex.data.model.ExploreData
import com.uwu.animex.data.model.ExploreItem

class GenresViewModel :
    LoadViewModel<List<ExploreItem>>(loader = { force -> Api.exploreGenres(force) }, refreshOnApiProviderChange = true)

class YearsViewModel :
    LoadViewModel<List<ExploreItem>>(loader = { force -> Api.exploreYears(force) }, refreshOnApiProviderChange = true)

class StudiosViewModel :
    LoadViewModel<List<ExploreItem>>(loader = { force -> Api.exploreStudios(force) }, refreshOnApiProviderChange = true)

class TypesViewModel :
    LoadViewModel<List<ExploreItem>>(
        loader = { force -> Api.explore(force = force, preview = false).typeOrDefault },
        refreshOnApiProviderChange = true,
    )

class ExploreViewModel :
    LoadViewModel<ExploreData>(
        loader = { force -> Api.explore(force = force, preview = true) },
        needsRevalidate = { !Api.fetchedThisSession("explore/data") },
        refreshOnApiProviderChange = true,
    )

object ExploreViewModels {
    fun genresFactory(): ViewModelProvider.Factory =
        viewModelFactory {
            initializer { GenresViewModel() }
        }

    fun yearsFactory(): ViewModelProvider.Factory =
        viewModelFactory {
            initializer { YearsViewModel() }
        }

    fun studiosFactory(): ViewModelProvider.Factory =
        viewModelFactory {
            initializer { StudiosViewModel() }
        }

    fun typesFactory(): ViewModelProvider.Factory =
        viewModelFactory {
            initializer { TypesViewModel() }
        }

    fun exploreFactory(): ViewModelProvider.Factory =
        viewModelFactory {
            initializer { ExploreViewModel() }
        }
}
