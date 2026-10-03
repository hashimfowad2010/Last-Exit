# Last Exit: upgraded build prompt

Paste everything below the line into your AI coding assistant. It is the original hackathon prompt rebuilt into a full spec: the math is exact, every message is written, the demo data is fixed, and the tests are listed with expected values. Edge cases that broke the first version are called out.

> The reference implementation in this repository was built from this spec. Its build machine could not reach Google Maven, so it uses the plain Android framework (custom Canvas views, SQLite, JobScheduler) instead of Compose, Room and WorkManager. The behaviour, math, copy, demo data and tests are identical. Section 2 lists both stacks.

---

You are a senior Android engineer and product designer helping me win a hackathon. Build **Last Exit**, a native Android app that warns people **before** they pass the point of no return on a limit, not when they hit it. Deliver a complete project that builds and runs from Android Studio's Run button on the first try.

## 0. Hackathon theme (judges will score against this)

> Build a monitoring or limit-tracking tool that helps people keep an eye on something that can be pushed too far (time running out on a task, money spent against a budget, effort or resources used up, risk building up in a decision) and warns them clearly before they cross the line and it's too late to turn back.

Judges reward four things: a sharp idea, a demo that works live, visible polish, and technical depth they can verify. Optimise for all four, in that order.

## 1. Product

**Pitch (one line):** Most trackers show where you are. Last Exit shows the last day you can still change course.

**Core insight:** For every limit there is a *last exit*: the latest day on which a realistic change in behaviour still keeps you under the limit. After it, even your best effort overshoots. Last Exit computes that date, counts down to it, and warns you before it passes, usually days or weeks before the limit itself is crossed.

**People it is for:**
- A student capping weekly study hours to avoid burning out before exams (TIME).
- A freelancer burning hours on a fixed-price project (TIME).
- Anyone with a monthly budget or a mobile data plan (MONEY, RESOURCE).
- Someone saying yes to too many commitments, each adding "risk points" (RISK).

**Definition of done:**
- [ ] Builds with Android Studio's Run button. No manual steps beyond opening the project.
- [ ] Forecast engine is pure Kotlin in its own module with no Android imports, plus JUnit tests (section 4).
- [ ] Home, Detail, Quick-log, What-if and Add/Edit tracker all work. Status shows icon + text + colour, never colour alone.
- [ ] A background check notifies only when a tracker moves to a worse stage. Tapping the notification opens that tracker.
- [ ] Demo mode goes green → amber → red **before** the limit is crossed, with banner, haptics, animation and a real notification.
- [ ] Rotation and process death lose nothing: open sheets, half-typed forms, the what-if slider, demo progress.
- [ ] Dark mode, dynamic colour (Android 12+), TalkBack labels, 48 dp touch targets.
- [ ] README with run steps, a 60-second demo script, and three differentiators.

## 2. Tech stack (exact versions; they are known to work together)

| Piece | Version |
|---|---|
| Android Gradle Plugin | 8.7.3 (Gradle wrapper **8.9**) |
| Kotlin | 2.0.21, with `org.jetbrains.kotlin.plugin.compose` 2.0.21 |
| KSP | 2.0.21-1.0.28 |
| Compose BOM | 2024.12.01 (Material 3 from the BOM) |
| activity-compose | 1.9.3 |
| lifecycle-runtime-compose / lifecycle-viewmodel-compose | 2.8.7 |
| navigation-compose | 2.8.5 |
| room-runtime / room-ktx / room-compiler (KSP) | 2.6.1, `room.generateKotlin=true` |
| work-runtime-ktx | 2.9.1 |
| core-ktx | 1.13.1 |
| JUnit / kotlinx-coroutines-test / Robolectric | 4.13.2 / 1.8.1 / 4.14.1 |

