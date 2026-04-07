# JavaDoc / KDoc Coverage Guide

This document catalogs the documentation that now exists across the codebase, including both:

- previously created JavaDoc/KDoc, and
- newly added JavaDoc/KDoc in the latest pass.

It serves as a quick index for reviewers and for requirement **"Code Documentation"** compliance.

---

## Scope

Documented source files under:

- `app/src/main/java/com/example/se_proj/`
- `app/src/main/java/com/example/se_proj/models/`
- `app/src/main/java/com/example/se_proj/rules/`
- `app/src/main/java/com/example/se_proj/adapters/`

---

## Documentation conventions used

- Each source file has a brief introductory class/file comment describing:
  - purpose in the app,
  - architectural/design role (where relevant),
  - current outstanding issues.
- Model classes include API/interface-focused docs (property contracts and generated data-class method notes).
- Utility/rules classes document public methods with inputs/outputs and behavior.

---

## Coverage index

## 1) Core Activities (`com.example.se_proj`)

| File | Documentation status | Purpose summary |
|---|---|---|
| `MainActivity.java` | ✅ Javadoc | Role-navigation hub for development/demo entry points. |
| `LoginActivity.kt` | ✅ KDoc | Firebase authentication + role-based redirect orchestration. |
| `AdminDashboardActivity.kt` | ✅ KDoc | Admin approvals/rejections and summary monitoring. |
| `AdminAuditActivity.kt` | ✅ KDoc | Audit log browsing, search, and overstay tab handling. |
| `GuardDashboardActivity.kt` | ✅ KDoc | Gate flow: visitor checks, entry/exit actions, occupancy updates. |
| `FacultyRequestsActivity.kt` | ✅ KDoc | Faculty request list with edit/cancel operations. |
| `RequestSubmissionActivity.kt` | ✅ KDoc | Faculty request creation + ad-hoc and reminder listeners. |
| `StudentRequestActivity.kt` | ✅ KDoc | Student guest-pass submission with validation constraints. |
| `WalkInRegistrationActivity.kt` | ✅ KDoc | Guard-created walk-in requests and approval waiting flow. |

### Outstanding-issues themes noted in Activity docs

- atomicity gaps in multi-write actions,
- listener/read-cost scaling,
- duplicate logging/idempotency concerns,
- schema consistency issues (`rollNumber`/`facultyId`/`studentId`),
- process-lifecycle effects on in-memory reminder state.

---

## 2) Models (`com.example.se_proj.models`)

| File | Documentation status | Interface/API docs included |
|---|---|---|
| `VisitorRequest.kt` | ✅ KDoc | Property contracts, lifecycle/security notes, data-class public interface note (`copy`, `equals`, `hashCode`, `toString`, components), outstanding issues. |
| `AuditLog.kt` | ✅ KDoc | Property contracts, append-only log semantics, data-class public interface note, outstanding issues. |

---

## 3) Rules / Utilities (`com.example.se_proj.rules`)

| File | Documentation status | Purpose summary |
|---|---|---|
| `RequestValidationUtils.java` | ✅ Javadoc | Validation rules engine for scheduled, walk-in, and student requests. |
| `RequestStatus.java` | ✅ Javadoc | Centralized status constants and status-policy predicates. |
| `VisitWindowEvaluator.java` | ✅ Javadoc | Strategy-style access-window decision evaluator for guard UI. |
| `AuditLogUtils.java` | ✅ Javadoc | Audit merge/sort/dedup and overstay/reminder logic. |
| `LoginInputUtils.java` | ✅ Javadoc | Login credential checks and ID/email normalization. |
| `UiFormatUtils.java` | ✅ Javadoc | Shared display formatting (dates/status/timestamps/visual states). |
| `ParkingOccupancyUtils.java` | ✅ Javadoc | Occupancy clamping/ratio/counter formatting helpers. |
| `UserProfileUtils.java` | ✅ Javadoc | Host-ID resolution from mixed profile fields. |

---

## 4) RecyclerView Adapters (`com.example.se_proj.adapters`)

| File | Documentation status | Purpose summary |
|---|---|---|
| `VisitorRequestAdapter.kt` | ✅ KDoc | Admin pending-request list binding + approve/reject callbacks. |
| `FacultyRequestAdapter.kt` | ✅ KDoc | Faculty request list binding + edit/cancel callbacks. |
| `AuditLogAdapter.kt` | ✅ KDoc | Audit/overstay row binding and visual-state rendering. |

Method/class notes were added for adapter public update methods and ViewHolder roles.

---

## Quick verification checklist

- [x] Introductory file/class docs present across active source files in the listed packages.
- [x] Model classes include interface/API-level docs.
- [x] Public utility methods are documented in rule classes.
- [x] Outstanding issues are called out in class/file introductions where applicable.

---

## Notes for future improvement

1. Add an automated doc quality gate (e.g., detekt custom rule / lint rule) to require top-level KDoc/Javadoc.
2. If desired, add a Gradle task to generate browsable API docs for Java sources and Dokka for Kotlin sources.
3. Standardize terminology to "KDoc/Javadoc" in contributor guidelines for consistency.
