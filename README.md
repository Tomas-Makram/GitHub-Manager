# 🐙 GitHub Manager

> A full-featured, secure web application for managing GitHub repositories — built with Spring Boot 4 and GitHub OAuth2.

[![Java](https://img.shields.io/badge/Java-17+-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1.2-6DB33F?style=flat-square&logo=spring&logoColor=white)](https://spring.io/projects/spring-boot)
[![Thymeleaf](https://img.shields.io/badge/Thymeleaf-3.x-005F0F?style=flat-square&logo=thymeleaf&logoColor=white)](https://www.thymeleaf.org/)
[![GitHub OAuth2](https://img.shields.io/badge/Auth-GitHub_OAuth2-181717?style=flat-square&logo=github&logoColor=white)](https://docs.github.com/en/apps/oauth-apps)
[![License](https://img.shields.io/badge/License-MIT-blue?style=flat-square)](LICENSE)

---

## 📋 Table of Contents

- [Overview](#-overview)
- [Features](#-features)
- [Architecture](#-architecture)
- [Technology Stack](#-technology-stack)
- [Project Structure](#-project-structure)
- [Getting Started](#-getting-started)
- [Configuration](#-configuration)
- [API Endpoints](#-api-endpoints)
- [Caching System](#-caching-system)
- [Security](#-security)
- [Internationalization](#-internationalization)
- [Frontend Features](#-frontend-features)
- [Error Handling](#-error-handling)
- [Validation](#-validation)

---

## 🔭 Overview

**GitHub Manager** is a production-ready Spring Boot web application that provides a clean, unified dashboard for managing your GitHub repositories. It uses **GitHub OAuth2** for secure authentication, calls the **GitHub REST API v3** for all operations, and delivers a fast, responsive UI via **Thymeleaf** and **Vanilla JavaScript**.

The application is designed with:
- **Security-first** approach — per-user cache isolation, CSRF protection, strict security headers
- **Performance** in mind — Caffeine-backed caching with tuned TTLs for each data type
- **Full i18n support** — Arabic (RTL) and English (LTR) out of the box
- **Smart upload** — real-time progress via SSE, diff-based uploads, and `.gitignore` enforcement

---

## ✨ Features

### 🏠 Home Dashboard
- Lists all your GitHub repositories (owned, collaborator, organization member)
- Repository cards with name, visibility badge (Public / Private), description, language, stats
- Filter by: All, Public, Private, Forks, Sources
- Real-time search by repository name
- One-click cache refresh to get fresh data from GitHub

### 📁 Repository Management
- **View** repository details: stats (stars, forks, watchers, open issues), description, branches, README
- **Create** new repository with name, description, and visibility settings
- **Change visibility** (Public ↔ Private)
- **Edit description** inline
- **Delete** repository with typed confirmation (must type the exact repo name)
- Rendered **README** with sanitized HTML, safe image proxying for private repos, and smooth link handling

### 🌿 Branch Management
- View all branches for a repository
- **Create** new branch from the default branch
- **Rename** any branch
- **Delete** any branch (with confirmation)

### 📤 Smart File Upload
- Upload individual files or entire folders
- **Real-time progress** via Server-Sent Events (SSE)
- **Diff-based upload** — compares local files with GitHub to only upload new or modified files
- **Force upload** mode — wipes commit history and replaces with a fresh commit
- Upload to any branch or the default branch
- **Secrets scanner** — detects API keys, tokens, and credentials before upload
- **`.gitignore` enforcement** — respects local `.gitignore` rules automatically
- **Custom `.gitignore` editor** — add exclusion patterns before uploading; appends to existing remote `.gitignore` if present on GitHub
- File size cap: 100 MB per file
- Parallel upload with a 4-thread pool + graceful shutdown on application stop

### 📋 Bulk Repository Operations
- Select multiple repositories and apply actions in one click
- **Make Private** — up to 25 repositories at once (with typed confirmation)
- **Make Public** — up to 25 repositories at once (with typed confirmation: type `PUBLIC`)
- **Delete** — up to 10 repositories at once (with typed confirmation: type `DELETE`)
- 150 ms cooldown between operations to respect GitHub rate limits

### 🌐 Internationalization
- Full **Arabic (RTL)** and **English (LTR)** support
- Language switch without page reload (`?lang=ar` / `?lang=en`)
- Language stored in session; validation error messages translated

### 🔔 Toast Notification System
- All success/error/info messages shown as floating toasts — no browser `alert()` dialogs
- Auto-dismiss with smooth animation
- Stacked notifications for sequential operations

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────┐
│                    Browser (Thymeleaf + JS)          │
│  index.html · repo-details.html · new-repo.html     │
│  Toast system · Secret scanner · GitIgnore engine   │
└──────────────────────┬──────────────────────────────┘
                       │ HTTP / SSE
┌──────────────────────▼──────────────────────────────┐
│               Spring MVC Controllers                 │
│  HomeController · RepositoryController              │
│  BranchController · UploadController                │
│  BulkController · AuthController                   │
│  AppErrorController                                 │
└──────────────────────┬──────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────┐
│                   Service Layer                      │
│  GitHubUserService     · GitHubRepositoryService    │
│  GitHubBranchService   · GitHubContentService       │
│  GitHubUploadService   · GitHubBulkService          │
│  HomePageService       · RepositoryPageService      │
│  CacheEvictionService  · RepoValidationService      │
│  MessageService        · GitHubErrorService         │
│  ErrorPageService      · LoginPageService           │
└──────────────────────┬──────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────┐
│              GitHubApiClient (Core HTTP)             │
│  RestClient (JDK 11 HTTP) · Retry with backoff      │
│  Pagination (up to 10 pages) · Bearer Auth headers  │
└──────────────────────┬──────────────────────────────┘
                       │ HTTPS
┌──────────────────────▼──────────────────────────────┐
│               GitHub REST API v3                     │
│           api.github.com                            │
└─────────────────────────────────────────────────────┘
```

### Cache Layer (Caffeine)

```
Cache Name        │ TTL   │ Max Size │ Key Pattern
──────────────────┼───────┼──────────┼──────────────────────────────
github-profile    │ 10 m  │ 200      │ username
github-repos      │  5 m  │ 200      │ username
github-repo       │  5 m  │ 500      │ username:owner:repo
github-branches   │  5 m  │ 500      │ username:owner:repo
github-issues     │  3 m  │ 500      │ username:owner:repo
github-readme     │ 10 m  │ 300      │ username:owner:repo
```

> **Security note:** Cache keys include the authenticated username — this ensures two GitHub users cannot see each other's cached private repository data.

---

## 🛠️ Technology Stack

| Category | Technology |
|---|---|
| **Language** | Java 17 |
| **Framework** | Spring Boot 4.1.2 |
| **Web** | Spring MVC (WebMVC) |
| **Templates** | Thymeleaf 3 + thymeleaf-extras-springsecurity6 |
| **Security** | Spring Security + GitHub OAuth2 |
| **Validation** | Jakarta Bean Validation (Hibernate Validator) |
| **HTTP Client** | Spring RestClient (JDK HttpClient backend) |
| **Caching** | Spring Cache + Caffeine |
| **Build Tool** | Maven |
| **Utility** | Lombok |
| **Frontend** | Vanilla JavaScript (ES6+), CSS3 |
| **Real-time** | Server-Sent Events (SSE) |

---

## 📂 Project Structure

```
src/main/
├── java/com/tomas/Github_Manager/
│   ├── GithubManagerApplication.java       # Entry point
│   │
│   ├── config/
│   │   ├── CacheConfig.java                # Caffeine cache setup (6 caches, TTLs, sizes)
│   │   ├── SecurityConfig.java             # OAuth2, security headers, AJAX 401 handler
│   │   └── WebConfig.java                  # i18n, locale resolver, validator wiring
│   │
│   ├── controller/
│   │   ├── ControllerSupport.java          # Shared helpers: token(), principal(), repoRedirect()
│   │   ├── AuthController.java             # GET/POST /login, /logout-success, /logout-confirm
│   │   ├── HomeController.java             # GET /, POST /cache/refresh
│   │   ├── RepositoryController.java       # CRUD for repositories + file-content endpoint
│   │   ├── BranchController.java           # Create / Delete / Rename branch
│   │   ├── UploadController.java           # Upload plan (diff), SSE progress, folder upload
│   │   ├── BulkController.java             # Bulk private/public/delete
│   │   └── AppErrorController.java         # Custom /error handler (implements ErrorController)
│   │
│   ├── service/
│   │   ├── GitHubApiClient.java            # Core HTTP client: REST calls, retry, pagination
│   │   ├── GitHubUserService.java          # User profile + repository list (cacheable)
│   │   ├── GitHubRepositoryService.java    # Repo details, issues, visibility, delete (cacheable)
│   │   ├── GitHubBranchService.java        # Branch list, create, delete, rename (cacheable)
│   │   ├── GitHubContentService.java       # README HTML, raw files, image proxy, .gitignore fetch
│   │   ├── GitHubUploadService.java        # Smart upload: diff tree, parallel blob upload, SSE
│   │   ├── GitHubBulkService.java          # Bulk operations with validation and rate limiting
│   │   ├── GitHubErrorService.java         # Maps GitHub HTTP errors to i18n messages
│   │   ├── CacheEvictionService.java       # Evicts user and repo caches after write operations
│   │   ├── HomePageService.java            # Builds HomeView record for home page
│   │   ├── RepositoryPageService.java      # Builds RepositoryPage record (repo+issues+branches+readme)
│   │   ├── RepoValidationService.java      # Validates owner/repo name pattern (regex safeguard)
│   │   ├── MessageService.java             # Locale-aware message resolution
│   │   ├── ErrorPageService.java           # Maps HTTP status codes to error page i18n keys
│   │   └── LoginPageService.java           # Login error flag logic
│   │
│   ├── model/                              # Form DTOs with Bean Validation annotations
│   │   ├── CreateRepoForm.java             # @NotBlank, @Size, @Pattern for new repo
│   │   ├── UpdateDescriptionForm.java      # @Size(max=350) for description
│   │   ├── CreateBranchForm.java           # @NotBlank, @Size, @Pattern for branch name
│   │   ├── RenameBranchForm.java           # @NotBlank for old + new branch names
│   │   ├── DeleteRepoForm.java             # @NotBlank for confirmation field
│   │   └── BulkActionForm.java             # @NotBlank action, @NotEmpty repos list
│   │
│   └── exception/
│       ├── GitHubServiceException.java     # Domain exception: status + i18n message key + args
│       └── GlobalExceptionHandler.java     # @ControllerAdvice: handles 413, ResponseStatus, Validation
│
└── resources/
    ├── application.properties              # App config (OAuth2, multipart limits, etc.)
    ├── messages.properties                 # English UI strings
    ├── messages_ar.properties              # Arabic UI strings
    │
    ├── static/
    │   ├── css/
    │   │   └── toast.css                   # Toast notification styles
    │   ├── js/
    │   │   ├── toast.js                    # Toast manager (success / info / error)
    │   │   ├── gitignore.js                # Client-side .gitignore engine (glob matching, negation, **)
    │   │   └── secret-scanner.js           # Pattern-based secrets detector (API keys, tokens, etc.)
    │   ├── favicon_light.png
    │   └── favicon_dark.png
    │
    └── templates/
        ├── login.html                      # GitHub OAuth2 login page
        ├── logout.html                     # Post-logout page
        ├── logout-confirm.html             # Logout confirmation dialog
        ├── index.html                      # Home dashboard (repo list, bulk, search, filters)
        ├── new-repo.html                   # Create repository form
        ├── repo-details.html               # Repository details (upload, branches, issues, README)
        └── error.html                      # Unified error page (auto-redirect in 5s)
```

---

## 🚀 Getting Started

### Prerequisites

- **Java 17+** (tested up to JDK 26)
- **Maven 3.8+** (or use the included `mvnw` wrapper)
- A **GitHub OAuth App** (see below)

### 1. Create a GitHub OAuth App

1. Go to **GitHub → Settings → Developer Settings → OAuth Apps → New OAuth App**
2. Fill in:
   - **Application name:** GitHub Manager (or any name)
   - **Homepage URL:** `http://localhost:8080`
   - **Authorization callback URL:** `http://localhost:8080/login/oauth2/code/github`
3. Click **Register application**
4. Copy your **Client ID** and generate a **Client Secret**

### 2. Configure the Application

Open `src/main/resources/application.properties` and set:

```properties
spring.security.oauth2.client.registration.github.client-id=YOUR_CLIENT_ID
spring.security.oauth2.client.registration.github.client-secret=YOUR_CLIENT_SECRET
```

### 3. Build and Run

```bash
# Using Maven wrapper (recommended)
./mvnw spring-boot:run

# Or build first, then run the JAR
./mvnw clean package -DskipTests
java -jar target/myGithub-Manager-1.0.1.jar
```

Open your browser at: **[http://localhost:8080](http://localhost:8080)**

---

## ⚙️ Configuration

Key settings in `application.properties`:

| Property | Description | Default |
|---|---|---|
| `spring.security.oauth2.client.registration.github.client-id` | GitHub OAuth App Client ID | *(required)* |
| `spring.security.oauth2.client.registration.github.client-secret` | GitHub OAuth App Client Secret | *(required)* |
| `spring.servlet.multipart.max-file-size` | Max size per uploaded file | `105MB` |
| `spring.servlet.multipart.max-request-size` | Max total upload request size | `210MB` |
| `server.port` | HTTP port | `8080` |

---

## 🌐 API Endpoints

### Pages (HTML)

| Method | Path | Description |
|---|---|---|
| `GET` | `/` | Home dashboard — repository list |
| `GET` | `/login` | Login page |
| `GET` | `/logout-confirm` | Logout confirmation |
| `GET` | `/logout-success` | Post-logout page |
| `GET` | `/repos/new` | New repository form |
| `GET` | `/repos/{owner}/{repoName}` | Repository details page |
| `GET` | `/error` | Custom error page |

### Repository Actions (Form POST → Redirect)

| Method | Path | Description |
|---|---|---|
| `POST` | `/repos/new` | Create a new repository |
| `POST` | `/repos/{owner}/{repoName}/visibility` | Toggle public/private |
| `POST` | `/repos/{owner}/{repoName}/description` | Update description |
| `POST` | `/repos/{owner}/{repoName}/delete` | Delete repository (requires typed confirmation) |
| `POST` | `/repos/{owner}/{repoName}/cache/refresh` | Evict repository cache |
| `POST` | `/cache/refresh` | Evict home page (user) cache |

### Branch Actions (Form POST → Redirect)

| Method | Path | Description |
|---|---|---|
| `POST` | `/repos/{owner}/{repoName}/branches` | Create new branch |
| `POST` | `/repos/{owner}/{repoName}/branches/delete` | Delete a branch |
| `POST` | `/repos/{owner}/{repoName}/branches/rename` | Rename a branch |

### Upload API (AJAX / JSON / SSE)

| Method | Path | Description |
|---|---|---|
| `GET` | `/repos/{owner}/{repoName}/upload-plan` | Get tree snapshot for diff comparison |
| `GET` | `/repos/{owner}/{repoName}/upload-progress/{uploadId}` | SSE stream for upload progress |
| `POST` | `/repos/{owner}/{repoName}/upload-folder` | Upload files (multipart) |

### Content API (AJAX / JSON)

| Method | Path | Description |
|---|---|---|
| `GET` | `/repos/{owner}/{repoName}/file-content` | Fetch a file's raw content (e.g., `.gitignore`) |
| `GET` | `/repos/{owner}/{repoName}/raw/{*path}` | Proxy for private repo README images |

### Bulk API (AJAX / JSON)

| Method | Path | Description |
|---|---|---|
| `POST` | `/repos/bulk/action` | Execute bulk action (private / public / delete) |

---

## ⚡ Caching System

The application uses **Caffeine** (in-process cache) with 6 independent caches, each scoped per user:

```
CacheEvictionService
  ├── evictUserCaches(username)
  │     ├── CACHE_PROFILE   → evict(username)
  │     └── CACHE_REPOS     → evict(username)
  │
  └── evictRepoCaches(username, owner, repo)
        ├── CACHE_REPO      → evict(username:owner:repo)
        ├── CACHE_BRANCHES  → evict(username:owner:repo)
        ├── CACHE_ISSUES    → evict(username:owner:repo)
        └── CACHE_README    → evict(username:owner:repo)
```

**Cache eviction is triggered automatically** after every write operation (create, update, delete, rename). Users can also manually trigger a refresh via the **"Refresh without cache"** button on any page.

---

## 🔐 Security

### OAuth2 Authentication
- Login via **GitHub OAuth2** only — no passwords stored
- `prompt=select_account` added to every OAuth2 request so GitHub always asks which account to use
- Session cookie (`JSESSIONID`) invalidated on logout
- Cookies cleared on logout

### Security Headers (configured in `SecurityConfig`)
- `X-Frame-Options: DENY` — prevents clickjacking
- `X-Content-Type-Options: nosniff` — prevents MIME sniffing
- `Referrer-Policy: strict-origin-when-cross-origin`

### Session Expiry Handling
- If a session expires during an **AJAX/JSON request**, the server returns `HTTP 401` (instead of redirecting to HTML login), so the JavaScript can handle it gracefully

### Input Validation
- All `owner` and `repo` path parameters validated against `^[A-Za-z0-9._-]{1,100}$` before being used in GitHub API URLs
- Form DTOs validated with Bean Validation (`@Valid` + `BindingResult`) in every controller method
- Uploaded file paths sanitized (no `..` traversal, no backslashes, max 300 characters)

### Cache Isolation
- Cache keys always include the authenticated `username` — prevents one user from reading another user's cached private repository data

---

## 🌍 Internationalization

The application is fully bilingual:

| Language | File | Direction |
|---|---|---|
| English | `messages.properties` | LTR |
| Arabic | `messages_ar.properties` | RTL |

**How it works:**
- Language is stored in the HTTP **session** via `SessionLocaleResolver`
- Language can be changed by appending `?lang=ar` or `?lang=en` to any URL
- HTML `dir` attribute is set dynamically by Thymeleaf: `th:dir="${#locale.language == 'ar' ? 'rtl' : 'ltr'}"`
- All validation error messages are translated (connected via `LocalValidatorFactoryBean`)

**Covered message keys include:**
- Navigation, login, logout
- Repository operations and errors
- Branch operations and errors
- Upload progress, errors, and statuses
- Bulk operation labels and confirmations
- Error page titles and messages (400, 401, 403, 404, 405, 413, 429, 500, 503)
- `.gitignore` and secrets scanner UI text
- Form validation messages

---

## 🖥️ Frontend Features

### File Tree with `.gitignore` Support
- Interactive file tree rendered entirely in JavaScript
- Collapsible directory nodes
- Per-file checkbox for selecting files to upload
- **`.gitignore` enforcement:** ignored files are disabled (checkbox locked), displayed with strikethrough text
- **Remote `.gitignore` import:** if no local `.gitignore` is present, the app fetches the one from GitHub and applies its rules

### Client-side `.gitignore` Engine (`gitignore.js`)
Full implementation in Vanilla JS with support for:
- `#` comments
- `!` negation rules
- Directory-only rules (`/` suffix)
- `**` glob patterns (e.g., `**/node_modules/`)
- `*` and `?` wildcards
- Anchored vs. unanchored rules
- Nested `.gitignore` files within subdirectories

### Secrets Scanner (`secret-scanner.js`)
Before upload, every file is scanned for patterns that look like:
- API keys (AWS, Google, GitHub personal access tokens)
- Private keys (`-----BEGIN ... KEY-----`)
- Database connection strings
- `.env`-style `SECRET=...` assignments
- And more

If secrets are found, a warning dialog is shown with:
- File name and line number
- Option to **cancel**, **exclude flagged files**, or **proceed anyway**

### Toast Notification System (`toast.js` + `toast.css`)
- Three types: `success`, `info`, `error`
- Auto-dismiss after 4 seconds
- Up to 5 toasts stacked simultaneously (oldest removed first)
- RTL-aware positioning

### Smart Upload Flow
```
1. User selects files / folder
2. .gitignore rules applied (local + remote)
3. Secrets scanner runs on all selected files
4. [Optional] Upload plan fetched — compares with GitHub (SHA-based diff)
5. Progress shown via SSE stream (file-by-file)
6. On completion, page reloads automatically
```

---

## 🚨 Error Handling

### `GlobalExceptionHandler` (`@ControllerAdvice`)
Handles globally:
- `MaxUploadSizeExceededException` → 413 page or JSON
- `ResponseStatusException` → HTML error page or JSON
- `GitHubServiceException` → i18n error page or JSON
- `MethodArgumentNotValidException` → 400 page or JSON

### `AppErrorController` (implements `ErrorController`)
Handles `/error` route with a friendly page that:
- Shows the HTTP status code and a translated message
- Auto-redirects to home after 5 seconds

### Supported Status Codes
`400` · `401` · `403` · `404` · `405` · `413` · `429` · `500` · `503`

---

## ✅ Validation

All form submissions are validated on the **server side** using Jakarta Bean Validation:

| Form | Validated Fields | Rules |
|---|---|---|
| `CreateRepoForm` | `repoName` | `@NotBlank`, `@Size(1–100)`, `@Pattern(^[A-Za-z0-9._-]+$)` |
| `CreateRepoForm` | `description` | `@Size(max=350)` |
| `UpdateDescriptionForm` | `newDescription` | `@Size(max=350)` |
| `CreateBranchForm` | `newBranchName` | `@NotBlank`, `@Size(max=250)`, `@Pattern` |
| `RenameBranchForm` | `oldBranchName`, `newBranchName` | `@NotBlank`, `@Size`, `@Pattern` |
| `DeleteRepoForm` | `confirmName` | `@NotBlank` |
| `BulkActionForm` | `action` | `@NotBlank` |
| `BulkActionForm` | `repos` | `@NotEmpty` |

Validation errors are displayed:
- **Below the relevant field** in forms (Thymeleaf `th:errors`)
- As a **toast notification** for AJAX operations

---

## 👥 Contributing

1. Fork the repository
2. Create your feature branch: `git checkout -b feature/your-feature`
3. Commit your changes: `git commit -m "Add your feature"`
4. Push to the branch: `git push origin feature/your-feature`
5. Open a Pull Request

---

## 📄 License

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for details.

---

<p align="center">
  Built with ❤️ using Spring Boot · Thymeleaf · GitHub REST API
</p>
