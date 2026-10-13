package com.uwu.animex.ui.viewmodel

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.uwu.animex.data.api.Api
import com.uwu.animex.data.model.HomeData
import com.uwu.animex.data.model.Movie

class HomeListViewModel : LoadViewModel<HomeData>(loader = { force -> Api.home(force) })

class ScheduleListViewModel : LoadViewModel<List<Movie>>(loader = { force -> Api.schedule(force) })

object ListViewModels {
    fun homeFactory(): ViewModelProvider.Factory =
        viewModelFactory { initializer { HomeListViewModel() } }

    fun scheduleFactory(): ViewModelProvider.Factory =
        viewModelFactory { initializer { ScheduleListViewModel() } }
}
