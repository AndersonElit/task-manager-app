import { useQuery } from '@tanstack/react-query';
import { getAllTasks } from '../api/tasks.api';
import type { TaskStatus } from '../types/task';

export const useTasks = (status?: TaskStatus) =>
  useQuery({
    queryKey: ['tasks', status],
    queryFn: () => getAllTasks(status),
  });
