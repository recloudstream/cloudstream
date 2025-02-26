// Copyright 2014 The Go Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package go;

import android.content.Context;

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.HashSet;
import java.util.logging.Logger;

import torrServer.TorrServer;

// Seq is a sequence of machine-dependent encoded values.
// Used by automatically generated language bindings to talk to Go.
public class Seq {
    private static Logger log = Logger.getLogger("GoSeq");

    // also known to bind/seq/ref.go and bind/objc/seq_darwin.m
    private static final int NULL_REFNUM = 41;

    // use single Ref for null Object
    public static final Ref nullRef = new Ref(NULL_REFNUM, null);

    // The singleton GoRefQueue
    private static GoRefQueue goRefQueue = null;

    static Boolean hasLoaded = false;

    // We want to lazy loadLibrary in case of crashes
    public static synchronized void load() {
        if (hasLoaded) return;
        hasLoaded = true;
        log.info("Starting loading gojni");
        System.loadLibrary("gojni");
        log.info("Starting loading gojni init");
        init();
        log.info("Starting loading gojni goRefQueue");
        goRefQueue = new GoRefQueue();
        log.info("Starting loading gojni Universe");
        Universe.touch();
        Universe._init();
        log.info("Starting loading gojni TorrServer");
        TorrServer.touch();
        TorrServer._init();
        log.info("Loaded torrent server");
    }

    // setContext sets the context in the go-library to be used in RunOnJvm.
    public static void setContext(Context context) {
        setContext((java.lang.Object)context);
    }

    private static native void init();

    // Empty method to run class initializer
    public static void touch() {}

    private Seq() {
    }

    // ctx is an android.context.Context.
    static native void setContext(java.lang.Object ctx);

    public static void incRefnum(int refnum) {
        tracker.incRefnum(refnum);
    }

    // incRef increments the reference count of Java objects.
    // For proxies for Go objects, it calls into the Proxy method
    // incRefnum() to make sure the Go reference count is positive
    // even if the Proxy is garbage collected and its Ref is finalized.
    public static int incRef(Object o) {
        return tracker.inc(o);
    }

    public static int incGoObjectRef(GoObject o) {
        return o.incRefnum();
    }

    // trackGoRef tracks a Go reference and decrements its refcount
    // when the given GoObject wrapper is garbage collected.
    //
    // TODO(crawshaw): We could cut down allocations for frequently
    // sent Go objects by maintaining a map to weak references. This
    // however, would require allocating two objects per reference
    // instead of one. It also introduces weak references, the bane
    // of any Java debugging session.
    //
    // When we have real code, examine the tradeoffs.
    public static void trackGoRef(int refnum, GoObject obj) {
        if (refnum > 0) {
            throw new RuntimeException("trackGoRef called with Java refnum " + refnum);
        }
        goRefQueue.track(refnum, obj);
    }

    public static Ref getRef(int refnum) {
        return tracker.get(refnum);
    }

    // Increment the Go reference count before sending over a refnum.
    // The ref parameter is only used to make sure the referenced
    // object is not garbage collected before Go increments the
    // count. It's the equivalent of Go's runtime.KeepAlive.
    public static native void incGoRef(int refnum, GoObject ref);

    // Informs the Go ref tracker that Java is done with this refnum.
    static native void destroyRef(int refnum);

    // decRef is called from seq.FinalizeRef
    static void decRef(int refnum) {
        tracker.dec(refnum);
    }

    // A GoObject is a Java class implemented in Go. When a GoObject
    // is passed to Go, it is wrapped in a Go proxy, to make it behave
    // the same as passing a regular Java class.
    public interface GoObject {
        // Increment refcount and return the refnum of the proxy.
        //
        // The Go reference count need to be bumped while the
        // refnum is passed to Go, to avoid finalizing and
        // invalidating it before being translated on the Go side.
        int incRefnum();
    }
    // A Proxy is a Java object that proxies a Go object. Proxies, unlike
    // GoObjects, are unwrapped to their Go counterpart when deserialized
    // in Go.
    public interface Proxy extends GoObject {}

