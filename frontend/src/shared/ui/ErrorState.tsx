type Props = {
  title?: string;
  message: string;
  onRetry?: () => void;
};

export function ErrorState({ title = 'Something went wrong', message, onRetry }: Props) {
  return (
    <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-6 text-center" role="alert">
      <h2 className="font-sans text-base font-semibold text-red-900">{title}</h2>
      <p className="mt-2 font-sans text-sm text-red-800">{message}</p>
      {onRetry && (
        <button
          type="button"
          onClick={onRetry}
          className="mt-4 rounded-lg bg-brand-700 px-4 py-2 font-sans text-sm font-semibold text-white hover:bg-brand-900"
        >
          Try again
        </button>
      )}
    </div>
  );
}
