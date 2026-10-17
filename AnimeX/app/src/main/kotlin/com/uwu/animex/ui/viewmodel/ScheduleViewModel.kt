package com.uwu.animex.ui.viewmodel

import com.uwu.animex.data.api.Api
import com.uwu.animex.data.model.Movie

class ScheduleViewModel :
    LoadViewModel<List<Movie>>(
        loader = { force -> Api.schedule(force) },
        needsRevalidate = { !Api.fetchedThisSession("schedule/data") },
    )
