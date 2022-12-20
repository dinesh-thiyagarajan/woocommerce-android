package com.woocommerce.android.ui.analytics.ranges.data

import com.woocommerce.android.extensions.oneWeekAgo
import com.woocommerce.android.extensions.startOfCurrentWeek
import com.woocommerce.android.ui.analytics.ranges.AnalyticsHubTimeRange
import com.woocommerce.android.ui.analytics.ranges.AnalyticsHubTimeRangeData
import java.util.Calendar
import java.util.Date

// Responsible for defining two ranges of data, one starting from the first day of the current week
// until the current date and the previous one, starting from the first day of the previous week
// until the same day of that week. E. g.
//
// Today: 29 Jul 2022
// Current range: Jul 25 until Jul 29, 2022
// Previous range: Jul 18 until Jul 22, 2022
//
class AnalyticsHubWeekToDateRangeData(
    referenceDate: Date,
    calendar: Calendar
) : AnalyticsHubTimeRangeData {
    override val currentRange: AnalyticsHubTimeRange
    override val previousRange: AnalyticsHubTimeRange

    init {
        calendar.time = referenceDate
        val startOfCurrentWeek = calendar.startOfCurrentWeek()
        currentRange = AnalyticsHubTimeRange(
            start = startOfCurrentWeek,
            end = referenceDate
        )

        val oneWeekAgo = referenceDate.oneWeekAgo(calendar)
        calendar.time = oneWeekAgo
        val startOfPreviousWeek = calendar.startOfCurrentWeek()
        previousRange = AnalyticsHubTimeRange(
            start = startOfPreviousWeek,
            end = oneWeekAgo
        )
    }
}
