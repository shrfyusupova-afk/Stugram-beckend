const fs = require("fs");
const admin = require("firebase-admin");

const { env } = require("./env");
const logger = require("../utils/logger");

let firebaseApp = null;
let firebaseMessaging = null;
let firebaseEnabled = false;
let firebaseStatus = {
  enabled: false,
  reason: "not_configured",
  missingFields: [],
  credentialSource: null,
  projectId: null,
};

const resolveCredential = () => {
  if (env.firebaseServiceAccountJson) {
    try {
      firebaseStatus.credentialSource = "service_account_json";
      return JSON.parse(env.firebaseServiceAccountJson);
    } catch (_error) {
      firebaseStatus = {
        ...firebaseStatus,
        enabled: false,
        reason: "invalid_service_account_json",
      };
      logger.warn("Push delivery disabled — Firebase not configured", {
        reason: firebaseStatus.reason,
      });
      return null;
    }
  }

  if (env.firebaseServiceAccountPath) {
    try {
      firebaseStatus.credentialSource = "service_account_path";
      const fileContent = fs.readFileSync(env.firebaseServiceAccountPath, "utf8");
      return JSON.parse(fileContent);
    } catch (error) {
      firebaseStatus = {
        ...firebaseStatus,
        enabled: false,
        reason: "invalid_service_account_path",
      };
      logger.warn("Push delivery disabled — Firebase not configured", {
        reason: firebaseStatus.reason,
        message: error.message,
      });
      return null;
    }
  }

  if (env.firebaseProjectId && env.firebaseClientEmail && env.firebasePrivateKey) {
    firebaseStatus.credentialSource = "env";
    return {
      projectId: env.firebaseProjectId,
      clientEmail: env.firebaseClientEmail,
      privateKey: env.firebasePrivateKey.replace(/\\n/g, "\n"),
    };
  }

  const missingFields = [
    !env.firebaseProjectId ? "FIREBASE_PROJECT_ID" : null,
    !env.firebaseClientEmail ? "FIREBASE_CLIENT_EMAIL" : null,
    !env.firebasePrivateKey ? "FIREBASE_PRIVATE_KEY" : null,
  ].filter(Boolean);

  firebaseStatus = {
    ...firebaseStatus,
    enabled: false,
    reason: "missing_env",
    missingFields,
  };
  logger.warn("Push delivery disabled — Firebase not configured", {
    missingFields,
  });
  return null;
};

try {
  const credentialPayload = resolveCredential();

  if (credentialPayload && credentialPayload.projectId && credentialPayload.clientEmail && credentialPayload.privateKey) {
    firebaseApp = admin.apps.length
      ? admin.app()
      : admin.initializeApp({
          credential: admin.credential.cert(credentialPayload),
        });
    firebaseMessaging = firebaseApp.messaging();
    firebaseEnabled = true;
    firebaseStatus = {
      enabled: true,
      reason: "configured",
      missingFields: [],
      credentialSource: firebaseStatus.credentialSource,
      projectId: credentialPayload.projectId,
    };
    logger.info("Firebase Admin initialized", {
      projectId: credentialPayload.projectId,
    });
  } else {
    firebaseStatus = {
      ...firebaseStatus,
      enabled: false,
      reason: firebaseStatus.reason || "not_configured",
    };
  }
} catch (error) {
  firebaseEnabled = false;
  firebaseApp = null;
  firebaseMessaging = null;
  firebaseStatus = {
    enabled: false,
    reason: "init_failed",
    missingFields: firebaseStatus.missingFields || [],
    credentialSource: firebaseStatus.credentialSource,
    projectId: null,
  };
  logger.error("Firebase Admin initialization failed", {
    message: error.message,
  });
}

const isFirebaseEnabled = () => firebaseEnabled;
const getFirebaseMessaging = () => firebaseMessaging;
const getFirebaseStatus = () => firebaseStatus;

module.exports = {
  isFirebaseEnabled,
  getFirebaseMessaging,
  getFirebaseStatus,
};