    // A Ref represents an instance of a Java object passed back and forth
    // across the language boundary.
    public static final class Ref {
        public final int refnum;

        private int refcnt;  // Track how many times sent to Go.

        public final Object obj;  // The referenced Java obj.

        Ref(int refnum, Object o) {
            if (refnum < 0) {
                throw new RuntimeException("Ref instantiated with a Go refnum " + refnum);
            }
            this.refnum = refnum;
            this.refcnt = 0;
            this.obj = o;
        }

        void inc() {
            // Count how many times this ref's Java object is passed to Go.
            if (refcnt == Integer.MAX_VALUE) {
                throw new RuntimeException("refnum " + refnum + " overflow");
            }
            refcnt++;
        }
    }

    static final RefTracker tracker = new RefTracker();

    static final class RefTracker {
        private static final int REF_OFFSET = 42;

        // Next Java object reference number.
        //
        // Reference numbers are positive for Java objects,
        // and start, arbitrarily at a different offset to Go
        // to make debugging by reading Seq hex a little easier.
        private int next = REF_OFFSET; // next Java object ref

        // Java objects that have been passed to Go. refnum -> Ref
        // The Ref obj field is non-null.
        // This map pins Java objects so they don't get GCed while the
        // only reference to them is held by Go code.
        private final RefMap javaObjs = new RefMap();

        // Java objects to refnum
        private final IdentityHashMap<Object, Integer> javaRefs = new IdentityHashMap<>();

        // inc increments the reference count of a Java object when it
        // is sent to Go. inc returns the refnum for the object.
        synchronized int inc(Object o) {
            if (o == null) {
                return NULL_REFNUM;
            }
            if (o instanceof Proxy) {
                return ((Proxy)o).incRefnum();
            }
            Integer refnumObj = javaRefs.get(o);
            if (refnumObj == null) {
                if (next == Integer.MAX_VALUE) {
                    throw new RuntimeException("createRef overflow for " + o);
                }
                refnumObj = next++;
                javaRefs.put(o, refnumObj);
            }
            int refnum = refnumObj;
            Ref ref = javaObjs.get(refnum);
            if (ref == null) {
                ref = new Ref(refnum, o);
                javaObjs.put(refnum, ref);
            }
            ref.inc();
            return refnum;
        }

        synchronized void incRefnum(int refnum) {
            Ref ref = javaObjs.get(refnum);
            if (ref == null) {
                throw new RuntimeException("referenced Java object is not found: refnum="+refnum);
            }
            ref.inc();
        }

        // dec decrements the reference count of a Java object when
        // Go signals a corresponding proxy object is finalized.
        // If the count reaches zero, the Java object is removed
        // from the javaObjs map.
        synchronized void dec(int refnum) {
            if (refnum <= 0) {
                // We don't keep track of the Go object.
                // This must not happen.
                log.severe("dec request for Go object "+ refnum);
                return;
            }
            if (refnum == Seq.nullRef.refnum) {
                return;
            }
            // Java objects are removed on request of Go.
            Ref obj = javaObjs.get(refnum);
            if (obj == null) {
                throw new RuntimeException("referenced Java object is not found: refnum="+refnum);
            }
            obj.refcnt--;
            if (obj.refcnt <= 0) {
                javaObjs.remove(refnum);
                javaRefs.remove(obj.obj);
            }
        }

        // get returns an existing Ref to a Java object.
        synchronized Ref get(int refnum) {
            if (refnum < 0) {
                throw new RuntimeException("ref called with Go refnum " + refnum);
            }
            if (refnum == NULL_REFNUM) {
                return nullRef;
            }
            Ref ref = javaObjs.get(refnum);
            if (ref == null) {
                throw new RuntimeException("unknown java Ref: "+refnum);
            }
            return ref;
        }
    }

