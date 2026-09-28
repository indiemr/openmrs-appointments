# Appointment booking error
_Exported on 9/14/2026 at 20:40:53 GMT+5:30 from Cursor (3.20.17)_

---

**User**

wot error is these?
ERROR - AppointmentController.saveAppointment(84) |2026-08-26T18:42:20,343| Runtime error while trying to create new appointment
org.openmrs.api.APIException: Selected date is outside the allowed booking window.
	at org.openmrs.module.appointments.helper.AppointmentServiceHelper.validate(AppointmentServiceHelper.java:96) ~[appointments-api-2.2.0-SNAPSHOT.jar:?]
	at org.openmrs.module.appointments.service.impl.AppointmentsServiceImpl.validate(AppointmentsServiceImpl.java:213) ~[appointments-api-2.2.0-SNAPSHOT.jar:?]
	at org.openmrs.module.appointments.service.impl.AppointmentsServiceImpl.validateAndSave(AppointmentsServiceImpl.java:155) ~[appointments-api-2.2.0-SNAPSHOT.jar:?]
	at sun.reflect.GeneratedMethodAccessor2333.invoke(Unknown Source) ~[?:?]
	at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43) ~[?:1.8.0_362]
	at java.lang.reflect.Method.invoke(Method.java:498) ~[?:1.8.0_362]
	at org.springframework.aop.support.AopUtils.invokeJoinpointUsingReflection(AopUtils.java:344) ~[spring-aop-5.2.14.RELEASE.jar:5.2.14.RELEASE]
	at org.springframework.aop.framework.ReflectiveMethodInvocation.invokeJoinpoint(ReflectiveMethodInvocation.java:198) ~[spring-aop-5.2.14.RELEASE.jar:5.2.14.RELEASE]
	at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:163) ~[spring-aop-5.2.14.RELEASE.jar:5.2.14.RELEASE]
	at org.springframework.aop.framework.adapter.AfterReturningAdviceInterceptor.invoke(AfterReturningAdviceInterceptor.java:55) ~[spring-aop-5.2.14.RELEASE.jar:5.2.14.RELEASE]
	at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:186) ~[spring-aop-5.2.14.RELEASE.jar:5.2.14.RELEASE]
	at org.springframework.aop.framework.adapter.ThrowsAdviceInterceptor.invoke(ThrowsAdviceInterceptor.java:112) ~[spring-aop-5.2.14.RELEASE.jar:5.2.14.RELEASE]
	at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:186) ~[spring-aop-5.2.14.RELEASE.jar:5.2.14.RELEASE]
	at org.springframework.aop.framework.adapter.AfterReturningAdviceInterceptor.invoke(AfterReturningAdviceInterceptor.java:55) ~[spring-aop-5.2.14.RELEASE.jar:5.2.14.RELEASE]
	at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:186) ~[spring-aop-5.2.14.RELEASE.jar:5.2.14.RELEASE]
	at org.springframework.aop.framework.adapter.MethodBeforeAdviceInterceptor.invoke(MethodBeforeAdviceInterceptor.java:56) ~[spring-aop-5.2.14.RELEASE.jar:5.2.14.RELEASE]
	at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:186) ~[spring-aop-5.2.14.RELEASE.jar:5.2.14.RELEASE]

---

**Cursor**

I'll look up where that booking-window check lives so I can explain the error from the actual code.

This is **not a crash**. The save was rejected by booking-window validation.

Someone tried to create an appointment whose date is **later than the service allows**. The module throws `APIException: Selected date is outside the allowed booking window.` `AppointmentController.saveAppointment` catches that, logs it as `"Runtime error while trying to create new appointment"`, and returns **HTTP 400**.

## What the check does

On create, `AppointmentsServiceImpl.validateAndSave` runs `AppointmentBookingWindowValidator`. That validator looks at the appointment service’s **`bookAheadDays`** (column `appointment_service.book_ahead_days`: “max days into the future patients can book”).

The rule in `AppointmentBookingRulesUtil` is:

```
lastBookableDate = today (in the service location’s timezone) + bookAheadDays
```

The selected date is allowed only if it is **on or before** `lastBookableDate`. If it is after that, this error is thrown.

Example: if today is 26 Aug 2026 and `bookAheadDays = 14`, the last bookable date is **9 Sep 2026**. Booking 10 Sep or later fails.

## When the check is skipped

The validator **does not** fail if:

- `bookAheadDays` is `null` (no limit)
- the appointment has no service or no date
- the appointment status is `Cancelled`

Timezone comes from the location’s `timezone` attribute, or the JVM default if that is missing.

## What to do

1. Confirm the appointment date vs the service’s `bookAheadDays`.
2. Book a date inside the window, **or** raise/clear `bookAheadDays` on that appointment service.
3. If the date looks valid, check timezone mismatch (location timezone vs the date sent by the client).

The `"Runtime error..."` line is just the controller’s generic catch-all; the real reason is the booking-window message.

---

**User**

so is it standard practise, how bookmyshow and TTD tickets are distributed based on opening tickets for upcoming X days

---

**Cursor**

Yes — **limiting bookings to the next X days is standard**. What this module does is the common **sliding window**. BookMyShow and TTD use a related idea, but they are closer to **inventory release** than clinic scheduling.

## What this module does

`bookAheadDays` is a rolling horizon:

- Today you can book **today through today + N days**
- Tomorrow the window moves forward by one day
- Combined with `leadTimeMinutes`, you also cannot book *too soon*

That is the usual pattern for clinics, labs, restaurants, salons, and many government appointment systems: keep the calendar short so slots stay current and no-shows stay manageable.

## How that compares to BookMyShow / TTD

| | This module (`bookAheadDays`) | BookMyShow | TTD-style tickets |
|---|---|---|---|
| Model | Sliding window from today | Showtimes released for a short upcoming range | Batch release of a future date (often at a fixed time) |
| When a new date appears | Automatically, when that day enters the window | When the theatre/distributor publishes it | When the portal opens that date’s quota |
| Demand control | Horizon + optional lead time | Seat inventory + showtime release | Quota + timed opening (rush / queue) |
| Goal | Keep clinic books realistic | Sell a film’s near-term shows | Fair (or at least gated) access to scarce slots |

So:

- **Same idea:** only the next X days are bookable; you do not open the whole year.
- **Different mechanism:** this module does **not** “open tickets at 10 AM for date D”. As soon as a day is inside `bookAheadDays`, it is bookable (subject to slots, lead time, unavailability, etc.).

BookMyShow is still a rolling “next few days” catalog, but dates appear because inventory was **published**, not only because the calendar ticked.

TTD is the other extreme: a **release event** (date + quota + clock). That creates a stampede. This appointments module is not built that way.

## When the clinic pattern is the right one

Use `bookAheadDays` (what you have) when you want:

- a stable daily clinic list
- less speculation months ahead
- capacity that can still change (doctor leave, unavailability)

You would need extra design (not just `bookAheadDays`) if you wanted TTD-like behavior: open date D at a fixed time, hold a quota, queue/lottery, then close.

**Short answer:** yes, “open only the next X days” is standard. This code is the healthcare sliding-window version of that. BookMyShow/TTD add **controlled release of inventory**, which this validator does not do.

---

**User**

we have to work on these, locking the appointment.

how should we proceed? maintaining a new table? or using status?

---

**Cursor**

I'll look at how appointment status and slot availability work today so the lock design fits this module instead of fighting it.

Use **status to hold the slot**, and a **small booking-request table for OTP/expiry**. Do not pick only one of those.

A hold is two different things:

1. **Occupancy** — this slot is taken so someone else cannot book it.
2. **A pending checkout session** — OTP, retries, `bookingRequestId`, `expiresAt`, masked phone.

Those should not live in the same place.

## Why status for the slot

Slot counting already treats **every status except `Cancelled` as occupying** the slot:

```35:39:api/src/main/java/org/openmrs/module/appointments/util/AppointmentServiceCapacityUtil.java
    public static final List<AppointmentStatus> SLOT_BLOCKING_STATUSES = Collections.unmodifiableList(
                                            EnumSet.allOf(AppointmentStatus.class).stream()
                                            .filter(status -> status != AppointmentStatus.Cancelled)
                                            .collect(Collectors.toList())
                                        );
```

If Booking OMOD saves a real appointment as `Locked` (or similar), availability drops immediately. That matches the diagram: *“Slot is held — others see lower availability.”*

A hold-only table does **not** get that for free. `getAvailableSlots` / `isSlotCapacityExceeded` only count appointments. You would have to union holds into every capacity path, or you will double-book.

Confirming is also a natural status change: `Locked → Scheduled`, which this module already knows how to audit and validate.

## Why not status alone

`AppointmentStatus` is a **clinic lifecycle** (`Requested` → `Scheduled`/`Confirmed` → `CheckedIn` → `Completed` / `Missed` / `Cancelled`). A 5-minute OTP hold is not a clinical state.

If you only add `Locked`:

- You still need **expiry, OTP hash, retry count, request id** — that is not appointment data.
- Clinic queues, search, summaries, and reminders will show half-booked rows unless you filter `Locked` everywhere.
- On create, `AppointmentCreateSideEffectsListener` still tries **billing** (calendar already skips non-confirmed). A hold must not create a bill.
- `DefaultAppointmentStatusChangeValidator` is sequence-based. `Locked` must be sequence `0`, and `Locked → Scheduled` / `Locked → Cancelled` must be explicit.

Do **not** reuse existing statuses:

| Status | Already means |
|---|---|
| `Tentative` / `WaitList` | Date-only / waitlist (`AppointmentStatusUtil.isTentative`) |
| `Requested` | Provider-accept workflow |
| `Scheduled` / `Confirmed` | Real booking (SMS + calendar fire) |

## Why not a new table alone

A `appointment_hold` table is the right model for **session** data, the wrong model for **capacity**.

You would have two sources of truth, two queries for “is this slot full?”, and a race on confirm (hold exists, someone else books a real appointment in the same millisecond).

BookMyShow/TTD-style locks work because the hold **is** the inventory row. Here, inventory is the `appointment` row. Put the hold there.

## Recommended split

```
FE → Gateway → Booking OMOD → Appointments OMOD
```

**Appointments OMOD (this repo)**  
- `POST` save with `status=Locked` (new enum, sequence 0).  
- Optional `holdExpiresAt` on the appointment (or only in the booking table — see below).  
- Capacity, booking window, lead time, unavailability validators run on that save.  
- `Locked → Scheduled` and `Locked → Cancelled` only.  
- Search / queue / SMS / billing / calendar **ignore `Locked`**.  
- Scheduler: expire `Locked` past `expiresAt` → `Cancelled` (or void).

**Booking OMOD (new table)**  
Something like `booking_request`:

- `uuid` / `bookingRequestId`
- `appointment_uuid`
- `otp_hash` (never store raw OTP)
- `attempts`
- `expires_at`
- `masked_phone`
- `status` (`PENDING` / `CONFIRMED` / `EXPIRED` / `FAILED`)

OTP verify, retries, and SMS live here. Appointments OMOD should not know about OTP.

That is what your diagram already describes: **appointment row + hold expiry**, with Booking OMOD owning confirm/OTP.

## How to proceed in this module

1. Add `Locked` to `AppointmentStatus` (sequence 0). Update `AppointmentStatusUtil` so it is neither tentative nor confirmed.
2. Teach capacity to keep counting `Locked` (already true if it is not `Cancelled`).
3. Exclude `Locked` from clinic lists, appointment summary, reminders, and `MarkAppointmentAsMissedTask`.
4. Skip billing (and any other create side effects) when status is `Locked`; run them on `Locked → Scheduled`.
5. Allow `Locked → Scheduled` and `Locked → Cancelled` in the status-change validator.
6. Add a scheduled task: expired `Locked` → `Cancelled`.
7. On confirm: verify OTP in Booking OMOD, then `changeStatus(Locked → Scheduled)` in the same logical transaction as you can.
8. On wrong OTP / timeout: `Cancelled` or void the locked appointment so the slot frees (`Cancelled` is what capacity already ignores).

Concurrency: validate capacity **inside the save transaction**. Two parallel `Locked` saves for the last seat should fail one of them (`isSlotCapacityExceeded`). Do not “lock in memory” on the FE.

## Practical rule

| Data | Where |
|---|---|
| Slot occupancy | Appointment row + `Locked` status |
| OTP, retries, `bookingRequestId` | Booking-request table |
| Expiry sweeper | Either `holdExpiresAt` on appointment, or `expires_at` on the booking row driving cancel |

Status without a request table leaves secrets and session state on the appointment. A table without a locked appointment will not hold the slot in this codebase.

If you want, next step can be a concrete enum/validator/listener change list for this repo (still in Ask mode: design only).

---

**User**

see status only cant be a reliable soln here, ig. because, once we locked, we have to revert it back within 7mins  window. and there will be concurrency case as well.

---

**Cursor**

You're right that an enum by itself cannot expire or serialize last-seat bookings. I'll check how saves and slot counts work today so the 7-minute window and races are explicit.

Status-only is not enough for those two cases. An enum cannot expire, and this module’s save path does not serialize last-seat bookings.

## Why status fails the 7-minute revert

`Locked` stays `Locked` until something writes a new status. That “something” is a job.

This repo’s schedulers are coarse (for example `MarkAppointmentAsMissedTask` walks a date range). They are not a 7:00.000 TTL. If the job runs late, the slot stays blocked. If it runs while the user confirms, you get `Locked → Scheduled` racing `Locked → Cancelled`.

Expiry has to be a **timestamp**, not a later status flip:

- Occupancy: count a hold only while `expires_at > now()`
- Confirm: `UPDATE … WHERE status = Locked AND expires_at > now()` (0 rows → expired)
- A sweeper only **cleans rows**; it must not be what frees the slot

Until occupancy queries use `expires_at`, flipping status at ~7 minutes will never be reliable.

## Why status also fails concurrency

Capacity check today is: `COUNT` overlapping appointments, then `INSERT`. There is no row lock, no `FOR UPDATE`, no version.

```210:243:api/src/main/java/org/openmrs/module/appointments/dao/impl/AppointmentDaoImpl.java
    public int countOverlappingAppointmentsForService
    (...) {
        ...
        criteria.setProjection(Projections.countDistinct("appointmentId"));
        Number count = (Number) criteria.uniqueResult();
        return count != null ? count.intValue() : 0;
    }
```

Two OTP starts on the last seat can both see `booked < capacity` and both insert `Locked`. Status does not fix that. A hold table does not fix that either unless the **decrement is atomic**.

## What to do instead

Treat the 7-minute window as a **hold**, and create the real appointment only on OTP success.

```
request  →  insert hold (expires_at = now+7m), decrement slot remaining
OTP ok   →  create appointment Scheduled, consume hold
timeout  →  hold ignored because expires_at passed (no status revert)
```

