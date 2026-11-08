/**
 * Defense-in-depth for post-login return paths (GHSA-wrjc-x8rr-h8h6 class).
 * Only allow same-app absolute paths. Validates raw and singly-decoded forms;
 * rejects backslashes (including %5C / double-encoded), protocol-relative URLs,
 * absolute URLs, and malformed percent-encoding (M2).
 */

const MAX_DECODE_PASSES = 2;

function isDangerousPathForm(value: string): boolean {
  if (!value.startsWith('/') || value.startsWith('//')) {
    return true;
  }
  if (value.includes('\\') || value.includes('://')) {
    return true;
  }
  return false;
}

/**
 * Reject paths that still contain an encoded backslash after decoding passes,
 * so a further decode cannot introduce `\`.
 */
function hasEncodedBackslash(value: string): boolean {
  return /%5c/i.test(value);
}

export function isSafeInternalPath(path: unknown): path is string {
  if (typeof path !== 'string' || path.length === 0) {
    return false;
  }

  if (isDangerousPathForm(path) || hasEncodedBackslash(path)) {
    return false;
  }

  let current = path;
  for (let pass = 0; pass < MAX_DECODE_PASSES; pass += 1) {
    let decoded: string;
    try {
      decoded = decodeURIComponent(current);
    } catch {
      // Malformed percent-encoding must not crash login; treat as unsafe.
      return false;
    }

    if (decoded === current) {
      break;
    }

    if (isDangerousPathForm(decoded) || hasEncodedBackslash(decoded)) {
      return false;
    }

    current = decoded;
  }

  return !isDangerousPathForm(current) && !hasEncodedBackslash(current);
}
