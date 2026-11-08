type Props = {
  title: string;
  message?: string;
};

export function EmptyState({ title, message }: Props) {
  return (
    <div className="rounded-xl border border-dashed border-brand-100 bg-white/60 px-4 py-10 text-center">
      <h2 className="font-sans text-base font-semibold text-brand-900">{title}</h2>
      {message && <p className="mt-2 font-sans text-sm text-brand-700">{message}</p>}
    </div>
  );
}
