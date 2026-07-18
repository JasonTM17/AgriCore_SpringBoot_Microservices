import eslint from "@eslint/js";
import reactHooks from "eslint-plugin-react-hooks";
import reactRefresh from "eslint-plugin-react-refresh";
import globals from "globals";
import tseslint from "typescript-eslint";

export default tseslint.config(
  {
    ignores: ["dist", "coverage", "src/generated"],
  },
  {
    files: ["**/*.{ts,tsx}"],
    extends: [eslint.configs.recommended, ...tseslint.configs.recommendedTypeChecked],
    languageOptions: {
      ecmaVersion: 2023,
      globals: {
        ...globals.browser,
        ...globals.node,
      },
      parserOptions: {
        projectService: true,
        tsconfigRootDir: import.meta.dirname,
      },
    },
  },
  reactHooks.configs.flat.recommended,
  {
    ...reactRefresh.configs.vite,
    rules: {
      ...reactRefresh.configs.vite.rules,
      "react-refresh/only-export-components": "off",
    },
  },
  {
    files: ["src/lib/auth/**/*.{ts,tsx}"],
    rules: {
      // Access-token getters intentionally close over a mutable ref for request-time reads.
      "react-hooks/refs": "off",
    },
  },
);
