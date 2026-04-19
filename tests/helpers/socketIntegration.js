const http = require("http");
const { io: createClient } = require("socket.io-client");

const { initSocketServer, closeSocketServer } = require("../../src/socket/socketServer");
const { registerChatSocket } = require("../../src/socket/chatSocket");

const waitForEvent = (socket, eventName, timeoutMs = 3000) =>
  new Promise((resolve, reject) => {
    const timeout = setTimeout(() => {
      cleanup();
      reject(new Error(`Timed out waiting for socket event: ${eventName}`));
    }, timeoutMs);

    const cleanup = () => {
      clearTimeout(timeout);
      socket.off(eventName, handler);
    };

    const handler = (payload) => {
      cleanup();
      resolve(payload);
    };

    socket.once(eventName, handler);
  });

const emitWithAck = (socket, eventName, payload = {}, timeoutMs = 3000) =>
  new Promise((resolve, reject) => {
    const timeout = setTimeout(() => reject(new Error(`Timed out waiting for ack: ${eventName}`)), timeoutMs);
    socket.emit(eventName, payload, (response) => {
      clearTimeout(timeout);
      resolve(response);
    });
  });

const createSocketTestHarness = async (app) => {
  const httpServer = http.createServer(app);
  initSocketServer(httpServer);
  registerChatSocket(require("../../src/socket/socketServer").getIo());

  await new Promise((resolve) => {
    httpServer.listen(0, "127.0.0.1", resolve);
  });

  const address = httpServer.address();
  const url = `http://127.0.0.1:${address.port}`;
  const sockets = new Set();

  const connectSocket = async ({ token, headers = {} }) => {
    const socket = createClient(url, {
      transports: ["websocket"],
      forceNew: true,
      reconnection: false,
      auth: token ? { token } : undefined,
      extraHeaders: headers,
    });

    sockets.add(socket);

    await new Promise((resolve, reject) => {
      const onConnect = () => {
        cleanup();
        resolve();
      };
      const onError = (error) => {
        cleanup();
        reject(error);
      };
      const cleanup = () => {
        socket.off("connect", onConnect);
        socket.off("connect_error", onError);
      };

      socket.once("connect", onConnect);
      socket.once("connect_error", onError);
    });

    return socket;
  };

  const connectSocketExpectError = async ({ token, headers = {} }) =>
    new Promise((resolve, reject) => {
      const socket = createClient(url, {
        transports: ["websocket"],
        forceNew: true,
        reconnection: false,
        auth: token ? { token } : undefined,
        extraHeaders: headers,
      });

      sockets.add(socket);

      const timeout = setTimeout(() => {
        cleanup();
        reject(new Error("Expected socket connection error"));
      }, 3000);

      const cleanup = () => {
        clearTimeout(timeout);
        socket.off("connect", onConnect);
        socket.off("connect_error", onError);
      };

      const onConnect = () => {
        cleanup();
        reject(new Error("Socket connected unexpectedly"));
      };

      const onError = (error) => {
        cleanup();
        resolve({ socket, error });
      };

      socket.once("connect", onConnect);
      socket.once("connect_error", onError);
    });

  const close = async () => {
    await closeAllSockets();
    await closeSocketServer();
    await new Promise((resolve) => httpServer.close(resolve));
  };

  const closeAllSockets = async () => {
    await Promise.all(
      [...sockets].map(
        (socket) =>
          new Promise((resolve) => {
            if (!socket.connected) {
              socket.close();
              resolve();
              return;
            }

            socket.once("disconnect", resolve);
            socket.close();
          })
      )
    );
    sockets.clear();
  };

  return {
    url,
    connectSocket,
    connectSocketExpectError,
    waitForEvent,
    emitWithAck,
    closeAllSockets,
    close,
  };
};

module.exports = {
  createSocketTestHarness,
  waitForEvent,
  emitWithAck,
};
