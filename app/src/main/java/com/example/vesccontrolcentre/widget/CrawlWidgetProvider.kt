package com.example.vesccontrolcentre.widget

import com.example.vesccontrolcentre.R
import com.example.vesccontrolcentre.model.ProfileType

class CrawlWidgetProvider : BaseProfileWidgetProvider(
    profileType = ProfileType.CRAWL,
    titleText = "CRAWL MODE",
    subtitleText = "LOW SPEED | SMOOTH THROTTLE",
    iconResId = R.drawable.ic_widget_crawl,
    activeBgResId = R.drawable.widget_bg_active_crawl
)
