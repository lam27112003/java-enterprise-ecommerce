type Props = {
  className?: string;
};

export function Skeleton({ className = '' }: Props) {
  return (
    <div
      className={`animate-pulse rounded-lg bg-brand-100/80 ${className}`}
      aria-hidden
    />
  );
}