- `compileSdk = 34`, `targetSdk = 34`, `minSdk = 26`, Java/Kotlin JVM target 17. Every library above supports compileSdk 34. **Do not bump any of them** without moving to compileSdk 35: core-ktx 1.15, activity 1.10, lifecycle 2.9 and work 2.10 require it.
- Gradle Kotlin DSL, a `gradle/libs.versions.toml` version catalog, and the Gradle wrapper files (`gradlew`, `gradle-wrapper.jar`, `gradle-wrapper.properties`).
- Modules: `:core` (Kotlin JVM, the engine, no Android) and `:app` (Android).
- Single activity, MVVM with `ViewModel` + `StateFlow`, Navigation Compose, Room, WorkManager, `NotificationCompat`.
- Charts are drawn with Compose `Canvas` (no chart library).
- **No-Google-Maven fallback** (what the reference build uses): framework Views with custom Canvas views, `SQLiteOpenHelper` with a cascading foreign key, `JobScheduler` (periodic, persisted) and a small route stack in one Activity. Only the UI layer changes; `:core` is identical.

## 3. Data model

**Tracker:** `id`, `name`, `type` (MONEY | TIME | RESOURCE | RISK), `unit`, `limit`, `startDate`, `deadline`, `minPaceFactor` (default 0.3), `lastNotifiedStatus` (default SAFE), `createdAt`.

**Entry:** `id`, `trackerId` (foreign key, `ON DELETE CASCADE`, indexed), `date`, `amount`, `note`, `oneOff` (Boolean), `createdAt`.

- Store dates as `epochDay` longs; store enums by name and parse leniently (unknown → default).
- `oneOff` marks a single big item (rent, a yearly fee). It counts toward *used* but not toward *pace*, so one large day doesn't trigger a false alarm.
- Negative amounts are allowed: refunds and corrections.
- `minPaceFactor` is the lowest pace the user could realistically hold, as a fraction of their current pace (0.3 = they could cut to 30%). The UI shows it as "How hard can you cut back?", a slider from 10% to 100% in 5% steps, where factor = 1 − cut.

## 4. Forecast engine (`:core`, pure Kotlin, never calls `LocalDate.now()`)

Input: `limit L > 0`, `startDate`, `deadline ≥ startDate`, `today`, `usages`, `minPaceFactor` (clamped to 0..1), and an optional `paceOverride` for the What-if slider. Throw `IllegalArgumentException` on invalid limits or dates.

**Day counting:** dates are inclusive, and today's entries count as part of today.
- `totalDays = deadline − start + 1`
- `E` (elapsed) = `today − start + 1`, clamped to `0..totalDays`. 0 means the period hasn't started.
- `R` (remaining) = whole days **after** today up to and including the deadline: `deadline − max(today, start − 1)`, clamped `≥ 0`. Before the start, every day is still ahead.
- Ignore entries dated after `today`. Entries before the start count toward U.

**Pace r:** use non-one-off entries only.
- `recent` = sum over the last `min(7, E)` days ÷ that many days. Entries dated before the start count as day 1.
- `overall` = sum ÷ E.
- `r = max(0, 0.7 × recent + 0.3 × overall)`. If `E = 0`, then `r = 0`.
- `paceOverride`, when set, replaces r. Keep the measured value for display.

**Core quantities:**
- `U` = sum of all counted usages (one-offs included); `m = r × minPaceFactor`.
- `projectedTotal P = U + r × R`
- `requiredPace = max(0, (L − U) ÷ R)`, or null when R = 0.
- Last exit: `x = (L − U − m×R) ÷ (r − m)` days from today. Keep pace r for x days, then drop to m, and you land exactly on L. If `r − m ≤ ε`, then x = +∞ when `L − U − m×R ≥ 0`, otherwise −∞.
- `daysToLastExit = floor(x + ε)`; `lastExitDate = today + daysToLastExit`. 0 means "change course today".
- Limit-hit day: when `r > 0` and `U ≤ L`, `h = ceil((L − U) ÷ r − ε)`. Report it only if `h ≤ R`.
- `crossedOn` = first date the running total of logged entries exceeded L.
- Use ε = 1e-9 everywhere you compare doubles.

**Stages:** `Status` is an enum with a severity, and every stage gets icon + label + colour.

