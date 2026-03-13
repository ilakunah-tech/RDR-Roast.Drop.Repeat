package com.rdr.roast.ui.chart

import com.rdr.roast.app.AppSettings
import com.rdr.roast.app.ChartConfig
import com.rdr.roast.app.ChartColors

/**
 * Applies theme and chart settings to the main roast chart and control chart.
 * Call when theme or chart settings change (e.g. after loading or saving settings).
 */
object ChartThemeAdapter {

    fun apply(curveChart: CurveChartFx, controlChart: ControlChartFx, settings: AppSettings) {
        curveChart.applySettings(settings.chartColors, settings.chartConfig)
        curveChart.applyTheme(settings.themeSettings)
        controlChart.applyTheme(settings.themeSettings, settings.chartConfig)
        controlChart.setTimeRangeMinutes(settings.chartConfig.timeRangeMin)
    }

    fun applyChartColorsAndConfig(curveChart: CurveChartFx, colors: ChartColors, config: ChartConfig) {
        curveChart.applySettings(colors, config)
    }
}
