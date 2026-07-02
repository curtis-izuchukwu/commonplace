# ParārePilot Architecture

ParārePilot uses a layered JavaFX architecture. Each layer has a clear responsibility and communicates only with the layer below it.

## Architecture Layers

| Layer                     | Responsibility                                                                                  |
| ------------------------- | ----------------------------------------------------------------------------------------------- |
| JavaFX FXML + Controllers | Handles the user interface, button clicks, screen updates, and user input                       |
| Service Layer             | Contains business logic, application rules, scoring, recommendations, and workflow coordination |
| Repository Layer          | Handles database access through JDBC                                                            |
| SQLite Database           | Stores modules, topics, worksheets, questions, attempts, answers, mistakes, and user statistics |

## Layer Overview

### Model Layer

The model layer contains plain Java records and enums representing application data.

Examples:

| Model              | Purpose                                                     |
| ------------------ | ----------------------------------------------------------- |
| `StudyModule`      | Represents a study module or course area                    |
| `Topic`            | Represents a topic within a module                          |
| `Worksheet`        | Represents a worksheet assigned to a topic                  |
| `Question`         | Represents a question inside a worksheet                    |
| `WorksheetAttempt` | Represents a user's attempt at completing a worksheet       |
| `Answer`           | Represents a user's answer to a question                    |
| `MistakeBankItem`  | Represents a stored mistake for later review                |
| `UserStats`        | Represents overall user progress and performance statistics |

Models do not contain JavaFX, SQL, or controller logic.

---

### Repository Layer

The repository layer is responsible for database access through JDBC.

Repositories create, read, update, and delete SQLite records.

Examples:

| Repository            | Purpose                                  |
| --------------------- | ---------------------------------------- |
| `ModuleRepository`    | Stores and retrieves study modules       |
| `TopicRepository`     | Stores and retrieves topics              |
| `WorksheetRepository` | Stores and retrieves worksheets          |
| `QuestionRepository`  | Stores and retrieves worksheet questions |
| `AttemptRepository`   | Stores and retrieves worksheet attempts  |
| `AnswerRepository`    | Stores and retrieves submitted answers   |
| `MistakeRepository`   | Stores and retrieves mistake bank items  |
| `UserStatsRepository` | Stores and retrieves user statistics     |

Repositories should not contain JavaFX controller logic or business decision-making.

---

### Service Layer

The service layer contains business behaviour and application rules.

Examples:

| Service                     | Purpose                                                  |
| --------------------------- | -------------------------------------------------------- |
| `WorksheetSelectionService` | Selects worksheets for the user based on weighting rules |
| `PriorityScoreService`      | Calculates worksheet priority scores                     |
| `AttemptService`            | Handles worksheet attempt creation and completion        |
| `ReflectionService`         | Processes post-attempt reflection and confidence updates |
| `TopicStatsService`         | Calculates topic-level progress and mastery              |
| `GamificationService`       | Handles streaks, XP, rewards, and motivational features  |
| `DashboardService`          | Provides summarised data for the dashboard               |

Services coordinate repositories and apply application rules.

---

### UI Layer

The UI layer contains JavaFX FXML screens and controllers.

Controllers handle button clicks, display data, and call services.

They do not directly perform SQL queries.

Examples:

| UI Component        | Purpose                                           |
| ------------------- | ------------------------------------------------- |
| FXML files          | Define the structure of JavaFX screens            |
| Controllers         | Handle user actions and update the UI             |
| View models or DTOs | Optional objects used to prepare data for display |

Controllers should call services rather than repositories directly.

## Main Data Flow

### Worksheet Creation and Attempt Flow

| Step | Action                                               |
| ---- | ---------------------------------------------------- |
| 1    | User creates a worksheet                             |
| 2    | Worksheet and questions are saved to SQLite          |
| 3    | User attempts the worksheet                          |
| 4    | Answers and marks are saved                          |
| 5    | Reflection updates worksheet stats and topic mastery |
| 6    | Mistakes are stored in the mistake bank              |
| 7    | Weighted selector recommends future worksheets       |

## Architecture Rule Summary

| Rule                             | Explanation                                          |
| -------------------------------- | ---------------------------------------------------- |
| Models stay simple               | Models only represent data                           |
| Controllers stay UI-focused      | Controllers handle screens, input, and display logic |
| Services own behaviour           | Services contain business rules and workflow logic   |
| Repositories own persistence     | Repositories handle SQLite queries and updates       |
| SQL stays out of controllers     | Controllers should not directly query the database   |
| JavaFX stays out of repositories | Repositories should not know anything about the UI   |

## Dependency Direction

| From             | Calls                                                            |
| ---------------- | ---------------------------------------------------------------- |
| UI Layer         | Service Layer                                                    |
| Service Layer    | Repository Layer                                                 |
| Repository Layer | SQLite Database                                                  |
| Model Layer      | Used across UI, services, and repositories as plain data objects |

The dependency direction should stay one-way. Higher layers may depend on lower layers, but lower layers should not depend on higher layers.
