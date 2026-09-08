# DATA4CIRC Portal Workflows Documentation

## Overview

This document describes the main workflows defined and managed by the DATA4CIRC Portal. The portal is a Spring Boot web application that facilitates circular economy data management through organization onboarding, data connector management, and SPIP (Secure Privacy-Preserving Infrastructure Platform) integration.

**Version:** 1.0
**Last Updated:** 2025-11-08

---

## Table of Contents

1. [Authentication & User Management](#1-authentication--user-management)
2. [Organization Onboarding](#2-organization-onboarding)
3. [Organization Management](#3-organization-management)
4. [Connector Management](#4-connector-management)
5. [SPIP Integration](#5-spip-integration)
6. [Dashboard & Monitoring](#6-dashboard--monitoring)
7. [Email Notifications](#7-email-notifications)
8. [Security & Authorization](#8-security--authorization)

---

## 1. Authentication & User Management

### 1.1 User Login Workflow

**Purpose:** Authenticate users and provide secure access to the portal.

**Endpoints:**
- `POST /login` - Process login credentials
- `GET /logout` - Terminate user session

**User Roles:**
- `PLATFORM_ADMIN` - System-wide administration
- `ORG_ADMIN` - Organization management
- `SPIP_PRIVILEGED_USER` - SPIP platform access
- `ORG_MEMBER` - Basic organization member

**Workflow Steps:**

```
┌─────────────┐
│ User enters │
│ credentials │
└──────┬──────┘
       │
       ▼
┌─────────────────────┐
│ Spring Security     │
│ authenticates via   │
│ UserService         │
└──────┬──────────────┘
       │
       ▼
┌─────────────────────┐      ┌──────────────┐
│ Check organization  │─────▶│ Block login  │
│ status = INACTIVE?  │ YES  │ (disabled)   │
└──────┬──────────────┘      └──────────────┘
       │ NO
       ▼
┌─────────────────────┐
│ Generate JWT token  │
└──────┬──────────────┘
       │
       ▼
┌─────────────────────┐
│ Redirect to         │
│ Dashboard           │
└─────────────────────┘
```

**Components:**
- **Controller:** `AuthController`
- **Service:** `UserService` (implements `UserDetailsService`)
- **Security:** Spring Security `formLogin`, session-based
- **Entity:** `User`

**Key Features:**
- Session-based authentication via Spring Security's `formLogin`
- Organization status check prevents login for inactive orgs
- Session management with Spring Security

**Security Considerations:**
- Passwords are encrypted using BCrypt
- Session invalidated and cleared on logout

---

### 1.2 User Profile Management

**Purpose:** Allow users to view their profile information.

**Endpoints:**
- `GET /profile` - View current user profile

**Workflow Steps:**

```
┌─────────────────┐
│ User navigates  │
│ to /profile     │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Fetch user data │
│ with eager-load │
│ organization    │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Display profile │
│ - Username      │
│ - Email         │
│ - Role          │
│ - Organization  │
└─────────────────┘
```

**Components:**
- **Controller:** `ProfileController`
- **Service:** `UserService`
- **Entity:** `User`, `Organization`

**Note:** User registration is currently **disabled**. New users are created through the organization onboarding approval process.

---

## 2. Organization Onboarding

### 2.1 Public Organization Request Workflow

**Purpose:** Allow prospective organizations to submit onboarding requests to join the DATA4CIRC platform.

**Endpoints:**
- `GET /join` - Public onboarding form
- `POST /api/onboarding/request` - Submit request (REST API)
- `GET /api/onboarding/request/{id}` - Check request status

**Workflow Steps:**

```
┌──────────────────┐
│ Visitor goes to  │
│ /join (public)   │
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│ Fill form:       │
│ - Company info   │
│ - Contact info   │
│ - Goals/needs    │
│ - GDPR consent   │
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│ Frontend JS      │
│ validates all    │
│ required fields  │
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│ POST to API      │
│ (JSON payload)   │
└────────┬─────────┘
         │
         ▼
┌──────────────────┐      ┌─────────────┐
│ Backend          │─────▶│ Return      │
│ validates:       │ FAIL │ 400 error   │
│ - Unique email   │      │ with reason │
│ - Unique company │      └─────────────┘
│ - No existing org│
└────────┬─────────┘
         │ PASS
         ▼
┌──────────────────┐
│ Create request   │
│ Status: PENDING  │
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│ Send             │
│ confirmation     │
│ email            │
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│ Return success   │
│ with request ID  │
└──────────────────┘
```

**Form Fields (OrganizationOnboardingRequest):**

**Organization Information:**
- Company Name (required)
- Organization Type (required)
- Industry Sector (required)
- Organization Size (required)
- Address, City, Country, Postal Code
- Company Description
- Website URL

**Contact Information:**
- Contact Person Name (required)
- Title/Position
- Email Address (required, unique)
- Phone Number

**Participation Details:**
- Participation Goals/Objectives
- Data Types to Share
- Data Needs
- Current Systems/Technologies
- GDPR Consent (required checkbox)

**Components:**
- **Controller:** `OnboardingController`
- **Service:** `OnboardingRequestService`, `EmailService`
- **Entity:** `OrganizationOnboardingRequest`
- **DTO:** `OnboardingRequestDTO`

**Validation Rules:**
- Email must not exist in any pending/approved request
- Company name must not exist in any pending/approved request
- Organization with same name must not exist in system
- GDPR consent is mandatory
- All required fields must be provided

**Database State:**
- Request stored with `status = PENDING`
- Timestamps: `createdAt`, `updatedAt`
- Links to Organization when approved

---

### 2.2 Admin Approval/Rejection Workflow

**Purpose:** Platform administrators review and approve/reject organization onboarding requests.

**Endpoints:**
- `GET /admin/onboarding` - List all requests
- `GET /admin/onboarding/{id}` - View specific request
- `POST /admin/onboarding/{id}/approve` - Approve request
- `POST /admin/onboarding/{id}/reject` - Reject request

**Authorization:** Requires `PLATFORM_ADMIN` role

**Approval Workflow:**

```
┌──────────────────┐
│ Admin views      │
│ /admin/onboarding│
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│ List shows:      │
│ - Pending (top)  │
│ - All historical │
│ - Pending count  │
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│ Admin clicks     │
│ request to view  │
│ details          │
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│ Review all info: │
│ - Company data   │
│ - Contact info   │
│ - Goals/needs    │
│ - Can add notes  │
└────────┬─────────┘
         │
    ┌────┴────┐
    │         │
    ▼         ▼
┌────────┐ ┌─────────┐
│APPROVE │ │ REJECT  │
└───┬────┘ └────┬────┘
    │           │
    │           ▼
    │      ┌──────────────────┐
    │      │ Update request:  │
    │      │ - Status=REJECTED│
    │      │ - Store notes    │
    │      │ - Log timestamp  │
    │      └────────┬─────────┘
    │               │
    │               ▼
    │      ┌──────────────────┐
    │      │ Send rejection   │
    │      │ email with reason│
    │      └──────────────────┘
    │
    ▼
┌──────────────────────┐
│ TRANSACTIONAL:       │
│ 1. Create Org        │
│    - Copy all data   │
│    - Status: ACTIVE  │
│    - Set contact info│
└────────┬─────────────┘
         │
         ▼
┌──────────────────────┐
│ 2. Create Admin User │
│    - Email=username  │
│    - Role: ORG_ADMIN │
│    - Linked to org   │
│    - Random password │
└────────┬─────────────┘
         │
         ▼
┌──────────────────────┐
│ 3. Initialize SPIP   │
│    data:             │
│    - Encryption attrs│
│    - Decryption attrs│
│    - Policies        │
│    - Key statuses    │
└────────┬─────────────┘
         │
         ▼
┌──────────────────────┐
│ 4. Update request:   │
│    - Status=ORG_     │
│      CREATED         │
│    - Link to org     │
│    - Log admin/time  │
└────────┬─────────────┘
         │
         ▼
┌──────────────────────┐
│ 5. Send approval     │
│    email with:       │
│    - Username        │
│    - Temp password   │
│    - Login URL       │
└──────────────────────┘
```

**Created on Approval:**

1. **Organization Entity:**
   - Name, Type, Industry, Size (from request)
   - Address details (from request)
   - Website, Description (from request)
   - `certificationStatus = ACTIVE`
   - Contact information
   - Primary contact details

2. **User Entity (Organization Admin):**
   - Username: Contact email
   - Email: Contact email
   - Role: `ORG_ADMIN`
   - Organization: Linked to created org
   - Password: Randomly generated 12-character secure password
   - Enabled: `true`

3. **SPIP Initialization (Automatic):**

   When an organization is approved, the system automatically creates baseline SPIP (Secure Privacy-Preserving Infrastructure Platform) data:

   **a) Encryption Attributes:**
   - Data classifications (e.g., "Confidential", "Phone")
   - User roles (e.g., "developer")
   - User properties (university, country, age)
   - Organization-specific attributes

   **b) Decryption Attributes:**
   - Subset of encryption attributes for access control
   - User roles with decryption privileges
   - Supply chain management roles (logistics, procurement)
   - System attributes (current_date, location_country)

   **c) Encryption Policies:**
   - Supply chain manufacturer policies (e.g., "SupplyChainAirBus", "SupplyChainBoeing")
   - Conditions: `user:role-is-procurement`, `location:country-is-Germany`
   - Geographic and role-based access rules

   **d) Decryption Policies:**
   - Mirror encryption policies for data access
   - Attribute-based access control rules

   **e) Cryptographic Key Status:**
   - Encryption key: `ENC-{orgId}-{year}`
   - Decryption key: `DEC-{orgId}-{year}`
   - Status: ACTIVE
   - Expiration: 1 year from creation

   **Method:** `SpipService.initializeSpipDataForOrganization(organization)`

   **Note:** This initialization provides baseline SPIP configuration that organizations can customize later through the SPIP dashboard. These attributes and policies enable immediate use of attribute-based encryption for data sharing.

4. **Request Update:**
   - Status: `ORGANIZATION_CREATED`
   - Organization: Link to created org
   - `processedBy`: Admin username
   - `processedAt`: Timestamp
   - `adminNotes`: Optional notes

**Components:**
- **Controller:** `OnboardingController`
- **Services:** `OnboardingRequestService`, `OrganizationService`, `UserService`, `EmailService`, `SpipService`
- **Entities:** `OrganizationOnboardingRequest`, `Organization`, `User`, `SpipAttribute`, `SpipPolicy`, `SpipKeyStatus`

**Email Templates:**
- `email/onboarding-confirmation` - Sent on request submission
- `email/onboarding-approved` - Sent on approval with credentials
- `email/onboarding-rejected` - Sent on rejection with reason

**Audit Trail:**
- All actions logged with admin username and timestamp
- Request status transitions tracked
- Admin notes preserved

---

## 3. Organization Management

### 3.1 Organization Directory (Public View)

**Purpose:** Public directory of all organizations in the DATA4CIRC network.

**Endpoints:**
- `GET /organizations` - List all organizations
- `GET /organizations/{id}` - View organization details

**Authorization:** Public (no authentication required)

**Workflow:**

```
┌──────────────────┐
│ Any user visits  │
│ /organizations   │
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│ Display all orgs │
│ - Name, Type     │
│ - Industry       │
│ - Cert. status   │
│ - Member count   │
│ - Connector count│
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│ Click on org     │
│ for details      │
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│ View full info:  │
│ - Description    │
│ - Contact info   │
│ - Website        │
│ - Address        │
│ - Statistics     │
└──────────────────┘
```

**Components:**
- **Controller:** `OrganizationController`
- **Service:** `OrganizationService`
- **Entity:** `Organization`

**Displayed Information:**
- Organization profile (name, type, industry)
- Certification status badge
- Contact information (if provided)
- Website link
- Member count (total users)
- Connector count (total connectors)

---

### 3.2 Organization Administration

**Purpose:** Platform admins and organization admins manage organization details and status.

**Endpoints:**
- `GET /organizations/new` - Create organization form
- `POST /organizations/new` - Create organization
- `GET /organizations/{id}/edit` - Edit organization form
- `POST /organizations/{id}/edit` - Update organization
- `POST /organizations/{id}/status` - Change certification status
- `POST /organizations/{id}/delete` - Deactivate organization

**Authorization:**
- Create: `PLATFORM_ADMIN` only
- Edit: `PLATFORM_ADMIN` or (`ORG_ADMIN` of that organization)
- Status change: `PLATFORM_ADMIN` only
- Delete: `PLATFORM_ADMIN` only

**Create Organization Workflow:**

```
┌──────────────────┐
│ Admin clicks     │
│ "New Org"        │
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│ Fill form:       │
│ - Name (unique)  │
│ - Type           │
│ - Industry       │
│ - Contact info   │
│ - Address        │
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│ Validate unique  │
│ name             │
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│ Create org with  │
│ Status: ACTIVE   │
└──────────────────┘
```

**Certification Status Management:**

**Status Values:**
- `ACTIVE` - Normal operation (users can login)
- `SUSPENDED` - Temporarily blocked (policy violation)
- `INACTIVE` - Deactivated (users cannot login)
- `RESTRICTED` - Limited access (pending review)

**Status Change Workflow:**

```
┌──────────────────┐
│ Admin views org  │
│ page             │
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│ Select new       │
│ status from      │
│ dropdown         │
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│ POST to update   │
│ status endpoint  │
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│ Validate status  │
│ transition       │
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│ Update org       │
│ status           │
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│ Audit log:       │
│ - Org ID/name    │
│ - Old → New      │
│ - Admin user     │
│ - Timestamp      │
└──────────────────┘
```

**Impact on Users:**
- `INACTIVE` organizations → Users blocked at login via `UserService.loadUserByUsername()`
- `SUSPENDED` → Same as INACTIVE (users cannot login)
- `ACTIVE` → Users can login normally
- `RESTRICTED` → Users can login but may have limited access

**Components:**
- **Controller:** `OrganizationController`
- **Service:** `OrganizationService`
- **Entity:** `Organization`

**Soft Delete:**
- Delete operation sets `certificationStatus = INACTIVE`
- Organization data is preserved (not removed from database)
- Users cannot login but historical data remains

---

## 4. Connector Management

### 4.1 Connector Lifecycle

**Purpose:** Manage data integration endpoints for organizations.

**Connector Types:**
- `DATA_PROVIDER` - Provides datasets to the network
- `DATA_CONSUMER` - Consumes data from network
- `SPIP_AGENT` - SPIP platform integration agent
- `EXTERNAL_API` - External API integration
- `DATABASE` - Database connector
- `FILE_SYSTEM` - File system connector

**Connector Status:**
- `ONLINE` - Active and reporting heartbeats
- `OFFLINE` - Not reporting/unreachable
- `ERROR` - Reported errors or failures
- `MAINTENANCE` - Intentionally disabled

**Endpoints:**
- `GET /connectors` - List user's organization connectors
- `GET /connectors/{id}` - View connector details
- `GET /connectors/new` - Create connector form
- `POST /connectors/new` - Create connector
- `GET /connectors/{id}/edit` - Edit connector form
- `POST /connectors/{id}/edit` - Update connector
- `POST /connectors/{id}/delete` - Delete connector
- `POST /connectors/{id}/heartbeat` - Report status (for monitoring)

**Authorization:**
- All endpoints require authentication
- Create requires user to belong to organization
- Edit/Delete restricted to `ORG_ADMIN` or `PLATFORM_ADMIN`
- View restricted to same organization or `PLATFORM_ADMIN`

**Create Connector Workflow:**

```
┌──────────────────┐
│ User clicks      │
│ "New Connector"  │
└────────┬─────────┘
         │
         ▼
┌──────────────────┐      ┌─────────────┐
│ Verify user has  │─────▶│ Throw error │
│ organization?    │  NO  │ (must join) │
└────────┬─────────┘      └─────────────┘
         │ YES
         ▼
┌──────────────────┐
│ Fill form:       │
│ - Name           │
│ - Description    │
│ - Type (select)  │
│ - Endpoint URL   │
│ - Config (JSON)  │
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│ Create connector │
│ - Org: User's org│
│ - Status: OFFLINE│
│ - Timestamps     │
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│ Show success     │
│ message          │
└──────────────────┘
```

**View/Edit Connector Workflow:**

```
┌──────────────────┐
│ User navigates   │
│ to connector     │
└────────┬─────────┘
         │
         ▼
┌──────────────────┐      ┌─────────────┐
│ Check access:    │─────▶│ 403         │
│ Same org OR      │  NO  │ Forbidden   │
│ PLATFORM_ADMIN?  │      └─────────────┘
└────────┬─────────┘
         │ YES
         ▼
┌──────────────────┐
│ Display details: │
│ - Name, Type     │
│ - Description    │
│ - Endpoint       │
│ - Configuration  │
│ - Status         │
│ - Last Heartbeat │
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│ Edit allowed for │
│ ORG_ADMIN or     │
│ PLATFORM_ADMIN   │
└──────────────────┘
```

**Connector Monitoring Workflow:**

```
┌──────────────────┐
│ External system  │
│ sends heartbeat  │
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│ POST /connectors/│
│ {id}/heartbeat   │
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│ Update:          │
│ - status=ONLINE  │
│ - lastHeartbeat= │
│   now()          │
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│ Dashboard shows  │
│ updated online   │
│ count            │
└──────────────────┘
```

**Components:**
- **Controller:** `ConnectorController`
- **Service:** `ConnectorService`
- **Entity:** `Connector`

**Key Features:**
- Organization-scoped access control
- Real-time status monitoring via heartbeat
- JSON configuration storage
- Audit trail with timestamps

**Configuration Field:**
- Stores connector-specific configuration as JSON string
- Format is flexible and connector-type dependent
- Examples: API keys, connection strings, credentials

---

## 5. SPIP Integration

**Overview:**

The SPIP (Secure Privacy-Preserving Infrastructure Platform) integration enables attribute-based encryption for secure data sharing in the circular economy dataspace.

**Automatic Initialization:**

When an organization is approved during the onboarding process, the system automatically initializes baseline SPIP data for that organization:
- Encryption and decryption attributes
- Encryption and decryption policies
- Cryptographic key statuses

This ensures that newly onboarded organizations have immediate access to SPIP functionality without manual configuration. Organizations can later customize these attributes and policies through the SPIP dashboard.

**Method:** `SpipService.initializeSpipDataForOrganization(organization)` is called during `OnboardingRequestService.approveRequest()`

---

### 5.1 SPIP Dashboard Workflow

**Purpose:** View and manage SPIP (Secure Privacy-Preserving Infrastructure Platform) attributes, policies, and cryptographic keys for attribute-based access control.

**Endpoints:**
- `GET /spip` - SPIP Dashboard (main view)
- `GET /spip/attributes` - Attribute viewer (placeholder)
- `GET /spip/policies` - Policy management (placeholder)
- `GET /spip/agents` - Agent status (placeholder)

**Authorization:**
- Requires `PLATFORM_ADMIN`, `ORG_ADMIN`, or `SPIP_PRIVILEGED_USER`

**SPIP Data Entities:**
- **SpipAttribute** - Encryption/Decryption attributes (roles, classifications, locations)
- **SpipPolicy** - Encryption/Decryption policy rules
- **SpipKeyStatus** - Cryptographic key status and expiration

**Dashboard Workflow:**

```
┌──────────────────┐
│ User navigates   │
│ to /spip         │
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│ Determine org    │
│ context:         │
└────────┬─────────┘
         │
    ┌────┴────────────┐
    │                 │
    ▼                 ▼
┌────────────┐   ┌──────────────┐
│PLATFORM_   │   │ ORG_ADMIN or │
│ADMIN       │   │ SPIP_PRIV    │
└─────┬──────┘   └──────┬───────┘
      │                 │
      │                 ▼
      │          ┌──────────────┐
      │          │ Use assigned │
      │          │ organization │
      │          └──────┬───────┘
      │                 │
      ▼                 │
┌─────────────────┐     │
│ Show org        │     │
│ selector        │     │
│ dropdown        │     │
└─────┬───────────┘     │
      │                 │
      ▼                 │
┌─────────────────┐     │
│ Admin selects   │     │
│ organization    │     │
└─────┬───────────┘     │
      │                 │
      └────────┬────────┘
               │
               ▼
        ┌──────────────┐      ┌─────────────┐
        │ Org selected?│─────▶│ Show org    │
        │              │  NO  │ selector UI │
        └──────┬───────┘      └─────────────┘
               │ YES
               ▼
        ┌──────────────┐
        │ Fetch SPIP   │
        │ data for org:│
        │ - Attributes │
        │ - Policies   │
        │ - Key Status │
        └──────┬───────┘
               │
               ▼
        ┌──────────────┐      ┌─────────────┐
        │ Real data    │─────▶│ Display     │
        │ exists?      │  YES │ from DB     │
        └──────┬───────┘      └─────────────┘
               │ NO
               ▼
        ┌──────────────┐
        │ Generate     │
        │ mock data    │
        │ for demo     │
        └──────┬───────┘
               │
               ▼
        ┌──────────────┐
        │ Display:     │
        │ - Summary    │
        │ - Attributes │
        │ - Policies   │
        │ - Keys       │
        └──────────────┘
```

**SPIP Attribute Types:**

**Encryption Attributes** (for data protection):
- User roles (e.g., SupplyChainManagement)
- Data classifications (e.g., Confidential, Phone)
- User properties (university, country, age)
- Geographic locations
- Organizational units

**Decryption Attributes** (for data access):
- Subset of encryption attributes
- Role-based access (e.g., procurement, logistics)
- Clearance levels

**SPIP Policy Examples:**

**Encryption Policies:**
- Policy Name: `SupplyChainManufacturerAirBus`
- Condition: `(user:SupplyChainManagement-is-procurement OR user:SupplyChainManagement-is-logistics) AND location:country-is-Germany`
- Purpose: Define who can encrypt data for AirBus supply chain

**Decryption Policies:**
- Policy Name: `SupplyChainManufacturerBoeing`
- Condition: `user:SupplyChainManagement-is-procurement AND location:country-is-USA`
- Purpose: Define who can decrypt Boeing supply chain data

**Key Status Information:**
- Key ID (e.g., `ENC-{orgId}-2025`)
- Key Type (ENCRYPTION or DECRYPTION)
- Status (ACTIVE, INACTIVE, EXPIRED)
- Creation and expiration dates
- Associated organization

**Components:**
- **Controller:** `SpipController`
- **Service:** `SpipService`, `OrganizationService`
- **Entities:** `SpipAttribute`, `SpipPolicy`, `SpipKeyStatus`

**Mock Data Generation:**
- If no real SPIP data exists, service generates realistic mock data
- Mock includes supply chain management scenarios
- Demonstrates attribute-based access control concepts
- Mock keys with realistic expiration dates

**Current Implementation:**
- **Read-only view** of SPIP data
- Data fetched from database or generated as mock
- Real SPIP platform integration would require backend implementation
- Policy creation/modification would be added in future

---

## 6. Dashboard & Monitoring

### 6.1 Main Dashboard

**Purpose:** Provide system overview with key metrics and quick navigation.

**Endpoints:**
- `GET /` - Main dashboard
- `GET /dashboard` - Redirects to `/`

**Authorization:** Public access (but most content requires login)

**Dashboard Display:**

```
┌──────────────────────────────────┐
│  DATA4CIRC Portal Dashboard      │
├──────────────────────────────────┤
│                                  │
│  Statistics Cards:               │
│  ┌────────┐ ┌────────┐          │
│  │  Total │ │ Online │          │
│  │  Orgs  │ │ Connec.│          │
│  │   ##   │ │   ##   │          │
│  └────────┘ └────────┘          │
│                                  │
│  ┌────────┐ ┌────────┐          │
│  │Dataset │ │ Recent │          │
│  │ Count  │ │ Offers │          │
│  │   ##   │ │   ##   │          │
│  └────────┘ └────────┘          │
│                                  │
│  Recent Organizations:           │
│  - Organization 1                │
│  - Organization 2                │
│  - Organization 3                │
│  - Organization 4                │
│  - Organization 5                │
│                                  │
│  Quick Links:                    │
│  - Organizations Directory       │
│  - My Connectors                 │
│  - SPIP Dashboard (if auth'd)    │
│  - Onboarding (if admin)         │
│                                  │
└──────────────────────────────────┘
```

**Metrics Displayed:**
- **Total Organizations**: Count of all organizations in system
- **Online Connectors**: Count of connectors with `status = ONLINE`
- **Dataset Count**: Placeholder (currently 0)
- **Recent Offers**: Placeholder (currently 0)

**Recent Activity:**
- Shows last 5 created organizations
- Ordered by creation date (most recent first)

**Components:**
- **Controller:** `DashboardController`
- **Services:** `OrganizationService`, `ConnectorService`

**Navigation:**
- Role-based menu items
- Quick access to main features
- Conditional links based on user role

---

## 7. Email Notifications

### 7.1 Email Service Workflow

**Purpose:** Send automated email notifications for onboarding and system events.

**Email Types:**

#### 7.1.1 Onboarding Confirmation Email

**Trigger:** User submits organization onboarding request

**Template:** `email/onboarding-confirmation`

**Recipients:** Contact email from request

**Content:**
- Company name
- Contact person name
- Confirmation of request receipt
- Next steps and timeline expectations
- Contact information for questions

**Workflow:**
```
Request Submitted → Send Confirmation → Update Request
```

---

#### 7.1.2 Onboarding Approval Email

**Trigger:** Platform admin approves onboarding request

**Template:** `email/onboarding-approved`

**Recipients:** Contact email (new organization admin)

**Content:**
- Company name
- Contact person name
- **Login credentials:**
  - Username (email address)
  - Temporary password (randomly generated)
  - Login URL
- Next steps for first login
- Password change recommendation

**Workflow:**
```
Admin Approves → Create Org & User → Send Credentials → User Can Login
```

**Security:**
- Password is 12-character randomly generated secure string
- User should change password on first login
- Temporary password sent via email only once

---

#### 7.1.3 Onboarding Rejection Email

**Trigger:** Platform admin rejects onboarding request

**Template:** `email/onboarding-rejected`

**Recipients:** Contact email from request

**Content:**
- Company name
- Contact person name
- Rejection notification
- Reason for rejection (from admin notes)
- Contact information for appeals or questions
- Possibility to reapply

**Workflow:**
```
Admin Rejects → Update Request → Send Rejection Notice
```

---

### 7.2 Email Configuration

**Configuration Properties:**
```yaml
app:
  email:
    from: noreply@example.com  # Sender email
    enabled: true                    # Enable/disable emails
  base-url: http://localhost:8080    # Base URL for links
```

**Email Service Features:**
- **Template Engine:** Thymeleaf for HTML email generation
- **Variables:** Dynamic content injection into templates
- **Error Handling:** Email failures logged but don't interrupt business flows
- **Async Support:** Can be configured for asynchronous sending

**Components:**
- **Service:** `EmailService`
- **Templates:** `src/main/resources/templates/email/`
- **Config:** Spring Mail properties

**Testing:**
- Development mode can disable emails via `app.email.enabled=false`
- Email content logged for verification
- Test profile uses mock email service

---

## 8. Security & Authorization

### 8.1 Session-Based Authentication

**Purpose:** Browser authentication via Spring Security's own session mechanism.

**How it works:** Login is handled entirely by Spring Security's `formLogin` — `AuthController`
renders the login page and handles logout, but authentication itself (credential check, session
creation) is Spring Security's own filter chain, backed by `UserService` (`UserDetailsService`) and
BCrypt password matching. The session is a standard `HttpSession`/`JSESSIONID` cookie; there is no
token-based auth layer in the portal today.

*(A JWT-based layer — `JwtUtil`/`JwtAuthenticationFilter`, an in-memory token blacklist — existed in
this codebase but was removed as dead code: nothing ever issued a token, so nothing could ever
present one for the filter to validate. See git history for the removal if the design is ever
revisited — e.g. as part of a move to an external identity provider.)*

---

### 8.2 Role-Based Access Control

**User Roles and Permissions:**

| Role | Permissions |
|------|-------------|
| `PLATFORM_ADMIN` | - Full system access<br>- Manage all organizations<br>- Approve onboarding requests<br>- Change org certification status<br>- View all SPIP data (any org)<br>- Access actuator endpoints |
| `ORG_ADMIN` | - Manage own organization<br>- Manage organization members<br>- Create/edit connectors for org<br>- View org SPIP data<br>- Approve org membership requests |
| `SPIP_PRIVILEGED_USER` | - Access SPIP dashboard<br>- View org SPIP data<br>- Manage connectors for org |
| `ORG_MEMBER` | - View organization info<br>- View connectors for org<br>- Basic portal access |

**Authorization Annotations:**

```java
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'ORG_ADMIN')")
@PreAuthorize("isAuthenticated()")
```

**URL-Based Access Control:**

**Public Endpoints:**
- `/login`, `/register`, `/join`
- `/api/auth/**`, `/api/onboarding/**`
- `/h2-console/**` (development only)
- `/actuator/health`, `/actuator/info`
- Static resources: `/css/**`, `/js/**`, `/images/**`, `/webjars/**`

**Protected Endpoints:**
- `/admin/**` → `PLATFORM_ADMIN` only
- `/organization/admin/**` → `PLATFORM_ADMIN` or `ORG_ADMIN`
- `/spip/**` → `PLATFORM_ADMIN`, `ORG_ADMIN`, or `SPIP_PRIVILEGED_USER`
- `/actuator/**` → `PLATFORM_ADMIN` (except health/info)
- All others → Authenticated users

---

### 8.3 Session Management

**Configuration:**
- **Session Creation Policy:** `IF_REQUIRED` (created only when needed)
- **CSRF Protection:** Enabled (except for `/h2-console/**` and `/api/onboarding/**`)
- **CORS:** Configurable via `CorsConfigurationSource` bean

**Security Headers:**
- X-Frame-Options: DENY
- X-Content-Type-Options: nosniff
- X-XSS-Protection: 1; mode=block

**Password Security:**
- BCrypt password encoding
- Configurable BCrypt strength
- Password validation on registration
- Temporary passwords for new admins

---

## 9. Entity Relationships

### 9.1 Core Entity Diagram

```
┌─────────────────────┐
│   Organization      │
│  ─────────────────  │
│  - id (PK)          │
│  - name (unique)    │
│  - type             │
│  - industry         │
│  - certStatus       │
│  - contactInfo      │
│  - address          │
│  - website          │
└──────┬──────────────┘
       │
       │ 1:N
       │
       ├──────────────┐
       │              │
       ▼              ▼
┌──────────────┐ ┌──────────────┐
│     User     │ │  Connector   │
│ ──────────── │ │ ──────────── │
│ - id (PK)    │ │ - id (PK)    │
│ - username   │ │ - name       │
│ - email      │ │ - type       │
│ - password   │ │ - endpoint   │
│ - role       │ │ - status     │
│ - enabled    │ │ - config     │
│ - orgId (FK) │ │ - orgId (FK) │
└──────────────┘ └──────────────┘

┌──────────────────────┐
│ OnboardingRequest    │
│ ──────────────────── │
│ - id (PK)            │
│ - companyName        │
│ - contactEmail       │
│ - status             │
│ - orgId (FK, null)   │
│ - processedBy        │
│ - processedAt        │
└──────────────────────┘

┌──────────────────────┐
│   Organization       │
└──────┬───────────────┘
       │ 1:N
       ├──────────────┬──────────────┬
       ▼              ▼              ▼
┌──────────────┐ ┌─────────────┐ ┌─────────────┐
│SpipAttribute │ │ SpipPolicy  │ │SpipKeyStatus│
│ ──────────── │ │ ─────────── │ │ ─────────── │
│ - id (PK)    │ │ - id (PK)   │ │ - id (PK)   │
│ - attrType   │ │ - policyType│ │ - keyId     │
│ - name       │ │ - name      │ │ - status    │
│ - value      │ │ - condition │ │ - expiresAt │
│ - orgId (FK) │ │ - orgId (FK)│ │ - orgId (FK)│
└──────────────┘ └─────────────┘ └─────────────┘
```

### 9.2 Status Enumerations

**OrganizationCertificationStatus:**
- `ACTIVE` - Normal operation
- `SUSPENDED` - Temporarily blocked
- `INACTIVE` - Deactivated
- `RESTRICTED` - Limited access

**OnboardingRequestStatus:**
- `PENDING` - Awaiting review
- `ORGANIZATION_CREATED` - Approved
- `REJECTED` - Declined

**ConnectorStatus:**
- `ONLINE` - Active and reporting
- `OFFLINE` - Not reachable
- `ERROR` - Errors reported
- `MAINTENANCE` - Disabled intentionally

**UserRole:**
- `PLATFORM_ADMIN`
- `ORG_ADMIN`
- `SPIP_PRIVILEGED_USER`
- `ORG_MEMBER`

---

## 10. Workflow Sequence Diagrams

### 10.1 Complete Onboarding Flow

```
Visitor        API           Service          Database        Email
  │             │              │                │              │
  ├─GET /join──→│              │                │              │
  │←───form─────┤              │                │              │
  │             │              │                │              │
  ├─POST────────→│              │                │              │
  │  request    │──validate───→│                │              │
  │             │              ├──create req───→│              │
  │             │              │                │              │
  │             │              │────────────────┼──send conf──→│
  │             │←─success─────┤                │              │
  │←───200 OK───┤              │                │              │
  │             │              │                │              │

Admin          Portal        Service          Database        Email
  │             │              │                │              │
  ├─GET admin/onboarding─────→│                │              │
  │             │──list reqs──→│──query─────────→              │
  │←───list─────┤              │                │              │
  │             │              │                │              │
  ├─POST approve/{id}─────────→│                │              │
  │             │              │──TRANSACTION───→              │
  │             │              │  1.Create Org  │              │
  │             │              │  2.Create User │              │
  │             │              │  3.Init SPIP   │              │
  │             │              │    - Attrs     │              │
  │             │              │    - Policies  │              │
  │             │              │    - Keys      │              │
  │             │              │  4.Update Req  │              │
  │             │              │←───commit──────┤              │
  │             │              │────────────────┼──send creds─→│
  │←───success──┤              │                │              │
  │             │              │                │              │
```

### 10.2 Connector Monitoring Flow

```
Connector     API            Service         Database
  │            │               │               │
  ├─heartbeat─→│               │               │
  │  POST      │──update───────→│               │
  │            │               ├──set ONLINE──→│
  │            │               ├──set time────→│
  │            │               │←──success─────┤
  │            │←──200 OK──────┤               │
  │←───ACK─────┤               │               │
  │            │               │               │

(if no heartbeat for X minutes)

Dashboard     Service         Database
  │            │               │
  ├─GET /─────→│               │
  │            ├──count────────→│
  │            │  ONLINE        │
  │            │←──count N─────┤
  │←──stats────┤               │
  │  Online:N  │               │
```

---

## 11. Configuration Profiles

### Development Profile (`dev`)
```yaml
spring:
  profiles: dev
  datasource:
    url: jdbc:h2:mem:d4c_portal_dev
  h2:
    console:
      enabled: true
      path: /h2-console
  jpa:
    hibernate:
      ddl-auto: create-drop
```

### Production Profile (`prod`)
```yaml
spring:
  profiles: prod
  datasource:
    url: jdbc:postgresql://localhost:5432/d4c_portal
  jpa:
    hibernate:
      ddl-auto: validate
```

### Test Profile (`test`)
```yaml
spring:
  profiles: test
  datasource:
    url: jdbc:h2:mem:testdb
  jpa:
    hibernate:
      ddl-auto: create-drop
```

---

## 12. Sample Development Users

| Username | Password | Role | Organization |
|----------|----------|------|--------------|
| admin | Demo_Admin#2025! | PLATFORM_ADMIN | N/A |
| john.doe | password | ORG_ADMIN | GreenTech Solutions |
| jane.smith | password | SPIP_PRIVILEGED_USER | EcoRecycle Corp |
| mike.wilson | password | ORG_MEMBER | Agricultural Plastics Institute |

---

## 13. API Endpoints Summary

### Authentication
- `POST /login` - User login
- `GET /logout` - User logout

### Dashboard
- `GET /` - Main dashboard
- `GET /dashboard` - Dashboard (redirect)

### Organizations
- `GET /organizations` - List all (public)
- `GET /organizations/{id}` - View details (public)
- `GET /organizations/new` - Create form (admin)
- `POST /organizations/new` - Create (admin)
- `GET /organizations/{id}/edit` - Edit form
- `POST /organizations/{id}/edit` - Update
- `POST /organizations/{id}/status` - Change status (admin)
- `POST /organizations/{id}/delete` - Deactivate (admin)

### Onboarding
- `GET /join` - Public onboarding form
- `POST /api/onboarding/request` - Submit request (public API)
- `GET /api/onboarding/request/{id}` - Check status (public API)
- `GET /admin/onboarding` - List requests (admin)
- `GET /admin/onboarding/{id}` - View request (admin)
- `POST /admin/onboarding/{id}/approve` - Approve (admin)
- `POST /admin/onboarding/{id}/reject` - Reject (admin)

### Connectors
- `GET /connectors` - List user's connectors
- `GET /connectors/{id}` - View connector
- `GET /connectors/new` - Create form
- `POST /connectors/new` - Create
- `GET /connectors/{id}/edit` - Edit form
- `POST /connectors/{id}/edit` - Update
- `POST /connectors/{id}/delete` - Delete
- `POST /connectors/{id}/heartbeat` - Report status

### SPIP
- `GET /spip` - SPIP dashboard
- `GET /spip/attributes` - View attributes
- `GET /spip/policies` - View policies
- `GET /spip/agents` - View agents

### Profile
- `GET /profile` - View user profile

---

## 14. Future Enhancements

### Planned Workflows

1. **User Management for Org Admins**
   - Invite users to organization
   - Manage user roles within org
   - Remove users from organization

2. **Data Catalog Integration**
   - Dataset registration workflow
   - Dataset discovery and search
   - Data sharing agreements

3. **Advanced SPIP Features**
   - Create/edit attributes
   - Create/edit policies
   - Key rotation workflow
   - Attribute assignment to users

4. **Notification System**
   - In-app notifications
   - Email notification preferences
   - Notification history

5. **Audit Logging**
   - Comprehensive audit trail
   - Audit log viewer
   - Export audit logs

6. **API Documentation**
   - Swagger/OpenAPI integration
   - API key management
   - Rate limiting

---

## Appendix A: Troubleshooting

### Common Issues

**Issue:** Users from INACTIVE organization cannot login
- **Cause:** Organization certification status check in `UserService.loadUserByUsername()`
- **Solution:** Platform admin must change organization status to ACTIVE

**Issue:** Emails not being sent
- **Check:** `app.email.enabled` configuration
- **Check:** SMTP server configuration
- **Solution:** Enable emails and configure SMTP settings

**Issue:** JWT token invalid after logout
- **Expected:** Tokens are blacklisted on logout
- **Solution:** User must login again to get new token

**Issue:** Cannot create connector
- **Cause:** User not assigned to organization
- **Solution:** User must be member of organization first

---

## Appendix B: Database Schema Notes

### Key Tables

- `organizations` - Organization directory
- `users` - System users with roles
- `connectors` - Data integration endpoints
- `organization_onboarding_requests` - Onboarding workflow
- `spip_attributes` - SPIP encryption/decryption attributes
- `spip_policies` - SPIP access control policies
- `spip_key_status` - Cryptographic key information

### Relationship Constraints

- Users must belong to one organization (FK constraint)
- Connectors must belong to one organization (FK constraint)
- Onboarding requests optionally link to organization (when approved)
- SPIP entities all link to organization (FK constraint)

---

## Appendix C: Security Best Practices

1. **Password Management**
   - Use strong passwords (enforced by BCrypt)
   - Change temporary passwords on first login
   - Never share credentials

2. **Token Management**
   - Protect JWT tokens (don't expose in logs)
   - Logout properly to blacklist tokens
   - Tokens expire after configured time

3. **Role Assignment**
   - Follow principle of least privilege
   - Audit role assignments regularly
   - Review PLATFORM_ADMIN access

4. **Organization Status**
   - INACTIVE status blocks all user logins
   - Use SUSPENDED for temporary blocks
   - Document status changes in audit log

---

**Document End**
