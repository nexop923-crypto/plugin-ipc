package com.netdata.plugin.ipc;

/**
 * Main entry point for Netdata IPC Plugin
 */
public class NetdataPlugin {

    public static void main(String[] args) {
        System.out.println("Netdata IPC Plugin Started");
        System.out.println("Version: 1.0.0");
    }

    public static String getVersion() {
        return "1.0.0";
    }

    public static String getName() {
        return "Netdata IPC Plugin";
    }
}
