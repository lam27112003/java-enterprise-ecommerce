type Props = {
  label?: string;
};

export function LoadingState({ label = 'Loading…' }: Props) {
  return (
    <div className="flex items-center justify-center py-16" role="status" aria-live="polite">
      <p className="font-sans text-brand-700">{label}</p>
    </div>
  );
}
