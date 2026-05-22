import { useQuery } from '@tanstack/react-query';
import { getTaskById } from '../api/tasks.api';

export const useTask = (id: string) =>
  useQuery({
    queryKey: ['tasks', id],
    queryFn: () => getTaskById(id),
    enabled: !!id,
  });
