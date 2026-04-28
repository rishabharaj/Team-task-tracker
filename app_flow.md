# 🔄 Task Tracker - Application Flow

This document outlines the step-by-step user journey through the Task Tracker app, detailing which Activities and database interactions are used at each step.

---

### Step 1: App Launch & Authentication Check
**What happens:** When the app is opened, it checks if an active user session exists.
- **Component Used:** `LoginActivity.java` (usually serves as the entry point).
- **Backend Service:** `FirebaseAuth.getInstance().getCurrentUser()`
- **Flow Logic:** 
  - If `currentUser != null`, proceed directly to check Team status.
  - If `currentUser == null`, display the Login UI.

---

### Step 2: User Login
**What happens:** The user clicks the "Sign in with Google" button.
- **Component Used:** `LoginActivity.java`, Google Sign-In Client.
- **Backend Service:** Firebase Authentication.
- **Flow Logic:**
  - An Intent to the Google Sign-In API is launched.
  - User selects their Google account.
  - An authentication token is generated and passed to Firebase.
  - Upon success, the app routes to the Team Selection screen (or Dashboard if a team is already cached).

---

### Step 3: Team Selection (Create or Join)
**What happens:** The user needs to associate themselves with a team to see tasks. They can either create a new team or join an existing one using a 6-digit code.
- **Component Used:** `CreateTeamActivity.java` (or JoinTeamActivity depending on specific implementation).
- **Backend Service:** Firebase Realtime Database.
- **Flow Logic:**
  - **Create Team:** Generates a random 6-digit code. Creates a new node in the Database under `/teams/{teamCode}`. Adds the current user as an Admin under `/teams/{teamCode}/members`.
  - **Join Team:** Validates the entered 6-digit code against the Database. If it exists, adds the user to `/teams/{teamCode}/members`.
  - **Local Storage:** The `teamCode` is saved in Android's `SharedPreferences` for future sessions.
  - Redirects to Dashboard.

---

### Step 4: The Dashboard (Main Hub)
**What happens:** The user views the overall status of the project, including statistics and the list of active tasks.
- **Component Used:** `DashboardActivity.java`, `RecyclerView`, Custom Adapters.
- **Backend Service:** Firebase Realtime Database.
- **Flow Logic:**
  - The app retrieves the `teamCode` from `SharedPreferences`.
  - Attaches a `ValueEventListener` to `/teams/{teamCode}/tasks`.
  - As data is fetched, the `RecyclerView` adapter is populated with tasks.
  - UI updates to show statistics (e.g., total tasks, completed tasks).
  - Navigation elements allow opening the Team Members list or Creating a new Task.

---

### Step 5: Creating a Task
**What happens:** The user wants to add a new task to the team's board.
- **Component Used:** `CreateTaskActivity.java`
- **Backend Service:** Firebase Realtime Database.
- **Flow Logic:**
  - User fills out details: Title, Description, Priority (High/Med/Low), and Status.
  - When "Save" is clicked, a unique key is generated for the task using `databaseReference.push().getKey()`.
  - A Task object is created and pushed to `/teams/{teamCode}/tasks/{taskId}`.
  - Activity finishes and returns to Dashboard, which automatically updates thanks to the real-time listener.

---

### Step 6: Viewing Team Members
**What happens:** The user checks who else is in the team.
- **Component Used:** `TeamMembersActivity.java`, `RecyclerView`, Glide (for images).
- **Backend Service:** Firebase Realtime Database.
- **Flow Logic:**
  - Attaches a listener to `/teams/{teamCode}/members`.
  - Fetches the user profiles (Name, Email, Photo URL).
  - Glide loads the profile pictures into the `RecyclerView` rows.

---

### Step 7: Logging Out
**What happens:** The user decides to sign out of the app.
- **Component Used:** Menu/Settings in `DashboardActivity.java`.
- **Backend Service:** Firebase Authentication.
- **Flow Logic:**
  - Calls `FirebaseAuth.getInstance().signOut()`.
  - Clears the `teamCode` from `SharedPreferences`.
  - Redirects the user back to `LoginActivity.java`.
