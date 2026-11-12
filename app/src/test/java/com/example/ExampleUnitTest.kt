package com.example

import com.example.data.model.SourceEntity
import com.example.data.model.SourceType
import com.example.encoder.DeviceCapabilityDetector
import com.example.encoder.StreamPreset
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `preset specifications meet hardware targets`() {
        val preset720p30 = DeviceCapabilityDetector.PRESET_720P_30
        assertEquals(1280, preset720p30.width)
        assertEquals(720, preset720p30.height)
        assertEquals(30, preset720p30.fps)
        assertTrue(preset720p30.defaultBitrateKbps in 2500..4000)

        val preset1080p60 = DeviceCapabilityDetector.PRESET_1080P_60
        assertEquals(1920, preset1080p60.width)
        assertEquals(1080, preset1080p60.height)
        assertEquals(60, preset1080p60.fps)
        assertTrue(preset1080p60.defaultBitrateKbps in 6000..9000)
    }

    @Test
    fun `source entity properties are consistent`() {
        val source = SourceEntity(
            sceneId = "scene-alpha",
            name = "Game Display",
            type = SourceType.SCREEN,
            width = 1f,
            height = 1f,
            x = 0f,
            y = 0f,
            chromaKeyEnabled = false
        )
        assertEquals("Game Display", source.name)
        assertEquals(SourceType.SCREEN, source.type)
        assertEquals(1f, source.width, 0.001f)
        assertEquals(1f, source.height, 0.001f)
        assertTrue(source.isVisible)
        assertFalse(source.isLocked)
    }

    @Test
    fun `chroma key configuration parameters`() {
        val cameraSource = SourceEntity(
            sceneId = "scene-beta",
            name = "Facecam",
            type = SourceType.CAMERA,
            chromaKeyEnabled = true,
            chromaKeyColor = 0xFF00FF00L,
            chromaSimilarity = 0.42f,
            chromaSmoothness = 0.15f
        )
        assertTrue(cameraSource.chromaKeyEnabled)
        assertEquals(0xFF00FF00L, cameraSource.chromaKeyColor)
        assertEquals(0.42f, cameraSource.chromaSimilarity, 0.001f)
    }
}
