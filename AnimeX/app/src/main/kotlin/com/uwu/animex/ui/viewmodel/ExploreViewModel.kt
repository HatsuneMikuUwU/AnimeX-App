package com.uwu.animex.ui.viewmodel

import com.uwu.animex.data.api.Api
import com.uwu.animex.data.model.ExploreData
import com.uwu.animex.data.model.ExploreItem

class GenresViewModel : LoadViewModel<List<ExploreItem>>(loader = { force -> Api.exploreGenres(force) })

class YearsViewModel : LoadViewModel<List<ExploreItem>>(loader = { force -> Api.exploreYears(force) })

class StudiosViewModel : LoadViewModel<List<ExploreItem>>(loader = { force -> Api.exploreStudios(force) })

class TypesViewModel : LoadViewModel<List<ExploreItem>>(
    loader = { force -> Api.explore(force = force, preview = false).typeOrDefault },
)

/** Full explore payload (genre / studio / year / type previews). */
class ExploreViewModel : LoadViewModel<ExploreData>(
    loader = { force -> Api.explore(force = force, preview = true) },
)

object ExploreViewModels {
    fun genresFactory(): androidx.lifecycle.ViewModelProvider.Factory =
        androidx.lifecycle.viewmodel.viewModelFactory {
            androidx.lifecycle.viewmodel.initializer { GenresViewModel() }
        }

    fun yearsFactory(): androidx.lifecycle.ViewModelProvider.Factory =
        androidx.lifecycle.viewmodel.viewModelFactory {
            androidx.lifecycle.viewmodel.initializer { YearsViewModel() }
        }

    fun studiosFactory(): androidx.lifecycle.ViewModelProvider.Factory =
        androidx.lifecycle.viewmodel.viewModelFactory {
            androidx.lifecycle.viewmodel.initializer { StudiosViewModel() }
        }

    fun typesFactory(): androidx.lifecycle.ViewModelProvider.Factory =
        androidx.lifecycle.viewmodel.viewModelFactory {
            androidx.lifecycle.viewmodel.initializer { TypesViewModel() }
        }

    fun exploreFactory(): androidx.lifecycle.ViewModelProvider.Factory =
        androidx.lifecycle.viewmodel.viewModelFactory {
            androidx.lifecycle.viewmodel.initializer { ExploreViewModel() }
        }
}
