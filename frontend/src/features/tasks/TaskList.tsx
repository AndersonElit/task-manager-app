import { useState } from 'react';
import { useTasks } from '../../hooks/useTasks';
import { useCreateTask } from '../../hooks/useCreateTask';
import type { TaskStatus } from '../../types/task';
import TaskCard from './TaskCard';
import TaskForm, { type TaskFormValues } from './TaskForm';
import Modal from '../../components/ui/Modal';
import Button from '../../components/ui/Button';
import Spinner from '../../components/ui/Spinner';
import EmptyState from '../../components/ui/EmptyState';
import { cn } from '../../lib/utils';

type Filter = TaskStatus | 'todos';

const filters: { label: string; value: Filter }[] = [
  { label: 'Todas', value: 'todos' },
  { label: 'Pendiente', value: 'pendiente' },
  { label: 'Completada', value: 'completada' },
];

export default function TaskList() {
  const [filter, setFilter] = useState<Filter>('todos');
  const [createOpen, setCreateOpen] = useState(false);
  const { data: tasks, isLoading, isError } = useTasks(filter === 'todos' ? undefined : filter);
  const { mutateAsync: create } = useCreateTask();

  const handleCreate = async (values: TaskFormValues) => {
    await create(values);
    setCreateOpen(false);
  };

  return (
    <div>
      <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
        <div className="flex gap-1 rounded-lg border border-gray-200 bg-white p-1">
          {filters.map((f) => (
            <button
              key={f.value}
              onClick={() => setFilter(f.value)}
              className={cn(
                'rounded-md px-3 py-1.5 text-sm font-medium transition-colors cursor-pointer',
                filter === f.value
                  ? 'bg-indigo-600 text-white'
                  : 'text-gray-600 hover:bg-gray-100'
              )}
            >
              {f.label}
            </button>
          ))}
        </div>
        <Button onClick={() => setCreateOpen(true)}>
          + Nueva tarea
        </Button>
      </div>

      {isLoading && (
        <div className="flex justify-center py-12">
          <Spinner />
        </div>
      )}

      {isError && (
        <EmptyState title="Error al cargar tareas" description="Verifica que el backend esté corriendo." />
      )}

      {!isLoading && !isError && tasks?.length === 0 && (
        <EmptyState
          title="No hay tareas"
          description={filter === 'todos' ? 'Crea tu primera tarea.' : `No hay tareas en estado "${filter}".`}
          action={
            <Button size="sm" onClick={() => setCreateOpen(true)}>
              + Nueva tarea
            </Button>
          }
        />
      )}

      {tasks && tasks.length > 0 && (
        <div className="flex flex-col gap-3">
          {tasks.map((task) => (
            <TaskCard key={task.id} task={task} />
          ))}
        </div>
      )}

      <Modal open={createOpen} title="Nueva tarea" onClose={() => setCreateOpen(false)}>
        <TaskForm onSubmit={handleCreate} onCancel={() => setCreateOpen(false)} />
      </Modal>
    </div>
  );
}