| Stage | Rule (first match wins) |
|---|---|
| PAST_THE_LINE (dark red) | `U > L`, or `x < −ε` |
| SAFE (green) | `R = 0` (period over or final day) and not over the limit; or `P ≤ L + ε` |
| LAST_EXIT (red) | `daysToLastExit ≤ redWindow`, where `redWindow = max(3, floor(0.15 × R))` |
| ACT_SOON (amber) | anything else that is projected over the limit |

**Early-estimate cap:** with one or two days of data, extrapolation lies. Without this rule, 15 h logged on day 1 of a 28-day, 40 h project reads "Past the line". While `E < min(3, ceil(0.2 × totalDays))` and the limit isn't already exceeded, cap the stage at ACT_SOON. Set `isCappedEarly = true` and report no last-exit date. Also expose `isEarlyEstimate = E < 3 || entries < 2` for a UI hint.

**Recovery suggestions:** compute these whenever the stage is not SAFE, the limit isn't exceeded, and R > 0.
- (a) Act today: pace `q = (L − U) ÷ R`, cut per day `r − q`, cut % `(r − q) ÷ r`, and a flag when `q < m` ("below what you said is realistic").
- (b) Wait until the last exit (k = daysToLastExit): pace `(L − U − r×k) ÷ (R − k)`. It equals m when x is a whole number.
- (c) Change nothing: you'd need `P − L` more limit, or you'd run out `R − h` days early. Moving the deadline later only adds more days of usage, so don't suggest it.

**Chart series** (also in `:core`): cumulative actual usage per day, from (today, U) to the end at pace r, the needed line from (today, U) to (end, L), the last-exit x position, and the limit-hit x position.

**Required unit tests, with exact expectations.** Fixture: start Nov 1, deadline Nov 30, today Nov 10 (E = 10, R = 20), 10 days of 60/day (U = 600, r = 60), factor 0.5 (m = 30).
| L | Expect |
|---|---|
| 1800 | P = 1800 exactly → SAFE |
| 1320 | x = 4 → ACT_SOON, last exit Nov 14, limit hit Nov 22 |
| 1290 | x = 3 → LAST_EXIT (boundary is inclusive), exit Nov 13, hit Nov 22 (h = 11.5 → 12); recovery: q = 34.5, cut 25.5 (42.5%), wait-pace 30.0, need 510 more, 8 days early |
| 1200 | x = 0 → LAST_EXIT "today" |
| 1199 | x = −1/30 → PAST_THE_LINE, overshoot at min pace = 1; act-today flagged unrealistic |
| 500 | U > L → PAST_THE_LINE, crossedOn Nov 9, no recovery |

Plus:
- Zero entries: SAFE, required pace 50.
- R = 0 on the deadline: SAFE if under, PAST if over.
- r = 0 from one-offs only: SAFE.
- Factor 1.0: no slower pace exists, so x = −∞ → PAST.
- Negative amounts: U drops, r never goes below 0.
- U == L: SAFE when r = 0, PAST at factor 0.5, LAST_EXIT today at factor 0.
- Before the start: E = 0, R = totalDays.
- Future-dated entries are ignored; entries before the start count.
- Weighted rate: 7 days at 10 then 7 days at 30 → 27.
- One-off excluded from pace.
- Red window: R = 20 → 3, R = 30 → 4, R = 100 → 15 (verify x = 14 → LAST_EXIT on a 100-day horizon).
- What-if override changes the stage but not `measuredPace`.
- Early cap: 15 h on day 1 of 28 days with L = 40 → ACT_SOON and capped; day 3 → PAST.

## 5. Copy deck (write the strings exactly; test the important ones)

- Chip labels: `Safe` / `Act soon` / `Last exit · 3d` (or `Last exit today`) / `Past the line` / `Done` once the period has ended. TalkBack reads `Last exit in 3 days`.
- Titles: `You're on track`, `Act soon: last exit in 4 days`, `Act soon: early warning`, `Last exit in 2 days`, `Last exit is today`, `Past the point of no return`, `Over the limit`.
- Card headline (one line in plain language):
  - ACT_SOON: `At this pace you hit your budget on Nov 22. You have 4 days to change course.`
  - LAST_EXIT: `At this pace you hit your budget on Oct 23. After Oct 17 it's too late to turn back.`
  - LAST_EXIT today: `Change course today: drop to $30/day to stay under.`
  - PAST, not yet over: `Even at your lowest possible pace, you'll exceed the limit by about $1.`
  - Over: `You're $100 over your budget.`
  - SAFE: `On track to finish at $1,420 of $1,500.`
  - No entries: `No entries yet. Log your first one to get a forecast.`
  - Early cap: `Early estimate: at this pace you'd hit your time budget on Oct 5. Log a couple more days before this firms up.`
