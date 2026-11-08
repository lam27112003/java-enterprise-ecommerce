type Props = {
  page: number;
  totalPages: number;
  totalElements: number;
  onPageChange: (page: number) => void;
};

export function PaginationControls({ page, totalPages, totalElements, onPageChange }: Props) {
  if (totalPages <= 1) {
    return (
      <p className="font-sans text-sm text-brand-700">
        {totalElements} result{totalElements === 1 ? '' : 's'}
      </p>
    );
  }

  return (
    <div className="flex flex-wrap items-center justify-between gap-3">
      <p className="font-sans text-sm text-brand-700">
        Page {page + 1} of {totalPages} · {totalElements} result{totalElements === 1 ? '' : 's'}
      </p>
      <div className="flex gap-2">
        <button
          type="button"
          disabled={page <= 0}
          onClick={() => onPageChange(page - 1)}
          className="rounded-lg border border-brand-200 bg-white px-3 py-1.5 font-sans text-sm font-semibold text-brand-900 disabled:opacity-40"
        >
          Previous
        </button>
        <button
          type="button"
          disabled={page + 1 >= totalPages}
          onClick={() => onPageChange(page + 1)}
          className="rounded-lg border border-brand-200 bg-white px-3 py-1.5 font-sans text-sm font-semibold text-brand-900 disabled:opacity-40"
        >
          Next
        </button>
      </div>
    </div>
  );
}
