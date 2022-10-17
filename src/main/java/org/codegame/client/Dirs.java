package org.codegame.client;

import org.apache.commons.lang3.SystemUtils;

class Dirs {
    static String UserHome() {
        return System.getProperty("user.home");
    }

    static String DataHome() {
        if (SystemUtils.IS_OS_WINDOWS)
            return DataHomeWindows();
        else if (SystemUtils.IS_OS_MAC)
            return DataHomeMacOS();
        return DataHomeXDG();
    }

    private static String DataHomeWindows() {
        return SystemUtils.getEnvironmentVariable("LOCALAPPDATA", UserHome() + "/AppData/Local");
    }

    private static String DataHomeMacOS() {
        return SystemUtils.getEnvironmentVariable("XDG_DATA_HOME", UserHome() + "/Library/Application Support");
    }

    private static String DataHomeXDG() {
        return SystemUtils.getEnvironmentVariable("XDG_DATA_HOME", UserHome() + "/.local/share");
    }
}
