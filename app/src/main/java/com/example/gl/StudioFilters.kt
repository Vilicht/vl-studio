package com.example.gl

import com.pedro.encoder.input.gl.render.filters.BrightnessFilterRender
import com.pedro.encoder.input.gl.render.filters.ContrastFilterRender
import com.pedro.encoder.input.gl.render.filters.GammaFilterRender
import com.pedro.encoder.input.gl.render.filters.GreyScaleFilterRender
import com.pedro.encoder.input.gl.render.filters.SaturationFilterRender
import com.pedro.encoder.input.gl.render.filters.`object`.ImageObjectFilterRender
import com.pedro.encoder.input.gl.render.filters.`object`.TextObjectFilterRender

object StudioFilters {
    fun createBrightnessFilter(brightness: Float): BrightnessFilterRender {
        return BrightnessFilterRender().apply {
            setBrightness(brightness)
        }
    }

    fun createContrastFilter(contrast: Float): ContrastFilterRender {
        return ContrastFilterRender().apply {
            setContrast(contrast)
        }
    }

    fun createSaturationFilter(saturation: Float): SaturationFilterRender {
        return SaturationFilterRender().apply {
            setSaturation(saturation)
        }
    }

    fun createGammaFilter(gamma: Float): GammaFilterRender {
        return GammaFilterRender().apply {
            setGamma(gamma)
        }
    }

    fun createGreyScaleFilter(): GreyScaleFilterRender {
        return GreyScaleFilterRender()
    }
}
