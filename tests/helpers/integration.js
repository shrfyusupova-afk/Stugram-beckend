const request = require("supertest");

const {
  initializeTestEnvironment,
  resetDatabase,
  closeTestEnvironment,
} = require("./testEnvironment");

const setupIntegrationTestSuite = () => {
  let app;
  let client;

  beforeAll(async () => {
    app = await initializeTestEnvironment();
    client = request(app);
  });

  afterEach(async () => {
    await resetDatabase();
  });

  afterAll(async () => {
    await closeTestEnvironment();
  });

  return {
    getApp: () => app,
    getClient: () => client,
  };
};

module.exports = { setupIntegrationTestSuite };
