const { Server } = require("socket.io");

const { env } = require("../config/env");
const logger = require("../utils/logger");
const { authenticateSocket } = require("./socketAuth");

let ioInstance = null;

const getSocketCorsOrigin = () => {
  if (env.clientUrl === true) return "*";
  return env.clientUrl;
};

const initSocketServer = (httpServer) => {
  ioInstance = new Server(httpServer, {
    cors: {
      origin: getSocketCorsOrigin(),
      credentials: true,
    },
    pingInterval: 20000,
    pingTimeout: 30000,
    connectTimeout: 20000,
  });

  ioInstance.use(authenticateSocket);

  logger.info("Socket.IO initialized");
  return ioInstance;
};

const getIo = () => {
  if (!ioInstance) {
    throw new Error("Socket.IO is not initialized");
  }

  return ioInstance;
};

const closeSocketServer = async () => {
  if (!ioInstance) return;

  await new Promise((resolve) => {
    ioInstance.close(() => resolve());
  });

  ioInstance = null;
};

module.exports = {
  initSocketServer,
  getIo,
  closeSocketServer,
};