    // GoRefQueue is a queue of GoRefs that are no longer live. An internal thread
    // processes the queue and decrement the reference count on the Go side.
    static class GoRefQueue extends ReferenceQueue<GoObject> {
        // The set of tracked GoRefs. If we don't hold on to the GoRef instances, the Java GC
        // will not add them to the queue when their referents are reclaimed.
        private final Collection<GoRef> refs = Collections.synchronizedCollection(new HashSet<GoRef>());

        void track(int refnum, GoObject obj) {
            refs.add(new GoRef(refnum, obj, this));
        }

        GoRefQueue() {
            Thread daemon = new Thread(new Runnable() {
                @Override public void run() {
                    while (true) {
                        try {
                            GoRef ref = (GoRef)remove();
                            refs.remove(ref);
                            destroyRef(ref.refnum);
                            ref.clear();
                        } catch (InterruptedException e) {
                            // Ignore
                        }
                    }
                }
            });
            daemon.setDaemon(true);
            daemon.setName("GoRefQueue Finalizer Thread");
            daemon.start();
        }
    }

    // A GoRef is a PhantomReference to a Java proxy for a Go object.
    // GoRefs are enqueued to the singleton GoRefQueue when no longer live,
    // so the corresponding reference count can be decremented.
    static class GoRef extends PhantomReference<GoObject> {
        final int refnum;

        GoRef(int refnum, GoObject obj, GoRefQueue q) {
            super(obj, q);
            if (refnum > 0) {
                throw new RuntimeException("GoRef instantiated with a Java refnum " + refnum);
            }
            this.refnum = refnum;
        }
    }

    // RefMap is a mapping of integers to Ref objects.
    //
    // The integers can be sparse. In Go this would be a map[int]*Ref.
    static final class RefMap {
        private int next = 0;
        private int live = 0;
        private int[] keys = new int[16];
        private Ref[] objs = new Ref[16];

        RefMap() {}

        Ref get(int key) {
            int i = Arrays.binarySearch(keys, 0, next, key);
            if (i >= 0) {
                return objs[i];
            }
            return null;
        }

        void remove(int key) {
            int i = Arrays.binarySearch(keys, 0, next, key);
            if (i >= 0) {
                if (objs[i] != null) {
                    objs[i] = null;
                    live--;
                }
            }
        }

        void put(int key, Ref obj) {
            if (obj == null) {
                throw new RuntimeException("put a null ref (with key "+key+")");
            }
            int i = Arrays.binarySearch(keys, 0, next, key);
            if (i >= 0) {
                if (objs[i] == null) {
                    objs[i] = obj;
                    live++;
                }
                if (objs[i] != obj) {
                    throw new RuntimeException("replacing an existing ref (with key "+key+")");
                }
                return;
            }
            if (next >= keys.length) {
                grow();
                i = Arrays.binarySearch(keys, 0, next, key);
            }
            i = ~i;
            if (i < next) {
                // Insert, shift everything afterwards down.
                System.arraycopy(keys, i, keys, i+1, next-i);
                System.arraycopy(objs, i, objs, i+1, next-i);
            }
            keys[i] = key;
            objs[i] = obj;
            live++;
            next++;
        }

        private void grow() {
            // Compact and (if necessary) grow backing store.
            int[] newKeys;
            Ref[] newObjs;
            int len = 2*roundPow2(live);
            if (len > keys.length) {
                newKeys = new int[keys.length*2];
                newObjs = new Ref[objs.length*2];
            } else {
                newKeys = keys;
                newObjs = objs;
            }

            int j = 0;
            for (int i = 0; i < keys.length; i++) {
                if (objs[i] != null) {
                    newKeys[j] = keys[i];
                    newObjs[j] = objs[i];
                    j++;
                }
            }
            for (int i = j; i < newKeys.length; i++) {
                newKeys[i] = 0;
                newObjs[i] = null;
            }

            keys = newKeys;
            objs = newObjs;
            next = j;

            if (live != next) {
                throw new RuntimeException("bad state: live="+live+", next="+next);
            }
        }

        private static int roundPow2(int x) {
            int p = 1;
            while (p < x) {
                p *= 2;
            }
            return p;
        }
    }
}
