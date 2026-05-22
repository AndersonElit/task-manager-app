import Badge from '../../components/ui/Badge';
import type { TaskStatus } from '../../types/task';

export default function TaskStatusBadge({ status }: { status: TaskStatus }) {
  return (
    <Badge color={status === 'completada' ? 'green' : 'yellow'}>
      {status}
    </Badge>
  );
}
