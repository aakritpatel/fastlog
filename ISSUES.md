# Open issues

Reported by Aakrit on 4 October 2026 after the first days of daily use of v1.2.

**All six are fixed in v1.3 (5 October 2026).** What each one turned out to be,
and how it was addressed, is recorded under each heading.

---

## 1. The current plan is invisible, and unchangeable, once a fast is running

> After you start a fast, what if the user wants to know or change the current
> plan?

Once a fast begins there is no way to see which protocol you are on, or to
switch it.

**Verified cause.** In `ui/TimerScreen.kt` the protocol label appears in exactly
two places — the Start button and the protocol chips — and both render only in
the `active == null` branch. The running-fast layout shows elapsed time, the
phase card, the start time and the end controls, and never names the plan. The
ongoing notification does name it, which means the app is currently worse than
its own notification at answering "what am I on?".

Changing the plan mid-fast is not supported at all: `goalHours` is fixed when
`startFast` writes the `ActiveFast`.


**Fixed in v1.3.** A PLAN card now sits on the running-fast screen showing the
protocol and its goal, with a Change action. The picker lists every protocol with
what it would mean right now — "3h 20m to go", or "already met" for a target
already behind you — so switching to a shorter plan is a deliberate choice.
Switching keeps the start time and moves only the target, then re-posts the
notification and reschedules the goal alarm.

---

## 2. Unclear what the over-goal state looks like, especially after a plan change

> What happens when your ongoing fasting hours are more than what the plan has?
> How does the UI look? This can happen normally or due to change in plan.

An over-goal state does exist — the ring switches to the amber accent, the label
becomes "GOAL REACHED" and the subtitle counts up as "+Xh past goal". It has not
been reviewed against real use, and it has never been seen in the case that
matters most here: a goal that moves *below* the time already elapsed because
the plan was changed mid-fast (issue 1). That path cannot currently be reached,
so it is also untested.

Worth settling as part of this: whether the ring should cap at full or visibly
overflow, and what the over-goal state should read when the overshoot is large.


**Fixed in v1.3.** The over-goal state now reads
"2h 14m past your 16h goal" rather than a bare "+2h", and a banner above the plan
card explains the state and that you may end or carry on. Because issue 1 made
plan changes possible, the case where a goal drops below the elapsed time is now
reachable and was tested directly.

---

## 3. The notification's information hierarchy is upside down

> The primary info for me is the ongoing timer, how many hours over. Secondary
> is how many hours left. Then the rest. Seeing the number of hours passed is
> difficult right now because it's the smallest info.

Current layout puts the live chronometer in the header line, which Android
renders in the smallest type on the notification, while the title and body carry
the protocol name and goal — the least urgent facts.

Wanted order:

1. Elapsed time, and how far past goal when over
2. Time remaining
3. Everything else (protocol, goal, actions)

Note for whoever picks this up: the elapsed time is currently free because
`setUsesChronometer` lets the system tick it. Moving elapsed time into the title
means rendering it ourselves, which costs a periodic update and therefore
battery. A custom `RemoteViews` layout, or `Chronometer` within one, is the
likely way to keep the system ticking it while placing it prominently. Do not
trade the no-service design away without weighing it.


**Fixed in v1.3.** The notification now uses a custom layout: elapsed time at
30sp as the dominant element, time remaining (or how far past goal) beneath it,
and the plan and goal smallest.

The battery trade-off flagged above was avoided rather than accepted. The elapsed
value is a real `Chronometer` widget inside the layout, so the system still ticks
it — the notification is prominent and free, with no foreground service and no
periodic wakeup. Note for future work: `Chronometer` counts from
`SystemClock.elapsedRealtime()`, not wall clock, so its base is set by subtracting
the elapsed duration from elapsed-realtime, not from the fast's start timestamp.

---

## 4. "End fast" in the notification just dismisses the shade

> It should open the app and conclude the fast for me and show me the history
> screen. Or some screen which shows that yayy your fast is over.

The action ends the fast correctly and cancels the notification, but gives no
visible confirmation — the shade closes and nothing else happens. Related to
issues 5 and 6; all three concern the end-of-fast flow.


**Fixed in v1.3.** The action now opens the app, ends the fast there and lands
on the completion summary.

It had to become a direct activity `PendingIntent` rather than the previous
broadcast: since Android 12 a notification action may not bounce through a
receiver to start an activity. `MainActivity` is `singleTask` and handles the
intent in both `onCreate` and `onNewIntent`, clearing the action afterwards so a
rotation cannot replay the request.

---

## 5. BUG: the app shows stale data after ending a fast from the notification

> This fast log didn't update until I closed the app and reopened it.

A real defect, and the most serious item here.

**Verified cause.** `FastingRepository` is constructed in four places —
`FastingViewModel`, `FastActionReceiver`, `GoalReachedReceiver` and
`BootReceiver` — and each instance builds its own
`MutableStateFlow(readFromDisk())` at construction. They share the file on disk
but share no in-memory state.

So when the notification's End fast action runs, the receiver's repository
writes to disk, and the ViewModel's repository, which the UI collects from,
never hears about it. Reopening the app constructs a fresh repository that
re-reads the file, which is exactly why the data appears only after a restart.

The fix is a single shared repository instance rather than per-caller ones, or a
file watcher that re-reads on external change. The first is simpler and also
makes issues 4 and 6 straightforward, since the app would then observe the end
of a fast no matter where it was triggered.


**Fixed in v1.3.** `FastingRepository` now has a private constructor and a
single process-wide instance behind `FastingRepository.get(context)`. Every
caller shares one in-memory state, so a write from anywhere is seen immediately
by the flow the UI collects.

---

## 6. No confirmation screen when a fast concludes

> There should be a screen that opens when you conclude your fast, just to show
> the user what is finally getting logged in history. It's a transient screen,
> only when you finish a fast, either on the homepage or via notifications.

Ending a fast currently drops you back to a reset timer with no summary. Wanted:
a transient screen showing what was recorded — duration, protocol, whether the
goal was met — reachable from both the in-app End fast and the notification
action.

Depends on issue 5: until the app observes externally-triggered endings, this
screen cannot be shown reliably when the fast ends from the shade.


**Fixed in v1.3.** Ending a fast — from the app or the notification — now shows
a summary: duration, whether the goal was met and by how much, the plan, start
and end times, the metabolic stage reached, total fasts logged and current streak.
"Done" returns to the timer, "See full history" opens History.

The signal is a transient in-memory value on the repository, deliberately not
persisted: a restart should not resurface a summary already dismissed.

---

## Order the work was done in

Issue 5 first, being a correctness bug whose fix unblocked 4 and 6; those three
together became one end-of-fast flow. Issue 1 then made issue 2 reachable and so
testable. Issue 3 was independent, and its battery trade-off was sidestepped by
keeping the system-ticked Chronometer inside a custom layout.
