# Task Manager App

Full-stack event-driven task management application with microservices architecture, local AWS emulation via [Floci](https://floci.io), and Kubernetes deployment.

---

## Architecture

```
User ──▶ API Gateway v2 (JWT/Cognito) ──▶ tasks-creator (:8080) ──▶ PostgreSQL
                                                    │
                                              publishes event
                                                    │
                                                    ▼
                                              SQS Queue ──▶ tasks-processor (:8081) ──▶ PostgreSQL
```

| Layer | Technology |
|-------|-----------|
| Frontend | React 19, TypeScript, Vite, Tailwind CSS 4, TanStack Query v5, React Router v7 |
| Backend | Java 21, Spring Boot 3.4.1 (WebFlux/R2DBC), hexagonal architecture |
| Database | PostgreSQL 15 |
| Messaging | AWS SQS (event-driven) |
| Auth | AWS Cognito (JWT) |
| Local AWS | Floci (lightweight AWS emulator) |
| IaC | Terraform |
| Orchestration | Kubernetes (k3s via Floci EKS) |

---

## Prerequisites

- **Docker** (>= 24.x)
- **Java 21** + Maven 3.9+
- **Node.js 22+** + npm 10+
- **Python 3** (for `tf-to-env.py`)
- **Terraform** (>= 1.6)
- **AWS CLI** v2
- **kubectl** (compatible with your cluster version)

---

## Quick Start (Local Development)

### 1. Start Floci (AWS local emulator)

```bash
./scripts/floci-start.sh
```

This creates a `floci-net` Docker network and runs a Floci container. Floci emulates SQS, RDS, Cognito, API Gateway, ECR, and EKS on `localhost:4566`.

### 2. Provision infrastructure with Terraform

```bash
cd terraform
terraform init
terraform apply -auto-approve
```

This creates:
- **SQS** queue (`task-created-queue`)
- **RDS** PostgreSQL instance (port `7001`, DB `taskmanager`, user `admin`, password `secret123`)
- **Cognito** user pool + test user (`testuser` / `Test1234!`)
- **API Gateway** v2 HTTP API with JWT authorizer
- **ECR** repositories for Docker images
- **EKS** cluster (`task-manager`)

### 3. Generate environment files

```bash
cd terraform
terraform output -json | python3 ../scripts/tf-to-env.py > ../.env.local
```

This creates `.env.local` (root) and `frontend/.env.local`.

### 4. Build and push Docker images

```bash
source .env.local
./scripts/build-push.sh
```

Builds and pushes three images to Floci ECR:
- `task-manager/tasks-creator`
- `task-manager/tasks-processor`
- `task-manager/frontend`

### 5. Deploy to Kubernetes

```bash
./scripts/k8s-deploy.sh
```

Deploys all services to the k3s cluster. The frontend becomes available on port `30080`.

### 6. Log in and use the app

Open `http://localhost:30080` and log in with:
- **Username:** `testuser`
- **Password:** `Test1234!`

### 7. Inspect pods and logs

The app runs on a k3s cluster inside a Docker container. Point `kubectl` at the generated kubeconfig (needed in every new terminal):

```bash
export KUBECONFIG="$HOME/.kube/config-floci-eks"
```

All resources live in the `task-manager` namespace, with three apps: `frontend`, `tasks-creator`, `tasks-processor`.

**View pods:**

```bash
kubectl get pods -n task-manager            # list pods
kubectl get pods -n task-manager -o wide    # include IP and node
kubectl get pods -n task-manager -w         # watch live
```

**View logs:**

```bash
# By app label (no need for the exact pod name)
kubectl logs -n task-manager -l app=frontend
kubectl logs -n task-manager -l app=tasks-creator
kubectl logs -n task-manager -l app=tasks-processor

# Follow live, last 100 lines
kubectl logs -n task-manager -l app=tasks-creator -f --tail=100

# Follow live across all pods of an app, prefixing each line with its pod name
kubectl logs -n task-manager -l app=tasks-creator -f --prefix

# A specific pod, or its previous container after a crash
kubectl logs -n task-manager <pod-name>
kubectl logs -n task-manager <pod-name> --previous
```

**Open a shell inside a pod:**

```bash
kubectl exec -it -n task-manager <pod-name> -- bash   # Java backends
kubectl exec -it -n task-manager <pod-name> -- sh     # if bash is missing (e.g. frontend)
```

**Diagnose a pod that won't start:**

```bash
kubectl describe pod -n task-manager <pod-name>
```

---

## Running Services Directly (Without Kubernetes)

For local development without containers:

### Backend — tasks-creator

```bash
cd backend/tasks-creator
cp infrastructure/entry-points/app/.env.example .env
# Edit .env with your values, then:
mvn spring-boot:run -pl infrastructure/entry-points/app
```

Starts on port `8080`.

### Backend — tasks-processor

```bash
cd backend/tasks-processor
cp infrastructure/entry-points/app/.env.example .env
# Edit .env, then:
mvn spring-boot:run -pl infrastructure/entry-points/app
```

Starts on port `8081`.

### Frontend (Vite dev server)

```bash
cd frontend
cp .env.example .env.local  # Or use values from Terraform output
# Edit .env.local with your Cognito/API Gateway values, then:
npm install
npm run dev
```

Starts on `http://localhost:8001` with proxy to Floci on `:4566`.

---

## Project Structure

```
task-manager-app/
├── frontend/                           # React SPA (TypeScript + Vite)
│   ├── src/
│   │   ├── api/                        # Axios client + endpoints
│   │   ├── components/ui/              # Reusable UI components
│   │   ├── features/auth/              # Login, AuthProvider, useAuth
│   │   ├── features/tasks/             # TaskList, TaskCard, TaskForm, TaskHistory
│   │   ├── hooks/                      # TanStack Query hooks
│   │   ├── pages/                      # LoginPage, TasksPage, TaskDetailPage
│   │   └── types/                      # TypeScript types
│   ├── Dockerfile                      # Multi-stage (Node build, Nginx serve)
│   └── nginx.conf
│
├── backend/
│   ├── scaffold/                       # JBang hexagonal project generator
│   ├── tasks-creator/                  # REST API + SQS producer (port 8080)
│   │   ├── domain/model/               # Entities, ports, events
│   │   ├── application/use-cases/      # Business logic
│   │   └── infrastructure/
│   │       ├── driven-adapters/        # Postgres (R2DBC), SQS publisher
│   │       └── entry-points/           # REST controller, app config
│   └── tasks-processor/                # SQS consumer + health API (port 8081)
│       ├── domain/model/
│       ├── application/use-cases/
│       └── infrastructure/
│           ├── driven-adapters/        # Postgres (R2DBC)
│           └── entry-points/           # SQS consumer, health controller
│
├── terraform/                          # Infrastructure as Code
│   ├── main.tf                         # AWS provider → Floci
│   ├── sqs.tf, rds.tf, cognito.tf     # Service definitions
│   ├── api_gateway.tf, ecr.tf, eks.tf
│   └── outputs.tf
│
├── k8s/                                # Kubernetes manifests
│   ├── namespace.yaml, configmap.yaml
│   ├── floci-service.yaml
│   ├── tasks-creator/deployment.yaml, service.yaml
│   ├── tasks-processor/deployment.yaml, service.yaml
│   └── frontend/deployment.yaml, service.yaml
│
├── docs/                               # Architecture docs, SQL schema, test plans
├── scripts/                            # Automation (floci-start, build-push, k8s-deploy)
└── .env.local                          # Generated env vars (do not commit)
```

---

## API Endpoints

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| POST | `/tasks` | Create a task | JWT |
| GET | `/tasks` | List all tasks (optional `?status=pending`) | JWT |
| GET | `/tasks/{id}` | Get task by ID | JWT |
| PUT | `/tasks/{id}` | Update task title/description | JWT |
| DELETE | `/tasks/{id}` | Delete task | JWT |
| GET | `/tasks/{id}/history` | Get status change history | JWT |

---

## Database Schema

| Table | Description |
|-------|-------------|
| `status` | Task status catalog (`pendiente`, `completada`) |
| `tasks` | Main task table (UUID PK, title, description, status_id, created_at, updated_at) |
| `tasks_status` | History of all status transitions per task |

Automatic triggers: `updated_at` on row modification, status change logging on every transition.

---

## Running Tests

### Frontend

```bash
cd frontend
npm test              # Vitest (watch mode)
npm run test:coverage # Vitest with coverage report
```

### Backend

```bash
cd backend/tasks-creator
mvn test

cd backend/tasks-processor
mvn test
```

---

## Environment Variables

### Root `.env.local`

| Variable | Description |
|----------|-------------|
| `AWS_ENDPOINT_URL` | Floci endpoint (`http://localhost:4566`) |
| `AWS_REGION` | AWS region (`us-east-1`) |
| `AWS_ACCESS_KEY_ID` | Access key (`test`) |
| `AWS_SECRET_ACCESS_KEY` | Secret key (`test`) |
| `SQS_QUEUE_URL` | SQS queue URL |
| `R2DBC_URL` | PostgreSQL R2DBC connection string |
| `DB_USERNAME` | Database username |
| `DB_PASSWORD` | Database password |
| `COGNITO_POOL_ID` | Cognito user pool ID |
| `COGNITO_CLIENT_ID` | Cognito app client ID |
| `API_GATEWAY_URL` | API Gateway base URL |

### Frontend `.env.local`

| Variable | Description |
|----------|-------------|
| `VITE_COGNITO_ENDPOINT` | Cognito endpoint |
| `VITE_COGNITO_CLIENT_ID` | Cognito app client ID |
| `VITE_API_URL` | API Gateway URL (relative, proxied by Vite) |

---

## Event Flow

1. User creates a task via `POST /tasks` → `tasks-creator` inserts it with status `pendiente`
2. `tasks-creator` publishes `TaskCreatedEvent` to SQS queue
3. `tasks-processor` consumes the event from SQS
4. `tasks-processor` updates the task status to `completada`
5. Every status change is automatically logged in `tasks_status` via DB trigger
