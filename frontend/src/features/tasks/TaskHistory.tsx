import { useTaskHistory } from '../../hooks/useTaskHistory';
import TaskStatusBadge from './TaskStatusBadge';
import Spinner from '../../components/ui/Spinner';
import { formatDate } from '../../lib/utils';

export default function TaskHistory({ taskId }: { taskId: string }) {
  const { data: history, isLoading } = useTaskHistory(taskId);

  if (isLoading) return <div className="flex justify-center py-6"><Spinner className="h-5 w-5" /></div>;
  if (!history || history.length === 0) return <p className="text-sm text-gray-400">Sin historial.</p>;

  return (
    <ol className="relative border-l border-gray-200 ml-2">
      {history.map((entry) => (
        <li key={entry.id} className="mb-5 ml-4">
          <div className="absolute -left-1.5 mt-1 h-3 w-3 rounded-full border border-white bg-indigo-400" />
          <div className="flex items-center gap-2">
            <TaskStatusBadge status={entry.status} />
            <span className="text-xs text-gray-400">{formatDate(entry.date)}</span>
          </div>
        </li>
      ))}
    </ol>
  );
}