That avoids “lock an appointment, then undo it.” There is nothing clinical to revert.

**Booking OMOD table** (session + TTL):

- `booking_request_id`, `appointment_uuid` (null until confirm)
- `service_id`, `slot_start`, `slot_end`, `patient_id`
- `expires_at`
- `otp_hash`, `attempts`
- `status`: `HELD` / `CONSUMED` / `EXPIRED`

**Slot counter** (concurrency): one row per bookable slot, not per patient.

```text
UPDATE appointment_slot
SET remaining = remaining - 1
WHERE service_id = ? AND slot_start = ? AND remaining > 0
```

0 rows updated → slot full. That is the same idea as BookMyShow inventory, not an appointment status.

On expire or failed OTP, increment `remaining` **only if** the hold was still `HELD` and unconsumed (same compare-and-swap idea). Lazy occupancy still uses `expires_at > now()` so a late increment cannot oversell if you also refuse to count expired holds.

## Confirm / timeout (no status gymnastics)

| Event | Action |
|---|---|
| OTP success before expiry | `UPDATE hold SET status=CONSUMED WHERE id=? AND status=HELD AND expires_at>now()` then `validateAndSave` Scheduled |
| OTP after 7 min | 0 rows; return expired; do not create appointment |
| Two confirms | Second `CONSUMED` update updates 0 rows |
| Two last-seat requests | Second `remaining > 0` update updates 0 rows |

Availability must add **unexpired holds** to booked count (today it only counts appointments). If it does not, the hold is invisible and you oversell.

## Status vs table, given your constraints

| | Status-only | Hold table + slot remaining |
|---|---|---|
| 7-min release | Needs a job; slot can stick | `expires_at` in the read path |
| Last-seat race | Current COUNT+INSERT loses | `UPDATE remaining WHERE remaining > 0` |
| Undo | Cancel/void fake appointments | Hold expires; no appointment row |
| OTP / retries | Does not belong on `Appointment` | Lives on `booking_request` |

Use `Locked` on an appointment row only if you also add `hold_expires_at` **and** a slot counter. For a 7-minute OTP gate, hold-first is simpler: Appointments OMOD stays “real bookings,” Booking OMOD owns TTL and races.

Do not depend on OpenMRS scheduler to unlock at 7 minutes. Do not depend on `AppointmentStatus` to serialize two POSTs. Those are the two reasons status-only is the wrong primitive here.

---

**User**

we should prefer maintain a appointment hold table, but how to handle expires at? 

---

**Cursor**

Handle `expires_at` as a **clock on the hold row**, not as a job that “unlocks” later. The slot is free the moment `now() >= expires_at`, even if the row is still `HELD`.

## 1. Store an instant, not a clinic timezone

`expires_at` is a duration (7 minutes), not a calendar date. Persist UTC datetime.

On insert:

```text
created_at  = DB now()
expires_at  = created_at + INTERVAL 7 MINUTE
status      = HELD
```

Use a global property for the 7 minutes (`appointments.holdMinutes=7`) so you can change it without a release.

Compare with **database time** (`CURRENT_TIMESTAMP` / `UTC_TIMESTAMP()`), not `new Date()` on the app. App and DB clocks drift; OTP confirm at 6:59 vs expire at 7:00 will disagree if each uses a different clock.

Do not use location timezone here. That is for `bookAheadDays` / slot display only.

## 2. Lazy expiry is the source of truth

A hold occupies a slot **only if**:

```text
status = HELD AND expires_at > now()
```

Put that predicate on every path:

| Path | Rule |
|---|---|
| Availability / capacity | `booked = real appointments + unexpired holds` |
| Create hold | same count, then insert, in one transaction |
| Confirm OTP | consume only if still unexpired |
| Timeout / wrong OTP | no-op if already expired |

Then you do **not** need a 7-minute scheduler for correctness. OpenMRS tasks will not hit 7:00.000 anyway.

Expired `HELD` rows are already dead. A sweeper only marks them `EXPIRED` and optionally deletes old rows.

## 3. Confirm and expire must be compare-and-swap

**Confirm** (OTP success):

```sql
UPDATE appointment_hold
SET status = 'CONSUMED',
    appointment_uuid = :apptUuid
WHERE uuid = :holdUuid
  AND status = 'HELD'
  AND expires_at > CURRENT_TIMESTAMP
```

0 rows → treat as expired (or already consumed). Do **not** create the appointment.

**Expire** (job, next availability read, or failed OTP after timeout):

```sql
UPDATE appointment_hold
SET status = 'EXPIRED'
WHERE uuid = :holdUuid
  AND status = 'HELD'
  AND expires_at <= CURRENT_TIMESTAMP
```

Confirm and expire both require `status = HELD`, so they cannot both win. That is the 7-minute race: user confirms at 6:59.9 vs clock passing 7:00.

Do not `DELETE` on expiry. Keep the row for audit; occupancy already ignores it.

## 4. Do not put TTL on a `remaining` counter without this

If you decrement `appointment_slot.remaining` when the hold is created, that counter **does not expire by itself**. After 7 minutes `remaining` stays low until you increment it again.

Two ways to avoid that:

**Preferred with a hold table:** do not keep a remaining counter. Occupancy is always:

```text
COUNT(appointments in slot, not Cancelled)
+ COUNT(holds in slot WHERE status = HELD AND expires_at > now())
```

When `expires_at` passes, the second count drops with no extra write. That is the clean `expires_at` design.

**If you still want a remaining counter** (stronger last-seat lock):

1. Decrement on hold insert (`WHERE remaining > 0`).
2. Increment **only** when the CAS expire update returns 1 row (or when confirm fails because the hold expired and you expire it in the same transaction).
3. On every availability/hold request, first expire due holds for that slot, then read remaining.

Never increment remaining because “the job thinks it is expired” without the CAS. Confirm could have consumed the hold in between.

## 5. Sweeper (hygiene only)

Run every 1–5 minutes, not as the unlock mechanism:

```sql
UPDATE appointment_hold
SET status = 'EXPIRED'
WHERE status = 'HELD'
  AND expires_at <= CURRENT_TIMESTAMP
```

If you use a remaining counter, for each row actually flipped `HELD → EXPIRED`, `remaining = remaining + 1` (capped at slot capacity).

Also hide expired holds in UI by the same `expires_at > now()` filter, so a late sweeper does not keep the slot looking taken.

## 6. What to return to the FE

On `POST /appointment/request` return `expiresAt` (UTC ISO-8601) and `expiresIn` (seconds). The client countdown is UX only. The server must re-check `expires_at` on confirm.

OTP resend: do **not** silently extend `expires_at` unless you mean to. Usual pattern: hold TTL stays 7 minutes from first request; OTP can be resent inside that window. If you refresh TTL on resend, cap total hold time (e.g. max 7 minutes from `created_at`) or people can sit on a slot forever.

## 7. Minimal column set

```text
appointment_hold
  uuid
  service_id, start_datetime, end_datetime, patient_id
  status          -- HELD | CONSUMED | EXPIRED | FAILED
  expires_at      -- UTC
  created_at
  otp_hash, attempts
  appointment_uuid  -- set on CONSUMED
```

**Rule:** `expires_at` is checked on read and on every state change. The job only cleans `HELD` rows that are already past `expires_at`. That is how the 7-minute window stays correct under concurrency.

---

**User**

ok, lets work on. all make sense.
give code step by step.
start from dao layer -> to impl.
give code step by step.

---

**Cursor**

I'll follow this module's DAO/service patterns and give you copy-pasteable layers. Ask mode means I can't write the files here — switch to Agent mode if you want them applied in the repo.

I'm in **Ask mode**, so this is the code to add — I cannot write it into the repo. Switch to **Agent mode** if you want it applied for you.

OTP stays in Booking OMOD. This slice is occupancy + `expires_at` only. Wire slot availability after these layers compile.

---

## Step 0 — Table and model (DAO needs these)

Append to `api/src/main/resources/liquibase.xml`:

```xml
<changeSet id="create-appointment-hold-table-20260902" author="senthil_athiban">
    <preConditions onFail="MARK_RAN">
        <not>
            <tableExists tableName="appointment_hold"/>
        </not>
    </preConditions>
    <comment>Temporary slot hold until OTP confirm; occupancy uses expires_at, not a job</comment>
    <createTable tableName="appointment_hold">
        <column name="appointment_hold_id" type="int" autoIncrement="true">
            <constraints primaryKey="true" nullable="false"/>
        </column>
        <column name="appointment_service_id" type="int">
            <constraints nullable="false"/>
        </column>
        <column name="patient_id" type="int">
            <constraints nullable="false"/>
        </column>
        <column name="patient_appointment_id" type="int"/>
        <column name="start_date_time" type="datetime">
            <constraints nullable="false"/>
        </column>
        <column name="end_date_time" type="datetime">
            <constraints nullable="false"/>
        </column>
        <column name="status" type="varchar(20)">
            <constraints nullable="false"/>
        </column>
        <column name="expires_at" type="datetime">
            <constraints nullable="false"/>
        </column>
        <column name="uuid" type="varchar(38)">
            <constraints nullable="false" unique="true"/>
        </column>
        <column name="date_created" type="datetime">
            <constraints nullable="false"/>
        </column>
        <column name="creator" type="int">
            <constraints nullable="false"/>
        </column>
        <column name="date_changed" type="datetime"/>
        <column name="changed_by" type="int"/>
        <column name="voided" type="boolean" defaultValueBoolean="false">
            <constraints nullable="false"/>
        </column>
        <column name="voided_by" type="int"/>
        <column name="date_voided" type="datetime"/>
        <column name="void_reason" type="varchar(255)"/>
    </createTable>
    <addForeignKeyConstraint constraintName="fk_appointment_hold_service"
        baseTableName="appointment_hold" baseColumnNames="appointment_service_id"
        referencedTableName="appointment_service" referencedColumnNames="appointment_service_id"/>
    <addForeignKeyConstraint constraintName="fk_appointment_hold_patient"
        baseTableName="appointment_hold" baseColumnNames="patient_id"
        referencedTableName="patient" referencedColumnNames="patient_id"/>
    <addForeignKeyConstraint constraintName="fk_appointment_hold_appointment"
        baseTableName="appointment_hold" baseColumnNames="patient_appointment_id"
        referencedTableName="patient_appointment" referencedColumnNames="patient_appointment_id"/>
    <addForeignKeyConstraint constraintName="fk_appointment_hold_creator"
        baseTableName="appointment_hold" baseColumnNames="creator"
        referencedTableName="users" referencedColumnNames="user_id"/>
    <createIndex indexName="idx_appointment_hold_slot_expiry" tableName="appointment_hold">
        <column name="appointment_service_id"/>
        <column name="start_date_time"/>
        <column name="end_date_time"/>
        <column name="status"/>
        <column name="expires_at"/>
    </createIndex>
</changeSet>
```

`api/src/main/java/org/openmrs/module/appointments/model/AppointmentHoldStatus.java`

```java
package org.openmrs.module.appointments.model;

public enum AppointmentHoldStatus {
    HELD,
    CONSUMED,
    EXPIRED,
    FAILED
}
```

`api/src/main/java/org/openmrs/module/appointments/model/AppointmentHold.java`

```java
package org.openmrs.module.appointments.model;

import org.openmrs.BaseOpenmrsData;
import org.openmrs.Patient;

import java.io.Serializable;
import java.util.Date;

public class AppointmentHold extends BaseOpenmrsData implements Serializable {
    private Integer appointmentHoldId;
    private AppointmentServiceDefinition service;
    private Patient patient;
    private Appointment appointment;
    private Date startDateTime;
    private Date endDateTime;
    private AppointmentHoldStatus status;
    private Date expiresAt;

    @Override
    public Integer getId() {
        return appointmentHoldId;
    }

    @Override
    public void setId(Integer id) {
        this.appointmentHoldId = id;
    }

    public Integer getAppointmentHoldId() { return appointmentHoldId; }
    public void setAppointmentHoldId(Integer appointmentHoldId) { this.appointmentHoldId = appointmentHoldId; }
    public AppointmentServiceDefinition getService() { return service; }
    public void setService(AppointmentServiceDefinition service) { this.service = service; }
    public Patient getPatient() { return patient; }
    public void setPatient(Patient patient) { this.patient = patient; }
    public Appointment getAppointment() { return appointment; }
    public void setAppointment(Appointment appointment) { this.appointment = appointment; }
    public Date getStartDateTime() { return startDateTime; }
    public void setStartDateTime(Date startDateTime) { this.startDateTime = startDateTime; }
    public Date getEndDateTime() { return endDateTime; }
    public void setEndDateTime(Date endDateTime) { this.endDateTime = endDateTime; }
    public AppointmentHoldStatus getStatus() { return status; }
    public void setStatus(AppointmentHoldStatus status) { this.status = status; }
    public Date getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Date expiresAt) { this.expiresAt = expiresAt; }
}
```

`api/src/main/resources/AppointmentHold.hbm.xml`

```xml
<?xml version="1.0"?>
<!DOCTYPE hibernate-mapping PUBLIC
        "-//Hibernate/Hibernate Mapping DTD 3.0//EN"
        "https://hibernate.org/dtd/hibernate-mapping-3.0.dtd" >
<hibernate-mapping package="org.openmrs.module.appointments.model">
    <class name="AppointmentHold" table="appointment_hold" batch-size="50">
        <id name="appointmentHoldId" type="java.lang.Integer" column="appointment_hold_id">
            <generator class="native">
                <param name="sequence">appointment_hold_id_seq</param>
            </generator>
        </id>
        <many-to-one name="service" class="org.openmrs.module.appointments.model.AppointmentServiceDefinition"
                     column="appointment_service_id" not-null="true"/>
        <many-to-one name="patient" class="org.openmrs.Patient" column="patient_id" not-null="true"/>
        <many-to-one name="appointment" class="org.openmrs.module.appointments.model.Appointment"
                     column="patient_appointment_id"/>
        <property name="startDateTime" type="java.util.Date" column="start_date_time" not-null="true"/>
        <property name="endDateTime" type="java.util.Date" column="end_date_time" not-null="true"/>
        <property name="status" column="status" not-null="true">
            <type name="org.hibernate.type.EnumType">
                <param name="enumClass">org.openmrs.module.appointments.model.AppointmentHoldStatus</param>
                <param name="useNamed">true</param>
            </type>
        </property>
        <property name="expiresAt" type="java.util.Date" column="expires_at" not-null="true"/>
        <property name="dateCreated" type="java.util.Date" column="date_created" not-null="true"/>
        <property name="dateChanged" type="java.util.Date" column="date_changed"/>
        <property name="uuid" type="java.lang.String" column="uuid" length="38" unique="true" not-null="true"/>
        <property name="voided" type="java.lang.Boolean" column="voided" length="1"/>
        <property name="dateVoided" type="java.util.Date" column="date_voided"/>
        <property name="voidReason" type="java.lang.String" column="void_reason" length="255"/>
        <many-to-one name="voidedBy" class="org.openmrs.User" column="voided_by"/>
        <many-to-one name="changedBy" class="org.openmrs.User" column="changed_by"/>
        <many-to-one name="creator" class="org.openmrs.User" column="creator" not-null="true"/>
    </class>
</hibernate-mapping>
```

