type Props = {
  title: string;
  description: string;
};

export function PlaceholderPage({ title, description }: Props) {
  return (
    <section>
      <h1 className="font-display text-3xl font-bold text-brand-900">{title}</h1>
      <p className="mt-3 max-w-xl font-sans text-brand-700">{description}</p>
      <p className="mt-6 font-sans text-sm text-brand-500">Coming in a later Phase 7 work package.</p>
    </section>
  );
}
