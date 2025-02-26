package go;

public abstract class Universe {
    private Universe() {
    }

    public static void touch() {
    }

    public static native void _init();

    private static final class proxyerror extends Exception implements Seq.Proxy, error {
        private final int refnum;

        public final int incRefnum() {
            Seq.incGoRef(this.refnum, this);
            return this.refnum;
        }

        proxyerror(int var1) {
            this.refnum = var1;
            Seq.trackGoRef(var1, this);
        }

        public String getMessage() {
            return this.error();
        }

        public native String error();
    }
}
