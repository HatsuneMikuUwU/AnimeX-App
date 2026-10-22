package com.uwu.animex.ui.viewmodel

import com.uwu.animex.data.model.Movie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PagedListViewModelTest {
    @Test
    fun failedRefreshDoesNotLeavePaginationLockedAfterCancellingLoadMore() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                var loadMoreCalls = 0
                val viewModel =
                    PagedListViewModel { page, force ->
                        when {
                            page == 0 && force -> error("refresh failed")
                            page == 0 -> List(20) { Movie(id = "initial-$it") }
                            else -> {
                                loadMoreCalls++
                                if (loadMoreCalls == 1) awaitCancellation()
                                listOf(Movie(id = "next"))
                            }
                        }
                    }

                runCurrent()
                assertTrue(viewModel.state.value.hasMore)
                viewModel.loadMore()
                runCurrent()
                assertTrue(viewModel.state.value.loadingMore)

                viewModel.refresh()
                runCurrent()
                assertFalse(viewModel.state.value.loadingMore)

                viewModel.loadMore()
                runCurrent()
                assertEquals(2, loadMoreCalls)
                assertFalse(viewModel.state.value.loadingMore)
                assertTrue(
                    viewModel.state.value.items
                        .any { it.id == "next" },
                )
            } finally {
                Dispatchers.resetMain()
            }
        }
}
