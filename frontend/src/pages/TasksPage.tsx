import TaskList from '../features/tasks/TaskList';

export default function TasksPage() {
  return (
    <div>
      <h1 className="mb-5 text-2xl font-bold text-gray-900">Tareas</h1>
      <TaskList />
    </div>
  );
}