Add `AppointmentHold.hbm.xml` to `omod/src/main/resources/config.xml` (`mappingFiles`), and to both `test-hibernate.cfg.xml` files.

---

## Step 1 — DAO interface

`api/src/main/java/org/openmrs/module/appointments/dao/AppointmentHoldDao.java`

```java
package org.openmrs.module.appointments.dao;

import org.openmrs.module.appointments.model.Appointment;
import org.openmrs.module.appointments.model.AppointmentHold;
import org.openmrs.module.appointments.model.AppointmentServiceDefinition;

import java.util.Date;

public interface AppointmentHoldDao {

    AppointmentHold save(AppointmentHold hold);

    AppointmentHold getByUuid(String uuid);

    int countUnexpiredOverlapping(AppointmentServiceDefinition service,
                                  Date slotStart,
                                  Date slotEnd);

    boolean consumeIfActive(String holdUuid, Appointment appointment);

    boolean expireIfDue(String holdUuid);

    int expireAllDue();

    boolean acquireSlotLock(String lockKey, int timeoutSeconds);

    void releaseSlotLock(String lockKey);
}
```

`countUnexpiredOverlapping` is occupancy: `HELD` and `expires_at > now()`.  
`consumeIfActive` / `expireIfDue` are compare-and-swap (0/1 rows).  
`acquireSlotLock` is MySQL `GET_LOCK` so two last-seat requests cannot both pass the count.

---

## Step 2 — DAO impl

`api/src/main/java/org/openmrs/module/appointments/dao/impl/AppointmentHoldDaoImpl.java`

```java
package org.openmrs.module.appointments.dao.impl;

import org.hibernate.Criteria;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.openmrs.module.appointments.dao.AppointmentHoldDao;
import org.openmrs.module.appointments.model.Appointment;
import org.openmrs.module.appointments.model.AppointmentHold;
import org.openmrs.module.appointments.model.AppointmentHoldStatus;
import org.openmrs.module.appointments.model.AppointmentServiceDefinition;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

public class AppointmentHoldDaoImpl implements AppointmentHoldDao {

    private SessionFactory sessionFactory;

    public void setSessionFactory(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    @Transactional
    @Override
    public AppointmentHold save(AppointmentHold hold) {
        sessionFactory.getCurrentSession().saveOrUpdate(hold);
        return hold;
    }

    @Override
    public AppointmentHold getByUuid(String uuid) {
        Criteria criteria = sessionFactory.getCurrentSession()
                .createCriteria(AppointmentHold.class);
        criteria.add(Restrictions.eq("uuid", uuid));
        return (AppointmentHold) criteria.uniqueResult();
    }

    @Override
    public int countUnexpiredOverlapping(AppointmentServiceDefinition service,
                                         Date slotStart,
                                         Date slotEnd) {
        Criteria criteria = sessionFactory.getCurrentSession()
                .createCriteria(AppointmentHold.class, "hold");
        criteria.add(Restrictions.eq("voided", false));
        criteria.createAlias("service", "service");
        criteria.add(Restrictions.eq("service.appointmentServiceId", service.getAppointmentServiceId()));
        criteria.add(Restrictions.eq("status", AppointmentHoldStatus.HELD));
        criteria.add(Restrictions.gt("expiresAt", new Date()));
        criteria.add(Restrictions.lt("startDateTime", slotEnd));
        criteria.add(Restrictions.gt("endDateTime", slotStart));
        criteria.setProjection(Projections.rowCount());
        Number count = (Number) criteria.uniqueResult();
        return count != null ? count.intValue() : 0;
    }

    @Override
    public boolean consumeIfActive(String holdUuid, Appointment appointment) {
        Query query = sessionFactory.getCurrentSession().createQuery(
                "update AppointmentHold h set h.status = :consumed, h.appointment = :appointment "
                        + "where h.uuid = :uuid and h.status = :held and h.expiresAt > current_timestamp()");
        query.setParameter("consumed", AppointmentHoldStatus.CONSUMED);
        query.setParameter("appointment", appointment);
        query.setParameter("uuid", holdUuid);
        query.setParameter("held", AppointmentHoldStatus.HELD);
        return query.executeUpdate() == 1;
    }

    @Override
    public boolean expireIfDue(String holdUuid) {
        Query query = sessionFactory.getCurrentSession().createQuery(
                "update AppointmentHold h set h.status = :expired "
                        + "where h.uuid = :uuid and h.status = :held and h.expiresAt <= current_timestamp()");
        query.setParameter("expired", AppointmentHoldStatus.EXPIRED);
        query.setParameter("uuid", holdUuid);
        query.setParameter("held", AppointmentHoldStatus.HELD);
        return query.executeUpdate() == 1;
    }

    @Override
    public int expireAllDue() {
        Query query = sessionFactory.getCurrentSession().createQuery(
                "update AppointmentHold h set h.status = :expired "
                        + "where h.status = :held and h.expiresAt <= current_timestamp()");
        query.setParameter("expired", AppointmentHoldStatus.EXPIRED);
        query.setParameter("held", AppointmentHoldStatus.HELD);
        return query.executeUpdate();
    }

    @Override
    public boolean acquireSlotLock(String lockKey, int timeoutSeconds) {
        Number result = (Number) sessionFactory.getCurrentSession()
                .createSQLQuery("SELECT GET_LOCK(:lockKey, :timeout)")
                .setParameter("lockKey", lockKey)
                .setParameter("timeout", timeoutSeconds)
                .uniqueResult();
        return result != null && result.intValue() == 1;
    }

    @Override
    public void releaseSlotLock(String lockKey) {
        sessionFactory.getCurrentSession()
                .createSQLQuery("SELECT RELEASE_LOCK(:lockKey)")
                .setParameter("lockKey", lockKey)
                .uniqueResult();
    }
}
```

**Expiry note:** `countUnexpiredOverlapping` uses `expiresAt > now`. An overdue `HELD` row does **not** occupy the slot, even before `expireAllDue`. The sweeper only flips `HELD → EXPIRED`.

`countUnexpiredOverlapping` still uses `new Date()` (JVM). After this compiles, change it to HQL with `current_timestamp()` so count and CAS share the DB clock:

```java
Query query = session.createQuery(
    "select count(h) from AppointmentHold h "
        + "where h.voided = false and h.service.appointmentServiceId = :serviceId "
        + "and h.status = :held and h.expiresAt > current_timestamp() "
        + "and h.startDateTime < :slotEnd and h.endDateTime > :slotStart");
```

Do that in the same DAO class once the Criteria version is working.

---

## Step 3 — Service interface

`api/src/main/java/org/openmrs/module/appointments/service/AppointmentHoldService.java`

```java
package org.openmrs.module.appointments.service;

import org.openmrs.annotation.Authorized;
import org.openmrs.module.appointments.model.Appointment;
import org.openmrs.module.appointments.model.AppointmentHold;
import org.springframework.transaction.annotation.Transactional;

import static org.openmrs.module.appointments.constants.PrivilegeConstants.MANAGE_APPOINTMENTS;
import static org.openmrs.module.appointments.constants.PrivilegeConstants.VIEW_APPOINTMENTS;

public interface AppointmentHoldService {

    @Transactional
    @Authorized({MANAGE_APPOINTMENTS})
    AppointmentHold createHold(AppointmentHold hold);

    @Transactional
    @Authorized({MANAGE_APPOINTMENTS})
    AppointmentHold consumeHold(String holdUuid, Appointment appointment);

    @Transactional
    @Authorized({MANAGE_APPOINTMENTS})
    boolean expireHold(String holdUuid);

    @Transactional
    @Authorized({MANAGE_APPOINTMENTS})
    int expireDueHolds();

    @Transactional(readOnly = true)
    @Authorized({VIEW_APPOINTMENTS})
    AppointmentHold getByUuid(String uuid);
}
```

Patient-facing Booking OMOD may need a dedicated privilege later; `MANAGE_APPOINTMENTS` is enough for this layer.

---

## Step 4 — Service impl

`api/src/main/java/org/openmrs/module/appointments/service/impl/AppointmentHoldServiceImpl.java`

```java
package org.openmrs.module.appointments.service.impl;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.module.appointments.dao.AppointmentDao;
import org.openmrs.module.appointments.dao.AppointmentHoldDao;
import org.openmrs.module.appointments.model.Appointment;
import org.openmrs.module.appointments.model.AppointmentHold;
import org.openmrs.module.appointments.model.AppointmentHoldStatus;
import org.openmrs.module.appointments.model.AppointmentServiceDefinition;
import org.openmrs.module.appointments.model.ServiceWeeklyAvailability;
import org.openmrs.module.appointments.service.AppointmentHoldService;
import org.openmrs.module.appointments.util.AppointmentServiceCapacityUtil;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

@Transactional
public class AppointmentHoldServiceImpl implements AppointmentHoldService {

    public static final String GP_HOLD_MINUTES = "appointments.holdMinutes";
    public static final int DEFAULT_HOLD_MINUTES = 7;
    private static final int LOCK_TIMEOUT_SECONDS = 5;

    private final Log log = LogFactory.getLog(getClass());
    private AppointmentHoldDao appointmentHoldDao;
    private AppointmentDao appointmentDao;

    public void setAppointmentHoldDao(AppointmentHoldDao appointmentHoldDao) {
        this.appointmentHoldDao = appointmentHoldDao;
    }

    public void setAppointmentDao(AppointmentDao appointmentDao) {
        this.appointmentDao = appointmentDao;
    }

    @Override
    public AppointmentHold createHold(AppointmentHold hold) {
        validateForCreate(hold);
        if (hold.getEndDateTime() == null) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(hold.getStartDateTime());
            calendar.add(Calendar.MINUTE, AppointmentServiceCapacityUtil.resolveDurationMins(
                    hold.getService(), null));
            hold.setEndDateTime(calendar.getTime());
        }

        String lockKey = slotLockKey(hold.getService(), hold.getStartDateTime());
        if (!appointmentHoldDao.acquireSlotLock(lockKey, LOCK_TIMEOUT_SECONDS)) {
            throw new APIException("Could not lock slot. Please retry.");
        }
        try {
            int capacity = resolveCapacity(hold);
            int bookedAppointments = appointmentDao.countOverlappingAppointmentsForService(
                    hold.getService(),
                    hold.getService().getProvider(),
                    hold.getStartDateTime(),
                    hold.getEndDateTime(),
                    null,
                    AppointmentServiceCapacityUtil.SLOT_BLOCKING_STATUSES);
            int bookedHolds = appointmentHoldDao.countUnexpiredOverlapping(
                    hold.getService(), hold.getStartDateTime(), hold.getEndDateTime());
            if (bookedAppointments + bookedHolds >= capacity) {
                throw new APIException("Selected time slot is full. Please choose another slot.");
            }

            hold.setStatus(AppointmentHoldStatus.HELD);
            hold.setExpiresAt(new Date(System.currentTimeMillis() + holdMinutes() * 60_000L));
            return appointmentHoldDao.save(hold);
        } finally {
            appointmentHoldDao.releaseSlotLock(lockKey);
        }
    }

    @Override
    public AppointmentHold consumeHold(String holdUuid, Appointment appointment) {
        boolean consumed = appointmentHoldDao.consumeIfActive(holdUuid, appointment);
        if (!consumed) {
            appointmentHoldDao.expireIfDue(holdUuid);
            throw new APIException("Appointment hold has expired or is no longer active.");
        }
        AppointmentHold hold = appointmentHoldDao.getByUuid(holdUuid);
        log.info("Consumed appointment hold " + holdUuid);
        return hold;
    }

    @Override
    public boolean expireHold(String holdUuid) {
        return appointmentHoldDao.expireIfDue(holdUuid);
    }

    @Override
    public int expireDueHolds() {
        int expired = appointmentHoldDao.expireAllDue();
        log.info("Expired " + expired + " appointment hold(s)");
        return expired;
    }

    @Override
    public AppointmentHold getByUuid(String uuid) {
        return appointmentHoldDao.getByUuid(uuid);
    }

    private void validateForCreate(AppointmentHold hold) {
        if (hold == null || hold.getService() == null || hold.getPatient() == null
                || hold.getStartDateTime() == null) {
            throw new APIException("service, patient and startDateTime are required");
        }
    }

    private int holdMinutes() {
        String value = Context.getAdministrationService()
                .getGlobalProperty(GP_HOLD_MINUTES, String.valueOf(DEFAULT_HOLD_MINUTES));
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : DEFAULT_HOLD_MINUTES;
        } catch (Exception e) {
            return DEFAULT_HOLD_MINUTES;
        }
    }

    private int resolveCapacity(AppointmentHold hold) {
        AppointmentServiceDefinition service = hold.getService();
        DayOfWeek dayOfWeek = AppointmentServiceCapacityUtil.toDayOfWeek(hold.getStartDateTime());
        List<ServiceWeeklyAvailability> weekly = AppointmentServiceCapacityUtil
                .getWeeklyAvailabilitiesForDay(service, dayOfWeek);
        ServiceWeeklyAvailability match = weekly.isEmpty() ? null : weekly.get(0);
        int durationMins = AppointmentServiceCapacityUtil.resolveDurationMins(service, null);
        return AppointmentServiceCapacityUtil.resolveSlotCapacity(service, match, durationMins);
    }

    private String slotLockKey(AppointmentServiceDefinition service, Date start) {
        return "ah-" + service.getAppointmentServiceId() + "-" + start.getTime();
    }
}
```

Confirm (Booking OMOD, **same transaction**):

1. `validateAndSave` the appointment as `Scheduled` / `Confirmed`
2. `consumeHold(holdUuid, appointment)`  
   If consume throws, the whole TX rolls back — no orphan appointment.

Timeout: call `expireHold(uuid)` or ignore; occupancy already ignores `expires_at <= now()`.

Sweeper: OpenMRS task that only calls `expireDueHolds()`.

---

## Step 5 — Spring beans

In `api/src/main/resources/moduleApplicationContext.xml` (same pattern as unavailability):

