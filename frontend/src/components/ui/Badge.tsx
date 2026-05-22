import { cn } from '../../lib/utils';

type Color = 'yellow' | 'green' | 'gray';

interface BadgeProps {
  color?: Color;
  children: React.ReactNode;
}

const colorClasses: Record<Color, string> = {
  yellow: 'bg-yellow-100 text-yellow-800',
  green: 'bg-green-100 text-green-800',
  gray: 'bg-gray-100 text-gray-700',
};

export default function Badge({ color = 'gray', children }: BadgeProps) {
  return (
    <span className={cn('inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium', colorClasses[color])}>
      {children}
    </span>
  );
}
