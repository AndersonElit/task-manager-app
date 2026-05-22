# Plan de Tests — Task Manager App (Unitarios + Integración)

## Contexto del proyecto

| Capa | Stack |
|---|---|
| Backend (tasks-creator) | Java 21, Spring Boot 3.4.1, WebFlux, R2DBC, SQS |
| Backend (tasks-processor) | Java 21, Spring Boot 3.4.1, WebFlux, R2DBC, SQS consumer |
| Frontend | React 19, TypeScript, Vite, React Query, React Hook Form + Zod |
| Base de datos | PostgreSQL 15 (R2DBC async) |
| Mensajería | AWS SQS |

Ninguno de los módulos cuenta con tests actualmente. La arquitectura hexagonal del backend facilita el testing por capas con dependencias invertidas.

---

## Dependencias de testing a agregar

### Backend (Maven — cada microservicio)

```xml
<!-- JUnit 5 + Mockito -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-test</artifactId>
  <scope>test</scope>
</dependency>

<!-- Reactor Test (StepVerifier) -->
<dependency>
  <groupId>io.projectreactor</groupId>
  <artifactId>reactor-test</artifactId>
  <scope>test</scope>
</dependency>

<!-- Test de controladores WebFlux -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-webflux</artifactId>
  <scope>test</scope>
</dependency>

<!-- TestContainers (integración con PostgreSQL y Floci) -->
<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>junit-jupiter</artifactId>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>postgresql</artifactId>
  <scope>test</scope>
</dependency>
<!-- Floci se levanta con GenericContainer — no requiere módulo extra -->

<!-- R2DBC driver para tests -->
<dependency>
  <groupId>io.r2dbc</groupId>
  <artifactId>r2dbc-postgresql</artifactId>
  <scope>test</scope>
</dependency>

<!-- Flyway para migraciones en tests -->
<dependency>
  <groupId>org.flywaydb</groupId>
  <artifactId>flyway-core</artifactId>
  <scope>test</scope>
</dependency>
```

### Frontend (npm)

```bash
npm install -D vitest @vitest/coverage-v8 jsdom @testing-library/react \
  @testing-library/jest-dom @testing-library/user-event msw \
  @testing-library/react-hooks react-router-dom
```

---

## Backend — tasks-creator

### 1. `TaskUseCase` (application/use-cases)

**Clase bajo test:** `TaskUseCase`  
**Tipo:** Unit test puro — mockear `TaskRepository` y `EventPublisher`  
**Framework:** JUnit 5 + Mockito + StepVerifier

#### Escenarios exitosos

| ID | Método | Descripción |
|---|---|---|
| TC-UC-01 | `create` | Crea tarea con título y descripción válidos, publica evento `TaskCreatedEvent`, retorna `Task` con status PENDIENTE |
| TC-UC-02 | `create` | Crea tarea sin descripción (campo opcional), flujo completa sin error |
| TC-UC-03 | `findAll` | Sin filtro de status, retorna `Flux` con todas las tareas |
| TC-UC-04 | `findAll` | Con `status=PENDIENTE`, delega filtro al repositorio correctamente |
| TC-UC-05 | `findAll` | Con `status=COMPLETADA`, delega filtro al repositorio correctamente |
| TC-UC-06 | `findById` | ID existente, retorna `Mono<Task>` con datos correctos |
| TC-UC-07 | `update` | Tarea existente, actualiza título y descripción, retorna tarea modificada |
| TC-UC-08 | `delete` | Tarea existente, elimina sin error, repositorio recibe el ID correcto |
| TC-UC-09 | `findHistory` | Tarea con historial, retorna `Flux<TaskStatusHistory>` ordenado |

#### Escenarios de error

| ID | Método | Descripción |
|---|---|---|
| TC-UC-E01 | `findById` | ID inexistente, lanza `TaskNotFoundException` |
| TC-UC-E02 | `update` | ID inexistente, lanza `TaskNotFoundException` |
| TC-UC-E03 | `delete` | ID inexistente, lanza `TaskNotFoundException` |
| TC-UC-E04 | `create` | Falla al publicar evento en SQS, el error se propaga como `Mono.error` |
| TC-UC-E05 | `create` | Repositorio lanza error de BD, evento no se publica |
| TC-UC-E06 | `findHistory` | Tarea sin historial, retorna `Flux.empty()` |

