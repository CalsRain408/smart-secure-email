
# Smart Secure Mail

## 1. Project Overview

Smart Secure Mail is a full-stack, enterprise-oriented
intelligent and secure email system.

The project is developed from scratch as a local,
demonstrable, testable application.

The primary objective is to deliver a working email
system with secure communication, isolated domains,
intelligent features, and reproducible tests.

This is a B2B product, not a consumer email clone.

### Core Principles

1. Security by design.
2. Correctness before feature complexity.
3. Simplicity over premature abstraction.
4. Clear separation of frontend and backend.
5. Every critical feature must be testable.
6. No simulated success for unfinished functionality.
7. Prioritize assignment requirements over extras.

## 2. Source of Truth

The original examination requirements are authoritative.

Read these documents before implementing major features:

- docs/requirements.md
- docs/architecture.md
- docs/protocol.md
- docs/security.md

If a requirement conflicts with an implementation choice,
follow the assignment and document the decision.

Never silently remove or weaken a required feature.

## 3. Technology Stack

### Frontend
- React
- TypeScript
- Vite
- Ant Design
- React Router
- TanStack Query

### Backend
- Java 21
- Spring Boot 3
- Spring Security
- Spring Data JPA
- PostgreSQL
- Redis

### Infrastructure
- Docker Compose
- Isolated local attachment storage
- Two independently configured server instances

### Testing
- JUnit 5
- Spring Boot Test
- Testcontainers
- Vitest
- Playwright

Avoid introducing additional frameworks unless necessary.
Explain any significant dependency addition.

## 4. Architecture

Use a modular monolithic architecture.

Main components:

1. Web Client
2. Mail Server
3. Inter-server Delivery Module
4. Authentication and Authorization Module
5. Mail Storage Module
6. Attachment Storage Module
7. Intelligence Module
8. Security and Audit Module

The server must support configuration-based deployment
for two independent email domains.

Example:

- domain-a.test
- domain-b.test

Each domain must have independent:

- Server process
- User records
- Mail records
- Attachment directory
- Database or database namespace
- Authentication and authorization boundaries

The two server instances may communicate only through
the documented inter-server protocol.

Direct access to another domain's private data,
database tables, or attachment directories is forbidden.

Do not implement microservices unless explicitly needed.

## 5. Required Features

All assignment requirements are mandatory unless
explicitly marked as optional.

### 5.1 Authentication

Implement:

- User registration
- Login and logout
- Password confirmation
- Password hashing
- Session or token authentication
- Login rate limiting
- Temporary lockout after repeated failures

Never store plaintext passwords.

### 5.2 Mail Management

Implement:

- Compose and send emails
- Inbox
- Sent folder
- Draft folder
- Group sending
- Contact groups
- Reply and quick reply suggestions
- Image attachments
- Mail recall
- Mail quick actions

Users may access only authorized mail resources.

### 5.3 Cross-domain Delivery

Support:

- Server A sending to Server B
- Server B sending to Server A
- Local delivery within one domain
- Explicit delivery status
- Failure handling
- Retry-safe message delivery

Use a documented application-level transport protocol.

Do not depend on a mature email server implementation.

Do not claim full SMTP/IMAP compatibility unless it
has actually been implemented and tested.

### 5.4 Intelligence

Implement at least two algorithmic enhancements.

Preferred initial features:

1. Keyword extraction and mail classification
2. Fuzzy mail search

Also implement basic phishing/spam detection because
it is an explicit security requirement.

Intelligent capabilities must have deterministic,
testable behavior.

External AI services are optional, not required.

### 5.5 Enterprise Features

Support a simple enterprise workspace model:

- Organization
- Organization members
- Contact groups
- Basic role-based access control
- Security event auditing

Roles may include:

- ADMIN
- MEMBER

Do not build complex enterprise administration
features before the required email flow works.

## 6. Security Requirements

Security is a non-negotiable part of the implementation.

### 6.1 Authentication and Sessions

