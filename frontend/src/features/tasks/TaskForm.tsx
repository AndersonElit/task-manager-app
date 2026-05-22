import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { zodResolver } from '@hookform/resolvers/zod';
import Input from '../../components/ui/Input';
import Textarea from '../../components/ui/Textarea';
import Button from '../../components/ui/Button';
import type { Task } from '../../types/task';

const schema = z.object({
  title: z
    .string()
    .min(1, 'El título es obligatorio')
    .max(255, 'Máximo 255 caracteres'),
  description: z.string().optional(),
});

export type TaskFormValues = z.infer<typeof schema>;

interface TaskFormProps {
  defaultValues?: Partial<TaskFormValues>;
  onSubmit: (values: TaskFormValues) => Promise<void>;
  onCancel: () => void;
  task?: Task;
}

export default function TaskForm({ defaultValues, onSubmit, onCancel }: TaskFormProps) {
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<TaskFormValues>({
    resolver: zodResolver(schema),
    defaultValues,
  });

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-4">
      <Input
        label="Título"
        placeholder="Descripción breve de la tarea"
        {...register('title')}
        error={errors.title?.message}
      />
      <Textarea
        label="Descripción"
        placeholder="Detalles opcionales..."
        {...register('description')}
        error={errors.description?.message}
      />
      <div className="flex justify-end gap-2">
        <Button type="button" variant="secondary" onClick={onCancel}>
          Cancelar
        </Button>
        <Button type="submit" loading={isSubmitting}>
          Guardar
        </Button>
      </div>
    </form>
  );
}
