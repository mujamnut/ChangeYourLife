# Phase 0 Foundation

## Goal

Create a buildable base for the Android app and backend before adding product features.

## Completed In This Step

- Root Gradle setup.
- Android module scaffold.
- Ktor backend module scaffold.
- Compose theme and starter app shell.
- Hilt application setup.
- Room database foundation for workspaces, pages, tasks, and reminders.
- Repository interfaces and local repository implementations.
- Home ViewModel connected to Room-backed counts and recent pages.
- Basic page editor route with local title and content editing.
- Basic task creation route with local open task tracking.
- Ktor `/health` endpoint.
- Backend PostgreSQL connection setup with Flyway migrations.
- JWT register/login/me auth routes with in-memory fallback for local development.
- Android Retrofit API client for backend auth.
- Android register/login screen, token persistence, auth-gated navigation, and logout.
- Workspace create/switch flow with active workspace persistence.
- Workspace-scoped Home counts, recent pages, open tasks, and task/page creation.
- Basic subpage creation and navigation inside the page editor.
- Subpage properties with Notion-style property type selection.
- Subpage creation is hidden inside subpages to avoid recursive page loops.
- Task edit flow with priority, due date, and reminder controls.
- Task-linked reminders with Android notification scheduling and Home reminder cards.
- Block editor MVP backed by serialized page content with text, heading, todo, bullet, quote, divider, and database table blocks.
- Block insert, delete, todo toggle, and manual move up/down controls.
- Database table block with editable title, columns, rows, cells, and Table/List/Board/Calendar/Gallery/Timeline/Dashboard views.

## Next Phase 0 Work

- Add search.
- Add more database views on top of the table model: map, feed, and richer chart/dashboard widgets.
- Add richer block editing polish such as keyboard-driven insertion, drag-and-drop reorder, and text formatting.
