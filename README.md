# FastLog

An intermittent fasting tracker for Android. Local-only, no ads, no accounts,
no subscriptions, every fasting protocol unlocked.

## Why it exists

Built as a replacement for commercial fasting apps that bundle ad SDKs,
analytics, attribution tracking and a paywall around longer fasts.

## The no-cloud guarantee

`AndroidManifest.xml` requests **no network permission**. Without
`android.permission.INTERNET` the operating system makes it impossible for this
app to open a socket, regardless of what the code does. That is a structural
guarantee enforced by Android, not a promise in a privacy policy.

Verify it on any build:

```bash
aapt2 dump xmltree --file AndroidManifest.xml app-release.apk | grep INTERNET
```

That returns nothing.

### Every permission, and why

| Permission | Why | Network? |
|---|---|---|
| `POST_NOTIFICATIONS` | The ongoing fasting indicator. Required from Android 13 | No |
| `RECEIVE_BOOT_COMPLETED` | Rebuild that indicator after a reboot | No |
| `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | Signature-level permission the app defines for *itself*, injected by `androidx.core`. Guards its own runtime receivers; no other app can hold it | No |

CSV export and import use the Storage Access Framework, so **no storage
permission is needed** — you pick the file, and the system hands the app a
one-off handle to just that file.

One further entry appears in a full manifest dump: `android.permission.DUMP` on
a receiver, from `androidx.profileinstaller`. That is a *guard* requiring
callers to hold DUMP; the app itself does not hold it.

All data lives in app-private internal storage, readable by no other app.

## Features

### Fasting

- Protocols: 12:12, 14:10, 16:8, 18:6, 20:4, OMAD, 24h, 36h, 48h, 72h, and a
  custom length from 1 hour to 7 days. Nothing is gated.
- Live timer ring with elapsed time, time remaining, and over-goal tracking.
- Seven metabolic stages keyed to hours elapsed.

### Ending a fast

Ending a fast — from the app or from the notification — opens a summary of
exactly what was recorded: duration, whether the goal was met and by how much,
the plan, start and end times, the stage reached, total fasts and current streak.

The notification's End fast action opens the app rather than acting silently.
It has to be a direct activity `PendingIntent`: since Android 12 a notification
action may not bounce through a broadcast receiver to start an activity.

### Changing the plan mid-fast

The running-fast screen names the plan you are on and lets you switch it. The
picker labels each option with what it would mean right now — "3h 20m to go", or
"already met" for a target already behind you. Switching keeps the start time and
moves only the goal, so choosing a shorter plan puts the fast straight into its
over-goal state.

### Ongoing notification

A non-dismissible indicator while a fast runs. Information order is deliberate:
elapsed time largest, then time remaining (or how far past goal), then the plan.

The elapsed value is a real `Chronometer` widget inside a custom layout, so the
system ticks it. It is both the most prominent element and free: **no foreground
service and no background polling**, so the indicator costs essentially no
battery. Note that `Chronometer` counts from `SystemClock.elapsedRealtime()`
rather than wall clock, so its base is set by subtracting the elapsed duration
from elapsed-realtime, not from the fast's start timestamp. The only
scheduled work is a single inexact alarm at goal time, which flips the
notification to its "goal reached" state. It is inexact on purpose — exact
alarms require the restricted `SCHEDULE_EXACT_ALARM` permission.

A fast is stored as an absolute start timestamp, so it survives app kill and
reboot. `BootReceiver` rebuilds the indicator and the alarm after a restart, and
opening the app re-asserts them too, in case an OEM drops the boot broadcast.

### Chart

Hours fasted per day, over 7 / 30 / 90 days, drawn on a Compose `Canvas` with no
charting dependency. Days without a fast render as gaps so lapses stay visible,
and a dashed line marks the average.

A fast counts on the day it **ends**, matching how the streak is counted. Two
fasts ending the same day are summed, so a single bar can exceed 24h when a long
fast completes — the chart says so rather than leaving it puzzling.

The average is taken over days actually fasted, not over the whole window;
dividing by empty days would make every range look worse the longer it got.

### Correcting times

You are rarely holding your phone at the moment a fast begins or ends, so every
timestamp is editable:

- **"I started earlier"** on the timer, to log a fast already under way.
- **"Change"** on a running fast's start time.
- **"Ended earlier"** when you broke the fast away from your phone.
- **Edit** on any past fast in History, for both ends.

The first three open a relative chooser — "2 hours ago", with the resulting
clock time shown — because backdating is nearly always recent. Older
corrections escalate to a full date and time picker, which is what History
edits use directly.

Rules enforced throughout: a fast cannot start or end in the future, and an end
must fall strictly after its start. The end chooser hides any offset that would
land at or before the fast's own beginning, so a zero-length fast cannot be
recorded. Editing times never changes the goal; it recomputes whether the goal
was met.

### CSV backup

- **Export** through the system file picker, to anywhere you like. Opens in Excel.
- **Auto-backup**: every change also rewrites `fastlog-backup.csv` in the app's
  own folder, so a restore file exists even if you never tap Export.
- **Import** merges on record id. Existing fasts are kept and never overwritten,
  so importing the same file twice adds nothing. That makes restore repeatable.
- Malformed rows are skipped instead of failing the import, so one bad line
  cannot cost you a restore.

## Build

Requires JDK 17 and the Android SDK (compileSdk 36).

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./gradlew :app:assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`

### Signing

Signed with a local self-signed keystore at `app/fastlog-release.jks`
(store/key password `fastlog`, alias `fastlog`, valid 30 years).

Keep this file. Android requires every update to be signed with the same key,
so losing it means you can only install a new version by uninstalling the old
one first, which deletes your fasting history.

## Project layout

```
model/      Protocol, FastPhase, FastRecord  — plain data, no Android deps
data/       FastingRepository (JSON on disk), StatsCalculator, CsvCodec,
            ChartData, TimeEditing
notify/     Ongoing notification, goal alarm, boot restore
ui/         Compose screens, theme, timer ring, pickers, formatting helpers
```

`FastingRepository` is a single process-wide instance obtained through
`FastingRepository.get(context)`. This matters: when each caller built its own,
a write from a notification action never reached the flow the UI collected, and
the screen only caught up after a restart. Do not reintroduce per-caller
instances.

`StatsCalculator` and the model classes are pure Kotlin, so they can be unit
tested without an emulator.

## Tests

```bash
./gradlew :app:testDebugUnitTest
```

48 unit tests over the pure logic: CSV round-trip and merge behaviour, chart
bucketing, streak counting, timezone-safe date editing, offset validation and
plan-change behaviour. `model/`, `data/CsvCodec`, `data/ChartData`,
`data/StatsCalculator` and `data/TimeEditing` hold no Android types precisely so
they can be tested on the JVM without an emulator.

## Updating an existing install

Keep the package id, the signing key and an increasing `versionCode`, and
Android treats the install as an update and preserves the app's data directory.
Confirm an upgrade kept data by comparing `ceDataInode` before and after:

```bash
adb shell dumpsys package dev.aakrit.fastlog | grep -E "ceDataInode|firstInstallTime"
```

An unchanged inode and `firstInstallTime` mean the data directory survived.

## Notes

The metabolic stage descriptions are general physiology, not medical advice.
Timings vary considerably between people, and extended fasts are worth
discussing with a doctor.
