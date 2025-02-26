package torrServer;

public abstract class TorrServer {
    private TorrServer() {
    }

    public static void touch() {
    }
    public static native void _init();

    public static native void addTrackers(String var0);

    public static native long startTorrentServer(String var0, long var1);

    public static native void stopTorrentServer();

    public static native void waitTorrentServer();
}