```java
// Ejemplo de estructura de test
@ExtendWith(MockitoExtension.class)
class TaskUseCaseTest {
    @Mock TaskRepository taskRepository;
    @Mock EventPublisher eventPublisher;
    @InjectMocks TaskUseCase useCase;

    @Test
    void create_withValidData_returnsTaskAndPublishesEvent() {
        // given
        var task = new Task(UUID.randomUUID(), "Título", "Desc", TaskStatus.PENDIENTE, ...);
        when(taskRepository.save(any())).thenReturn(Mono.just(task));
        when(eventPublisher.publish(any())).thenReturn(Mono.empty());

        // when
        var result = useCase.create("Título", "Desc");

        // then
        StepVerifier.create(result)
            .expectNextMatches(t -> t.status() == TaskStatus.PENDIENTE)
            .verifyComplete();
        verify(eventPublisher).publish(any(TaskCreatedEvent.class));
    }
}
```

---

### 2. `TaskController` (infrastructure/entry-points/rest-api)

**Clase bajo test:** `TaskController`  
**Tipo:** Slice test con `@WebFluxTest` — mockear `TaskUseCase`  
**Framework:** JUnit 5 + `WebTestClient` + Mockito

#### Escenarios exitosos

| ID | Endpoint | Descripción |
|---|---|---|
| TC-CT-01 | `POST /api/v1/tasks` | Body válido → HTTP 201 + `TaskResponse` en body |
| TC-CT-02 | `GET /api/v1/tasks` | Sin query param → HTTP 200 + array de tareas |
| TC-CT-03 | `GET /api/v1/tasks?status=pendiente` | Query param válido → HTTP 200 + tareas filtradas |
| TC-CT-04 | `GET /api/v1/tasks/{id}` | ID existente → HTTP 200 + tarea |
| TC-CT-05 | `PUT /api/v1/tasks/{id}` | Body válido + ID existente → HTTP 200 + tarea actualizada |
| TC-CT-06 | `DELETE /api/v1/tasks/{id}` | ID existente → HTTP 204 sin body |
| TC-CT-07 | `GET /api/v1/tasks/{id}/history` | Historial disponible → HTTP 200 + lista |

#### Escenarios de error

| ID | Endpoint | Descripción |
|---|---|---|
| TC-CT-E01 | `POST /api/v1/tasks` | Título vacío → HTTP 400 con mensaje de validación |
| TC-CT-E02 | `POST /api/v1/tasks` | Título mayor a 255 chars → HTTP 400 |
| TC-CT-E03 | `POST /api/v1/tasks` | Body sin campo `title` → HTTP 400 |
| TC-CT-E04 | `GET /api/v1/tasks/{id}` | ID inexistente → HTTP 404 |
| TC-CT-E05 | `PUT /api/v1/tasks/{id}` | ID inexistente → HTTP 404 |
| TC-CT-E06 | `DELETE /api/v1/tasks/{id}` | ID inexistente → HTTP 404 |
| TC-CT-E07 | `GET /api/v1/tasks?status=invalido` | Status no reconocido → HTTP 400 |

```java
@WebFluxTest(TaskController.class)
class TaskControllerTest {
    @Autowired WebTestClient webTestClient;
    @MockBean TaskUseCase taskUseCase;

    @Test
    void createTask_withValidBody_returns201() {
        var task = new Task(UUID.randomUUID(), "Mi tarea", null, TaskStatus.PENDIENTE, ...);
        when(taskUseCase.create("Mi tarea", null)).thenReturn(Mono.just(task));

        webTestClient.post().uri("/api/v1/tasks")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"title":"Mi tarea"}""")
            .exchange()
            .expectStatus().isCreated()
            .expectBody(TaskResponse.class)
            .value(r -> assertThat(r.title()).isEqualTo("Mi tarea"));
    }
}
```

---

### 3. `GlobalErrorHandler` (infrastructure/entry-points/rest-api)

**Tipo:** Unit test + integración con `@WebFluxTest`

| ID | Excepción entrada | HTTP esperado | Descripción |
|---|---|---|---|
| TC-EH-01 | `TaskNotFoundException` | 404 | Mensaje con ID de la tarea |
| TC-EH-02 | `MethodArgumentNotValidException` | 400 | Lista de errores de validación |
| TC-EH-03 | `IllegalArgumentException` | 400 | Mensaje de la excepción |
| TC-EH-04 | `Exception` genérica | 500 | Mensaje genérico de error interno |

---

### 4. `SqsEventPublisher` (infrastructure/driven-adapters/rabbit-producer)

**Tipo:** Unit test — mockear `SqsAsyncClient`

| ID | Escenario | Descripción |
|---|---|---|
| TC-SQS-01 | Éxito | Evento serializado a JSON y enviado al queue URL correcto |
| TC-SQS-02 | Éxito | `MessageBody` contiene todos los campos de `TaskCreatedEvent` |
| TC-SQS-03 | Error SQS | Cliente SQS falla → error propagado como `Mono.error` |
| TC-SQS-04 | Éxito | Serialización produce JSON válido parseable |

---

## Backend — tasks-processor

