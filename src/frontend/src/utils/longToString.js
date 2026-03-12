/**
 * Long-to-String conversion utilities.
 *
 * Java Long values can exceed JS Number.MAX_SAFE_INTEGER (2^53 - 1).
 * Always call longToString() on ID fields before displaying. JSON.parse()
 * will silently truncate large Long values before this utility ever sees them,
 * but converting to String ensures the value is never misrepresented as a Number
 * and is safe to use in comparisons, display, and URL construction.
 */

/**
 * Convert a single Long value to String.
 *
 * @param {number|string|null|undefined} value - The value to convert
 * @returns {string|null} String representation, or null if value is null/undefined
 */
export function longToString(value) {
  if (value === null || value === undefined) {
    return null;
  }
  return String(value);
}

/**
 * Return a shallow copy of obj with each named field coerced to String.
 * Fields that are null or undefined are left unchanged (not set to "null").
 *
 * @param {Object|null|undefined} obj - The source object
 * @param {string[]} fields - Array of field names to coerce
 * @returns {Object|null|undefined} New object with named fields as strings, or obj unchanged if falsy
 */
export function normalizeLongIds(obj, fields) {
  if (!obj) {
    return obj;
  }
  const result = { ...obj };
  for (const field of fields) {
    if (result[field] !== null && result[field] !== undefined) {
      result[field] = String(result[field]);
    }
  }
  return result;
}
