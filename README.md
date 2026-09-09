# Rounds

An Android app for recurring checks that have to happen inside a time window —
and a nudge when one doesn't.

## The model

- **Check** — the thing being tracked. It declares how it may be logged: with a
  **photo**, with a **tap** (plus an optional note), or both. Changing this later
  only limits what can be added next; entries already logged are always kept.
- **Entry** — one logged check: a timestamp, an optional note, and for photo
  entries a file.
- **Schedule** — when a check is expected. Four kinds:
  - `Once` — a single named date
  - `Weekly` — chosen days of the week
  - `Monthly` — chosen days of the month (a day past the end of a short month
    lands on its last day)
  - `Every N minutes/hours/days/weeks/months`

## Window semantics

A schedule produces **check windows**. Every window needs at least one entry;
if it closes empty, a notification fires at the closing time.

An entry only counts for the window it falls inside. An extra check at 05:00
does not satisfy a 06:00–09:00 window — that window still notifies at 09:00.
A window whose end time is at or before its start crosses midnight. For
`Every N …` schedules the period itself is the window.

`RecurrenceEngineTest` pins this behaviour down, including the cross-midnight,
short-month, and calendar-month interval cases.

## Reminders

One exact alarm is kept, set for the earliest upcoming deadline across all
schedules; when it fires the app judges every window that closed since it last
ran and arms the next alarm. Progress is tracked by "last deadline evaluated"
rather than by which alarm fired, so a reboot, Doze, or a killed process only
delays the check — the next run catches up. An hourly `WorkManager` job backs
the alarm up, and a boot receiver re-arms after a restart.

Exact alarms need `SCHEDULE_EXACT_ALARM`; without it the app falls back to an
inexact alarm and says so on the list screen.

## Photos

Stored at `filesDir/photos/<checkId>/`, one folder per check, with only the
relative path in the database. The photo screen browses them as a grid,
long-press multi-selects, and deleting removes the entry and the file together.
Capture goes through `ACTION_IMAGE_CAPTURE`, so the app needs no camera
permission.

## Build

```
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Kotlin, Jetpack Compose (Material 3), Room, WorkManager, Coil.
minSdk 26, compileSdk 35.
