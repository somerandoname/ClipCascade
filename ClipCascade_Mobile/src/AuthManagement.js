import { DOMParser } from 'react-native-html-parser';
import { NativeModules } from 'react-native';
import {
  getDataFromAsyncStorage,
  setDataInAsyncStorage,
  getMultipleDataFromAsyncStorage,
} from './AsyncStorageManagement';

const FETCH_TIMEOUT = 5000;
const LOGIN_URL = '/login';
const VALIDATE_URL = '/validate-session';
const CSRF_URL = '/csrf-token';
const SERVER_MODE_URL = '/server-mode';
const WEBSOCKET_ENDPOINT = '/clipsocket';
const WEBSOCKET_ENDPOINT_P2P = '/p2psignaling';
const STUN_URL = '/stun-url';
const MAXSIZE_URL = '/max-size';

export const fetchTimeout = async (input, init, timeout_ms = FETCH_TIMEOUT) => {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), timeout_ms);
  try {
    return await fetch(input, { ...init, signal: controller.signal });
  } finally {
    clearTimeout(timeoutId);
  }
};

export const convertToWebSocketUrl = (inputUrl, endpoint) => {
  if (!inputUrl || typeof inputUrl !== 'string') {
    throw new Error('Invalid URL provided');
  }

  inputUrl = inputUrl.trim().replace(/\/+$/, '').toLowerCase();

  let wsUrl;
  if (inputUrl.startsWith('https://')) {
    wsUrl = inputUrl.replace('https://', 'wss://');
  } else if (inputUrl.startsWith('http://')) {
    wsUrl = inputUrl.replace('http://', 'ws://');
  } else {
    throw new Error(`Unsupported protocol in URL: ${inputUrl}`);
  }

  if (endpoint != null) {
    wsUrl += endpoint;
    wsUrl = wsUrl.replace(/\/+$/, '');
  }

  return wsUrl;
};

export const validateSession = async serverUrl => {
  try {
    const cleanUrl = serverUrl.trim().replace(/\/+$/, '');
    const response = await fetchTimeout(cleanUrl + VALIDATE_URL, {
      method: 'GET',
    });

    if (response.ok) {
      const text = await response.text();
      return text.trim() === 'OK';
    }
    return false;
  } catch (error) {
    return false;
  }
};

