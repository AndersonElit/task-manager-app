import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { zodResolver } from '@hookform/resolvers/zod';
import { useAuth } from './useAuth';
import Input from '../../components/ui/Input';
import Button from '../../components/ui/Button';

const schema = z.object({
  username: z.string().min(1, 'El usuario es obligatorio'),
  password: z.string().min(1, 'La contraseña es obligatoria'),
});

type FormValues = z.infer<typeof schema>;

export default function LoginForm() {
  const { login } = useAuth();
  const { register, handleSubmit, formState: { errors, isSubmitting }, setError } = useForm<FormValues>({
    resolver: zodResolver(schema),
  });

  const onSubmit = async (values: FormValues) => {
    try {
      await login(values.username, values.password);
    } catch (err: unknown) {
      const msg =
        (err as { response?: { data?: { message?: string; __type?: string } } })
          ?.response?.data?.message
        ?? (err as { response?: { data?: { __type?: string } } })
          ?.response?.data?.__type
        ?? 'Error al conectar con el servidor';
      setError('root', { message: msg });
    }
  };

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-4">
      <Input
        label="Usuario"
        placeholder="testuser"
        {...register('username')}
        error={errors.username?.message}
      />
      <Input
        label="Contraseña"
        type="password"
        placeholder="••••••••"
        {...register('password')}
        error={errors.password?.message}
      />
      {errors.root && (
        <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-600">
          {errors.root.message}
        </p>
      )}
      <Button type="submit" loading={isSubmitting} className="w-full justify-center">
        Iniciar sesión
      </Button>
    </form>
  );
}