```xml
<bean id="appointmentHoldDao" class="org.openmrs.module.appointments.dao.impl.AppointmentHoldDaoImpl">
    <property name="sessionFactory" ref="sessionFactory"/>
</bean>

<bean id="appointmentHoldService" class="org.springframework.transaction.interceptor.TransactionProxyFactoryBean">
    <property name="transactionManager" ref="transactionManager"/>
    <property name="target">
        <bean class="org.openmrs.module.appointments.service.impl.AppointmentHoldServiceImpl">
            <property name="appointmentHoldDao" ref="appointmentHoldDao"/>
            <property name="appointmentDao" ref="appointmentDao"/>
        </bean>
    </property>
    <property name="preInterceptors" ref="serviceInterceptors"/>
    <property name="transactionAttributeSource" ref="transactionAttributeSource"/>
</bean>

<bean parent="serviceContext">
    <property name="moduleService">
        <list merge="true">
            <value>org.openmrs.module.appointments.service.AppointmentHoldService</value>
            <ref bean="appointmentHoldService"/>
        </list>
    </property>
</bean>
```

Mirror the DAO bean in `api/src/test/resources/TestingApplicationContext.xml`.

Add global property `appointments.holdMinutes` = `7` (liquibase `insert` into `global_property`, or set in the UI).

---

## After this compiles

`AppointmentSlotAvailabilityServiceImpl.countBookedAppointments` must add:

```java
booked += appointmentHoldDao.countUnexpiredOverlapping(service, slotStart, slotEnd);
```

Until that is in, the calendar can still show a seat while a hold exists. Do that next, then Booking OMOD OTP + `POST /appointment/request`.

If you switch to Agent mode, we can add Step 0–5 in the repo first, then wire availability.

---

**User**

where to inject:
booked += appointmentHoldDao.countUnexpiredOverlapping(service, slotStart, slotEnd);

---

**Cursor**

I'll check the slot-availability service so I can point to the exact place that count should go.

Put it in **`countBookedAppointments`**, not next to line 198.

That method is the occupancy counter for both:

- **Calendar / slot list** — `buildSlotsForWindow` line 198  
- **Save validation** — `isSlotCapacityExceeded` line 100  

If you only add the hold count at line 198, the UI looks full but `AppointmentSlotCapacityValidator` can still allow another booking.

```227:233:api/src/main/java/org/openmrs/module/appointments/service/impl/AppointmentSlotAvailabilityServiceImpl.java
    private int countBookedAppointments(...) {
        ...
        return appointmentDao.countOverlappingAppointmentsForService(...);
    }
```

Change it to:

```java
private int countBookedAppointments(AppointmentServiceDefinition service, Provider provider,
        Date slotStart, Date slotEnd, String excludeAppointmentUuid) {
    List<AppointmentStatus> statuses = AppointmentServiceCapacityUtil.SLOT_BLOCKING_STATUSES;
    int booked;
    if (provider != null) {
        booked = appointmentDao.countOverlappingAppointmentsForProvider(
                provider, slotStart, slotEnd, excludeAppointmentUuid, statuses);
    } else {
        booked = appointmentDao.countOverlappingAppointmentsForService(
                service, provider, slotStart, slotEnd, excludeAppointmentUuid, statuses);
    }
    booked += appointmentHoldDao.countUnexpiredOverlapping(service, slotStart, slotEnd);
    return booked;
}
```

Then inject the DAO the same way as `appointmentUnavailabilityDao`:

1. Field + setter on `AppointmentSlotAvailabilityServiceImpl` (around lines 27–41).
2. Spring property on the slot-availability bean in:
   - `api/src/main/resources/moduleApplicationContext.xml` (around line 414)
   - `api/src/test/resources/TestingApplicationContext.xml`
   - `omod/src/test/resources/TestingApplicationContext.xml`

```xml
<property name="appointmentHoldDao" ref="appointmentHoldDao"/>
```

Leave line 198 as `countBookedAppointments(...)`. It will pick up holds automatically.

Do **not** add a second hold count in `AppointmentHoldServiceImpl.createHold` beyond what you already have there — that path uses the DAOs directly under `GET_LOCK`. This change is only so availability reads and capacity validation see the same occupancy.

---

**User**

check the code from git files, all good?

---

**Cursor**

I'll review the hold-related git changes against the wiring and occupancy paths we discussed.

Not all good yet. The hold design is in the right place, but a few wiring gaps will fail at startup, and occupancy is not fully consistent.

## What is right

- Liquibase `appointment_hold` + model + DAO + service match the hold-table approach.
- Occupancy is added inside `countBookedAppointments`, so both slot listing and `isSlotCapacityExceeded` pick it up.
- `moduleApplicationContext.xml` registers `appointmentHoldDao`, `appointmentHoldService`, and injects the DAO into the slot-availability bean.
- Consume/expire use compare-and-swap with `current_timestamp()`.

## Must fix (will not run as-is)

**1. No setter on slot availability**

You added the field and Spring property, but no `setAppointmentHoldDao`. XML injection needs the setter. Startup will fail with “no setter for property `appointmentHoldDao`”, then NPE on `countBookedAppointments`.

```java
public void setAppointmentHoldDao(AppointmentHoldDao appointmentHoldDao) {
    this.appointmentHoldDao = appointmentHoldDao;
}
```

Put it next to `setAppointmentDao` in `AppointmentSlotAvailabilityServiceImpl`.

**2. Hibernate mapping not registered**

`AppointmentHold.hbm.xml` exists but is missing from:

- `omod/src/main/resources/config.xml` (`mappingFiles`)
- `api/src/test/resources/test-hibernate.cfg.xml`
- `omod/src/test/resources/test-hibernate.cfg.xml`

Without that, Hibernate has no `AppointmentHold` entity. DAO calls will fail.

**3. Test Spring contexts not updated**

`api/src/test/resources/TestingApplicationContext.xml` and `omod/src/test/resources/TestingApplicationContext.xml` still have:

```xml
<property name="appointmentDao" ref="appointmentDao"/>
<property name="appointmentUnavailabilityDao" ref="appointmentUnavailabilityDao"/>
```

No `appointmentHoldDao` bean, and no property on the slot-availability bean. Tests that load that context will fail once you add the production setter.

## Should fix (logic)

**4. Two clocks for expiry**

`countUnexpiredOverlapping` uses `new Date()` (JVM). Consume/expire use `current_timestamp()` (DB). If those clocks differ, a hold can occupy a slot but fail confirm, or the reverse.

Use HQL `h.expiresAt > current_timestamp()` in the count query too.

**5. `createHold` does not use the same booked count as availability**

Availability, when the service has a provider, counts **provider** overlaps. `createHold` always counts **service** overlaps (`countOverlappingAppointmentsForService`). Last-seat holds can disagree with the calendar.

Use the same rule as `countBookedAppointments` (provider vs service).

**6. Capacity uses the first weekly window**

```127:132:api/src/main/java/org/openmrs/module/appointments/service/impl/AppointmentHoldServiceImpl.java
        List<ServiceWeeklyAvailability> weekly = ...
        ServiceWeeklyAvailability match = weekly.isEmpty() ? null : weekly.get(0);
```

If a day has two windows with different `maxAppointmentsPerSlot`, you may lock against the wrong capacity. Mirror `resolveWeeklyAvailabilityForAppointment` in the slot service.

## Smaller notes

- Unused import: `Session` in `AppointmentHoldDaoImpl`.
- After `consumeIfActive`, `getByUuid` can still see `HELD` (HQL bulk update does not update the persistence context). Evict/refresh, or skip the reload if callers do not need the entity.
- Liquibase has no FKs for `changed_by` / `voided_by` (unavailability does). Optional.
- No REST, sweeper task, or tests yet — fine for this slice.

**Bottom line:** do not treat this as done until (1) the setter, (2) `AppointmentHold.hbm.xml` in `config.xml` + test hibernate cfg, and (3) both test `TestingApplicationContext.xml` files are fixed. Those three are blockers.

---

**User**

give file and code for these:
2. Hibernate mapping not registered

AppointmentHold.hbm.xml exists but is missing from:

omod/src/main/resources/config.xml (mappingFiles)
api/src/test/resources/test-hibernate.cfg.xml
omod/src/test/resources/test-hibernate.cfg.xml
Without that, Hibernate has no AppointmentHold entity. DAO calls will fail.

3. Test Spring contexts not updated

api/src/test/resources/TestingApplicationContext.xml and omod/src/test/resources/TestingApplicationContext.xml still have:


<property name="appointmentDao" ref="appointmentDao"/>
<property name="appointmentUnavailabilityDao" ref="appointmentUnavailabilityDao"/>
No appointmentHoldDao bean, and no property on the slot-availability bean. Tests that load that context will fail once you add the production setter.

Should fix (logic)
4. Two clocks for expiry

countUnexpiredOverlapping uses new Date() (JVM). Consume/expire use current_timestamp() (DB). If those clocks differ, a hold can occupy a slot but fail confirm, or the reverse.

Use HQL h.expiresAt > current_timestamp() in the count query too.

5. createHold does not use the same booked count as availability

Availability, when the service has a provider, counts provider overlaps. createHold always counts service overlaps (countOverlappingAppointmentsForService). Last-seat holds can disagree with the calendar.

Use the same rule as countBookedAppointments (provider vs service).

---

**Cursor**

I'll read the current files so the snippets match what's already there.

Copy these in as written.

---

## 2. Register the Hibernate mapping

**`omod/src/main/resources/config.xml`** — add one line after `AppointmentUnavailability.hbm.xml`:

```xml
	<mappingFiles>
		Speciality.hbm.xml
		AppointmentServiceDefinition.hbm.xml
		AppointmentServiceAttributeType.hbm.xml
		AppointmentServiceAttribute.hbm.xml
		ServiceWeeklyAvailability.hbm.xml
		Appointment.hbm.xml
		AppointmentAudit.hbm.xml
		AppointmentServiceType.hbm.xml
		AppointmentProvider.hbm.xml
		AppointmentRecurringPattern.hbm.xml
		AppointmentReason.hbm.xml
		AppointmentUnavailability.hbm.xml
		AppointmentHold.hbm.xml
	</mappingFiles>
```

**`api/src/test/resources/test-hibernate.cfg.xml`** — add after the unavailability mapping:

```xml
        <mapping resource="AppointmentUnavailability.hbm.xml"/>
        <mapping resource="AppointmentHold.hbm.xml"/>
	</session-factory>
```

**`omod/src/test/resources/test-hibernate.cfg.xml`** — same:

```xml
        <mapping resource="AppointmentUnavailability.hbm.xml"/>
        <mapping resource="AppointmentHold.hbm.xml"/>
	</session-factory>
```

---

## 3. Test Spring contexts

### `api/src/test/resources/TestingApplicationContext.xml`

After `appointmentUnavailabilityDao` (~line 154), add:

```xml
    <bean id="appointmentHoldDao" class="org.openmrs.module.appointments.dao.impl.AppointmentHoldDaoImpl">
        <property name="sessionFactory" ref="sessionFactory"/>
    </bean>
```

Change the slot-availability bean (~line 284) to:

```xml
            <bean class="org.openmrs.module.appointments.service.impl.AppointmentSlotAvailabilityServiceImpl">
                <property name="appointmentServiceDao" ref="appointmentServiceDao"/>
                <property name="appointmentDao" ref="appointmentDao"/>
                <property name="appointmentHoldDao" ref="appointmentHoldDao"/>
                <property name="appointmentUnavailabilityDao" ref="appointmentUnavailabilityDao"/>
            </bean>
```

### `omod/src/test/resources/TestingApplicationContext.xml`

After `appointmentUnavailabilityDao` (~line 223), add the same `appointmentHoldDao` bean.

Change the slot-availability bean (~line 259) to:

```xml
			<bean class="org.openmrs.module.appointments.service.impl.AppointmentSlotAvailabilityServiceImpl">
				<property name="appointmentServiceDao" ref="appointmentServiceDao"/>
				<property name="appointmentDao" ref="appointmentDao"/>
				<property name="appointmentHoldDao" ref="appointmentHoldDao"/>
				<property name="appointmentUnavailabilityDao" ref="appointmentUnavailabilityDao"/>
			</bean>
```

You do **not** need `appointmentHoldService` in the test contexts unless a test calls it. Slot availability only needs the DAO.

---

## 4. Same clock for occupancy count

**File:** `api/src/main/java/org/openmrs/module/appointments/dao/impl/AppointmentHoldDaoImpl.java`

Replace `countUnexpiredOverlapping` (and drop unused `Criteria` / `new Date()` there):

```java
    @Override
    public int countUnexpiredOverlapping(AppointmentServiceDefinition service,
                                         Date slotStart,
                                         Date slotEnd) {
        Query query = sessionFactory.getCurrentSession().createQuery(
                "select count(h) from AppointmentHold h "
                        + "where h.voided = false "
                        + "and h.service.appointmentServiceId = :serviceId "
                        + "and h.status = :held "
                        + "and h.expiresAt > current_timestamp() "
                        + "and h.startDateTime < :slotEnd "
                        + "and h.endDateTime > :slotStart");
        query.setParameter("serviceId", service.getAppointmentServiceId());
        query.setParameter("held", AppointmentHoldStatus.HELD);
        query.setParameter("slotEnd", slotEnd);
        query.setParameter("slotStart", slotStart);
        Number count = (Number) query.uniqueResult();
        return count != null ? count.intValue() : 0;
    }
```

You can then remove unused imports: `Criteria`, `Session`, `Projections`, `Restrictions`. Keep `Query`, `Date`.

---

## 5. Same booked-count rule as availability

**File:** `api/src/main/java/org/openmrs/module/appointments/service/impl/AppointmentHoldServiceImpl.java`

Replace the `countOverlappingAppointmentsForService` block in `createHold` (~lines 59–65) with:

```java
            org.openmrs.Provider provider = hold.getService().getProvider();
            int bookedAppointments;
            if (provider != null) {
                bookedAppointments = appointmentDao.countOverlappingAppointmentsForProvider(
                        provider,
                        hold.getStartDateTime(),
                        hold.getEndDateTime(),
                        null,
                        AppointmentServiceCapacityUtil.SLOT_BLOCKING_STATUSES);
            } else {
                bookedAppointments = appointmentDao.countOverlappingAppointmentsForService(
                        hold.getService(),
                        provider,
                        hold.getStartDateTime(),
                        hold.getEndDateTime(),
                        null,
                        AppointmentServiceCapacityUtil.SLOT_BLOCKING_STATUSES);
            }
```

Add:

```java
import org.openmrs.Provider;
```

That matches `countBookedAppointments` in `AppointmentSlotAvailabilityServiceImpl`.

---

Confirm you also have `setAppointmentHoldDao` on `AppointmentSlotAvailabilityServiceImpl` (item 1). Without that, the XML property in step 3 will still fail.

---

**User**

check now, i guess i missed these:
After appointmentUnavailabilityDao (~line 223), add the same appointmentHoldDao bean.

---

**Cursor**

I'll check the omod test context and the other wiring files you just changed.

Yes — that is the one still missing.

