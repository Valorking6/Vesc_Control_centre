package com.example.vesccontrolcentre.widget

import com.example.vesccontrolcentre.R
import com.example.vesccontrolcentre.model.ProfileType

class MaxPowerWidgetProvider : BaseProfileWidgetProvider(
    profileType = ProfileType.MAX_POWER,
    titleText = "MAX POWER",
    subtitleText = "PERFORMANCE | FULL BOOST",
    iconResId = R.drawable.ic_widget_max_power,
    activeBgResId = R.drawable.widget_bg_active_max_power
)