- Subline under amber or red: `You haven't crossed the limit yet: $678 left.` This is the point of the product, so show it prominently.
- The "limit" noun depends on type: budget / time budget / allowance / risk threshold.
- Amounts: money shows the symbol first, with no decimals at $100 and up and 2 decimals below that (`$42.50`); other units go after the number (`12.5 h`, `3.25 GB`, `40 pts`). Paces read `$42/day`.

## 6. Screens

1. **Home**
   - Cards sorted worst status first, then soonest last exit.
   - Each card shows: type icon, name, date range, status chip, a big used amount ("$1,240 of $2,000"), days left, the one-line headline, and a **Log** button.
   - The progress bar shows used (solid), the projection (translucent) and a limit tick when the projection overshoots.
   - A summary banner tinted by the worst status: "2 limits need attention".
   - The notification-permission card (section 7).
   - Empty state: "Nothing tracked yet", with **Add a limit** and **Watch the 60-second demo**. A Demo button sits in the header, and a FAB adds a tracker.
2. **Detail**
   - A hero panel tinted by status (icon, title, headline, the "haven't crossed yet" subline, and a chip that pulses when red).
   - The Canvas chart: actual (solid, filled), projection (dashed, status colour), needed pace (dotted green), limit (dashed red, labelled), the limit-hit ✕, a "Now" marker, and a vertical **LAST EXIT · date** marker. Use round y ticks (1/2/2.5/5 × 10ⁿ) and a legend.
   - Key numbers: used, left, your pace, needed pace, projected, limit, last exit, limit hit, days left, lowest pace.
   - Recovery steps (a)(b)(c) as a numbered list.
   - The "How hard can you cut back?" slider, saved on release.
   - History with swipe-to-delete, Undo in a snackbar, and a TalkBack "Delete entry" action.
   - Overflow menu: Edit and Delete (with a confirmation dialog). An extended **Log** FAB.
3. **Quick-log bottom sheet**
   - Amount field with a decimal, signed keypad that accepts `,` or `.`.
   - Chips: +1 / +5 / +10, or for RISK "Small +5 / Medium +10 / Big +20".
   - Note (60 characters max), Today/Yesterday, and a one-off switch.
   - **Live preview: "After this entry: Careful, this moves you from Safe to Act soon…"**, the warning *before* the decision is made.
   - Log stays disabled until the amount is a non-zero number.
4. **What if?** (on Detail)
   - A slider from 0 to `max(2r, 1.5 × requiredPace, 1)`.
   - The hero, chart, numbers and stage all update live; nothing is saved.
   - Show a "WHAT-IF · nothing is saved · Reset" strip.
   - Give a haptic tick each time the drag crosses a stage boundary.
5. **Add/Edit tracker**
   - Template cards, each filling every field. Dates are computed from today.
     - Monthly Budget: MONEY, local currency symbol, 2000, today → end of month (roll to next month if fewer than 13 days are left), factor 0.4.
     - Student Study Hours (burnout cap): TIME, h, 30, 7 days, 0.3.
     - Freelance Project Hours: TIME, h, 40, 28 days, 0.5.
     - Mobile Data Plan: RESOURCE, GB, 15, monthly, 0.2.
     - Overload Risk: RISK, pts, 100, 14 days, 0.3.
   - Validation shows inline errors after the first Save attempt:
     - Name: required, at most 40 characters.
     - Unit: required, at most 6 characters.
     - Limit: a number between 0 (exclusive) and 1e9.
     - Dates: deadline ≥ start; new trackers can't have a deadline in the past; the period is at most 366 days.
   - Live hint: "That's about $68.97/day on average over 29 days."