In `omod/src/test/resources/TestingApplicationContext.xml` you wired the **property** on the slot-availability bean:

```262:262:omod/src/test/resources/TestingApplicationContext.xml
				<property name="appointmentHoldDao" ref="appointmentHoldDao"/>
```

There is **no** `appointmentHoldDao` bean. After `appointmentUnavailabilityDao` (~line 223) it still goes straight to `appointmentUnavailabilityService`. OMOD tests will fail with `No bean named 'appointmentHoldDao'`.

The API test context already has it:

```156:158:api/src/test/resources/TestingApplicationContext.xml
    <bean id="appointmentHoldDao" class="org.openmrs.module.appointments.dao.impl.AppointmentHoldDaoImpl">
        <property name="sessionFactory" ref="sessionFactory"/>
    </bean>
```

Add the same block in the omod file, after `appointmentUnavailabilityDao`:

```xml
    <bean id="appointmentUnavailabilityDao" class="org.openmrs.module.appointments.dao.impl.AppointmentUnavailabilityDaoImpl">
        <property name="sessionFactory">
            <ref bean="sessionFactory"/>
        </property>
    </bean>

    <bean id="appointmentHoldDao" class="org.openmrs.module.appointments.dao.impl.AppointmentHoldDaoImpl">
        <property name="sessionFactory" ref="sessionFactory"/>
    </bean>

    <bean id="appointmentUnavailabilityService" class="org.springframework.transaction.interceptor.TransactionProxyFactoryBean">
```

Everything else from the last pass looks in place: setter, `config.xml` mapping, both `test-hibernate.cfg.xml`, API test bean + property, HQL `current_timestamp()`, provider vs service count in `createHold`.

Optional cleanup: `AppointmentHoldDaoImpl` still imports unused `Session`, `Projections`, `Restrictions`.

---

**User**

wot is these?
<bean id="appointmentUnavailabilityDao" class="org.openmrs.module.appointments.dao.impl.AppointmentUnavailabilityDaoImpl">
    <property name="sessionFactory">
        <ref bean="sessionFactory"/>
    </property>
</bean>

<bean id="appointmentHoldDao" class="org.openmrs.module.appointments.dao.impl.AppointmentHoldDaoImpl">
    <property name="sessionFactory" ref="sessionFactory"/>
</bean>

<bean id="appointmentUnavailabilityService" class="org.springframework.transaction.interceptor.TransactionProxyFactoryBean">

---

**Cursor**

That snippet is **Spring wiring**: it tells the test (and the app) which Java class to use for each name, and what to inject into it.

It is **not** appointment-hold business logic. Unavailability is already there; you only add the middle bean.

## 1. `appointmentUnavailabilityDao` (already in the file)

```xml
<bean id="appointmentUnavailabilityDao" class="...AppointmentUnavailabilityDaoImpl">
    <property name="sessionFactory">
        <ref bean="sessionFactory"/>
    </property>
</bean>
```

- **id** — name other beans use: `ref="appointmentUnavailabilityDao"`.
- **class** — Hibernate DAO that reads/writes `appointment_unavailability`.
- **sessionFactory** — Hibernate session, injected via `setSessionFactory(...)`.

This bean was already in the omod test context. Do not replace it.

## 2. `appointmentHoldDao` (the one you still need)

```xml
<bean id="appointmentHoldDao" class="...AppointmentHoldDaoImpl">
    <property name="sessionFactory" ref="sessionFactory"/>
</bean>
```

Same idea for holds: create `AppointmentHoldDaoImpl` and give it Hibernate.

The slot-availability bean already has:

```xml
<property name="appointmentHoldDao" ref="appointmentHoldDao"/>
```

That `ref` only works if a bean with **id** `appointmentHoldDao` exists. Without this block, Spring says there is no bean named `appointmentHoldDao`.

The two `sessionFactory` forms are equivalent:

```xml
<property name="sessionFactory">
    <ref bean="sessionFactory"/>
</property>
```

```xml
<property name="sessionFactory" ref="sessionFactory"/>
```

Use either. The API test file already uses the short form.

## 3. `appointmentUnavailabilityService` (already there)

That is the next bean **after** the DAOs. I included its opening tag only so you can see **where** to paste `appointmentHoldDao`: between the unavailability DAO and the unavailability **service**. Do not paste a new empty service bean.

So the omod file should look like:

1. unavailability DAO (existing)  
2. **hold DAO (add this)**  
3. unavailability service (existing, unchanged)

`AppointmentHoldDaoImpl` needs a Hibernate `SessionFactory` the same way the unavailability DAO does. This XML is how tests (and production `moduleApplicationContext.xml`) provide it.

---

**User**

wot is the final code?

---

**Cursor**

In **`omod/src/test/resources/TestingApplicationContext.xml`**, insert the hold DAO between the two existing beans. Do not change the unavailability DAO or service.

Final block:

```xml
    <bean id="appointmentUnavailabilityDao" class="org.openmrs.module.appointments.dao.impl.AppointmentUnavailabilityDaoImpl">
        <property name="sessionFactory">
            <ref bean="sessionFactory"/>
        </property>
    </bean>

    <bean id="appointmentHoldDao" class="org.openmrs.module.appointments.dao.impl.AppointmentHoldDaoImpl">
        <property name="sessionFactory" ref="sessionFactory"/>
    </bean>

    <bean id="appointmentUnavailabilityService" class="org.springframework.transaction.interceptor.TransactionProxyFactoryBean">
        <property name="transactionManager">
            <ref bean="transactionManager"/>
        </property>
        <property name="target">
            <bean class="org.openmrs.module.appointments.service.impl.AppointmentUnavailabilityServiceImpl">
                <constructor-arg ref="appointmentUnavailabilityDao"/>
                <constructor-arg ref="appointmentServiceService"/>
            </bean>
        </property>
        <property name="preInterceptors">
            <ref bean="serviceInterceptors"/>
        </property>
        <property name="transactionAttributeSource">
            <ref bean="transactionAttributeSource"/>
        </property>
    </bean>
```

Only the middle bean is new. Leave the `appointmentHoldDao` property on `appointmentSlotAvailabilityService` as it is.

The API test file already has this DAO bean; you do not need to add it there again.

---

**User**

@api/src/test/resources/TestingApplicationContext.xml already fine right?

---

**Cursor**

Yes. **`api/src/test/resources/TestingApplicationContext.xml` is already fine.** Leave it alone.

It has both pieces:

1. The DAO bean (lines 156–158)
2. The slot-availability property `appointmentHoldDao` (line 291)

The file that still needs the bean is **`omod/src/test/resources/TestingApplicationContext.xml`**: it references `appointmentHoldDao` but does not define it.

---

**User**

now check the git files

---

**Cursor**

Almost. Production and API tests look wired. One test context is still broken.

## Still missing

**`omod/src/test/resources/TestingApplicationContext.xml`**  
You added the property:

```xml
<property name="appointmentHoldDao" ref="appointmentHoldDao"/>
```

There is still **no** bean:

```xml
<bean id="appointmentHoldDao" class="org.openmrs.module.appointments.dao.impl.AppointmentHoldDaoImpl">
    <property name="sessionFactory" ref="sessionFactory"/>
</bean>
```

OMOD tests that load this context will fail with `No bean named 'appointmentHoldDao'`. Put that bean after `appointmentUnavailabilityDao`, same as in the API test file.

`api/src/test/resources/TestingApplicationContext.xml` is already correct.

## What looks good now

- Setter on `AppointmentSlotAvailabilityServiceImpl`
- Holds counted inside `countBookedAppointments`
- `AppointmentHold.hbm.xml` in `config.xml` and both `test-hibernate.cfg.xml`
- Production `moduleApplicationContext.xml` (DAO + service + slot-availability inject)
- Liquibase table
- Untracked Java: DAO, model, service; HQL `current_timestamp()`; provider vs service count in `createHold`

## Small leftovers (not blockers)

- Unused imports in `AppointmentHoldDaoImpl`: `Session`, `Projections`, `Restrictions`
- `resolveCapacity` still uses `weekly.get(0)` (wrong window if a day has two)
- No tests / REST / sweeper yet (expected for this slice)

**Do this next:** add the `appointmentHoldDao` bean in the omod test context. After that, this layer is in good shape to commit when you want. Remember the 7 new untracked files need `git add` too.

---

**User**

why @api/src/main/java/org/openmrs/module/appointments/dao/impl/AppointmentHoldDaoImpl.java is having raw sql queries? cant we use Criteria builder like @api/src/main/java/org/openmrs/module/appointments/dao/impl/AppointmentDaoImpl.java ?

---

**Cursor**

Most of that file is **not** raw SQL. `AppointmentDaoImpl` also cannot do everything this DAO needs with Criteria.

## What is actually in `AppointmentHoldDaoImpl`

| Method | API | Why |
|---|---|---|
| `getByUuid` | Hibernate **Criteria** (same as `AppointmentDaoImpl`) | Simple select |
| `countUnexpiredOverlapping` | **HQL** (`createQuery`) | Need DB `current_timestamp()`, not `new Date()` |
| `consumeIfActive` / `expireIfDue` / `expireAllDue` | **HQL UPDATE** | Atomic `UPDATE … WHERE status = HELD AND expires_at > now()` |
| `acquireSlotLock` / `releaseSlotLock` | **Native SQL** (`createSQLQuery`) | MySQL `GET_LOCK` / `RELEASE_LOCK` — not an entity |

`createQuery(...)` is Hibernate HQL (entity names, parameters). Raw SQL is only `GET_LOCK`.

`AppointmentDaoImpl` uses the old Hibernate `Criteria` API (`Restrictions`, `Projections`), not JPA `CriteriaBuilder`. `AppointmentUnavailabilityDaoImpl` is the one on JPA CriteriaBuilder.

## Why Criteria is a poor fit here

**1. Count** — you *can* write this like `countOverlappingAppointmentsForService`, except expiry must use **database** time. Criteria’s natural form is:

```java
criteria.add(Restrictions.gt("expiresAt", new Date())); // JVM clock — we rejected this
```

Hibernate Criteria has no `current_timestamp()` helper. You either stay on HQL, or cheat with:

```java
criteria.add(Restrictions.sqlRestriction("expires_at > current_timestamp()"));
```

That is still SQL, just hidden inside Criteria.

**2. Consume / expire** — Criteria is for **SELECT**. `AppointmentDaoImpl` never bulk-updates rows with a condition.

Confirm must be one statement:

```sql
UPDATE appointment_hold
SET status = 'CONSUMED'
WHERE uuid = ? AND status = 'HELD' AND expires_at > now()
```

0 rows = expired or already consumed. Two threads cannot both win.

If you Criteria-load the hold, check expiry in Java, then `save()`, you lose that unless you add `LockMode.PESSIMISTIC_WRITE`. HQL `executeUpdate() == 1` is the CAS.

JPA `CriteriaUpdate` can do the same update, but it is not what `AppointmentDaoImpl` uses, and it is more verbose than the HQL you have.

**3. `GET_LOCK`** — must stay native SQL. There is no Criteria for MySQL advisory locks.

## What to change vs keep

- **Keep HQL** for `consumeIfActive`, `expireIfDue`, `expireAllDue`. That is the right tool.
- **Keep SQL** for `GET_LOCK` / `RELEASE_LOCK`.
- **Optional:** rewrite `countUnexpiredOverlapping` in Criteria to match `AppointmentDaoImpl`, using `sqlRestriction("expires_at > current_timestamp()")` so the clock stays the DB.

Example Criteria count (same behaviour as the HQL):

```java
Criteria criteria = sessionFactory.getCurrentSession()
        .createCriteria(AppointmentHold.class, "hold");
criteria.createAlias("service", "service");
criteria.add(Restrictions.eq("voided", false));
criteria.add(Restrictions.eq("service.appointmentServiceId", service.getAppointmentServiceId()));
criteria.add(Restrictions.eq("status", AppointmentHoldStatus.HELD));
criteria.add(Restrictions.sqlRestriction("expires_at > current_timestamp()"));
criteria.add(Restrictions.lt("startDateTime", slotEnd));
criteria.add(Restrictions.gt("endDateTime", slotStart));
criteria.setProjection(Projections.rowCount());
Number count = (Number) criteria.uniqueResult();
return count != null ? count.intValue() : 0;
```

Do not convert consume/expire to load-and-save Criteria. That is how last-seat and 7-minute races come back.

---

**User**

ok, we can hold on.
explain abt each query, what and how is it needed, and works

---

**Cursor**

These methods are the hold lifecycle. They are used in this order:

```
request OTP  →  GET_LOCK → count → save HELD → RELEASE_LOCK
confirm OTP  →  consumeIfActive (HELD → CONSUMED)
timeout      →  expireIfDue / expireAllDue  (or just ignore: count already skips expired)
```

---

## 1. `save`

```java
session.saveOrUpdate(hold);
```

**What:** Insert or update one `appointment_hold` row.

**Why:** After capacity is free, persist `status=HELD` and `expires_at = now + 7 minutes`.

**How:** Normal Hibernate. OpenMRS interceptors typically set `uuid`, `creator`, `dateCreated`. Not a custom query.

---

## 2. `getByUuid`

```java
criteria.add(Restrictions.eq("uuid", uuid));
```

**What:** Load one hold by UUID.

**Why:** After confirm, return the row (and later for Booking OMOD / debugging).

**How:** Same style as `AppointmentDaoImpl`. `uniqueResult()` is one row or `null`.

---

## 3. `countUnexpiredOverlapping`

```hql
select count(h) from AppointmentHold h
where h.voided = false
  and h.service.appointmentServiceId = :serviceId
  and h.status = :held
  and h.expiresAt > current_timestamp()
  and h.startDateTime < :slotEnd
  and h.endDateTime > :slotStart
```

**What:** How many **live** holds sit on this slot.

**Why:** Occupancy = appointments + holds. Used in:

- `createHold` (is the last seat taken?)
- `countBookedAppointments` (calendar `available` count)

Without this, someone can hold the last seat and the UI still shows it as free.

**How the filters work:**

| Clause | Meaning |
|---|---|
| `voided = false` | Ignore soft-deleted rows |
| `serviceId` | This appointment service only |
| `status = HELD` | Consumed/expired holds must not occupy |
| `expiresAt > current_timestamp()` | Past 7 minutes → not counted, even if still `HELD`. DB clock, same as consume/expire |
| `start < slotEnd AND end > slotStart` | Interval overlap (same as appointment overlap in `AppointmentDaoImpl`) |

Example: slot 10:00–10:15. A hold 10:00–10:15 matches. A hold that expired at 9:58 does not, even if status is still `HELD`.

---

## 4. `consumeIfActive` — confirm OTP

```hql
update AppointmentHold h
set h.status = CONSUMED, h.appointment = :appointment
where h.uuid = :uuid
  and h.status = HELD
  and h.expiresAt > current_timestamp()
```

