Recurring checks that have to happen inside a time window, with a reminder
when a window closes empty.

## What it does

- **Schedules** — a single date, days of the week, days of the month, or
  every N minutes / hours / days / weeks / months.
- **Windows** — each schedule needs at least one entry inside its window. An
  entry only counts for the window it falls in, so a check made shortly before
  a window opens does not satisfy it; that window still reminds you when it
  closes. A window whose end is at or before its start crosses midnight.
- **Logging** — a photo (camera or library) or a tap with an optional note.
  Turning a type off later never removes entries already recorded.
- **Timeline** — every entry with its time, note and photo, grouped by day.
- **Photos** — one folder per check, browsable as a grid, long-press to
  multi-select and delete entry plus file together.

## Reminders

One exact alarm is kept for the earliest upcoming deadline. Progress is tracked
by the last deadline evaluated rather than by which alarm fired, so a reboot or
a spell in Doze delays the check instead of losing it. An hourly WorkManager job
backs it up and a boot receiver re-arms after a restart.

## Install

Download `Rounds-1.0.apk` and open it on the device. Android will ask you to
allow installs from whichever app you opened it with, since it is not from Play.

Requires Android 8.0 (API 26) or newer. Grant notifications on first launch, and
allow exact alarms when prompted — without that permission reminders still fire,
just a few minutes late.