## 7. Notifications and background work

- Create a high-importance channel, "Limit warnings", at app start.
- On Android 13+, explain first ("Last Exit only notifies you when a limit moves closer to the point of no return. No daily spam."), then request `POST_NOTIFICATIONS`. If the permission was permanently denied, or notifications are off, the button becomes **Open settings** (`ACTION_APP_NOTIFICATION_SETTINGS`).
- A periodic background job (WorkManager, 3 h, flex 1 h) loads every tracker, computes the stage, compares it with `lastNotifiedStatus`, and acts:
  - **Worse:** notify and store the new stage.
  - **Better:** store it silently, so a later relapse alerts again.
  - **Same:** do nothing.
- In the foreground, run the same comparison after every change and on resume. Show an in-app banner plus haptics instead of a system notification, and record the stage so it isn't repeated.
- Notification: title `Monthly budget: Last exit in 2 days`; body = headline + `Cut to $36/day now, or by Nov 14 at the latest.`; BigTextStyle, status colour, auto-cancel.
- The content intent is the deep link `lastexit://tracker/{id}` with `FLAG_IMMUTABLE`. The activity is `singleTop` and handles `onNewIntent`. The Demo deep link is `lastexit://demo`.

## 8. Demo mode (the core of the pitch)

All demo data lives in memory. It never touches Room or the real trackers.

The scenario is a $1,500 monthly budget over 30 days, factor 0.4, starting on the 1st of the current month. Daily spends:

`38, 45.5, 31, 52, 40, 36.5, 48, 44, 39, 96, 128, 84, 76, 82, 71, 77, 74, 86, 81, 77, 90, 83, 72, 95, 78, 84, 88, 76, 91, 86`

with believable notes (groceries, concert tickets, weekend trip, takeaway…). With the engine above this gives:

| Day | Event |
|---|---|
| 1–10 | SAFE |
| 11 | **ACT_SOON**, 12 days before the crossing |
| 15 | **LAST_EXIT** ("Last exit in 2 days… $589 left"), 8 days before the crossing |
| 18 | PAST_THE_LINE, 5 days before the crossing |
| 23 | Limit actually crossed; the month ends at $2,149 |

Write unit tests for this arc:
- The stages appear in order.
- Status never improves on the untouched path.
- Red arrives at least 5 days before the crossing, with money still left.

**Take the exit:** from the next day, spend `floor(0.9 × act-today pace)` per day. Taking it on day 15 means $35/day, back to SAFE on day 22, and the month finishes at **$1,436 of $1,500**. Test that taking the exit on *any* amber or red day ends under the limit and green, and that the plan pace is ≥ m.

**Controls:**
- Simulated date (big), "Day 15 of 30", a month progress bar.
- Play/Pause (64 dp), Restart, +1 day.
- Speed 1× / 5× / 20× (1.2 s, 0.24 s and 60 ms per day).
- "Pause on each warning" (on by default).

**On each worsening:**
- A slide-in banner with the title and the headline plus "The limit isn't crossed yet: $X left".
- A **Take the exit** action.
- An escalating vibration.
- The chip pops, shakes and changes colour.
- A **real notification** prefixed `[Demo]`.
- Auto-pause.

**On the crossing:** "Limit crossed on Oct 23. Last Exit warned you 8 days earlier."

Also show:
- A **Timeline** card listing each milestone with "N days before the crossing".
- An outcome card at day 30, with a **Run it again** button.

Request the notification permission when Demo opens.

## 9. Design: calm until urgent

- Fixed status colours that ignore dynamic colour. Light mode as main/container:
  - SAFE `#1B7F3B`/`#DCF2E3`
  - ACT_SOON `#B25E00`/`#FFE9C2`
  - LAST_EXIT `#D32F2F`/`#FDE0DE`
  - PAST `#7A0E0E`/`#F2CFCF`
  - Dark mode: `#6FD18E`, `#FFC560`, `#FF7B72`, `#E04848` on deep containers.