export const performLogin = async (serverUrl, username, password) => {
  const cleanUrl = serverUrl.trim().replace(/\/+$/, '');
  const { NativeBridgeModule } = NativeModules;
  if (NativeBridgeModule?.clearCookies) {
    try {
      await NativeBridgeModule.clearCookies();
    } catch (e) {
      // ignore
    }
  }

  // 1. Fetch login page to extract CSRF token and initial cookie
  const loginPageResponse = await fetchTimeout(cleanUrl + LOGIN_URL, {
    method: 'GET',
  });

  if (!loginPageResponse.ok) {
    throw new Error(`Failed to fetch login page: ${loginPageResponse.status}`);
  }

  const htmlText = await loginPageResponse.text();
  let csrfToken = '';

  try {
    const parser = new DOMParser();
    const doc = parser.parseFromString(htmlText, 'text/html');
    const inputElements = doc.getElementsByTagName('input');
    for (let i = 0; i < inputElements.length; i++) {
      const nameAttr = inputElements[i].getAttribute('name');
      if (nameAttr === '_csrf') {
        csrfToken = inputElements[i].getAttribute('value');
        break;
      }
    }
  } catch (e) {
    // Regex fallback
  }

  if (!csrfToken) {
    const match =
      htmlText.match(/name=["']_csrf["']\s+value=["']([^"']+)["']/i) ||
      htmlText.match(/value=["']([^"']+)["']\s+name=["']_csrf["']/i);
    if (match) {
      csrfToken = match[1];
    }
  }

  if (!csrfToken) {
    throw new Error('No CSRF token found in login page');
  }

  const setCookieHeader = loginPageResponse.headers.get('set-cookie');

  // 2. Submit credentials and CSRF token
  const formData = new URLSearchParams();
  formData.append('username', username);
  formData.append('password', password);
  formData.append('_csrf', csrfToken);

  const headers = {
    'Content-Type': 'application/x-www-form-urlencoded',
  };
  if (setCookieHeader) {
    headers.Cookie = setCookieHeader;
  }

  const loginResponse = await fetchTimeout(cleanUrl + LOGIN_URL, {
    method: 'POST',
    headers,
    body: formData.toString(),
  });

  const loginResponseText = await loginResponse.text();
  if (
    !loginResponse.ok ||
    loginResponseText.toLowerCase().includes('bad credentials')
  ) {
    throw new Error(`Login failed: ${loginResponse.status}`);
  }

  // 3. Update CSRF token in storage
  try {
    const csrfResp = await fetchTimeout(cleanUrl + CSRF_URL, { method: 'GET' });
    if (csrfResp.ok) {
      const csrfData = await csrfResp.json();
      if (csrfData.token) {
        await setDataInAsyncStorage('csrf_token', csrfData.token);
      }
    }
  } catch (e) {
    // Non-critical
  }

  // 4. Update server mode & URLs in storage if needed
  try {
    const serverModeResponse = await fetchTimeout(cleanUrl + SERVER_MODE_URL, {
      method: 'GET',
    });
    if (serverModeResponse.ok) {
      const serverModeText = await serverModeResponse.text();
      const mode = String(JSON.parse(serverModeText).mode);
      await setDataInAsyncStorage('server_mode', mode);

      if (mode === 'P2P') {
        await setDataInAsyncStorage('maxsize', '-1');
        const stunResp = await fetchTimeout(cleanUrl + STUN_URL, { method: 'GET' });
        if (stunResp.ok) {
          const stunData = await stunResp.json();
          await setDataInAsyncStorage('stun_url', String(stunData.url));
        }
        const wsUrl = convertToWebSocketUrl(cleanUrl, WEBSOCKET_ENDPOINT_P2P);
        await setDataInAsyncStorage('websocket_url', wsUrl);
      } else if (mode === 'P2S') {
        await setDataInAsyncStorage('stun_url', '');
        const maxResp = await fetchTimeout(cleanUrl + MAXSIZE_URL, { method: 'GET' });
        if (maxResp.ok) {
          const maxData = await maxResp.json();
          await setDataInAsyncStorage('maxsize', String(maxData.maxsize));
        }
        const wsUrl = convertToWebSocketUrl(cleanUrl, WEBSOCKET_ENDPOINT);
        await setDataInAsyncStorage('websocket_url', wsUrl);
      }
    }
  } catch (e) {
    // Non-critical
  }

  return true;
};

let authPromise = null;

export const reauthenticateIfNeeded = async () => {
  if (authPromise) {
    return authPromise;
  }

  authPromise = (async () => {
    try {
      const { server_url, username, save_password } =
        await getMultipleDataFromAsyncStorage([
          'server_url',
          'username',
          'save_password',
        ]);

      if (!server_url) {
        return { authenticated: false, reauthenticated: false, reason: 'No server URL configured' };
      }

      // Check if session is already valid
      const isValid = await validateSession(server_url);
      if (isValid) {
        return { authenticated: true, reauthenticated: false };
      }

      // Session is not valid - check if saved credentials are available
      if (save_password !== 'true') {
        return {
          authenticated: false,
          reauthenticated: false,
          reason: 'Session expired and save_password is false',
        };
      }

      const password = await getDataFromAsyncStorage('password');
      if (!password || !username) {
        return {
          authenticated: false,
          reauthenticated: false,
          reason: 'Missing username or password in storage',
        };
      }

      // Perform re-login in background
      await performLogin(server_url, username, password);
      return { authenticated: true, reauthenticated: true };
    } catch (e) {
      return {
        authenticated: false,
        reauthenticated: false,
        reason: e.message || String(e),
      };
    } finally {
      authPromise = null;
    }
  })();

  return authPromise;
};
