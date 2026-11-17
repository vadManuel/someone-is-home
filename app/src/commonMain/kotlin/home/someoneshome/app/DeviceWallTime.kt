package home.someoneshome.app

import home.someoneshome.platform.localWallTime
import home.someoneshome.ui.WallTime

/**
 * **The phone's own wall clock, handed to `ui`** (D-243): `platform`'s reading in `ui`'s type,
 * because `ui` may not see `platform`. Display only; the round never reads it.
 */
fun deviceWallTime(): WallTime = localWallTime().let { now ->
    WallTime(hour = now.hour, minute = now.minute, weekday = now.weekday, day = now.day, month = now.month)
}
