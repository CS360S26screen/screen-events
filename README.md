# Campus Gate Access System — Formatted Product Backlog

> **Priority Legend:** 🔴 High &nbsp;|&nbsp; 🟡 Medium &nbsp;|&nbsp; 🟢 Low  
> **Checkpoint Legend:** 🏁 Half-point &nbsp;|&nbsp; 🎯 Full-point

| #| Requirement | Story Points | Risk | Checkpoint |
|:-:|---|:-:|:-:|:-:|
| 1 | As a **faculty/staff member**, I want to submit, view, edit, and cancel visitor requests with guest details, purpose, and visit schedule, so that my visitors can enter campus without delays and my records stay accurate. | 8 | 🟡 | 🏁 Half |
| 2 | As a **security administrator**, I want to approve or reject submitted visitor requests while the system prevents duplicate entries and confirms successful submissions to users, so that only valid and reviewed requests reach the gate. | 8 | 🟡 | 🏁 Half |
| 3 | As a **faculty/staff member**, I want to define a visit start and end time and receive reminders before the visit window closes, so that I can manage my guest's approved visit duration properly. | 5 | 🟡 | 🏁 Half |
| 4 | As a **security guard/security administrator**, I want the system to display and enforce approved visit windows by flagging early/late arrivals, blocking expired approvals, supporting supervisor override, and allowing configurable duration rules, so that campus time-based access policies are enforced reliably. | 13 | 🔴 | 🏁 Half |
| 5 | As a **security guard**, I want to record visitor entry and exit events accurately, so that the system always knows who is currently on campus and when they arrived or left. | 8 | 🟡 | 🏁 Half |
| 6 | As a **security administrator**, I want immutable timestamped logs, overstay detection, searchable records, and downloadable reports, so that security investigations and operational monitoring remain accurate and trustworthy. | 13 | 🔴 | 🏁 Half |
| 7 | As a **security guard**, I want to search for a host and send a real-time approval request for an unscheduled visitor, while the host can approve or deny from their device and I can immediately see the response, so that walk-in visitors can be handled quickly without phone calls or manual coordination. | 13 | 🔴 | 🏁 Half |
| 8 | As a **student/faculty host or security administrator**, I want the system to enforce guest-limit rules, detect active guests, notify hosts when limits are reached, and free the slot immediately on checkout, so that ad-hoc approvals comply with campus visitor policy. | 8 | 🟡 | 🏁 Half |
| 9 | As a **student**, I want to pre-register and manage a guest pass with the guest's details and a valid visit window, so that my guest can be pre-cleared before arriving at campus. | 8 | 🟡 | 🏁 Half |
| 10 | As a **security guard/security administrator**, I want the system to verify guest passes using CNIC and host roll number, enforce one active pass per student, show clear access decisions, notify relevant users, and provide expected-arrival, occupancy, and overstay views, so that student guest access remains controlled and visible. | 13 | 🔴 | 🏁 Half |
| 11 | As a **security administrator**, I want a timestamped audit trail of all entry, exit, and denied-access events with clear reasons, so that I can investigate incidents and identify patterns of unauthorized access attempts. | 8 | 🟡 | 🏁 Half |
| 12 | As a **security administrator**, I want to search the audit history by visitor CNIC and host roll number, so that I can trace a person's or host's access activity across multiple days or weeks. | 5 | 🟢 | 🏁 Half |
| 13 | As a **security guard**, I want to search by a host's roll number to see whether a guest is currently registered for that host, so that I can verify student-linked visitors quickly at the gate. | 3 | 🟢 | 🏁 Half |
| 14 | As a **guard** at the campus entrance intersection, I want to view the remaining capacity in the main parking area, so that I can direct traffic either toward parking or toward the drive-through lane accordingly.<br><br>**Acceptance criteria:**<br>• Guard can view the live parking capacity.<br>• Each car entering the parking area is tracked and counted.<br>• Each car leaving the parking area is tracked.<br>• The app updates near-instantly with an acceptable error of about 2–3 cars. | 8 | 🔴 | 🏁 Half |
| 15 | As a **security administrator**, I want the system to require and link both a vehicle credential and a student credential for vehicle entry, and validate them against registered ownership data, so that every vehicle entering campus has strict and traceable accountability.<br><br>**Acceptance criteria:**<br>• Two valid scans (Car + Student) are required to authorize entry.<br>• Entry is blocked if either credential is missing, expired, or mismatched.<br>• The system links the vehicle to the registered student owner in the logs. | 13 | 🔴 | 🎯 Full |
| 16 | As a **security administrator**, I want the system to support special exit handling and generate mismatch/duplicate-entry warnings and reports, so that suspicious vehicle activity can be monitored and investigated.<br><br>**Acceptance criteria:**<br>• For exit, only the car credential may be scanned when the student is being dropped off by a driver.<br>• Admin can generate reports for cases where the driver is not the vehicle owner.<br>• Admin can see when a vehicle or person has entered twice without a matching exit stamp. | 8 | 🔴 | 🎯 Full |
| 17 | As **campus security**, I want access to labs, wings, rooms, and restricted zones to be controlled by user role, authorization list, and schedule, so that prohibited areas remain secure outside approved access conditions.<br><br>**Acceptance criteria:**<br>• Access is granted only to authorized users on the relevant approval list.<br>• Permissions can be enforced at building, wing, or room level.<br>• A scan returns a clear Allowed or Denied result. | 13 | 🔴 | 🎯 Full |
| 18 | As a **security administrator**, I want every successful or failed zone-access attempt to be logged with timestamp, location, and user ID, so that restricted-area activity can be audited properly.<br><br>**Acceptance criteria:**<br>• Every attempt is logged with timestamp, location, and user ID. | 8 | 🔴 | 🎯 Full |
| 19 | As a **security administrator**, I want to manage a real-time blacklist of people and vehicles with temporary or permanent status, so that unauthorized entities are blocked across all entry points immediately.<br><br>**Acceptance criteria:**<br>• Admin can blacklist by Student ID, Staff ID, Visitor Name, or Vehicle ID.<br>• Blacklist entries support Permanent or Temporary status with automatic expiry dates. | 8 | 🟡 | 🎯 Full |
| 20 | As a **security guard**, I want the system to show a high-priority visual alert when a blacklisted entity is scanned, so that I can respond immediately and stop unauthorized access.<br><br>**Acceptance criteria:**<br>• Guards receive an immediate high-priority alert when a blacklisted person or vehicle is scanned. | 5 | 🔴 | 🎯 Full |
| 21 | As a **security administrator**, I want to digitally track a delivery rider's full visit lifecycle from gate entry to destination and exit, so that delivery visits can be monitored without verbal coordination and riders who stay too long can be flagged.<br><br>**Acceptance criteria:**<br>• Guards can create a delivery record with rider details, company, destination, and receiving host/faculty details.<br>• The system calculates total time on campus by matching entry and exit scans.<br>• Riders who exceed the safe duration threshold are flagged.<br>• Admin can filter traffic as Delivery vs Standard Visitor. | 8 | 🟡 | 🎯 Full |
| 22 | As a **guard**, I want to validate the relationship between a student and an authorized parent/guardian during pickup, so that only verified and approved guardians can access campus for student pickup.<br><br>**Acceptance criteria:**<br>• Scanning a student ID shows the list of authorized guardians with picture, CNIC, and recent access details.<br>• Guards can confirm guardian identity using CNIC.<br>• The system clearly flags revoked or blacklisted guardians. | 8 | 🔴 | 🎯 Full |
| 23 | As a **security administrator**, I want to immediately deactivate lost or stolen IDs across all scanners and checkpoints, so that unauthorized access using found credentials is prevented at once.<br><br>**Acceptance criteria:**<br>• Marking an ID as lost/stolen propagates instantly to all scanners and access points.<br>• The card's lab/wing privileges are revoked immediately.<br>• If the deactivated card is scanned, access is blocked and the attempt is logged with the gate/location. | 13 | 🔴 | 🎯 Full |
| 24 | As a **security administrator**, I want to issue replacement IDs while preserving lockout history and recording who reported the loss and when the lockout began, so that the credential lifecycle remains auditable.<br><br>**Acceptance criteria:**<br>• Admin can issue a new ID while maintaining the old credential's lockout history.<br>• The system records who reported the loss and when the lockout was initiated. | 5 | 🟡 | 🎯 Full |
| 25 | As a **safety administrator**, I want to trigger emergency mode for the whole campus or a selected building, so that normal access rules are suspended, evacuation is enabled, and emergency responders can operate with accurate occupancy awareness.<br><br>**Acceptance criteria:**<br>• Emergency Mode can be triggered for the whole campus or a single building.<br>• Standard access restrictions are suspended for evacuation routes.<br>• The system generates a Last Known Occupants report for the affected zone. | 13 | 🔴 | 🎯 Full |
| 26 | As a **guard/safety administrator**, I want the interface to switch to an emergency UI and require a controlled manual reset back to normal mode with full movement auditing, so that emergency operations remain visible, safe, and fully traceable.<br><br>**Acceptance criteria:**<br>• Guard UI highlights evacuation routes and active danger zones during emergency mode.<br>• Returning to normal mode requires a manual reset.<br>• A full audit log of movements during the emergency is generated. | 8 | 🔴 | 🎯 Full |

