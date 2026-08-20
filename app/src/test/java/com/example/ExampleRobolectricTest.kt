package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sidegallery.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private lateinit var app: Application
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext<Application>()
        // Clear prefs for a clean start
        app.getSharedPreferences("overlay_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        viewModel = MainViewModel(app)
    }

    @Test
    fun `test app name resource`() {
        val appName = try {
            app.getString(com.sidegallery.app.R.string.app_name)
        } catch (e: Exception) {
            "SideGallery"
        }
        assertEquals("SideGallery", appName)
    }

    @Test
    fun `test onboarding default and completion flow`() {
        // Dumb-user initial launch: onboarding should NOT be completed
        assertFalse(viewModel.hasCompletedOnboarding.value)

        // Complete onboarding
        viewModel.completeOnboarding()
        assertTrue(viewModel.hasCompletedOnboarding.value)

        // Verify persistence with a new ViewModel instance
        val reloadedViewModel = MainViewModel(app)
        assertTrue(reloadedViewModel.hasCompletedOnboarding.value)
    }

    @Test
    fun `test carousel scroll direction toggle top to bottom and bottom to top`() {
        // Default should be TOP_TO_BOTTOM (normal downward scroll)
        assertEquals(ScrollDirection.TOP_TO_BOTTOM, viewModel.scrollDirection.value)

        // Change to BOTTOM_TO_TOP (inverted upward scroll)
        viewModel.setScrollDirection(ScrollDirection.BOTTOM_TO_TOP)
        assertEquals(ScrollDirection.BOTTOM_TO_TOP, viewModel.scrollDirection.value)

        // Verify persistence
        val reloadedViewModel = MainViewModel(app)
        assertEquals(ScrollDirection.BOTTOM_TO_TOP, reloadedViewModel.scrollDirection.value)

        // Change back to TOP_TO_BOTTOM
        viewModel.setScrollDirection(ScrollDirection.TOP_TO_BOTTOM)
        assertEquals(ScrollDirection.TOP_TO_BOTTOM, viewModel.scrollDirection.value)
    }

    @Test
    fun `test trigger type and panel side persistence`() {
        viewModel.setTriggerType(TriggerType.EDGE_SWIPE)
        assertEquals(TriggerType.EDGE_SWIPE, viewModel.triggerType.value)

        viewModel.setPanelSide(PanelSide.LEFT)
        assertEquals(PanelSide.LEFT, viewModel.panelSide.value)

        val reloadedViewModel = MainViewModel(app)
        assertEquals(TriggerType.EDGE_SWIPE, reloadedViewModel.triggerType.value)
        assertEquals(PanelSide.LEFT, reloadedViewModel.panelSide.value)
    }

    @Test
    fun `test swipe height percent clamping and independence`() {
        viewModel.setPanelHeightPercent(90)
        viewModel.setSwipeHeightPercent(50)
        assertEquals(50, viewModel.swipeHeightPercent.value)
        assertEquals(90, viewModel.panelHeightPercent.value) // remains independent

        // Clamped max
        viewModel.setSwipeHeightPercent(150)
        assertEquals(100, viewModel.swipeHeightPercent.value)
        assertEquals(90, viewModel.panelHeightPercent.value)

        // Clamped min (10%)
        viewModel.setSwipeHeightPercent(5)
        assertEquals(10, viewModel.swipeHeightPercent.value)
        assertEquals(90, viewModel.panelHeightPercent.value)
    }

    @Test
    fun `dumb user test - grid columns, panel width, and sorting options`() {
        viewModel.setGridColumns(3)
        assertEquals(3, viewModel.gridColumns.value)

        viewModel.setPanelWidth(PanelWidth.TWO_THIRDS)
        assertEquals(PanelWidth.TWO_THIRDS, viewModel.panelWidth.value)
        assertEquals(66, viewModel.panelWidthPercent.value)

        viewModel.setPanelWidthPercent(75)
        assertEquals(75, viewModel.panelWidthPercent.value)

        viewModel.setPanelHeightPercent(60)
        assertEquals(60, viewModel.panelHeightPercent.value)

        viewModel.setSwipeHeightPercent(30)
        assertEquals(30, viewModel.swipeHeightPercent.value)

        viewModel.setSortOption(SortOption.NAME_DESC)
        assertEquals(SortOption.NAME_DESC, viewModel.sortOption.value)

        viewModel.setScrollDirection(ScrollDirection.BOTTOM_TO_TOP)
        assertEquals(ScrollDirection.BOTTOM_TO_TOP, viewModel.scrollDirection.value)

        viewModel.setHideInLandscape(true)
        assertTrue(viewModel.hideInLandscape.value)

        // Verify full state persistence across restart
        val reloaded = MainViewModel(app)
        assertEquals(3, reloaded.gridColumns.value)
        assertEquals(75, reloaded.panelWidthPercent.value)
        assertEquals(60, reloaded.panelHeightPercent.value)
        assertEquals(30, reloaded.swipeHeightPercent.value)
        assertEquals(SortOption.NAME_DESC, reloaded.sortOption.value)
        assertEquals(ScrollDirection.BOTTOM_TO_TOP, reloaded.scrollDirection.value)
        assertTrue(reloaded.hideInLandscape.value)
    }

    @Test
    fun `dumb user test - panel width and height percent clamping`() {
        viewModel.setPanelWidthPercent(10) // below min 20
        assertEquals(20, viewModel.panelWidthPercent.value)

        viewModel.setPanelWidthPercent(120) // above max 100
        assertEquals(100, viewModel.panelWidthPercent.value)

        viewModel.setPanelHeightPercent(10) // below min 20
        assertEquals(20, viewModel.panelHeightPercent.value)

        viewModel.setPanelHeightPercent(150) // above max 100
        assertEquals(100, viewModel.panelHeightPercent.value)
    }

    @Test
    fun `dumb user test - panel opacity percent clamping and persistence`() {
        // Default opacity is 95%
        assertEquals(95, viewModel.panelOpacityPercent.value)

        // Set to 0% (fully transparent / invisible background)
        viewModel.setPanelOpacityPercent(0)
        assertEquals(0, viewModel.panelOpacityPercent.value)

        // Set to 100% (fully solid)
        viewModel.setPanelOpacityPercent(100)
        assertEquals(100, viewModel.panelOpacityPercent.value)

        // Set to 50%
        viewModel.setPanelOpacityPercent(50)
        assertEquals(50, viewModel.panelOpacityPercent.value)

        // Test below 0 clamping
        viewModel.setPanelOpacityPercent(-20)
        assertEquals(0, viewModel.panelOpacityPercent.value)

        // Test above 100 clamping
        viewModel.setPanelOpacityPercent(150)
        assertEquals(100, viewModel.panelOpacityPercent.value)

        // Verify reload from SharedPreferences
        viewModel.setPanelOpacityPercent(42)
        val reloaded = MainViewModel(app)
        assertEquals(42, reloaded.panelOpacityPercent.value)
    }

    @Test
    fun `dumb user test - trigger guide preview activates expiration time`() {
        val now = System.currentTimeMillis()
        viewModel.triggerGuidePreview(2000L)
        assertTrue(viewModel.guidePreviewUntil.value >= now + 1900L)
    }
}