Returns `true` only if **exactly 1 row** was updated.

**What:** Turn this hold into a real booking, in one shot.

**Why:** Confirm must not succeed if:

- the 7 minutes already passed, or
- another request already consumed it, or
- a sweeper already expired it

**How (compare-and-swap):** The `WHERE` is the lock. Two confirms at once: only one gets `executeUpdate() == 1`. The other gets 0 → service throws “hold expired or no longer active”.

Linking `appointment` stores which `patient_appointment` this hold became.

If this is 0, `consumeHold` then calls `expireIfDue` to mark the row `EXPIRED` when time is up.

---

## 5. `expireIfDue` — one hold

```hql
update AppointmentHold h
set h.status = EXPIRED
where h.uuid = :uuid
  and h.status = HELD
  and h.expiresAt <= current_timestamp()
```

**What:** Mark **this** hold expired, only if it is still `HELD` and time is up.

**Why:**

- User abandons OTP / max retries
- Confirm failed because the window closed

**How:** Same CAS idea. If the user confirmed a millisecond earlier (`CONSUMED`), this updates **0 rows**. You cannot expire a consumed hold.

Occupancy does **not** wait for this. `countUnexpiredOverlapping` already ignores `expires_at <= now()`. This is bookkeeping so the row is not stuck as `HELD`.

---

## 6. `expireAllDue` — sweeper

```hql
update AppointmentHold h
set h.status = EXPIRED
where h.status = HELD
  and h.expiresAt <= current_timestamp()
```

**What:** Same as (5), for every overdue `HELD` row. Returns how many flipped.

**Why:** Hygiene. Without a job, old rows stay `HELD` forever even though they no longer count as booked.

**How:** Call from an OpenMRS scheduled task (`expireDueHolds()`). Not what frees the slot; the count query already did that at expiry time.

---

## 7. `acquireSlotLock` / `releaseSlotLock`

```sql
SELECT GET_LOCK(:lockKey, :timeout)
SELECT RELEASE_LOCK(:lockKey)
```

**What:** MySQL named lock for one slot, e.g. `ah-{serviceId}-{startMillis}`.

**Why:** Count + insert is two steps. Without a lock:

1. Both users see `booked = 1`, `capacity = 2`
2. Both insert holds
3. Slot is oversold

`GET_LOCK` makes only one `createHold` for that slot run the count+save at a time.

**How:**

- `GET_LOCK(name, 5)` waits up to 5 seconds. `1` = you have it, `0` = timeout → “Please retry.”
- Lock is per **DB connection**. `createHold` holds it in `try`, always `RELEASE_LOCK` in `finally`.
- Different slots use different keys, so they do not block each other.

This is the only **native SQL**. Hibernate has no entity for MySQL advisory locks.

---

## How they fit together

**Create hold (last seat, two users):**

```
User A                         User B
GET_LOCK(slot) ─────────────►  waits
count appts+holds = 1/2
save HELD
RELEASE_LOCK ───────────────►  GET_LOCK
                               count = 2/2 → reject
```

**Confirm vs 7-minute expiry:**

```
T+6:59  consumeIfActive  WHERE expires_at > now  → 1 row → CONSUMED
T+7:01  consumeIfActive  → 0 rows → fail
        expireIfDue      WHERE expires_at <= now → EXPIRED
```

**Calendar while someone is in OTP:**

`countBookedAppointments` = overlapping appointments + `countUnexpiredOverlapping`. Available seats drop immediately; they come back when `expires_at` passes, even before the sweeper runs.

---

**User**

expose api to lock the slot.

---

**Cursor**

I'll look at how this module's REST controllers are structured so the lock-slot API matches them.

I'm in **Ask mode**, so this is the code to add — I can't write it into the repo. Switch to Agent mode if you want it applied.

This module should only **lock the slot**. OTP stays in Booking OMOD: it calls this API, then sends SMS.

Existing `@Controller`s are component-scanned; no extra XML.

---

## Endpoint

`POST /openmrs/ws/rest/v1/appointmentHold`

```json
{
  "patientUuid": "patient-uuid",
  "serviceUuid": "service-uuid",
  "startDateTime": "2026-09-10T10:00:00.000+0530",
  "endDateTime": "2026-09-10T10:15:00.000+0530"
}
```

`endDateTime` is optional; `createHold` fills it from service duration.

**201:**

```json
{
  "uuid": "hold-uuid",
  "status": "HELD",
  "patientUuid": "...",
  "serviceUuid": "...",
  "startDateTime": "...",
  "endDateTime": "...",
  "expiresAt": "...",
  "expiresInSeconds": 420
}
```

**400** if the slot is full or the lock cannot be taken.

---

## 1. Request — `omod/.../web/contract/AppointmentHoldRequest.java`

```java
package org.openmrs.module.appointments.web.contract;

import java.util.Date;

public class AppointmentHoldRequest {
    private String patientUuid;
    private String serviceUuid;
    private Date startDateTime;
    private Date endDateTime;

    public String getPatientUuid() { return patientUuid; }
    public void setPatientUuid(String patientUuid) { this.patientUuid = patientUuid; }
    public String getServiceUuid() { return serviceUuid; }
    public void setServiceUuid(String serviceUuid) { this.serviceUuid = serviceUuid; }
    public Date getStartDateTime() { return startDateTime; }
    public void setStartDateTime(Date startDateTime) { this.startDateTime = startDateTime; }
    public Date getEndDateTime() { return endDateTime; }
    public void setEndDateTime(Date endDateTime) { this.endDateTime = endDateTime; }
}
```

## 2. Response — `omod/.../web/contract/AppointmentHoldResponse.java`

```java
package org.openmrs.module.appointments.web.contract;

import java.util.Date;

public class AppointmentHoldResponse {
    private String uuid;
    private String status;
    private String patientUuid;
    private String serviceUuid;
    private Date startDateTime;
    private Date endDateTime;
    private Date expiresAt;
    private long expiresInSeconds;

    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPatientUuid() { return patientUuid; }
    public void setPatientUuid(String patientUuid) { this.patientUuid = patientUuid; }
    public String getServiceUuid() { return serviceUuid; }
    public void setServiceUuid(String serviceUuid) { this.serviceUuid = serviceUuid; }
    public Date getStartDateTime() { return startDateTime; }
    public void setStartDateTime(Date startDateTime) { this.startDateTime = startDateTime; }
    public Date getEndDateTime() { return endDateTime; }
    public void setEndDateTime(Date endDateTime) { this.endDateTime = endDateTime; }
    public Date getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Date expiresAt) { this.expiresAt = expiresAt; }
    public long getExpiresInSeconds() { return expiresInSeconds; }
    public void setExpiresInSeconds(long expiresInSeconds) { this.expiresInSeconds = expiresInSeconds; }
}
```

## 3. Mapper — `omod/.../web/mapper/AppointmentHoldMapper.java`

```java
package org.openmrs.module.appointments.web.mapper;

import org.apache.commons.lang.StringUtils;
import org.openmrs.Patient;
import org.openmrs.api.APIException;
import org.openmrs.api.PatientService;
import org.openmrs.module.appointments.model.AppointmentHold;
import org.openmrs.module.appointments.model.AppointmentServiceDefinition;
import org.openmrs.module.appointments.service.AppointmentServiceDefinitionService;
import org.openmrs.module.appointments.web.contract.AppointmentHoldRequest;
import org.openmrs.module.appointments.web.contract.AppointmentHoldResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AppointmentHoldMapper {

    @Autowired
    private PatientService patientService;

    @Autowired
    private AppointmentServiceDefinitionService appointmentServiceDefinitionService;

    public AppointmentHold fromRequest(AppointmentHoldRequest request) {
        if (request == null || StringUtils.isBlank(request.getPatientUuid())
                || StringUtils.isBlank(request.getServiceUuid())
                || request.getStartDateTime() == null) {
            throw new APIException("patientUuid, serviceUuid and startDateTime are required");
        }

        Patient patient = patientService.getPatientByUuid(request.getPatientUuid());
        if (patient == null) {
            throw new APIException("Patient not found");
        }
        AppointmentServiceDefinition service = appointmentServiceDefinitionService
                .getAppointmentServiceByUuid(request.getServiceUuid());
        if (service == null || Boolean.TRUE.equals(service.getVoided())) {
            throw new APIException("Appointment service not found");
        }

        AppointmentHold hold = new AppointmentHold();
        hold.setPatient(patient);
        hold.setService(service);
        hold.setStartDateTime(request.getStartDateTime());
        hold.setEndDateTime(request.getEndDateTime());
        return hold;
    }

    public AppointmentHoldResponse constructResponse(AppointmentHold hold) {
        AppointmentHoldResponse response = new AppointmentHoldResponse();
        response.setUuid(hold.getUuid());
        response.setStatus(hold.getStatus() != null ? hold.getStatus().name() : null);
        response.setPatientUuid(hold.getPatient() != null ? hold.getPatient().getUuid() : null);
        response.setServiceUuid(hold.getService() != null ? hold.getService().getUuid() : null);
        response.setStartDateTime(hold.getStartDateTime());
        response.setEndDateTime(hold.getEndDateTime());
        response.setExpiresAt(hold.getExpiresAt());
        if (hold.getExpiresAt() != null) {
            long seconds = (hold.getExpiresAt().getTime() - System.currentTimeMillis()) / 1000L;
            response.setExpiresInSeconds(Math.max(seconds, 0));
        }
        return response;
    }
}
```

## 4. Controller — `omod/.../web/controller/AppointmentHoldController.java`

```java
package org.openmrs.module.appointments.web.controller;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.module.appointments.model.AppointmentHold;
import org.openmrs.module.appointments.service.AppointmentHoldService;
import org.openmrs.module.appointments.web.contract.AppointmentHoldRequest;
import org.openmrs.module.appointments.web.contract.AppointmentHoldResponse;
import org.openmrs.module.appointments.web.mapper.AppointmentHoldMapper;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.RestUtil;
import org.openmrs.module.webservices.rest.web.v1_0.controller.BaseRestController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping(value = "/rest/" + RestConstants.VERSION_1 + "/appointmentHold")
public class AppointmentHoldController extends BaseRestController {

    private final Log log = LogFactory.getLog(getClass());

    @Autowired
    private AppointmentHoldService appointmentHoldService;

    @Autowired
    private AppointmentHoldMapper appointmentHoldMapper;

    @RequestMapping(method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<Object> lockSlot(@RequestBody AppointmentHoldRequest request) {
        try {
            AppointmentHold hold = appointmentHoldMapper.fromRequest(request);
            AppointmentHold saved = appointmentHoldService.createHold(hold);
            return new ResponseEntity<>(appointmentHoldMapper.constructResponse(saved), HttpStatus.CREATED);
        } catch (RuntimeException e) {
            log.error("Failed to lock appointment slot", e);
            return new ResponseEntity<>(RestUtil.wrapErrorResponse(e, e.getMessage()), HttpStatus.BAD_REQUEST);
        }
    }

    @RequestMapping(method = RequestMethod.GET, value = "/{uuid}")
    @ResponseBody
    public ResponseEntity<Object> getHold(@PathVariable("uuid") String uuid) {
        AppointmentHold hold = appointmentHoldService.getByUuid(uuid);
        if (hold == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(appointmentHoldMapper.constructResponse(hold), HttpStatus.OK);
    }
}
```

Caller needs **Manage Appointments** (`createHold` is `@Authorized`). Booking OMOD should use a service user with that privilege.

---

## How Booking OMOD uses it

```
FE  POST /appointment/request
      → Gateway
      → Booking OMOD
           POST /openmrs/ws/rest/v1/appointmentHold   ← this API
           send OTP
           return { holdUuid, maskedPhone, expiresIn }
FE  POST /appointment/confirm { holdUuid, otp }
      → Booking verifies OTP
      → save appointment + consumeHold(holdUuid, appointment)
```

Do **not** put OTP on this controller.

---

## Later (not required to lock)

| Method | Use |
|---|---|
| `GET /appointmentHold/{uuid}` | Already above |
| `DELETE /appointmentHold/{uuid}` | User cancels OTP — needs a new CAS `HELD → FAILED` **without** waiting for `expires_at` (`expireIfDue` only works after the window) |

Also still add the `appointmentHoldDao` bean in `omod/src/test/resources/TestingApplicationContext.xml` if that is missing, or OMOD tests will fail when they hit slot availability.

---

**User**

check the git files for controller.

---

**Cursor**

The controller layer matches the module’s REST style and is wired correctly. A few nits, nothing that blocks the lock API.

## What is there

Untracked (need `git add`):

- `AppointmentHoldController.java`
- `AppointmentHoldMapper.java`
- `AppointmentHoldRequest.java`
- `AppointmentHoldResponse.java`

`webModuleApplicationContext.xml` already scans `org.openmrs.module.appointments.web`, so `@Controller` / `@Component` will load. `AppointmentHoldService` is already a Spring bean in `moduleApplicationContext.xml`.

| Piece | Verdict |
|---|---|
| `POST /rest/v1/appointmentHold` | Correct: map → `createHold` → `201` |
| Errors | Same as `AppointmentController`: `RestUtil.wrapErrorResponse`, `400` |
| Mapper | Resolves patient/service; rejects missing fields / unknown uuids |
| Response | `uuid`, `status`, slot, `expiresAt`, `expiresInSeconds` |
| `GET /{uuid}` | Fine for inspecting a hold |

No extra XML is required for the controller.

## Small issues (not blockers)

**GET vs POST errors.** `lockSlot` catches `RuntimeException`. `getHold` does not, so a privilege failure (`VIEW_APPOINTMENTS`) can become a 500 instead of 400/403. Same as several other GETs here; optional to wrap.

**Auth on POST.** `createHold` is `@Authorized(MANAGE_APPOINTMENTS)`. Unauthenticated or under-privileged callers get an auth exception, which you currently return as **400**, not 401/403. Same as save-appointment.

**GET of an expired hold.** You still return the row; `expiresInSeconds` is `0`. That is reasonable.

**No `DELETE`.** User-cancel before 7 minutes still needs `HELD → FAILED` without waiting for `expires_at`. Not required for lock.

## Still missing outside the controller

`omod/src/test/resources/TestingApplicationContext.xml` still has `ref="appointmentHoldDao"` **without** a bean named `appointmentHoldDao`. OMOD tests that load slot availability will fail.

## Call it

```http
POST /openmrs/ws/rest/v1/appointmentHold
Content-Type: application/json

{
  "patientUuid": "...",
  "serviceUuid": "...",
  "startDateTime": "2026-09-10T10:00:00.000+0530",
  "endDateTime": "2026-09-10T10:15:00.000+0530"
}
```

Session must have **Manage Appointments**.

**Controller: good to go.** Add the omod test DAO bean, then `git add` the four new web files plus the untracked API hold files.

