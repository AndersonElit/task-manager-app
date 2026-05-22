import apiClient from './client';
import type { Task, TaskHistory, CreateTaskPayload, UpdateTaskPayload, TaskStatus } from '../types/task';

export const getAllTasks = async (status?: TaskStatus): Promise<Task[]> => {
  const params = status ? { status } : {};
  const { data } = await apiClient.get<Task[]>('/api/v1/tasks', { params });
  return data;
};

export const getTaskById = async (id: string): Promise<Task> => {
  const { data } = await apiClient.get<Task>(`/api/v1/tasks/${id}`);
  return data;
};

export const createTask = async (payload: CreateTaskPayload): Promise<Task> => {
  const { data } = await apiClient.post<Task>('/api/v1/tasks', payload);
  return data;
};

export const updateTask = async (id: string, payload: UpdateTaskPayload): Promise<Task> => {
  const { data } = await apiClient.put<Task>(`/api/v1/tasks/${id}`, payload);
  return data;
};

export const deleteTask = async (id: string): Promise<void> => {
  await apiClient.delete(`/api/v1/tasks/${id}`);
};

export const getTaskHistory = async (id: string): Promise<TaskHistory[]> => {
  const { data } = await apiClient.get<TaskHistory[]>(`/api/v1/tasks/${id}/history`);
  return data;
};
