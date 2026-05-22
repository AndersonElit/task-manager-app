# Plan: Frontend React — Task Manager

## Stack tecnológico

| Herramienta | Rol |
|---|---|
| **React 19 + Vite** | Framework y bundler |
| **TypeScript** | Tipado estático |
| **Tailwind CSS v4** | Estilos utilitarios |
| **TanStack Query v5** | Cache y sincronización de servidor |
| **React Router v7** | Ruteo declarativo |
| **Axios** | Cliente HTTP con interceptor JWT |
| **React Hook Form + Zod** | Formularios y validación |

---

## Arquitectura de carpetas

```
frontend/
├── public/
├── src/
│   ├── api/                    # Capa de acceso a datos
│   │   ├── client.ts           # Axios instance + interceptor Bearer
│   │   ├── tasks.api.ts        # Endpoints CRUD de tareas
│   │   └── auth.api.ts         # Login contra Cognito (Floci)
│   │
│   ├── hooks/                  # Custom hooks (TanStack Query)
│   │   ├── useTasks.ts
│   │   ├── useTask.ts
│   │   ├── useCreateTask.ts
│   │   ├── useUpdateTask.ts
│   │   ├── useDeleteTask.ts
│   │   └── useTaskHistory.ts
│   │
│   ├── components/             # Componentes reutilizables (sin estado de negocio)
│   │   ├── ui/
│   │   │   ├── Button.tsx
│   │   │   ├── Input.tsx
│   │   │   ├── Textarea.tsx
│   │   │   ├── Badge.tsx       # chip pendiente / completada
│   │   │   ├── Modal.tsx
│   │   │   ├── Spinner.tsx
│   │   │   └── EmptyState.tsx
│   │   └── layout/
│   │       ├── AppLayout.tsx   # Header + main + footer
│   │       └── ProtectedRoute.tsx
│   │
│   ├── features/               # Módulos de negocio
│   │   ├── auth/
│   │   │   ├── LoginForm.tsx
│   │   │   └── useAuth.ts      # contexto de sesión + token
│   │   └── tasks/
│   │       ├── TaskList.tsx         # tabla/grid + filtro de estado
│   │       ├── TaskCard.tsx         # fila/card de una tarea
│   │       ├── TaskStatusBadge.tsx  # badge coloreado por estado
│   │       ├── TaskForm.tsx         # crear y editar (title, description)
│   │       ├── TaskDeleteButton.tsx # botón + confirmación
│   │       └── TaskHistory.tsx      # timeline de cambios de estado
│   │
│   ├── pages/
│   │   ├── LoginPage.tsx
│   │   ├── TasksPage.tsx       # lista + filtro + acciones
│   │   └── TaskDetailPage.tsx  # detalle + historial
│   │
│   ├── types/
│   │   └── task.ts             # interfaces Task, TaskHistory, TaskStatus
│   │
│   ├── lib/
│   │   └── utils.ts            # formatDate, cn (classnames helper)
│   │
│   ├── App.tsx
│   └── main.tsx
├── .env.example
├── tailwind.config.ts
└── vite.config.ts
```

---

## Contrato de tipos (derivado del backend)

```ts
// src/types/task.ts

export type TaskStatus = 'pendiente' | 'completada';

export interface Task {
  id: string;
  title: string;
  description: string | null;
  status: TaskStatus;
  createdAt: string;
  updatedAt: string;
}

export interface TaskHistory {
  id: string;
  status: TaskStatus;
  date: string;
}

export interface CreateTaskPayload {
  title: string;
  description?: string;
}

// UpdateTaskPayload intencionalemente NO incluye `status`:
// el estado lo actualiza el processor de forma automática.
export interface UpdateTaskPayload {
  title: string;
  description?: string;
}
```

---

## Capa API (`src/api/`)

### `client.ts` — Axios con interceptor JWT

```ts
// Adjunta automáticamente el Bearer token en cada request
// y redirige al login cuando el backend responde 401.
const apiClient = axios.create({ baseURL: import.meta.env.VITE_API_URL });

apiClient.interceptors.request.use((config) => {
  const token = sessionStorage.getItem('id_token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});
```

### `tasks.api.ts` — Endpoints

| Función | Método | Ruta |
|---|---|---|
| `getAllTasks(status?)` | GET | `/api/v1/tasks?status=...` |
| `getTaskById(id)` | GET | `/api/v1/tasks/:id` |
| `createTask(payload)` | POST | `/api/v1/tasks` |
| `updateTask(id, payload)` | PUT | `/api/v1/tasks/:id` |
| `deleteTask(id)` | DELETE | `/api/v1/tasks/:id` |
| `getTaskHistory(id)` | GET | `/api/v1/tasks/:id/history` |

### `auth.api.ts` — Login Cognito

