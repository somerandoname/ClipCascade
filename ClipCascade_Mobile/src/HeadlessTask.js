import { DeviceEventEmitter } from 'react-native';
import {
  setDataInAsyncStorage,
  getDataFromAsyncStorage,
  clearAsyncStorage,
} from './AsyncStorageManagement'; // persistent storage
import StartForegroundService from './StartForegroundService'; // foreground service

import notifee from '@notifee/react-native';

module.exports = async data => {
  try {
    const getForegroundServiceRunningStatus = async () => {
      // Get websocket(foreground service) status (enabled/disabled)
      let wsIsRunning_s = await getDataFromAsyncStorage('wsIsRunning');
      return wsIsRunning_s === null ? 'false' : wsIsRunning_s;
    };

    if (data && data.event === 'BOOT_COMPLETED') {
      const relaunch_on_boot = await getDataFromAsyncStorage(
        'relaunch_on_boot',
      );
      if (relaunch_on_boot !== null && relaunch_on_boot === 'true') {
        if ((await getForegroundServiceRunningStatus()) === 'true') {
          await setDataInAsyncStorage('wsStatusMessage', '');
          const result = await StartForegroundService(data);
          if (result[0] === false) {
            throw result[1];
          }
        }
      }
    } else if (data && data['event'] === 'PING') {
      DeviceEventEmitter.emit('CLIPCASCADE_PING');
    } else if (data && data.event === 'START_SERVICE') {
      // Force start the service regardless of previous state if received via broadcast
      await setDataInAsyncStorage('wsIsRunning', 'true');
      await setDataInAsyncStorage('wsStatusMessage', '🚀 Starting via broadcast...');
      await setDataInAsyncStorage('wsForegroundServiceTerminated', 'false');
      const result = await StartForegroundService();
      if (result[0] === false) {
        throw result[1];
      }
    } else if (data && data.event === 'STOP_SERVICE') {
      await setDataInAsyncStorage('wsIsRunning', 'false');
      await setDataInAsyncStorage('wsStatusMessage', '🛑 Stopping via broadcast...');
      await notifee.cancelAllNotifications();
      await notifee.stopForegroundService();
      await setDataInAsyncStorage('wsForegroundServiceTerminated', 'true');
    }

  } catch (e) {
    console.error('Error in Headless JS Task:', e);
  }
};
