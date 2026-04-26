const js = require("@eslint/js");

const commonGlobals = {
  __dirname: "readonly",
  Buffer: "readonly",
  AbortController: "readonly",
  clearInterval: "readonly",
  clearTimeout: "readonly",
  console: "readonly",
  fetch: "readonly",
  module: "readonly",
  process: "readonly",
  setImmediate: "readonly",
  require: "readonly",
  setInterval: "readonly",
  setTimeout: "readonly",
  URL: "readonly",
  URLSearchParams: "readonly",
};

const testGlobals = {
  afterAll: "readonly",
  afterEach: "readonly",
  beforeAll: "readonly",
  beforeEach: "readonly",
  describe: "readonly",
  expect: "readonly",
  it: "readonly",
  jest: "readonly",
  test: "readonly",
};

module.exports = [
  {
    ignores: [
      "coverage/**",
      "docs/**/*.mongosh.js",
      "node_modules/**",
      "Stugram-beckend/**",
    ],
  },
  js.configs.recommended,
  {
    files: ["**/*.js"],
    languageOptions: {
      ecmaVersion: 2022,
      sourceType: "commonjs",
      globals: {
        ...commonGlobals,
      },
    },
    rules: {
      "no-control-regex": "off",
      "no-extra-boolean-cast": "off",
      "no-unused-vars": ["warn", { argsIgnorePattern: "^_", varsIgnorePattern: "^_" }],
    },
  },
  {
    files: ["tests/**/*.js"],
    languageOptions: {
      globals: {
        ...commonGlobals,
        ...testGlobals,
      },
    },
  },
];
