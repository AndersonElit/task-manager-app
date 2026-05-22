import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import type { Task } from '../../types/task';
import { useUpdateTask } from '../../hooks/useUpdateTask';
import TaskStatusBadge from './TaskStatusBadge';
import TaskDeleteButton from './TaskDeleteButton';
import TaskForm, { type TaskFormValues } from './TaskForm';
import Modal from '../../components/ui/Modal';
import Button from '../../components/ui/Button';
import { formatDate } from '../../lib/utils';

export default function TaskCard({ task }: { task: Task }) {
  const navigate = useNavigate();
  const [editOpen, setEditOpen] = useState(false);
  const { mutateAsync } = useUpdateTask(task.id);

  const handleEdit = async (values: TaskFormValues) => {
    await mutateAsync(values);
    setEditOpen(false);
  };

  return (
    <>
      <div className="flex items-start justify-between rounded-xl border border-gray-200 bg-white p-4 shadow-sm hover:shadow-md transition-shadow">
        <div
          className="flex-1 min-w-0 cursor-pointer"
          onClick={() => navigate(`/tasks/${task.id}`)}
        >
          <div className="flex items-center gap-2 mb-1">
            <TaskStatusBadge status={task.status} />
            <span className="text-xs text-gray-400">{formatDate(task.createdAt)}</span>
          </div>
          <p className="font-medium text-gray-900 truncate">{task.title}</p>
          {task.description && (
            <p className="text-sm text-gray-500 mt-0.5 line-clamp-2">{task.description}</p>
          )}
        </div>
        <div className="flex items-center gap-1 ml-3 shrink-0">
          <Button variant="ghost" size="sm" onClick={() => setEditOpen(true)}>
            <svg className="h-4 w-4 text-gray-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
            </svg>
          </Button>
          <TaskDeleteButton id={task.id} title={task.title} />
        </div>
      </div>
      <Modal open={editOpen} title="Editar tarea" onClose={() => setEditOpen(false)}>
        <TaskForm
          defaultValues={{ title: task.title, description: task.description ?? '' }}
          onSubmit={handleEdit}
          onCancel={() => setEditOpen(false)}
        />
      </Modal>
    </>
  );
}
