import type { ShippingAddress } from '../lib/shippingAddress';

type Props = {
  value: ShippingAddress;
  disabled?: boolean;
  onChange: (next: ShippingAddress) => void;
};

export function ShippingAddressForm({ value, disabled, onChange }: Props) {
  function set<K extends keyof ShippingAddress>(key: K, next: ShippingAddress[K]) {
    onChange({ ...value, [key]: next });
  }

  const fieldClass =
    'mt-1 w-full rounded-lg border border-brand-200 bg-white px-3 py-2 font-sans text-sm text-brand-900 disabled:opacity-60';

  return (
    <fieldset className="space-y-3" disabled={disabled}>
      <legend className="font-display text-lg font-semibold text-brand-900">Shipping address</legend>
      <label className="block font-sans text-sm text-brand-800">
        Full name
        <input
          className={fieldClass}
          value={value.fullName}
          onChange={(e) => set('fullName', e.target.value)}
          autoComplete="name"
          required
        />
      </label>
      <label className="block font-sans text-sm text-brand-800">
        Address line 1
        <input
          className={fieldClass}
          value={value.line1}
          onChange={(e) => set('line1', e.target.value)}
          autoComplete="address-line1"
          required
        />
      </label>
      <label className="block font-sans text-sm text-brand-800">
        Address line 2
        <input
          className={fieldClass}
          value={value.line2}
          onChange={(e) => set('line2', e.target.value)}
          autoComplete="address-line2"
        />
      </label>
      <div className="grid gap-3 sm:grid-cols-2">
        <label className="block font-sans text-sm text-brand-800">
          City
          <input
            className={fieldClass}
            value={value.city}
            onChange={(e) => set('city', e.target.value)}
            autoComplete="address-level2"
            required
          />
        </label>
        <label className="block font-sans text-sm text-brand-800">
          State / region
          <input
            className={fieldClass}
            value={value.region}
            onChange={(e) => set('region', e.target.value)}
            autoComplete="address-level1"
            required
          />
        </label>
      </div>
      <div className="grid gap-3 sm:grid-cols-2">
        <label className="block font-sans text-sm text-brand-800">
          Postal code
          <input
            className={fieldClass}
            value={value.postalCode}
            onChange={(e) => set('postalCode', e.target.value)}
            autoComplete="postal-code"
            required
          />
        </label>
        <label className="block font-sans text-sm text-brand-800">
          Country
          <input
            className={fieldClass}
            value={value.country}
            onChange={(e) => set('country', e.target.value)}
            autoComplete="country"
            required
          />
        </label>
      </div>
    </fieldset>
  );
}