### 5. `CompleteTaskUseCase` (application/use-cases)

**Tipo:** Unit test — mockear `TaskRepository`

| ID | Escenario | Descripción |
|---|---|---|
| TC-CP-01 | Éxito | Llama `updateStatus(taskId, COMPLETADA)` con el UUID correcto |
| TC-CP-02 | Éxito | Retorna `Mono<Void>` que completa sin error |
| TC-CP-03 | Error BD | Repositorio falla → error se propaga |
| TC-CP-04 | Éxito | No llama otros métodos del repositorio |

---

### 6. `SqsMessageConsumer` (infrastructure/entry-points/rabbit-consumer)

**Tipo:** Unit test — mockear `SqsAsyncClient` y `CompleteTaskUseCase`

| ID | Escenario | Descripción |
|---|---|---|
| TC-MC-01 | Éxito | Mensaje JSON válido → deserializa `TaskCreatedEvent` → llama use case |
| TC-MC-02 | Éxito | Tras procesar mensaje, lo elimina del queue (DeleteMessage) |
| TC-MC-03 | Error JSON | Mensaje malformado → no llama use case, no elimina mensaje |
| TC-MC-04 | Error use case | Use case falla → mensaje no se elimina (reintento posible) |
| TC-MC-05 | Éxito | Campo `taskId` del evento se pasa correctamente al use case |

---

## Frontend — React + TypeScript

### 7. API Client (`src/api/client.ts`)

**Tipo:** Unit test — mockear `sessionStorage` y `axios`  
**Framework:** Vitest + msw (Mock Service Worker)

| ID | Escenario | Descripción |
|---|---|---|
| TC-API-01 | Éxito | Interceptor inyecta `Authorization: Bearer <token>` si existe en sessionStorage |
| TC-API-02 | Éxito | Sin token en sessionStorage, request se envía sin header Authorization |
| TC-API-03 | Error 401 | Respuesta 401 → limpia sessionStorage y redirige a `/login` |
| TC-API-04 | Éxito | `baseURL` se configura desde variable de entorno `VITE_API_URL` |

---

### 8. API Tasks (`src/api/tasks.api.ts`)

**Tipo:** Unit test con MSW

| ID | Función | Escenario | Descripción |
|---|---|---|---|
| TC-TK-01 | `getAllTasks()` | Éxito | GET sin filtro → retorna array de `Task[]` |
| TC-TK-02 | `getAllTasks('pendiente')` | Éxito | GET con `?status=pendiente` en la URL |
| TC-TK-03 | `getTaskById(id)` | Éxito | GET por ID → retorna `Task` |
| TC-TK-04 | `getTaskById(id)` | Error 404 | API retorna 404 → lanza error |
| TC-TK-05 | `createTask(payload)` | Éxito | POST con body → retorna `Task` creada |
| TC-TK-06 | `createTask(payload)` | Error 400 | API retorna 400 → lanza error con mensaje |
| TC-TK-07 | `updateTask(id, payload)` | Éxito | PUT → retorna `Task` actualizada |
| TC-TK-08 | `deleteTask(id)` | Éxito | DELETE → resuelve sin valor |
| TC-TK-09 | `getTaskHistory(id)` | Éxito | GET → retorna `TaskHistory[]` |

---

### 9. Custom Hooks (`src/hooks/`)

**Tipo:** Unit test con `renderHook` + `QueryClientProvider` + MSW  
**Setup:** Cada test usa un `QueryClient` fresco con `retry: false`

#### `useTasks`

| ID | Escenario | Descripción |
|---|---|---|
| TC-HK-01 | Éxito | Hook devuelve `data` con array de tareas tras fetch exitoso |
| TC-HK-02 | Cargando | Estado `isLoading: true` mientras se espera respuesta |
| TC-HK-03 | Error API | `isError: true` cuando el endpoint falla |
| TC-HK-04 | Filtro | Hook con `status='pendiente'` llama al endpoint con query param correcto |

#### `useCreateTask`

| ID | Escenario | Descripción |
|---|---|---|
| TC-HK-05 | Éxito | `mutate()` ejecuta, `isSuccess: true`, invalida cache `['tasks']` |
| TC-HK-06 | Error | API devuelve error, `isError: true`, cache no se invalida |
| TC-HK-07 | Pendiente | `isPending: true` durante la mutación |

#### `useUpdateTask`

| ID | Escenario | Descripción |
|---|---|---|
| TC-HK-08 | Éxito | Invalida cache `['tasks']` y `['tasks', id]` tras actualización |
| TC-HK-09 | Error | API 404 → `isError: true` |

#### `useDeleteTask`

| ID | Escenario | Descripción |
|---|---|---|
| TC-HK-10 | Éxito | Invalida cache `['tasks']` |
| TC-HK-11 | Error | API falla → `isError: true` |

