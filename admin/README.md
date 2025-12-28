# iAnctChinese Admin

This is the admin frontend for iAnctChinese, built with Vite + Vue 3 + TypeScript.

## Requirements

- Node.js 18+

## Install

```bash
npm install
```

## Development

```bash
npm run dev
```

The dev server proxies API requests to `http://localhost:8080` by default.

## Build

```bash
npm run build
```

Build output is emitted to `admin/dist` and is intended to be served at `/admin/`.

## Configuration

The API base URL is controlled by `.env`:

```ini
VITE_API_BASE_URL=/api
```

You can override the API base at runtime from the top bar in the UI.

## Deployment

- Serve the `dist` folder under `/admin/`.
- Ensure the backend serves the SPA with history fallback for `/admin/*` routes.
- Keep `/api` pointing to the backend service.
