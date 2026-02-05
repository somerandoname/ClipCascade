package com.clipcascade;

import com.clipcascade.ILogcatCallback;

interface ILogcatService {
    void startMonitoring(ILogcatCallback callback) = 1;
    void stopMonitoring() = 2;
    void destroy() = 16777114;
}