---

### 10. `AuthProvider` (`src/features/auth/AuthProvider.tsx`)

**Tipo:** Unit test — mockear `loginApi` y `sessionStorage`

| ID | Función | Escenario | Descripción |
|---|---|---|---|
| TC-AU-01 | `login` | Éxito | Llama `loginApi`, guarda token y username en `sessionStorage`, `isAuthenticated: true` |
| TC-AU-02 | `login` | Error | Credenciales inválidas → `isAuthenticated: false`, sessionStorage vacío |
| TC-AU-03 | `logout` | Éxito | Limpia `sessionStorage`, `isAuthenticated: false`, redirige a `/login` |
| TC-AU-04 | Inicialización | Token existente | Al montar, detecta token en sessionStorage → `isAuthenticated: true` |
| TC-AU-05 | Inicialización | Sin token | Al montar sin token → `isAuthenticated: false` |

---

### 11. `TaskForm` (`src/features/tasks/TaskForm.tsx`)

**Tipo:** Component test con `@testing-library/react`

| ID | Escenario | Descripción |
|---|---|---|
| TC-TF-01 | Éxito | Rellena título + descripción → llama `onSubmit` con los valores correctos |
| TC-TF-02 | Éxito | Formulario de edición muestra `defaultValues` pre-rellenados |
| TC-TF-03 | Error validación | Submit sin título → muestra error "requerido", `onSubmit` no se llama |
| TC-TF-04 | Error validación | Título > 255 chars → muestra error de longitud |
| TC-TF-05 | Éxito | Botón "Cancelar" llama `onCancel` |
| TC-TF-06 | Éxito | Descripción vacía es permitida (campo opcional) |
| TC-TF-07 | Accesibilidad | Mensajes de error están asociados al input via `aria-describedby` |

```tsx
// Ejemplo de estructura de test
describe('TaskForm', () => {
  it('muestra error cuando el título está vacío', async () => {
    const onSubmit = vi.fn();
    render(<TaskForm onSubmit={onSubmit} onCancel={vi.fn()} />);

    await userEvent.click(screen.getByRole('button', { name: /guardar/i }));

    expect(screen.getByText(/requerido/i)).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
  });
});
```

---

### 12. `TaskCard` (`src/features/tasks/TaskCard.tsx`)

| ID | Escenario | Descripción |
|---|---|---|
| TC-TC-01 | Render | Muestra título y descripción de la tarea |
| TC-TC-02 | Render | Badge de status muestra "pendiente" o "completada" correctamente |
| TC-TC-03 | Interacción | Click en eliminar muestra confirmación antes de llamar `useDeleteTask` |
| TC-TC-04 | Render | Tarea sin descripción no muestra campo vacío |

---

### 13. `ProtectedRoute` (`src/components/layout/ProtectedRoute.tsx`)

| ID | Escenario | Descripción |
|---|---|---|
| TC-PR-01 | Autenticado | Usuario autenticado → renderiza children |
| TC-PR-02 | No autenticado | Usuario no autenticado → redirige a `/login` |

---

### 14. Componentes UI (`src/components/ui/`)

**Tipo:** Snapshot + interacción básica

| ID | Componente | Escenario | Descripción |
|---|---|---|---|
| TC-UI-01 | `Button` | Loading | Muestra spinner y está deshabilitado cuando `isLoading: true` |
| TC-UI-02 | `Button` | Click | Llama `onClick` cuando no está deshabilitado |
| TC-UI-03 | `Input` | Error | Muestra mensaje de error cuando `errorMessage` está definido |
| TC-UI-04 | `Modal` | Abierto | Renderiza children cuando `isOpen: true` |
| TC-UI-05 | `Modal` | Cerrado | No renderiza children cuando `isOpen: false` |
| TC-UI-06 | `TaskStatusBadge` | Pendiente | Color/texto correcto para status pendiente |
| TC-UI-07 | `TaskStatusBadge` | Completada | Color/texto correcto para status completada |

---

## Estructura de archivos propuesta

