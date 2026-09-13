package com.example.vesccontrolcentre.widget

import com.example.vesccontrolcentre.R
import com.example.vesccontrolcentre.model.ProfileType

class LongRangeWidgetProvider : BaseProfileWidgetProvider(
    profileType = ProfileType.LONG_RANGE,
    titleText = "LONG RANGE",
    subtitleText = "EFFICIENCY | CONSERVE BATTERY",
    iconResId = R.drawable.ic_widget_long_range,
    activeBgResId = R.drawable.widget_bg_active_long_range
)
