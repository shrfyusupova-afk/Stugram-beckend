const mongoose = require("mongoose");
const { MongoMemoryServer } = require("mongodb-memory-server");
const { redis } = require("../../src/config/redis");

let mongoServer = null;
let appInstance = null;

const initializeTestEnvironment = async () => {
  if (!mongoServer) {
    mongoServer = await MongoMemoryServer.create({
      instance: {
        ip: "127.0.0.1",
        port: 27027,
      },
    });
    process.env.MONGO_URI = mongoServer.getUri("stugram_test");
  }

  if (mongoose.connection.readyState === 0) {
    await mongoose.connect(process.env.MONGO_URI);
  }

  if (!appInstance) {
    appInstance = require("../../src/app");
  }

  return appInstance;
};

const resetDatabase = async () => {
  const { collections } = mongoose.connection;
  await Promise.all(Object.values(collections).map((collection) => collection.deleteMany({})));
  if (redis && typeof redis.flushall === "function") {
    await redis.flushall();
  }
};

const closeTestEnvironment = async () => {
  if (mongoose.connection.readyState !== 0) {
    await mongoose.disconnect();
  }

  if (mongoServer) {
    await mongoServer.stop();
  }

  mongoServer = null;
  appInstance = null;
};

module.exports = {
  initializeTestEnvironment,
  resetDatabase,
  closeTestEnvironment,
};