```
backend/
└── tasks-creator/
    ├── application/use-cases/src/test/java/
    │   └── TaskUseCaseTest.java
    ├── infrastructure/entry-points/rest-api/src/test/java/
    │   ├── TaskControllerTest.java
    │   ├── GlobalErrorHandlerTest.java
    │   └── integration/
    │       └── TaskApiIntegrationTest.java          ← @SpringBootTest + TestContainers
    ├── infrastructure/driven-adapters/postgres/src/test/java/
    │   └── integration/
    │       └── TaskRepositoryAdapterIntegrationTest.java
    └── infrastructure/driven-adapters/rabbit-producer/src/test/java/
        ├── SqsEventPublisherTest.java
        └── integration/
            └── SqsEventPublisherIntegrationTest.java ← Floci

backend/
└── tasks-processor/
    ├── application/use-cases/src/test/java/
    │   └── CompleteTaskUseCaseTest.java
    ├── infrastructure/entry-points/rabbit-consumer/src/test/java/
    │   ├── SqsMessageConsumerTest.java
    │   └── integration/
    │       └── SqsMessageConsumerIntegrationTest.java ← Floci + TestContainers
    └── infrastructure/driven-adapters/postgres/src/test/java/
        └── integration/
            └── TaskRepositoryAdapterIntegrationTest.java

frontend/
└── src/
    ├── api/
    │   ├── client.test.ts
    │   └── tasks.api.test.ts
    ├── hooks/
    │   ├── useTasks.test.ts
    │   ├── useCreateTask.test.ts
    │   ├── useUpdateTask.test.ts
    │   └── useDeleteTask.test.ts
    ├── features/
    │   ├── auth/
    │   │   └── AuthProvider.test.tsx
    │   └── tasks/
    │       ├── TaskForm.test.tsx
    │       └── TaskCard.test.tsx
    ├── pages/
    │   ├── integration/
    │   │   ├── TasksPage.integration.test.tsx        ← flujos completos de página
    │   │   ├── TaskDetailPage.integration.test.tsx
    │   │   └── LoginPage.integration.test.tsx
    └── components/
        ├── layout/
        │   └── ProtectedRoute.test.tsx
        └── ui/
            ├── Button.test.tsx
            ├── Input.test.tsx
            └── Modal.test.tsx
```

---

## Configuración de Vitest (frontend)

```ts
// vitest.config.ts
import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/test/setup.ts',
    coverage: {
      provider: 'v8',
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/main.tsx', 'src/vite-env.d.ts'],
    },
  },
});
```

```ts
// src/test/setup.ts
import '@testing-library/jest-dom';
import { server } from './mocks/server';

beforeAll(() => server.listen());
afterEach(() => server.resetHandlers());
afterAll(() => server.close());
```

---

---

## Tests de integración — Backend

Los tests de integración verifican que las capas colaboran correctamente con infraestructura real (PostgreSQL via TestContainers, SQS via Floci). Se distinguen de los unit tests en que levantan contexto de Spring y contenedores Docker efímeros.

**Convención:** los tests de integración se ubican en paquetes `integration/` dentro de cada módulo y se etiquetan con `@Tag("integration")` para poder excluirlos del pipeline rápido si se necesita.

---

### 15. `TaskRepositoryAdapter` — tasks-creator (driven-adapters/postgres)

**Tipo:** Integración con BD real  
**Setup:** TestContainers PostgreSQL + schema aplicado desde `docs/model.sql`  
**Framework:** `@SpringBootTest` (slice R2DBC) + StepVerifier

| ID | Método | Escenario | Descripción |
|---|---|---|---|
| TI-RP-01 | `save` | Éxito | Persiste tarea en BD, retorna entidad con UUID generado y `created_at` poblado |
| TI-RP-02 | `save` | Error unicidad | Título duplicado → falla con constraint violation |
| TI-RP-03 | `findAll` | Sin filtro | Retorna todas las tareas existentes en la BD |
| TI-RP-04 | `findAll` | Con filtro status | Solo retorna tareas del status solicitado |
| TI-RP-05 | `findById` | Existente | Retorna tarea correcta desde BD |
| TI-RP-06 | `findById` | Inexistente | Retorna `Mono.empty()` |
| TI-RP-07 | `update` | Éxito | Actualiza campos en BD, `updated_at` es posterior a `created_at` |
| TI-RP-08 | `delete` | Existente | Elimina registro, consulta posterior no lo encuentra |
| TI-RP-09 | `findHistory` | Con historial | Trigger de BD genera registro en `tasks_status` al cambiar status |
| TI-RP-10 | `findHistory` | Sin cambios | Retorna `Flux.empty()` para tarea recién creada |

```java
@SpringBootTest
@Testcontainers
@Tag("integration")
class TaskRepositoryAdapterIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
        .withInitScript("schema.sql");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () ->
            "r2dbc:postgresql://" + postgres.getHost() + ":" +
            postgres.getMappedPort(5432) + "/" + postgres.getDatabaseName());
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
    }

    @Autowired TaskRepositoryAdapter repository;

    @Test
    void save_persistsTaskWithGeneratedId() {
        var task = new TaskEntity(null, "Test task", null, 1, null, null);

        StepVerifier.create(repository.save(task))
            .expectNextMatches(t -> t.id() != null && t.title().equals("Test task"))
            .verifyComplete();
    }
}
```

---