- LAST_EXIT and PAST chips are filled; SAFE and ACT_SOON are tonal.
- Neutrals and the accent come from dynamic colour on Android 12+. Below that, use the teal brand `#0F766E`.
- Large tabular numbers (`tnum`), 22–24 dp corner radii, generous spacing.
- Motion: animated colour transitions, a pulsing halo on red chips, pop + shake on worsening, the chart drawing in on first show. Respect "Remove animations".
- Haptics: ACT_SOON 60 ms; LAST_EXIT 90-90-160; PAST 140-80-140-80-260.
- Every icon has a content description (or is marked decorative). Cards read a full TalkBack sentence: `Monthly budget. Status: Last exit in 2 days. Used $911 of $1,500. …`. Section titles are headings, and banners are live regions.

## 10. State, lifecycle, robustness

- Save the following in `SavedStateHandle` (or `onSaveInstanceState`) so a rotation or process death loses nothing: the back stack, open quick-log sheet contents, form fields and template, the what-if pace, and demo day, speed, playing state, exit plan and milestones.
- Database I/O happens off the main thread, and a storage failure is logged, never a crash.
- Guard every API above 26: `VibratorManager` (31), system colour resources (31), insets controller (30), accessibility headings (28), `POST_NOTIFICATIONS` (33).

## 11. Tests beyond the engine

Write Robolectric tests (`@GraphicsMode(NATIVE)`, run on `sdk = [26, 31, 34]`) that drive the real UI and save a PNG of each state:
- Empty home; demo amber, then red, with exactly one notification titled `[Demo] … Last exit`.
- Taking the exit leads to "Back on track"; skipping it leads to "Limit actually crossed".
- Rotation keeps the demo day.
- Create from a template, then quick-log with the early-estimate preview.
- Detail and What-if (stored data unchanged); worst-first ordering on Home; dark mode.
- The deep link opens the right tracker.
- The background check notifies once and doesn't repeat.
- Form validation; edit, delete entry with Undo, delete tracker.
- Every drawable inflates.

## 12. Build order (each step ends compiling with tests green)

1. Project setup (catalog, wrapper, modules), then the engine and its unit tests. Print the test table.
2. Persistence, repository, ViewModels.
3. Home, Add/Edit with templates and validation, Quick-log with live preview.
4. Detail with the Canvas chart and LAST EXIT marker; What-if; cut-back slider.
5. Notifications, the background job, deep links.
6. Demo mode, including Take the exit, the timeline and the outcome card.
7. Polish: empty states, animations, haptics, accessibility, dark mode, Robolectric flows, README.

## 13. Pitfalls to avoid

- Keep KSP's version in step with Kotlin (`2.0.21-1.0.28`). Kotlin 2.x needs the Compose compiler *Gradle plugin*, not `kotlinCompilerExtensionVersion`.
- PendingIntents need `FLAG_IMMUTABLE`. Activities with intent filters need `android:exported`. Create the channel before calling `notify`.
- WorkManager's minimum period is 15 minutes; don't promise minute-level checks.
- Avoid `removeFirst()` and `removeLast()` on mutable lists. With JDK 21 they resolve to Java methods that crash on Android below 15; use `removeAt(0)` and `removeAt(lastIndex)`.
- Never derive `today` inside the engine. Pass it in, or Demo mode can't fast-forward.
- Don't let one big first entry scream "too late": use the early-estimate cap and the one-off flag.

## 14. Output format

- Start with the full file tree, then complete, compilable files with packages and imports.
- No placeholders, no `TODO`s, no "left as an exercise".
- Keep functions small and comment the non-obvious math.
- When something is ambiguous, state the assumption in one line and keep going; don't stop to ask.
- End with a README containing:
  - how to run on an emulator and on a physical device (USB debugging), and how to install the APK;
  - a 60-second demo script;
  - three bullets on what makes this different from a normal budget tracker:
    1. It warns about the *last day you can still recover*, not the day you hit the limit.
    2. It is honest about what a realistic cut-back can do (`minPaceFactor`, one-offs, the early-estimate cap).
    3. It shows its work: the exact recovery numbers and a chart with the exit marked.