---

**User**

if any issue means, give code to solve.

---

**Cursor**

One real break remains. The rest of the controller is fine.

## Must fix: missing test bean

**File:** `omod/src/test/resources/TestingApplicationContext.xml`

Line 262 already has `ref="appointmentHoldDao"`, but there is **no bean** with that id. Insert this **between** `appointmentUnavailabilityDao` and `appointmentUnavailabilityService`:

```xml
    <bean id="appointmentUnavailabilityDao" class="org.openmrs.module.appointments.dao.impl.AppointmentUnavailabilityDaoImpl">
        <property name="sessionFactory">
            <ref bean="sessionFactory"/>
        </property>
    </bean>

    <bean id="appointmentHoldDao" class="org.openmrs.module.appointments.dao.impl.AppointmentHoldDaoImpl">
        <property name="sessionFactory" ref="sessionFactory"/>
    </bean>

    <bean id="appointmentUnavailabilityService" class="org.springframework.transaction.interceptor.TransactionProxyFactoryBean">
```

Do not change the slot-availability property. Do not touch `api/src/test/resources/TestingApplicationContext.xml` (already has this bean).

---

## Optional: unused imports

**File:** `api/src/main/java/org/openmrs/module/appointments/dao/impl/AppointmentHoldDaoImpl.java`

Replace the imports with:

```java
import org.hibernate.Criteria;
import org.hibernate.Query;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Restrictions;
import org.openmrs.module.appointments.dao.AppointmentHoldDao;
import org.openmrs.module.appointments.model.Appointment;
import org.openmrs.module.appointments.model.AppointmentHold;
import org.openmrs.module.appointments.model.AppointmentHoldStatus;
import org.openmrs.module.appointments.model.AppointmentServiceDefinition;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
```

Remove unused `Session`, `Projections`. Keep `Criteria` and `Restrictions` for `getByUuid`.

---

That is all that is required for the lock API to start and for OMOD tests to wire. `DELETE` (cancel before 7 minutes) is a new feature, not a bug in what you have.

---

**User**

@omod/src/test/resources/TestingApplicationContext.xml:256-268 how to change wot u expect? i dont know

---

**Cursor**

**Do not change lines 256–268.** That block is already correct. `ref="appointmentHoldDao"` is the consumer.

What is missing is the **bean it points at**. Spring looks for `id="appointmentHoldDao"` and does not find it.

Add it **above**, after `appointmentUnavailabilityDao` (after line 223).

**Leave this as-is (256–268):**

```xml
	<bean id="appointmentSlotAvailabilityService" class="org.springframework.transaction.interceptor.TransactionProxyFactoryBean">
		<property name="transactionManager" ref="transactionManager"/>
		<property name="target">
			<bean class="org.openmrs.module.appointments.service.impl.AppointmentSlotAvailabilityServiceImpl">
				<property name="appointmentServiceDao" ref="appointmentServiceDao"/>
				<property name="appointmentDao" ref="appointmentDao"/>
				<property name="appointmentHoldDao" ref="appointmentHoldDao"/>
				<property name="appointmentUnavailabilityDao" ref="appointmentUnavailabilityDao"/>
			</bean>
		</property>
		<property name="preInterceptors" ref="serviceInterceptors"/>
		<property name="transactionAttributeSource" ref="transactionAttributeSource"/>
	</bean>
```

**Insert only this after line 223:**

```xml
    <bean id="appointmentUnavailabilityDao" class="org.openmrs.module.appointments.dao.impl.AppointmentUnavailabilityDaoImpl">
        <property name="sessionFactory">
            <ref bean="sessionFactory"/>
        </property>
    </bean>

    <bean id="appointmentHoldDao" class="org.openmrs.module.appointments.dao.impl.AppointmentHoldDaoImpl">
        <property name="sessionFactory" ref="sessionFactory"/>
    </bean>

    <bean id="appointmentUnavailabilityService" class="org.springframework.transaction.interceptor.TransactionProxyFactoryBean">
```

So: keep the existing unavailability DAO, **paste the 3-line `appointmentHoldDao` bean**, then the existing unavailability service stays next.

`ref` = “use that bean”. `id` = “this is the bean”. You already have the `ref`; you still need the `id`.

---

**User**

still am confused, u r in agent mode. do changes wot u want in @api/src/test/resources/TestingApplicationContext.xml 

---

**Cursor**

I'll check both test Spring files and apply the missing wiring. The API file may already be complete; the omod one was the one missing the bean.

The API file already has the DAO bean. I'll add `appointmentHoldService` there to match production, and add the missing DAO bean in the omod test context — that's the one that was actually broken.

Two files were updated.

**`api/src/test/resources/TestingApplicationContext.xml`** already had the `appointmentHoldDao` bean (lines 156–158) and the `appointmentHoldDao` property on slot availability. I did **not** change that. I only added `appointmentHoldService` (and registered it with `serviceContext`) so API tests can call `AppointmentHoldService` the same way production does.

**`omod/src/test/resources/TestingApplicationContext.xml`** was the broken one. Line 262 already had `ref="appointmentHoldDao"` — that stays as-is. I inserted the missing bean **above** it:

```xml
<bean id="appointmentHoldDao" class="org.openmrs.module.appointments.dao.impl.AppointmentHoldDaoImpl">
    <property name="sessionFactory" ref="sessionFactory"/>
</bean>
```

`ref` means “use this bean.” `id` is the bean itself. The API file already had both; the omod file only had the `ref`.

---

**User**

ok, done.
for api. in fe, i need.
give payload and response in ts, with react query + mutation

---

**Cursor**

Lock API is `POST /ws/rest/v1/appointmentHold`. Session must include **Manage Appointments**. OTP is not on this endpoint.

## Types

```ts
export type AppointmentHoldStatus = 'HELD' | 'CONSUMED' | 'EXPIRED' | 'FAILED';

export interface AppointmentHoldPayload {
  patientUuid: string;
  serviceUuid: string;
  /** ISO-8601, e.g. 2026-09-10T10:00:00.000+0530 */
  startDateTime: string;
  endDateTime?: string;
}

export interface AppointmentHoldResponse {
  uuid: string;
  status: AppointmentHoldStatus;
  patientUuid: string;
  serviceUuid: string;
  startDateTime: string;
  endDateTime: string;
  expiresAt: string;
  expiresInSeconds: number;
}
```

Example payload:

```ts
const payload: AppointmentHoldPayload = {
  patientUuid: 'patient-uuid',
  serviceUuid: 'service-uuid',
  startDateTime: '2026-09-10T10:00:00.000+0530',
  endDateTime: '2026-09-10T10:15:00.000+0530', // optional
};
```

Example 201 body:

```ts
{
  uuid: 'hold-uuid',
  status: 'HELD',
  patientUuid: 'patient-uuid',
  serviceUuid: 'service-uuid',
  startDateTime: '2026-09-10T04:30:00.000+0000',
  endDateTime: '2026-09-10T04:45:00.000+0000',
  expiresAt: '2026-09-10T04:37:00.000+0000',
  expiresInSeconds: 420,
}
```

OpenMRS may serialize `Date` as ISO **or** epoch ms. If parse fails, coerce with `new Date(value)`.

400 from `RestUtil.wrapErrorResponse` looks like:

```ts
{ error: { message: string; code?: string; detail?: string } }
```

---

## React Query mutation (OpenMRS 3 `openmrsFetch`)

```ts
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { openmrsFetch, restBaseUrl } from '@openmrs/esm-framework';

const HOLD_URL = `${restBaseUrl}/appointmentHold`;

export function useLockAppointmentSlot() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (payload: AppointmentHoldPayload) => {
      const res = await openmrsFetch(HOLD_URL, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });

      if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        throw new Error(body?.error?.message ?? body?.message ?? 'Failed to lock slot');
      }

      return (await res.json()) as AppointmentHoldResponse;
    },
    onSuccess: (hold) => {
      queryClient.setQueryData(['appointmentHold', hold.uuid], hold);
      queryClient.invalidateQueries({ queryKey: ['appointmentSlots'] });
    },
  });
}

export function useAppointmentHold(uuid?: string) {
  return useQuery({
    queryKey: ['appointmentHold', uuid],
    enabled: Boolean(uuid),
    queryFn: async () => {
      const res = await openmrsFetch(`${HOLD_URL}/${uuid}`);
      if (!res.ok) {
        throw new Error('Hold not found');
      }
      return (await res.json()) as AppointmentHoldResponse;
    },
    refetchInterval: (query) => {
      const hold = query.state.data;
      if (!hold || hold.status !== 'HELD' || hold.expiresInSeconds <= 0) {
        return false;
      }
      return 1000;
    },
  });
}
```

If you are not on O3, swap `openmrsFetch` for `fetch` with cookies/`Authorization`.

---

## Usage

```tsx
const lockSlot = useLockAppointmentSlot();

function onSelectSlot(slot: { startDateTime: string; endDateTime: string }) {
  lockSlot.mutate(
    {
      patientUuid,
      serviceUuid,
      startDateTime: slot.startDateTime,
      endDateTime: slot.endDateTime,
    },
    {
      onSuccess: (hold) => {
        // start OTP; countdown = hold.expiresInSeconds
        // keep hold.uuid for confirm
      },
      onError: (err) => {
        // slot full / lock timeout / validation
      },
    },
  );
}

// lockSlot.isPending — disable the slot button
// lockSlot.data?.uuid — pass to Booking confirm
```

After a successful lock, refetch available slots so `available` drops. Drive the 7-minute countdown from `expiresAt` or `expiresInSeconds`; the server still enforces expiry on confirm.

---

**User**

in booking omod, during otp verification only, we will book appointment. but since it is in hold, api throw error selected time slot is full, because we havent handled how to handle the held appt to booked while creating appt.

booking omod logic:
@Override
	public BookedAppointmentInfo confirm(String requestUuid, ConfirmAppointmentRequest request) {
		String otp = request == null ? null : IndiemrUtils.trimToNull(request.getOtp());
		if (otp == null) {
			throw new IllegalArgumentException("otp is required");
		}
		
		AppointmentHold hold = appointmentHoldService.getByUuid(requestUuid);
		if (hold == null) {
			throw new IllegalArgumentException("booking request not found");
		}
		
		if (AppointmentHoldStatus.CONSUMED.equals(hold.getStatus()) && hold.getAppointment() != null) {
			return toBookedInfo(hold.getAppointment(), requestUuid);
		}
		
		if (!AppointmentHoldStatus.HELD.equals(hold.getStatus())) {
			throw new IllegalArgumentException("booking request is no longer active");
		}
		if (hold.getExpiresAt() != null && hold.getExpiresAt().before(new Date())) {
			appointmentHoldService.expireHold(requestUuid);
			throw new IllegalArgumentException("booking request has expired");
		}
		
		if (!otpStore.verify(requestUuid, otp)) {
			throw new IllegalArgumentException("Invalid or expired OTP");
		}
		
		Appointment appointment = buildAppointmentFromHold(hold);
		Appointment saved = appointmentsService.validateAndSave(appointment);
		appointmentHoldService.consumeHold(requestUuid, saved);
		return toBookedInfo(saved, requestUuid);
	}

api error:
@Override
	public BookedAppointmentInfo confirm(String requestUuid, ConfirmAppointmentRequest request) {
		String otp = request == null ? null : IndiemrUtils.trimToNull(request.getOtp());
		if (otp == null) {
			throw new IllegalArgumentException("otp is required");
		}
		
		AppointmentHold hold = appointmentHoldService.getByUuid(requestUuid);
		if (hold == null) {
			throw new IllegalArgumentException("booking request not found");
		}
		
		if (AppointmentHoldStatus.CONSUMED.equals(hold.getStatus()) && hold.getAppointment() != null) {
			return toBookedInfo(hold.getAppointment(), requestUuid);
		}
		
		if (!AppointmentHoldStatus.HELD.equals(hold.getStatus())) {
			throw new IllegalArgumentException("booking request is no longer active");
		}
		if (hold.getExpiresAt() != null && hold.getExpiresAt().before(new Date())) {
			appointmentHoldService.expireHold(requestUuid);
			throw new IllegalArgumentException("booking request has expired");
		}
		
		if (!otpStore.verify(requestUuid, otp)) {
			throw new IllegalArgumentException("Invalid or expired OTP");
		}
		
		Appointment appointment = buildAppointmentFromHold(hold);
		Appointment saved = appointmentsService.validateAndSave(appointment);
		appointmentHoldService.consumeHold(requestUuid, saved);
		return toBookedInfo(saved, requestUuid);
	}

---

**Cursor**

The hold still occupies the slot when `validateAndSave` runs, so capacity treats the seat as taken. I'll confirm the count path and outline a concrete fix.

This is occupancy double-counting, not OTP.

`confirm` still has `status = HELD` when it calls `validateAndSave`. Capacity is:

```text
booked = overlapping appointments + unexpired HELD holds
```

Your hold is that extra 1. If capacity is 1, `booked >= capacity` → **"Selected time slot is full."** `consumeHold` never runs.

You also **cannot** flip the order (`consume` then `save`) as written: `consumeIfActive` sets `patient_appointment_id`, so the appointment row must exist first.

Keep this order:

```text
validateAndSave  →  consumeHold
```

On save, **exclude this hold uuid** from the hold count (same idea as `excludeAppointmentUuid` on reschedule).

---

## 1. Transient flag on `Appointment`

Same pattern as `sendSms` (not persisted):

```java
/** Not persisted. When converting a hold to an appointment, skip this hold in slot occupancy. */
private String convertingHoldUuid;

public String getConvertingHoldUuid() { return convertingHoldUuid; }
public void setConvertingHoldUuid(String convertingHoldUuid) { this.convertingHoldUuid = convertingHoldUuid; }
```

---

## 2. DAO — exclude that hold from the count

`AppointmentHoldDao`:

```java
int countUnexpiredOverlapping(AppointmentServiceDefinition service,
                              Date slotStart,
                              Date slotEnd,
                              String excludeHoldUuid);
```

Keep a 3-arg overload that passes `null`, or change all callers.

In `AppointmentHoldDaoImpl`, add:

```java
if (StringUtils.isNotBlank(excludeHoldUuid)) {
    query.setParameter("excludeUuid", excludeHoldUuid);
    // and in HQL:
    // and h.uuid <> :excludeUuid
}
```

Full HQL:

```hql
select count(h) from AppointmentHold h
where h.voided = false
  and h.service.appointmentServiceId = :serviceId
  and h.status = :held
  and h.expiresAt > current_timestamp()
  and h.startDateTime < :slotEnd
  and h.endDateTime > :slotStart
  and (:excludeUuid is null or h.uuid <> :excludeUuid)
```

Hibernate sometimes dislikes `is null or` with parameters; branching in Java is safer:

