# OverloadAlert

**OverloadAlert** is a high-performance Android application designed to help runners prevent injuries by analyzing training load and predicting physiological risk. By integrating with Strava and Google Calendar, it provides an intelligent, adaptive training schedule based on the **Acute:Chronic Workload Ratio (ACWR)**.

---

## 🏗 Architecture Overview

The project is built using **Clean Architecture** principles and follows the **MVVM (Model-View-ViewModel)** design pattern. This ensures a strict separation of concerns, making the codebase highly testable, maintainable, and scalable.

### Layers:
1.  **UI Layer (Presentation):** Built with **Jetpack Compose** and **Material 3**. It uses ViewModels to manage state and Mappers to transform domain data into UI-ready models.
2.  **Domain Layer (Business Logic):** The core of the app. It contains pure Kotlin business logic, Use Cases, and Repository interfaces. It is completely independent of Android frameworks.
3.  **Data Layer (Infrastructure):** Handles data persistence (Room SQLite), network communication (Retrofit/Strava/Google), and repository implementations.

---

## 📂 Project Structure

```text
kth.nova.overloadalert
├── data
│   ├── adapter         # Custom Moshi type adapters (e.g., Color, LocalDate)
│   ├── local           # Room Database, DAOs, and File Storage (Plan/Analysis)
│   ├── remote          # Retrofit API services, DTOs, and Auth Interceptors
│   └── repository      # Concrete implementations of Repository interfaces
├── domain
│   ├── model           # Pure domain data classes (Run, Analysis, Plan)
│   ├── repository      # Repository interfaces (The boundary between layers)
│   └── usecases        # Reusable business logic (AnalyzeRunData, PlanGenerator)
├── ui
│   ├── screens         # Feature-based packages (Home, Graphs, History, Plan)
│   │   └── [feature]
│   │       ├── Screen.kt       # Declarative UI
│   │       ├── ViewModel.kt    # State management
│   │       ├── UiState.kt      # UI-specific data models
│   │       └── UiMapper.kt     # Logic to map domain models -> UI state
│   └── theme           # Material 3 theme and styling
├── di                  # Manual Dependency Injection (AppComponent)
└── worker              # Background tasks (WorkManager) and Notifications
```

---

## 🚀 Key Performance Features

OverloadAlert is engineered for efficiency and low battery consumption through several advanced techniques:

### 1. Incremental Analysis Engine
Unlike traditional apps that re-calculate your entire history on every launch, OverloadAlert uses a **Cached Analysis** system. When new runs are synced, the analyzer only processes the \"tail\" of the data (typically the last 40 days) to update rolling averages, saving massive amounts of CPU power.

### 2. \"What-If\" Simulation Planning
The `WeeklyTrainingPlanGenerator` performs day-by-day simulations of future runs. It uses a specialized **Simulation Mode** that performs fast, in-memory math to predict future injury risk without touching the database, allowing for complex plan optimization in milliseconds.

### 3. Intelligent Synchronization
The `RunningRepository` employs a smart fetch strategy. It bootstraps with a long history (120 days) but performs daily syncs fetching only the last 5 days of data, minimizing network traffic.

---

## 🛠 Tech Stack

- **UI:** Jetpack Compose, Material 3
- **Asynchronous Logic:** Kotlin Coroutines & Flow
- **Local Database:** Room (SQLite)
- **Networking:** Retrofit & OkHttp
- **JSON Serialization:** Moshi
- **Charts:** MPAndroidChart
- **Background Tasks:** WorkManager
- **External APIs:** Strava (Activities), Google Calendar (Sync)
- **Dependency Injection:** Manual Injection (Service Locator pattern via `AppComponent`)

---

## 🔐 Permissions & Security

- **Internet:** Required for Strava and Google API communication.
- **Notifications:** Used to alert the user of training risk changes.
- **OAuth 2.0:** Secure authentication for both Strava and Google, with tokens managed via Android's `EncryptedSharedPreferences`.

---

## 👨‍💻 Developer Notes

This project was developed as part of a mobile application development course, focusing on implementing advanced APIs and optimizing for mobile constraints (battery, memory, and responsiveness).