### 16. API REST completa — tasks-creator (`@SpringBootTest`)

**Tipo:** Integración HTTP end-to-end dentro del microservicio  
**Setup:** TestContainers PostgreSQL + Floci SQS + contexto Spring completo  
**Framework:** `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `WebTestClient`

| ID | Flujo | Escenario | Descripción |
|---|---|---|---|
| TI-API-01 | Crear tarea | Éxito | POST → 201, tarea persiste en BD, evento publicado en SQS |
| TI-API-02 | Crear tarea | Título duplicado | POST con mismo título → 409 o 400 según manejo de constraint |
| TI-API-03 | Listar tareas | BD vacía | GET → 200 con array vacío |
| TI-API-04 | Listar tareas | Con datos | GET → 200 con tareas insertadas previamente |
| TI-API-05 | Listar con filtro | Status pendiente | GET `?status=pendiente` → solo tareas PENDIENTE |
| TI-API-06 | Obtener tarea | Existente | GET `/tasks/{id}` → 200 con datos correctos |
| TI-API-07 | Obtener tarea | Inexistente | GET `/tasks/{uuid-random}` → 404 |
| TI-API-08 | Actualizar tarea | Éxito | PUT → 200, cambios persisten en BD |
| TI-API-09 | Actualizar tarea | Inexistente | PUT `/tasks/{uuid-random}` → 404 |
| TI-API-10 | Eliminar tarea | Éxito | DELETE → 204, tarea ya no existe en BD |
| TI-API-11 | Eliminar tarea | Inexistente | DELETE `/tasks/{uuid-random}` → 404 |
| TI-API-12 | Historial | Con cambios | GET `/tasks/{id}/history` → lista de transiciones de status |
| TI-API-13 | Flujo completo | CRUD | Crear → Leer → Actualizar → Eliminar en secuencia |

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Tag("integration")
class TaskApiIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
        .withInitScript("schema.sql");

    // Floci expone la misma API que LocalStack en el puerto 4566
    @Container
    static GenericContainer<?> floci = new GenericContainer<>("floci/floci:latest")
        .withExposedPorts(4566)
        .waitingFor(Wait.forHttp("/").forPort(4566).forStatusCodeMatching(code -> code < 500));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        // PostgreSQL
        registry.add("spring.r2dbc.url", () ->
            "r2dbc:postgresql://" + postgres.getHost() + ":" +
            postgres.getMappedPort(5432) + "/" + postgres.getDatabaseName());
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);

        // Floci (SQS) — mismas propiedades que usa application.yml
        String flociEndpoint = "http://" + floci.getHost() + ":" + floci.getMappedPort(4566);
        registry.add("aws.endpoint-url", () -> flociEndpoint);
        registry.add("aws.region", () -> "us-east-1");
        registry.add("aws.access-key", () -> "test");
        registry.add("aws.secret-key", () -> "test");
        registry.add("sqs.queue-url", () -> flociEndpoint + "/000000000000/task-created-queue");
    }

    @Autowired WebTestClient webTestClient;

    @Test
    @Order(1)
    void createTask_persistsAndPublishesEvent() {
        webTestClient.post().uri("/api/v1/tasks")
            .bodyValue("""{"title":"Integración","description":"desc"}""")
            .exchange()
            .expectStatus().isCreated()
            .expectBody()
            .jsonPath("$.id").isNotEmpty()
            .jsonPath("$.status").isEqualTo("pendiente");
    }
}
```

---

### 17. `SqsEventPublisher` con Floci — tasks-creator

**Tipo:** Integración con SQS real (Floci)  
**Setup:** TestContainers Floci

| ID | Escenario | Descripción |
|---|---|---|
| TI-SQS-01 | Éxito | Publica mensaje en queue de Floci, mensaje recuperable via `ReceiveMessage` |
| TI-SQS-02 | Contenido | Body del mensaje contiene `taskId`, `title`, `description`, `createdAt` |
| TI-SQS-03 | Queue inexistente | URL de queue inválida → error propagado |
| TI-SQS-04 | Múltiples eventos | Publicar N eventos → N mensajes en queue |

---

### 18. `TaskRepositoryAdapter` — tasks-processor (driven-adapters/postgres)

**Tipo:** Integración con BD real  
**Setup:** TestContainers PostgreSQL

| ID | Método | Escenario | Descripción |
|---|---|---|---|
| TI-PR-01 | `updateStatus` | Éxito | Cambia status de PENDIENTE a COMPLETADA en BD |
| TI-PR-02 | `updateStatus` | Trigger activo | Cambio de status genera registro en `tasks_status` automáticamente |
| TI-PR-03 | `updateStatus` | ID inexistente | No actualiza ninguna fila, operación no lanza excepción |

---