```java
String hql = "select count(h) from AppointmentHold h "
    + "where h.voided = false "
    + "and h.service.appointmentServiceId = :serviceId "
    + "and h.status = :held "
    + "and h.expiresAt > current_timestamp() "
    + "and h.startDateTime < :slotEnd "
    + "and h.endDateTime > :slotStart";
if (StringUtils.isNotBlank(excludeHoldUuid)) {
    hql += " and h.uuid <> :excludeUuid";
}
```

`createHold` still calls this with `excludeHoldUuid = null`.

---

## 3. Capacity check — only on confirm/save

`countBookedAppointments` already takes `excludeAppointmentUuid`. Add hold exclude:

```java
private int countBookedAppointments(..., String excludeAppointmentUuid, String excludeHoldUuid) {
    ...
    booked += appointmentHoldDao.countUnexpiredOverlapping(
            service, slotStart, slotEnd, excludeHoldUuid);
    return booked;
}
```

In `isSlotCapacityExceeded`:

```java
int booked = countBookedAppointments(
        service, provider, slotStart, slotEnd,
        excludeAppointmentUuid,
        appointment.getConvertingHoldUuid());
```

**Do not** pass a hold uuid from `getAvailableSlots`. The calendar must still treat the hold as taken.

---

## 4. Booking OMOD `confirm`

```java
Appointment appointment = buildAppointmentFromHold(hold);
appointment.setConvertingHoldUuid(requestUuid);  // <-- required

Appointment saved = appointmentsService.validateAndSave(appointment);
appointmentHoldService.consumeHold(requestUuid, saved);
```

Put `confirm` in **one `@Transactional`**. Then if `consumeHold` fails, the appointment insert rolls back.

Idempotent path you already have is fine: `CONSUMED` + `hold.getAppointment()`.

---

## Why not consume first

| Order | Problem |
|---|---|
| Save then consume (today) | Hold still counted → slot full |
| Consume then save | `consumeIfActive` needs a persisted appointment for the FK |

Exclude-on-save is the right conversion.

---

## After this

Capacity 1, one `HELD` hold:

- Other patients: hold counts → slot full (correct)
- This confirm: hold uuid excluded → `booked = 0` appointments + `0` other holds → save OK → consume → hold no longer counted, appointment is

I'm in Ask mode and cannot edit the repo. Switch to Agent mode if you want this applied in the appointments module.

---

**User**

give code and file, wherever we want to make changes.

---

**Cursor**

Copy these in this order. Booking OMOD is last (that repo, not this one).

---

## 1. `api/src/main/java/org/openmrs/module/appointments/model/Appointment.java`

Next to `sendSms` (around line 56):

```java
    /**
     * Transient request-time flag. Not persisted.
     * When converting a hold into an appointment, this hold uuid is excluded from slot occupancy.
     */
    private String convertingHoldUuid;
```

Next to `setSendSms` (around line 284):

```java
    public String getConvertingHoldUuid() {
        return convertingHoldUuid;
    }

    public void setConvertingHoldUuid(String convertingHoldUuid) {
        this.convertingHoldUuid = convertingHoldUuid;
    }
```

Do **not** add this to `Appointment.hbm.xml`.

---

## 2. `api/src/main/java/org/openmrs/module/appointments/dao/AppointmentHoldDao.java`

Replace the 3-arg count with 4-arg (keep a default in the impl, or only 4-arg and update callers):

```java
    int countUnexpiredOverlapping(AppointmentServiceDefinition service,
                                  Date slotStart,
                                  Date slotEnd,
                                  String excludeHoldUuid);
```

---

## 3. `api/src/main/java/org/openmrs/module/appointments/dao/impl/AppointmentHoldDaoImpl.java`

Add:

```java
import org.apache.commons.lang.StringUtils;
```

Replace `countUnexpiredOverlapping` with:

```java
    @Override
    public int countUnexpiredOverlapping(AppointmentServiceDefinition service,
                                         Date slotStart,
                                         Date slotEnd,
                                         String excludeHoldUuid) {
        String hql = "select count(h) from AppointmentHold h "
                + "where h.voided = false "
                + "and h.service.appointmentServiceId = :serviceId "
                + "and h.status = :held "
                + "and h.expiresAt > current_timestamp() "
                + "and h.startDateTime < :slotEnd "
                + "and h.endDateTime > :slotStart";
        if (StringUtils.isNotBlank(excludeHoldUuid)) {
            hql += " and h.uuid <> :excludeUuid";
        }
        Query query = sessionFactory.getCurrentSession().createQuery(hql);
        query.setParameter("serviceId", service.getAppointmentServiceId());
        query.setParameter("held", AppointmentHoldStatus.HELD);
        query.setParameter("slotEnd", slotEnd);
        query.setParameter("slotStart", slotStart);
        if (StringUtils.isNotBlank(excludeHoldUuid)) {
            query.setParameter("excludeUuid", excludeHoldUuid);
        }
        Number count = (Number) query.uniqueResult();
        return count != null ? count.intValue() : 0;
    }
```

---

## 4. `api/src/main/java/org/openmrs/module/appointments/service/impl/AppointmentHoldServiceImpl.java`

`createHold` still counts **all** holds. Pass `null`:

```java
            int bookedHolds = appointmentHoldDao.countUnexpiredOverlapping(
                    hold.getService(), hold.getStartDateTime(), hold.getEndDateTime(), null);
```

---

## 5. `api/src/main/java/org/openmrs/module/appointments/service/impl/AppointmentSlotAvailabilityServiceImpl.java`

**`isSlotCapacityExceeded`** (around line 106) — exclude the converting hold:

```java
        String excludeAppointmentUuid = resolveExcludeAppointmentUuid(appointment, slotStart, slotEnd);
        int booked = countBookedAppointments(service, provider, slotStart, slotEnd,
                excludeAppointmentUuid, appointment.getConvertingHoldUuid());
        return booked >= capacity;
```

**`buildSlotsForWindow`** (around line 204) — calendar must still count holds; pass `null`:

```java
                int booked = countBookedAppointments(service, provider, s, e, excludeAppointmentUuid, null);
```

**`countBookedAppointments`:**

```java
    private int countBookedAppointments(AppointmentServiceDefinition service, Provider provider,
            Date slotStart, Date slotEnd, String excludeAppointmentUuid, String excludeHoldUuid) {
        List<AppointmentStatus> statuses = AppointmentServiceCapacityUtil.SLOT_BLOCKING_STATUSES;
        int booked;
        if (provider != null) {
            booked = appointmentDao.countOverlappingAppointmentsForProvider(
                    provider, slotStart, slotEnd, excludeAppointmentUuid, statuses);
        } else {
            booked = appointmentDao.countOverlappingAppointmentsForService(
                    service, provider, slotStart, slotEnd, excludeAppointmentUuid, statuses);
        }
        booked += appointmentHoldDao.countUnexpiredOverlapping(
                service, slotStart, slotEnd, excludeHoldUuid);
        return booked;
    }
```

---

## 6. Booking OMOD `confirm` (other repo)

One extra line **before** `validateAndSave`. Keep save-then-consume (appointment must exist for the hold FK):

```java
		Appointment appointment = buildAppointmentFromHold(hold);
		appointment.setConvertingHoldUuid(requestUuid);

		Appointment saved = appointmentsService.validateAndSave(appointment);
		appointmentHoldService.consumeHold(requestUuid, saved);
		return toBookedInfo(saved, requestUuid);
```

Mark `confirm` `@Transactional` so if `consumeHold` fails, the appointment insert rolls back.

---

Capacity 1 + your hold: confirm excludes that hold → save succeeds → consume → hold no longer occupies. Other users still see the slot as full until then.

---

**User**

int booked = countBookedAppointments(service, provider, s, e, excludeAppointmentUuid, null);

where should we inject?

---

**Cursor**

Change **line 205 only** — inside `buildSlotsForWindow`, the branch that is **not** blocked.

That is the **calendar / available-slots** path. Always pass `null` so live holds still occupy the seat.

Replace:

```205:205:api/src/main/java/org/openmrs/module/appointments/service/impl/AppointmentSlotAvailabilityServiceImpl.java
                int booked = countBookedAppointments(service, provider, s, e, excludeAppointmentUuid);
```

with:

```java
                int booked = countBookedAppointments(service, provider, s, e, excludeAppointmentUuid, null);
```

Leave **line 106–107** as it is. That is **confirm/save**. It must pass `appointment.getConvertingHoldUuid()`.

---

You currently have **two** `countBookedAppointments` methods (lines 234 and 249). After line 205 uses 6 args, **delete the 5-arg one** (234–247). Keep only the 6-arg method that calls:

```java
appointmentHoldDao.countUnexpiredOverlapping(service, slotStart, slotEnd, excludeHoldUuid);
```

Do **not** pass a hold uuid from `getAvailableSlots`. `null` there is intentional.

---

**User**

it works fine.
for rescheduling appointment also, do we need to make any changes similarly? i guess we can use same validateAndSave(appointment); only thing we have to pass the uuid of appointment to reschedule.

---

**Cursor**

You do **not** need another occupancy change in this module. `convertingHoldUuid` already covers confirm. Reschedule is a **different API** than “save with the old uuid”.

## How reschedule works here

`POST /appointment/{uuid}/reschedule` does **not** update that row. It:

1. Cancels the old appointment (`Cancelled` → no longer occupies)
2. Clears uuid on the **new** object
3. `validateAndSave` a **new** appointment (new uuid)

```421:447:api/src/main/java/org/openmrs/module/appointments/service/impl/AppointmentsServiceImpl.java
    public Appointment reschedule(String originalAppointmentUuid, Appointment newAppointment, ...) {
        ...
        changeStatus(prevAppointment, AppointmentStatus.Cancelled.toString(), new Date());
        ...
        newAppointment.setUuid(null);
        ...
        validateAndSave(newAppointment);
```

So “pass the uuid of the appointment to reschedule” belongs on **`reschedule(oldUuid, newAppointment)`**, not on `validateAndSave` as the existing uuid.

If you `validateAndSave` with the **old** uuid, you **edit in place**. That skips cancel, new uuid, reschedule SMS/events (`RescheduleEventsAdvice`).

## Hold + OTP reschedule

Same as a new booking: hold the **new** slot, then convert the hold.

| Step | What |
|---|---|
| 1 | `POST /appointmentHold` for the **new** slot |
| 2 | OTP |
| 3 | `newAppt.setConvertingHoldUuid(holdUuid)` then `appointmentsService.reschedule(oldAppointmentUuid, newAppt, false)` |
| 4 | `consumeHold(holdUuid, saved)` |

`reschedule` already calls `validateAndSave`. Transient `convertingHoldUuid` is still on `newAppt`, so the hold is excluded from capacity. No extra change in `AppointmentSlotAvailabilityServiceImpl`.

`confirm` sketch:

```java
Appointment newAppt = buildAppointmentFromHold(hold);
newAppt.setConvertingHoldUuid(requestUuid);

Appointment saved = StringUtils.isNotBlank(originalAppointmentUuid)
        ? appointmentsService.reschedule(originalAppointmentUuid, newAppt, false)
        : appointmentsService.validateAndSave(newAppt);

appointmentHoldService.consumeHold(requestUuid, saved);
```

Keep this in one `@Transactional`.

Staff reschedule **without** OTP/hold: existing `/{uuid}/reschedule` is enough. Do not set `convertingHoldUuid`.

## What not to do

- Do not `validateAndSave` with the **old** uuid for patient OTP reschedule.
- Do not skip `convertingHoldUuid` on the new object — same “slot full” bug as first book.
- Do not consume the hold before `reschedule`; the new appointment must exist for the FK.

## FE slots

When listing slots to reschedule, pass the current appointment:

`GET .../appointmentService/availableSlots?uuid=...&date=...&excludeAppointmentUuid={oldAppointmentUuid}&patientUuid=...`

That is already supported. It excludes the current booking from booked count / “patient busy”. It is **not** a substitute for `convertingHoldUuid` on confirm.

**Short answer:** no new occupancy logic. Use `reschedule(oldUuid, newAppt)` + `setConvertingHoldUuid(holdUuid)` + `consumeHold`. `validateAndSave` alone is only for a **new** booking, not for reschedule.

---

**User**

@api/src/main/java/org/openmrs/module/appointments/service/impl/AppointmentsServiceImpl.java:140-149 cant we use these same on? we dont want to cancel and create new one.
ig, validateAndSave will handle that.

---

**Cursor**

Yes — you can **update in place** with `validateAndSave` and **not** call `reschedule()`. That method does not cancel and recreate. It only validates, then `appointmentDao.save` (`saveOrUpdate`).

If the object already has an **`appointmentId`**, Hibernate **updates** that row. Same uuid, no cancel.

`reschedule()` is a different path: cancel old + insert new (new uuid). Skip it if you want to keep the same appointment.

## How to call it

Load the existing row, change times from the hold, then save. Do **not** `new Appointment()` and only set uuid.

```java
Appointment existing = appointmentsService.getAppointmentByUuid(originalAppointmentUuid);
if (existing == null) {
    throw new IllegalArgumentException("appointment not found");
}

existing.setStartDateTime(hold.getStartDateTime());
existing.setEndDateTime(hold.getEndDateTime());
existing.setService(hold.getService());
existing.setConvertingHoldUuid(requestUuid); // still required

Appointment saved = appointmentsService.validateAndSave(existing);
appointmentHoldService.consumeHold(requestUuid, saved);
```

`convertingHoldUuid` is still required. Capacity counts the **new** slot’s hold; the old uuid does **not** exclude that hold. `resolveExcludeAppointmentUuid` only skips the **appointment** row when it already sits in the **target** slot (same-slot edit). Moving to a **new** slot still needs the hold excluded.

## What you get vs `reschedule()`

| | `validateAndSave(existing)` | `reschedule(oldUuid, newAppt)` |
|---|---|---|
| Row | Same uuid / id | Old cancelled, new uuid |
| Events | `BAHMNI_APPOINTMENT_UPDATED` (because `getId() != null`) | Cancel + create (reschedule advice) |
| SMS | Update listener, **not** reschedule-from/to SMS | Reschedule SMS |
| History | One appointment, times changed | Two appointments |

`AppointmentEventsAdvice` uses `appointment.getId()` **before** save: `null` → CREATED, set → UPDATED. If you build a new object with only uuid and no id, you get a **CREATED** event (and possibly a unique-uuid clash). Always `getAppointmentByUuid` first.

`validateAndSave` also runs **create** validators (`appointmentValidators`), not `editAppointmentValidators`. Capacity, booking window, and lead time still run — which is what you want for the new slot.

**Bottom line:** same `validateAndSave` is enough if you mutate the **loaded** appointment and set `convertingHoldUuid`. Do not use `reschedule()` unless you want cancel + new appointment.

