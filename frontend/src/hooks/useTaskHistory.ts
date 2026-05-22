import { useQuery } from '@tanstack/react-query';
import { getTaskHistory } from '../api/tasks.api';

export const useTaskHistory = (id: string) =>
  useQuery({
    queryKey: ['tasks', id, 'history'],
    queryFn: () => getTaskHistory(id),
    enabled: !!id,
  });
