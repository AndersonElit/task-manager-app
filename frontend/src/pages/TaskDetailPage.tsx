import { useParams, useNavigate } from 'react-router-dom';
import { useTask } from '../hooks/useTask';
import TaskStatusBadge from '../features/tasks/TaskStatusBadge';
import TaskHistory from '../features/tasks/TaskHistory';
import Spinner from '../components/ui/Spinner';
import Button from '../components/ui/Button';
import { formatDate } from '../lib/utils';

export default function TaskDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { data: task, isLoading, isError } = useTask(id!);

  if (isLoading) {
    return <div className="flex justify-center py-16"><Spinner /></div>;
  }

  if (isError || !task) {
    return (
      <div className="text-center py-16">
        <p className="text-gray-500 mb-4">No se encontró la tarea.</p>
        <Button variant="secondary" onClick={() => navigate('/tasks')}>
          Volver
        </Button>
      </div>
    );
  }

  return (
    <div className="max-w-2xl">
      <Button variant="ghost" size="sm" className="mb-4" onClick={() => navigate('/tasks')}>
        ← Volver
      </Button>

      <div className="rounded-xl border border-gray-200 bg-white p-6 shadow-sm">
        <div className="flex items-start justify-between gap-4 mb-4">
          <h2 className="text-xl font-semibold text-gray-900">{task.title}</h2>
          <TaskStatusBadge status={task.status} />
        </div>

        {task.description && (
          <p className="text-sm text-gray-600 mb-4">{task.description}</p>
        )}

        <div className="flex gap-4 text-xs text-gray-400">
          <span>Creada: {formatDate(task.createdAt)}</span>
          <span>Actualizada: {formatDate(task.updatedAt)}</span>
        </div>
      </div>

      <div className="mt-6 rounded-xl border border-gray-200 bg-white p-6 shadow-sm">
        <h3 className="text-base font-semibold text-gray-800 mb-4">Historial de estados</h3>
        <TaskHistory taskId={task.id} />
      </div>
    </div>
  );
}