# CRC Card Catalogue  
<br>
<img width="2316" height="1702" alt="CRC_Sheet1" src="https://github.com/user-attachments/assets/f93e1f11-21a2-45d7-8c68-2422e08c0ead" />
<br>
<img width="2316" height="1142" alt="CRC_Sheet2" src="https://github.com/user-attachments/assets/11c951c4-d567-445f-9da4-27178ce0e818" />

# Figma & Storyboards

The system interface is designed as a native Android application following **Material Design 3** guidelines. The prototype demonstrates the end-to-end lifecycle of a visitor, from pre-registration to gate check-out.

## Prototype Access
* **Interactive Figma Prototype:** [View Live Design & Prototype](https://www.figma.com/design/tQIICpUnr8L6641cflMZ0C/1_1f?node-id=0-1&t=lFUJ7BZwAxbqwnWO-1)
* **Storyboard:** ![Storyboard](./1_1f_(2).png)

---

## Core User Journeys (How to Navigate)

1.  **Flow 1: Security Guard Gate & Ad-Hoc Check-In**
    * Covers visitor lookup, dual-credential verification (Vehicle + ID), and real-time ad-hoc host approval requests.
2.  **Flow 2: Student/Faculty Guest Pass Pre-Registration**
    * Covers the data entry point, and guest-limit enforcement (error handling).
3.  **Flow 3: Security Admin Audit & Oversight**
    * Provides a view of immutable logs, searchable history, and real-time campus occupancy tracking.

---

## Key UI Features Implemented
* **Role-Based Dashboards:** Distinct interfaces for Guards (Utilitarian), Students (Simplified), and Admins (Data-Heavy).
* **State-Aware UI:** Buttons dynamically toggle based on visitor status (e.g., *Check-In* transforms to *Check-Out*).
* **Conflict Prevention:** Integrated error states for overbooked guest slots and expired visit windows.

---

## Halfway Point UML Diagrams
<img width="1410" height="495" alt="UML1" src="https://github.com/user-attachments/assets/855fc595-5012-4c3d-8819-ecce6d52317a" />

<img width="1430" height="319" alt="UML2" src="https://github.com/user-attachments/assets/66093b96-7412-4ca2-9da9-13446f3bb329" />

## Full-Point Extension for User Stories 18-21

The full-point implementation keeps the halfway UML pattern: activities own screens and input events, models represent Firestore documents, and rule/service classes hold reusable security logic.

| Story | Feature | Model class | Rule/service class | Firestore collection | Main UI connection |
|---:|---|---|---|---|---|
| 18 | Zone access logging | `ZoneAccessLog` | `ZoneAccessLogger` | `zone_access_logs`, mirrored to `access_logs` | `GuardDashboardActivity` writes every successful and failed scan; `AdminAuditActivity` displays the Zone Logs tab. |
| 19 | Blacklist management | `BlacklistEntry` | `BlacklistService` | `blacklist` | `AdminDashboardActivity` opens Manage Blacklist dialogs to add, expire, and deactivate entries. |
| 20 | Guard visual alerts | `Alert` | `AlertManager` | `alerts` | `GuardDashboardActivity` listens for unacknowledged high-priority alerts and blocks blacklisted scans. |
| 21 | Delivery lifecycle tracking | `DeliveryLog` | `DeliveryTrackingService` | `delivery_logs` | `GuardDashboardActivity` records rider, company, vehicle, destination, and receiving host/faculty details at entry; `AdminAuditActivity` displays Delivery Logs. |

### Class Connections

`GuardDashboardActivity` now coordinates gate actions:

1. CNIC scan goes through `BlacklistService.checkBlacklist`.
2. A blacklist hit calls `AlertManager.triggerHighPriorityAlert`, blocks access, and records a denied `ZoneAccessLog`.
3. A normal visitor lookup continues through the existing `VisitorRequest` flow and still writes `AuditLog` records for admin review.
4. Check-in, check-out, override, expired window, and missing approval outcomes are also written by `ZoneAccessLogger`.
5. Delivery Entry and Delivery Exit buttons delegate to `DeliveryTrackingService`, which creates or closes `DeliveryLog` records and flags riders over the allowed duration.

`AdminDashboardActivity` remains the request approval hub, with an added Manage Blacklist control. It delegates blacklist add/remove/expiry behavior to `BlacklistService` instead of embedding Firestore rules in the screen.

`AdminAuditActivity` keeps using `AuditLogAdapter`. Zone and delivery records are projected into `AuditLog` rows so existing audit UI behavior stays aligned with `AuditLogUtils`, while the source records remain strongly typed as `ZoneAccessLog` and `DeliveryLog`.


---

## Final Checkpoint UML Diagrams
**Domain Model / Firestore Documents**
<img width="2411" height="633" alt="DomainModel-UML1" src="https://github.com/user-attachments/assets/8c2554d4-9f5f-4c42-bbe3-9bc9ecde5000" />
**Rules and Service Layer**
<img width="1099" height="358" alt="RULES:SERVICES-UML2" src="https://github.com/user-attachments/assets/f777dacc-3cfd-48aa-a7ee-baf6009aee14" />
**Activity Controllers**
<img width="1200" height="1149" alt="CONTROLLERS-UML3" src="https://github.com/user-attachments/assets/96ef6c1d-6684-4285-9e17-e11dfabadd72" />
**RecyclerView Adapter Layer**
<img width="1096" height="352" alt="Adapter-UML4(updated)" src="https://github.com/user-attachments/assets/416f75af-b1c1-4a8b-af95-d02f93b560f9" />








