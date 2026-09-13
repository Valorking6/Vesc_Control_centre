package com.example.vesccontrolcentre.widget

import com.example.vesccontrolcentre.R
import com.example.vesccontrolcentre.model.ProfileType

class NormalWidgetProvider : BaseProfileWidgetProvider(
    profileType = ProfileType.NORMAL,
    titleText = "NORMAL",
    subtitleText = "BALANCED | DAILY RIDE",
    iconResId = R.drawable.ic_widget_normal,
    activeBgResId = R.drawable.widget_bg_active_normal
)
