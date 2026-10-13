package com.uwu.animex.ui.viewmodel

import com.uwu.animex.data.api.Api
import com.uwu.animex.data.model.HomeData

class HomeViewModel : LoadViewModel<HomeData>(loader = { force -> Api.home(force) })
