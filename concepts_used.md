# 💡 Core Concepts Used in Task Tracker App

This document outlines the fundamental software development concepts and specific Android/Firebase features utilized to build the Task Tracker application.

## 1. Cloud-Based Authentication (OAuth 2.0)
The app uses **Firebase Authentication** combined with Google's OAuth 2.0 implementation. 
- **Concept:** Delegating user authentication to a secure, trusted third-party (Google) rather than handling passwords directly. 
- **Application:** Users sign in with their Google accounts, and the app receives a secure token and profile metadata (Name, Email, Profile Picture).

## 2. NoSQL Realtime Database
- **Concept:** Storing data in a flexible, JSON-like tree structure rather than strict tables with rows and columns.
- **Application:** Using **Firebase Realtime Database** to store Teams, Tasks, and User profiles. Data is structured hierarchically. When a user updates a task status, the database instantly pushes that change to all other team members connected to the app via WebSockets.

## 3. Event-Driven Programming
- **Concept:** The flow of the program is determined by events (e.g., user actions, sensor outputs, or messages from other programs/threads).
- **Application:** We use Firebase `ValueEventListeners`. The app doesn't constantly ask the database "Are there new tasks?". Instead, it attaches a listener and waits. When the database changes, an event fires, and the app's UI updates automatically.

## 4. The ViewHolder Pattern & Recycling Views
- **Concept:** Efficiently displaying large datasets by reusing UI components rather than creating new ones as the user scrolls.
- **Application:** Implemented via **RecyclerView** and custom **Adapters**. When a task item scrolls off the top of the screen, its layout is "recycled" and moved to the bottom to display the next task, saving memory and preventing lag.

## 5. Explicit Intents & Context Passing
- **Concept:** Android's way of communicating between different application components (like screens/Activities).
- **Application:** Using `Intent` to navigate from `LoginActivity` to `DashboardActivity`, or passing data (like a Team Code or Task ID) between screens using `Intent.putExtra()`.

## 6. Material Design Guidelines
- **Concept:** A design system created by Google to help build high-quality digital experiences.
- **Application:** The app utilizes **Material Components** for Android, such as `MaterialCardView`, `FloatingActionButton` (FAB), and custom Shape Themes (rounded corners, specific color palettes, drop shadows) to create a modern, tactile UI.

## 7. Asynchronous Image Loading & Caching
- **Concept:** Fetching images from the internet on a background thread so the main UI thread doesn't freeze, and caching them locally to save bandwidth on subsequent loads.
- **Application:** Utilizing the **Glide** library. With a single line of code, Glide fetches the Google profile picture URL, resizes it, handles the background thread, caches it, and applies it to an `ImageView` (often shaped as a circle).

## 8. Multi-Tenant Architecture (Team Isolation)
- **Concept:** Designing the software so that multiple distinct groups of users (Teams) share the same application instance and database, but their data is strictly isolated from one another.
- **Application:** The database structure groups data by a unique "Team Code". When users query the database, they use their associated team code as the root path, ensuring they never see or modify tasks belonging to a different team.
