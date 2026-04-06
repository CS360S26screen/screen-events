# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Android campus visitor management system ("SE_proj") built with Kotlin/Java, Firebase (Auth + Firestore), and View Binding. Users authenticate via Firebase Auth and are routed by role (admin, guard, faculty, student) to role-specific dashboards.

## Build & Test Commands

```bash
# Build the project
./gradlew assembleDebug

# Run all unit tests
./gradlew test

# Run a single test class
./gradlew testDebugUnitTest --tests "com.example.se_proj.rules.RequestValidationUtilsTest"

# Clean build
./gradlew clean assembleDebug
```

## Architecture

### Two-layer pattern: Activities (Kotlin) + Rules (Java)

- **Activities** (`app/src/main/java/com/example/se_proj/*.kt`) — Android UI layer. Each role has dedicated activities. Uses View Binding (no XML `findViewById`). Talks to Firestore directly (no repository layer).
- **Rules utilities** (`app/src/main/java/com/example/se_proj/rules/*.java`) — Pure Java, no Android dependencies. All business logic (validation, formatting, status transitions) lives here so it can be unit tested without instrumentation.
- **Unit tests** (`app/src/test/java/com/example/se_proj/rules/*.java`) — Mirror the rules package 1:1. JUnit 4, plain JVM.

### Key conventions

- Date format: `dd/MM/yyyy`. Time format: `HH:mm`. Java 8 time API via core library desugaring (`minSdk 24`).
- CNIC is a 13-digit Pakistani national ID; validation strips non-digits then checks length.
- `VisitorRequest` is the central Firestore model. `status` field drives workflow; status logic is in `RequestStatus.java`.
- Firestore `Users` collection is keyed by Firebase Auth UID. Login has a fallback that links legacy email-keyed docs to UID-keyed docs.

### Role routing from LoginActivity (launcher activity)

| Role      | Target Activity              |
|-----------|------------------------------|
| admin     | AdminDashboardActivity       |
| guard     | GuardDashboardActivity       |
| faculty   | RequestSubmissionActivity    |
| student   | StudentRequestActivity       |

### Models & Adapters

- `models/` — Firestore data classes (`VisitorRequest`, `AuditLog`)
- `adapters/` — RecyclerView adapters for list screens
