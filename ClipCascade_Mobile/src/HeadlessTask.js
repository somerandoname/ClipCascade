import { DeviceEventEmitter } from 'react-native';
import {
  setDataInAsyncStorage,
  getDataFromAsyncStorage,
  clearAsyncStorage,
} from './AsyncStorageManagement'; // persistent storage
import StartForegroundService from './StartForegroundService'; // foreground service

module.exports = async data => {
  try {
    const enableForegroundService = async () => {
      // Get websocket(foreground service) status (enabled/disabled)
      let wsIsRunning_s = await getDataFromAsyncStorage('wsIsRunning');
      return wsIsRunning_s === null ? 'false' : wsIsRunning_s;
    };

    if (data && data['event'] === 'BOOT_COMPLETED') {
      const relaunch_on_boot = await getDataFromAsyncStorage(
        'relaunch_on_boot',
      );
      if (relaunch_on_boot !== null && relaunch_on_boot === 'true') {
        if ((await enableForegroundService()) === 'true') {
          await setDataInAsyncStorage('wsStatusMessage', '');
          const result = await StartForegroundService(data);
          if (result[0] === false) {
            throw result[1];
          }
        }
      }
    } else if (data && data['event'] === 'PING') {
      DeviceEventEmitter.emit('CLIPCASCADE_PING');
    }
  } catch (e) {
    console.error('Error in Headless JS Task:', e);
  }
};