### 19. `SqsMessageConsumer` con Floci — tasks-processor

**Tipo:** Integración completa (SQS + BD)  
**Setup:** TestContainers PostgreSQL + Floci SQS

| ID | Escenario | Descripción |
|---|---|---|
| TI-MC-01 | Flujo completo | Publicar evento en Floci → consumer lo recibe → BD actualizada a COMPLETADA |
| TI-MC-02 | Mensaje eliminado | Tras procesar, mensaje ya no está en el queue |
| TI-MC-03 | Mensaje inválido | JSON malformado → mensaje visible de nuevo en queue (no eliminado) |
| TI-MC-04 | Múltiples mensajes | N eventos publicados → N tareas actualizadas en BD |
| TI-MC-05 | Tarea inexistente | Evento con `taskId` que no existe en BD → no falla el consumer |

---

### 20. Flujo end-to-end entre microservicios

**Tipo:** Integración entre tasks-creator y tasks-processor  
**Setup:** TestContainers PostgreSQL + Floci SQS, ambos microservicios levantados  
**Nota:** Puede ejecutarse como un test en un módulo separado `e2e/` o via Docker Compose en CI

| ID | Flujo | Descripción |
|---|---|---|
| TI-E2E-01 | Crear → Completar | POST a tasks-creator crea tarea PENDIENTE y publica evento; tasks-processor lo consume y la marca COMPLETADA |
| TI-E2E-02 | Consistencia de datos | BD refleja status COMPLETADA tras el flujo, historial tiene 2 entradas (creación + completado) |
| TI-E2E-03 | Idempotencia | Mismo evento procesado dos veces → status sigue COMPLETADA, sin duplicados en historial |

---

## Tests de integración — Frontend

Los tests de integración de frontend verifican que componentes, hooks y capas de API colaboran correctamente bajo condiciones reales de rendering, routing y estado. Usan MSW para interceptar peticiones HTTP sin depender del backend real.

---

### 21. Flujo de autenticación (`LoginPage` → rutas protegidas)

**Tipo:** Integración de página con router y contexto  
**Setup:** `MemoryRouter` + `AuthProvider` + MSW

| ID | Flujo | Escenario | Descripción |
|---|---|---|---|
| TI-FE-AU-01 | Login exitoso | Éxito | Usuario completa formulario → MSW retorna token → redirige a `/tasks` |
| TI-FE-AU-02 | Login fallido | Error 401 | MSW retorna 401 → muestra mensaje de error en formulario, no redirige |
| TI-FE-AU-03 | Acceso directo | Sin token | Navegar a `/tasks` sin autenticarse → redirige a `/login` |
| TI-FE-AU-04 | Persistencia | Recarga simulada | Token en sessionStorage → rutas protegidas accesibles sin re-login |
| TI-FE-AU-05 | Logout | Éxito | Click en logout → sessionStorage limpio → redirige a `/login` |
| TI-FE-AU-06 | Expiración | 401 en request | API devuelve 401 durante sesión activa → redirige a `/login` automáticamente |

---

### 22. `TasksPage` — listado y filtrado

**Tipo:** Integración de página completa  
**Setup:** Router + `QueryClientProvider` + `AuthProvider` + MSW

| ID | Flujo | Escenario | Descripción |
|---|---|---|---|
| TI-FE-TP-01 | Carga inicial | Éxito | Página monta → MSW retorna tareas → lista se renderiza con títulos |
| TI-FE-TP-02 | Carga inicial | BD vacía | MSW retorna `[]` → se muestra componente `EmptyState` |
| TI-FE-TP-03 | Carga inicial | Error API | MSW retorna 500 → se muestra mensaje de error |
| TI-FE-TP-04 | Filtro status | Pendiente | Cambiar filtro a "pendiente" → nueva llamada API con `?status=pendiente` |
| TI-FE-TP-05 | Crear tarea | Flujo completo | Abrir modal → rellenar form → submit → MSW 201 → tarea aparece en lista |
| TI-FE-TP-06 | Crear tarea | Validación frontend | Submit sin título → error visible, no se llama a la API |
| TI-FE-TP-07 | Eliminar tarea | Flujo completo | Click eliminar → confirmación → MSW 204 → tarea desaparece de la lista |
| TI-FE-TP-08 | Eliminar tarea | Error API | MSW retorna 500 → tarea sigue en lista, mensaje de error visible |
| TI-FE-TP-09 | Estado loading | Carga en progreso | Spinner visible mientras MSW tiene delay en la respuesta |