- Use Spring Security.
- Hash passwords using Argon2id or BCrypt.
- Never log credentials, tokens, or secrets.
- Use TLS for authenticated communication.
- Prefer HttpOnly, Secure, SameSite cookies for
  browser authentication.
- Implement CSRF protection when using cookie auth.
- Validate session expiration and logout behavior.
- Reject unauthorized requests on the server.

### 6.2 Authorization

- Never trust user IDs supplied by the frontend.
- Derive the acting user from authenticated context.
- Enforce ownership checks for mail operations.
- Enforce organization and domain isolation.
- Reject cross-user and cross-tenant access.
- Apply authorization to every sensitive endpoint.

### 6.3 Input Validation

- Validate all request payloads.
- Apply size limits to request bodies and attachments.
- Use parameterized database queries.
- Prevent SQL injection, XSS, and path traversal.
- Escape untrusted content when rendering email bodies.
- Do not execute scripts contained in email content.
- Sanitize HTML if rich-text mail is supported.

### 6.4 Attachments

- Accept only explicitly allowed image formats.
- Validate file signatures, not just extensions.
- Generate server-side storage identifiers.
- Never use user-supplied paths as storage paths.
- Enforce authorization for attachment downloads.
- Apply limits on file size and image dimensions.
- Isolate storage by domain or tenant.

Attachment deduplication must not bypass
authorization or storage isolation.

### 6.5 Rate Limiting

Apply rate limiting to:

- Login
- Registration
- Mail sending
- Attachment uploading
- Inter-server requests

Return meaningful errors without exposing secrets.

### 6.6 Mail Recall

Mail recall must be treated as a sensitive operation.

- Authenticate the caller.
- Verify original sender ownership.
- Verify message ID and delivery state.
- Check recall policy and allowed scope.
- Make repeated recall requests idempotent.
- Record recall attempts in audit logs.

Cross-domain recall must use authenticated,
integrity-protected server-to-server requests.

Never claim a remote mail was deleted unless the
receiving server confirms the operation.

Recall is best-effort, not guaranteed deletion
from external copies or previously downloaded content.

### 6.7 AI Safety

- Treat email content as untrusted input.
- Never execute instructions embedded in emails.
- AI suggestions must not trigger sensitive actions
  automatically.
- Sending, deleting, and recalling require explicit
  user authorization.
- Do not transmit private mail to external AI providers
  without documented configuration and consent.

## 7. API and Protocol Design

Use REST-style JSON APIs for client-server operations.

API prefix:

/api/v1

Use consistent response structures.

Document:

- Request schemas
- Response schemas
- Authentication
- Error codes
- Pagination
- File upload/download behavior

For inter-server mail delivery:

- Define an explicit envelope schema.
- Use stable message identifiers.
- Authenticate peer servers.
- Protect transport confidentiality and integrity.
- Validate recipient domains.
- Reject unknown peers where applicable.
- Apply replay protection and idempotency.
- Implement clear delivery acknowledgment semantics.

Use reliable cryptographic libraries.
Do not invent custom cryptographic algorithms.

## 8. Backend Development Rules

Follow a domain-oriented package structure.

Each module should contain only necessary layers,
such as:

- Controller
- Service
- Repository
- Entity
- DTO
- Mapper

Guidelines:

1. Controllers handle HTTP concerns.
2. Services implement business rules.
3. Repositories handle persistence.
4. DTOs define external contracts.
5. Entities must not be exposed directly as API responses.
6. Apply transaction boundaries explicitly.
7. Handle errors using centralized exception handling.
8. Use structured logging.
9. Never suppress exceptions silently.
10. Avoid unnecessary abstraction and duplication.

All database schema changes must use versioned migrations.

## 9. Frontend Development Rules

Build a professional enterprise email interface.

Design principles:

- Clean and consistent B2B visual language
- Clear information hierarchy
- Efficient workflows
- Responsive layouts
- Accessible controls
- Explicit feedback for destructive operations

Required views:

