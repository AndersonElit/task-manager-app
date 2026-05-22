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

export interface UpdateTaskPayload {
  title: string;
  description?: string;
}