```tsx
// Ejemplo de estructura de test de integración de página
describe('TasksPage — integración', () => {
  it('muestra lista de tareas al cargar', async () => {
    server.use(
      http.get('/api/v1/tasks', () =>
        HttpResponse.json([
          { id: '1', title: 'Tarea A', status: 'pendiente', createdAt: '...' },
        ])
      )
    );

    render(
      <MemoryRouter initialEntries={['/tasks']}>
        <AuthProvider>
          <QueryClientProvider client={queryClient}>
            <TasksPage />
          </QueryClientProvider>
        </AuthProvider>
      </MemoryRouter>
    );

    expect(await screen.findByText('Tarea A')).toBeInTheDocument();
  });
});
```

---

### 23. `TaskDetailPage` — detalle, edición e historial

**Tipo:** Integración de página  
**Setup:** Router con params + `QueryClientProvider` + `AuthProvider` + MSW

| ID | Flujo | Escenario | Descripción |
|---|---|---|---|
| TI-FE-TD-01 | Carga detalle | Éxito | Página monta con `id` en ruta → MSW retorna tarea → datos visibles |
| TI-FE-TD-02 | Carga detalle | ID inexistente | MSW retorna 404 → redirige o muestra mensaje de no encontrado |
| TI-FE-TD-03 | Editar tarea | Flujo completo | Abrir modal de edición → modificar título → submit → MSW 200 → cambio visible |
| TI-FE-TD-04 | Editar tarea | Validación | Submit con título vacío → error, sin llamada API |
| TI-FE-TD-05 | Historial | Con entradas | MSW retorna historial → timeline renderizada con fechas y status |
| TI-FE-TD-06 | Historial | Sin entradas | MSW retorna `[]` → sección de historial vacía o mensaje descriptivo |
| TI-FE-TD-07 | Navegación | Volver | Click en "volver" → navega a `/tasks` |

---

## Configuración de Vitest (frontend)

```ts
// vitest.config.ts
import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/test/setup.ts',
    coverage: {
      provider: 'v8',
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/main.tsx', 'src/vite-env.d.ts'],
    },
  },
});
```

```ts
// src/test/setup.ts
import '@testing-library/jest-dom';
import { server } from './mocks/server';

beforeAll(() => server.listen());
afterEach(() => server.resetHandlers());
afterAll(() => server.close());
```

```ts
// src/test/mocks/server.ts
import { setupServer } from 'msw/node';
import { handlers } from './handlers';

export const server = setupServer(...handlers);
```

```ts
// src/test/mocks/handlers.ts  — handlers por defecto (happy path)
import { http, HttpResponse } from 'msw';

export const handlers = [
  http.get('/api/v1/tasks', () => HttpResponse.json([])),
  http.post('/api/v1/tasks', () => HttpResponse.json({ id: '1', title: 'Nueva', status: 'pendiente' }, { status: 201 })),
  http.get('/api/v1/tasks/:id', ({ params }) =>
    HttpResponse.json({ id: params.id, title: 'Tarea', status: 'pendiente' })
  ),
  http.put('/api/v1/tasks/:id', ({ params }) =>
    HttpResponse.json({ id: params.id, title: 'Actualizada', status: 'pendiente' })
  ),
  http.delete('/api/v1/tasks/:id', () => new HttpResponse(null, { status: 204 })),
  http.get('/api/v1/tasks/:id/history', () => HttpResponse.json([])),
];
```

---

## Prioridad de implementación

| Prioridad | Tipo | Módulo | Justificación |
|---|---|---|---|
| 1 | Unit | `TaskUseCase` | Núcleo del negocio, mayor impacto, sin dependencias de infraestructura |
| 2 | Unit | `TaskController` | Surface de API pública, detecta errores de mapeo HTTP |
| 3 | Unit | `GlobalErrorHandler` | Respuestas de error consistentes |
| 4 | Unit | `CompleteTaskUseCase` | Lógica del segundo servicio |
| 5 | Unit | `TaskForm` | Componente de mayor interacción de usuario |
| 6 | Unit | `AuthProvider` | Seguridad y acceso |
| 7 | Unit | `useTasks / useCreateTask` | Flujos principales del frontend |
| 8 | Integración | `TaskRepositoryAdapter` (ambos) | Valida SQL real y triggers de BD |
| 9 | Integración | `TaskApiIntegrationTest` | Flujo HTTP → BD completo sin mocks |
| 10 | Integración | `TasksPage` + `TaskDetailPage` | Flujos de usuario end-to-end en el frontend |
| 11 | Integración | `SqsEventPublisher` + `SqsMessageConsumer` | Integración con SQS (Floci) |
| 12 | Integración | Flujo E2E inter-servicios | Crear → Completar via SQS, requiere ambos servicios activos |
| 13 | Unit | `SqsEventPublisher / SqsMessageConsumer` | Tests unitarios de la lógica de mensajería |
| 14 | Unit | Componentes UI | Regresión visual |
