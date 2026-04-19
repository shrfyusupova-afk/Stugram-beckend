module.exports = {
  testEnvironment: "node",
  rootDir: ".",
  testMatch: ["<rootDir>/tests/**/*.test.js"],
  setupFiles: ["<rootDir>/tests/setup/env.js"],
  setupFilesAfterEnv: ["<rootDir>/tests/setup/jest.setup.js"],
  clearMocks: true,
  restoreMocks: true,
  verbose: false,
};
