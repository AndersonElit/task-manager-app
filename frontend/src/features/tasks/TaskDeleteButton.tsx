import { useState } from 'react';
import { useDeleteTask } from '../../hooks/useDeleteTask';
import Button from '../../components/ui/Button';
import Modal from '../../components/ui/Modal';

export default function TaskDeleteButton({ id, title }: { id: string; title: string }) {
  const [open, setOpen] = useState(false);
  const { mutateAsync, isPending } = useDeleteTask();

  const handleConfirm = async () => {
    await mutateAsync(id);
    setOpen(false);
  };

  return (
    <>
      <Button variant="ghost" size="sm" onClick={() => setOpen(true)}>
        <svg className="h-4 w-4 text-red-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
        </svg>
      </Button>
      <Modal open={open} title="Eliminar tarea" onClose={() => setOpen(false)}>
        <p className="text-sm text-gray-600 mb-6">
          ¿Estás seguro de que deseas eliminar <strong>"{title}"</strong>? Esta acción no se puede deshacer.
        </p>
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={() => setOpen(false)}>
            Cancelar
          </Button>
          <Button variant="danger" loading={isPending} onClick={handleConfirm}>
            Eliminar
          </Button>
        </div>
      </Modal>
    </>
  );
}
