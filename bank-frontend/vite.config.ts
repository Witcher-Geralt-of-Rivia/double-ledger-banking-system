import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react-swc";
import path from "path";

// https://vitejs.dev/config/
export default defineConfig(({ command, mode }) => {
  // Production bundles have no default backend: the URL must come from the
  // environment (e.g. Vercel project settings). Failing here keeps a build
  // from silently shipping the localhost fallback in src/lib/api-client.ts.
  if (command === "build" && mode === "production" && !loadEnv(mode, __dirname, "VITE_").VITE_API_BASE_URL) {
    throw new Error(
      "VITE_API_BASE_URL is not set for this production build. " +
        "Set it in the hosting provider's environment variables (see bank-frontend/README.md).",
    );
  }

  return {
    server: {
      host: "::",
      port: 8081,
      hmr: {
        overlay: false,
      },
    },
    plugins: [react()],
    resolve: {
      alias: {
        "@": path.resolve(__dirname, "./src"),
      },
    },
  };
});