Llama al endpoint de Cognito (Floci) con `USER_PASSWORD_AUTH` para obtener el `IdToken` y guardarlo en `sessionStorage`.

---

## Variables de entorno

```env
# .env.example
VITE_API_URL=http://localhost:4566/...   # URL del API Gateway (del .env.local generado por setup-local.sh)
VITE_COGNITO_CLIENT_ID=...
VITE_COGNITO_ENDPOINT=http://localhost:4566
```

---

## Páginas y flujo de navegación

```
/login          → LoginPage
/tasks          → TasksPage         (protegida)
/tasks/:id      → TaskDetailPage    (protegida)
```

---

## Descripción de componentes clave

### `LoginPage`
- Formulario con campos `username` y `password` (React Hook Form + Zod).
- Llama a `auth.api.ts`, guarda el `IdToken`, redirige a `/tasks`.

### `TasksPage`
- Filtro de estado: tres opciones (Todos / Pendiente / Completada) con tabs o botones.
- Tabla o grid de `TaskCard` componentes.
- Botón "Nueva tarea" abre un `Modal` con `TaskForm`.
- Cada `TaskCard` expone botones Editar y Eliminar.

### `TaskCard`
- Muestra: `title`, `description` truncada, `TaskStatusBadge`, `createdAt`.
- NO muestra controles para cambiar el estado (es responsabilidad del processor).

### `TaskStatusBadge`
- `pendiente` → chip amarillo.
- `completada` → chip verde.

### `TaskForm`
- Campos: `title` (obligatorio, max 255 chars) y `description` (opcional).
- Reutilizable para crear y editar.
- Validación con Zod alineada a las constraints del backend.

### `TaskDeleteButton`
- Muestra diálogo de confirmación antes de ejecutar `deleteTask`.
- Invalida el cache de TanStack Query al completar.

### `TaskDetailPage`
- Muestra el detalle completo de una tarea.
- Sección de historial con `TaskHistory` (timeline cronológico).

### `TaskHistory`
- Lista de entradas `{ status, date }` ordenadas cronológicamente.
- Cada entrada indica cuándo pasó la tarea a cada estado.

---

## Gestión de estado del servidor (TanStack Query)

| Hook | Query key | Qué hace |
|---|---|---|
| `useTasks(status?)` | `['tasks', status]` | Lista paginada con filtro |
| `useTask(id)` | `['tasks', id]` | Detalle de una tarea |
| `useTaskHistory(id)` | `['tasks', id, 'history']` | Historial de estados |
| `useCreateTask` | — | Mutation: crea + invalida `['tasks']` |
| `useUpdateTask` | — | Mutation: actualiza + invalida `['tasks', id]` |
| `useDeleteTask` | — | Mutation: elimina + invalida `['tasks']` |

---

## Pasos de implementación

### Paso 1 — Scaffolding del proyecto
- `npm create vite@latest frontend -- --template react-ts`
- Instalar dependencias: `tailwindcss`, `@tanstack/react-query`, `react-router-dom`, `axios`, `react-hook-form`, `zod`, `@hookform/resolvers`
- Configurar Tailwind (PostCSS, `tailwind.config.ts`, import en `index.css`).
- Configurar `QueryClientProvider` y `BrowserRouter` en `main.tsx`.

### Paso 2 — Tipos y capa API
- Definir `src/types/task.ts`.
- Implementar `src/api/client.ts` con el interceptor JWT.
- Implementar `src/api/tasks.api.ts` y `src/api/auth.api.ts`.

### Paso 3 — Autenticación
- Crear `useAuth` context (token en `sessionStorage`, login/logout).
- Implementar `LoginForm` y `LoginPage`.
- Implementar `ProtectedRoute`.

### Paso 4 — Componentes UI base
- `Button`, `Input`, `Textarea`, `Badge`, `Modal`, `Spinner`, `EmptyState`.

### Paso 5 — Feature de tareas
- Hooks TanStack Query (`useTasks`, `useCreateTask`, `useUpdateTask`, `useDeleteTask`).
- `TaskStatusBadge`, `TaskForm`, `TaskCard`, `TaskDeleteButton`.
- `TaskList` con filtro de estado.
- `TasksPage` integrando todo.

### Paso 6 — Detalle e historial
- Hook `useTask` y `useTaskHistory`.
- `TaskHistory` (timeline).
- `TaskDetailPage`.

### Paso 7 — Pulido
- Estados de carga (skeleton / spinner) en cada sección.
- Mensajes de error con `EmptyState` o toast.
- `AppLayout` con header (nombre de usuario, logout).
- Responsividad básica con Tailwind.

---

## Restricciones explícitas

- El frontend **no expone ni envía** el campo `status` en los formularios de creación ni edición.
- `UpdateTaskPayload` solo admite `title` y `description`.
- La transición de estado `pendiente → completada` la realiza el `tasks-processor` vía SQS; el frontend solo la muestra.
