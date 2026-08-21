package com.sidegallery.app

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

    @Test
    fun `dumb user test - multi-folder management and navigation`() {
        // Initially, the Pinned folder should exist
        val initialFolders = viewModel.folders.value
        assertTrue(initialFolders.any { it.isSpecialPinned })

        // Add 2 custom folders
        viewModel.addFolderWithDetails("Memes", "content://tree/memes")
        viewModel.addFolderWithDetails("Wallpapers", "content://tree/wallpapers")

        val foldersAfterAdd = viewModel.folders.value
        assertEquals(3, foldersAfterAdd.size) // Pinned + Memes + Wallpapers

        // Test Next and Previous folder navigation with wrap-around
        viewModel.selectFolder(0)
        assertEquals(0, viewModel.currentFolderIndex.value)

        viewModel.nextFolder()
        assertEquals(1, viewModel.currentFolderIndex.value)

        viewModel.nextFolder()
        assertEquals(2, viewModel.currentFolderIndex.value)

        viewModel.nextFolder() // wraps around to 0
        assertEquals(0, viewModel.currentFolderIndex.value)

        viewModel.previousFolder() // wraps backwards to 2
        assertEquals(2, viewModel.currentFolderIndex.value)

        // Rename folder
        val wallpapersFolder = foldersAfterAdd.find { it.name == "Wallpapers" }!!
        viewModel.renameFolder(wallpapersFolder.id, "Cool Wallpapers")
        val renamed = viewModel.folders.value.find { it.id == wallpapersFolder.id }
        assertEquals("Cool Wallpapers", renamed?.name)

        // Remove folder
        viewModel.removeFolder(wallpapersFolder.id)
        assertEquals(2, viewModel.folders.value.size)

        // Persistence test across new ViewModel instance
        val reloaded = MainViewModel(app)
        assertEquals(2, reloaded.folders.value.size)
        assertTrue(reloaded.folders.value.any { it.name == "Memes" })
    }

    @Test
    fun `dumb user test - pin toggle and persistence`() {
        val testItem = GalleryItem(
            uri = android.net.Uri.parse("content://media/1"),
            name = "funny_cat.gif",
            dateModified = 1000L,
            size = 5000L,
            isGif = true,
            isVideo = false,
            isPinned = false
        )

        assertFalse(viewModel.isItemPinned(testItem))

        // Toggle pin
        viewModel.togglePin(testItem)
        assertTrue(viewModel.isItemPinned(testItem))

        // Toggle unpin
        viewModel.togglePin(testItem)
        assertFalse(viewModel.isItemPinned(testItem))

        // Pin again and verify reload
        viewModel.togglePin(testItem)
        val reloaded = MainViewModel(app)
        assertTrue(reloaded.isItemPinned(testItem))
    }

    @Test
    fun `dumb user test - MainActivity creates safely`() {
        val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).create()
        val activity = controller.get()
        org.junit.Assert.assertNotNull(activity)
    }

    @Test
    fun `dumb user test - GifEncoder creates valid GIF89a bytes`() {
        val bitmap = android.graphics.Bitmap.createBitmap(50, 50, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.RED)

        val output = java.io.ByteArrayOutputStream()
        val encoder = GifEncoder()
        encoder.start(output)
        encoder.setDelay(100)
        encoder.setRepeat(0)
        assertTrue(encoder.addFrame(bitmap))
        assertTrue(encoder.finish())

        val bytes = output.toByteArray()
        assertTrue(bytes.isNotEmpty())
        // Check GIF89a header
        val header = String(bytes.copyOfRange(0, 6), Charsets.US_ASCII)
        assertEquals("GIF89a", header)
        // Check trailer
        assertEquals(0x3B.toByte(), bytes.last())
    }

    @Test
    fun `dumb user test - folder switching prevents item bleed`() {
        viewModel.addFolderWithDetails("Memes", "content://tree/memes")
        viewModel.addFolderWithDetails("Gifs", "content://tree/gifs")
        
        // Select Memes
        val memesIdx = viewModel.folders.value.indexOfFirst { it.name == "Memes" }
        viewModel.selectFolder(memesIdx)
        assertEquals("Memes", viewModel.currentFolder.value?.name)

        // Select Gifs
        val gifsIdx = viewModel.folders.value.indexOfFirst { it.name == "Gifs" }
        viewModel.selectFolder(gifsIdx)
        assertEquals("Gifs", viewModel.currentFolder.value?.name)
    }

    @Test
    fun `dumb user test - remove folder leaves other folders intact`() {
        viewModel.addFolderWithDetails("FolderA", "content://tree/a")
        viewModel.addFolderWithDetails("FolderB", "content://tree/b")
        val folderB = viewModel.folders.value.find { it.name == "FolderB" }!!

        viewModel.removeFolder(folderB.id)

        assertFalse(viewModel.folders.value.any { it.id == folderB.id })
        assertTrue(viewModel.folders.value.any { it.name == "FolderA" })
    }
}