- Login and Registration
- Inbox
- Sent
- Drafts
- Mail Composer
- Mail Detail
- Contacts and Groups
- Basic Account Settings

Use TypeScript strictly.

Keep API logic outside presentation components.

Use TanStack Query for server state.

Handle loading, empty, error, and success states.

Never rely on frontend validation alone.

Do not use mock responses in production flows.

## 10. Testing Requirements

Every implemented feature must have reproducible tests.

### Unit Tests

Cover:

- Business rules
- Input validation
- Authorization decisions
- Mail state transitions
- Classification and spam rules

### Integration Tests

Cover:

- Registration and login
- Sending and receiving mail
- Draft persistence
- Group sending
- Image attachment handling
- Mail recall
- Cross-domain communication

### Security Tests

Cover:

- Brute-force login protection
- High-frequency send protection
- Unauthorized mail access
- Domain isolation
- Invalid recall requests
- Malicious HTML and XSS attempts
- Attachment path traversal
- Phishing/spam sample detection

### Concurrency Tests

Verify multiple clients can concurrently:

- Login
- Send emails
- Receive emails
- Read inbox contents

Test failures must be reported rather than hidden.

## 11. Implementation Workflow

Follow this order unless a dependency requires otherwise.

Phase 1: Project initialization
- Create repository structure
- Configure frontend and backend
- Configure database and migrations
- Add Docker Compose and example environments

Phase 2: Core mail infrastructure
- Domain configuration
- User model
- Authentication
- Mail persistence
- Local mail delivery

Phase 3: Cross-domain communication
- Server-to-server protocol
- Peer authentication
- Cross-domain delivery
- Delivery acknowledgments and retry handling

Phase 4: Mail client
- Authentication screens
- Inbox, sent, drafts
- Compose and reply
- Groups and attachments

Phase 5: Security features
- Rate limiting
- Access control
- Safe attachment handling
- Recall verification
- Spam/phishing detection

Phase 6: Intelligence
- Search
- Keyword extraction
- Classification
- Quick reply recommendations
- Useful mail actions

Phase 7: Validation and delivery
- Integration tests
- Security tests
- Concurrency tests
- Documentation
- Final demo verification

Maintain an end-to-end runnable system at each phase.

## 12. Agent Working Rules

Before making changes:

1. Inspect the relevant existing code.
2. Identify the affected modules.
3. Check project requirements and architecture.
4. Explain the implementation plan for major changes.
5. Prefer incremental, reviewable modifications.

During implementation:

1. Reuse existing patterns.
2. Avoid unnecessary new dependencies.
3. Keep code readable and maintainable.
4. Preserve public contracts unless changes are planned.
5. Add tests for business-critical behavior.
6. Never introduce hardcoded credentials.
7. Never disable security to make tests pass.
8. Never silently change requirements.

After implementation:

1. Run relevant tests.
2. Check formatting and compilation.
3. Verify affected API contracts.
4. Update relevant documentation.
5. Summarize changes and verification results.
6. Report remaining risks and limitations.

Do not report a feature as complete without verification.

Do not fabricate test results.

When a command fails, report the failure and investigate
its cause rather than claiming success.

## 13. Definition of Done

A feature is complete only when:

- Required behavior is implemented.
- Server-side authorization is enforced.
- Input validation is present.
- Relevant success and failure cases are tested.
- No new critical security issue is introduced.
- Documentation reflects actual behavior.
- The feature works through the real client/server flow.

The project is ready for submission only when:

- Two isolated servers start successfully.
- Both domains can exchange mail.
- Client workflows are functional.
- Required intelligent features work.
- Security protections are demonstrable.
- Tests and their results are reproducible.
- Startup scripts and configuration are included.
- Protocol and threat-model documentation are complete.

## 14. Communication

- Use Chinese for explanations, plans, and summaries.
- Use English for code identifiers.
- Follow existing naming conventions.
- Use concise English technical comments when needed.
- State assumptions explicitly.
- Ask before making high-impact architectural changes.

Prioritize practical implementation,
security correctness, and demonstrable results.
