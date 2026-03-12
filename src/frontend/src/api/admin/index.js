/**
 * Admin API domain — centralized API calls for the admin section.
 *
 * FOUND-06 pattern: All API calls are centralized in this api/ folder, organized by domain.
 * - Each domain gets its own subfolder: api/admin/, api/clients/, api/topups/, etc.
 * - Domain index files export async functions that call the backend and return data.
 * - No component or composable calls axios/fetch directly — always import from this folder.
 *
 * Phase 14+ will populate this file with real API functions (getClients, createClient, etc.).
 * This placeholder establishes the pattern and folder structure.
 */

// Example structure (implemented in Phase 14):
// export async function getClients(params) { ... }
// export async function createClient(data) { ... }
