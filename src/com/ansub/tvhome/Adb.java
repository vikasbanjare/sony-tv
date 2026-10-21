package com.ansub.tvhome;

import android.content.Context;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;

// A small adb client, so an app on the TV can talk to the TV's own adb (Network debugging, port
// 5555) the way a laptop does, and run shell commands with the shell's powers: screenshots and
// key presses in any app. The TV asks "Allow debugging?" once for this app's key (tick Always
// allow), then remembers it across restarts, exactly as it does for a laptop.
//
// Only the classic TCP flavour is spoken (CNXN / AUTH / OPEN / WRTE / OKAY / CLSE), which is what
// Network debugging on TVs uses.
final class Adb {
    private static final int A_CNXN = 0x4e584e43, A_AUTH = 0x48545541, A_OPEN = 0x4e45504f,
            A_OKAY = 0x59414b4f, A_CLSE = 0x45534c43, A_WRTE = 0x45545257;
    private static final int AUTH_TOKEN = 1, AUTH_SIGNATURE = 2, AUTH_RSAPUBLICKEY = 3;
    private static final int MAX_DATA = 4096;
    // PKCS#1 v1.5 signature of a SHA-1 digest: this prefix, then the 20-byte token.
    private static final byte[] SHA1_PREFIX = {0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14};

    private final KeyPair key;
    private Socket socket;
    private DataInputStream in;
    private OutputStream out;
    private int nextId = 1;

    Adb(Context c) throws Exception {
        key = loadOrMakeKey(c);
    }

    // The key lives in the app's private files, like ~/.android/adbkey on a laptop.
    private static KeyPair loadOrMakeKey(Context c) throws Exception {
        File pub = new File(c.getFilesDir(), "adbkey.pub"), priv = new File(c.getFilesDir(), "adbkey");
        KeyFactory kf = KeyFactory.getInstance("RSA");
        if (pub.exists() && priv.exists()) {
            return new KeyPair(kf.generatePublic(new X509EncodedKeySpec(read(pub))), kf.generatePrivate(new PKCS8EncodedKeySpec(read(priv))));
        }
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        KeyPair kp = g.generateKeyPair();
        write(pub, kp.getPublic().getEncoded());
        write(priv, kp.getPrivate().getEncoded());
        return kp;
    }

    private static byte[] read(File f) throws IOException {
        try (FileInputStream i = new FileInputStream(f)) {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int n;
            while ((n = i.read(b)) > 0) o.write(b, 0, n);
            return o.toByteArray();
        }
    }

    private static void write(File f, byte[] data) throws IOException {
        try (FileOutputStream o = new FileOutputStream(f)) { o.write(data); }
    }

    // ---- The connection -------------------------------------------------------------------------

    // Connects and authenticates. On the very first run the TV shows "Allow debugging?" and this
    // waits (up to `waitMs`) for the answer. Returns false if it was refused or timed out.
    boolean connect(String host, int port, int waitMs) throws Exception {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 5000);
        socket.setSoTimeout(waitMs);
        in = new DataInputStream(socket.getInputStream());
        out = socket.getOutputStream();
        send(A_CNXN, 0x01000000, MAX_DATA, "host::\0".getBytes("UTF-8"));
        boolean sentKey = false;
        while (true) {
            Message m = recv();
            if (m.command == A_CNXN) { socket.setSoTimeout(0); return true; }
            if (m.command != A_AUTH || m.arg0 != AUTH_TOKEN) return false;
            if (!sentKey) {
                send(A_AUTH, AUTH_SIGNATURE, 0, sign(m.data));
                sentKey = true;
            } else {
                // Our signature wasn't accepted: offer the public key. The TV asks the user now.
                send(A_AUTH, AUTH_RSAPUBLICKEY, 0, publicKey());
                sentKey = false; // after approval adbd sends a fresh token to sign
                Message n = recv();
                if (n.command == A_CNXN) { socket.setSoTimeout(0); return true; }
                if (n.command != A_AUTH) return false;
                send(A_AUTH, AUTH_SIGNATURE, 0, sign(n.data));
                sentKey = true;
            }
        }
    }

    void close() {
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        socket = null;
    }

    boolean isOpen() { return socket != null && socket.isConnected() && !socket.isClosed(); }

    // Runs a shell command to completion and returns its output.
    String shell(String cmd, int timeoutMs) throws Exception {
        Stream s = open("shell:" + cmd);
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            byte[] chunk = s.read();
            if (chunk == null) break;
            o.write(chunk);
        }
        s.close();
        return new String(o.toByteArray(), "UTF-8");
    }

    // A long-running shell command whose output is read line by line.
    Stream open(String dest) throws Exception {
        int local = nextId++;
        send(A_OPEN, local, 0, (dest + "\0").getBytes("UTF-8"));
        while (true) {
            Message m = recv();
            if (m.command == A_OKAY && m.arg1 == local) return new Stream(local, m.arg0);
            if (m.command == A_CLSE && m.arg1 == local) throw new IOException("adb refused " + dest);
            // Something for another stream: with one stream at a time, ignore it.
        }
    }

    final class Stream {
        final int local, remote;
        private boolean closed;
        Stream(int local, int remote) { this.local = local; this.remote = remote; }

        // The next chunk of output, or null when the command ends.
        byte[] read() throws Exception {
            if (closed) return null;
            while (true) {
                Message m = recv();
                if (m.arg1 != local && m.command != A_CNXN) continue;
                if (m.command == A_WRTE) { send(A_OKAY, local, remote, null); return m.data; }
                if (m.command == A_CLSE) { closed = true; return null; }
            }
        }

        void write(byte[] data) throws Exception {
            send(A_WRTE, local, remote, data);
            while (true) {
                Message m = recv();
                if (m.command == A_OKAY && m.arg1 == local) return;
                if (m.command == A_CLSE && m.arg1 == local) { closed = true; throw new IOException("closed"); }
                if (m.command == A_WRTE && m.arg1 == local) send(A_OKAY, local, remote, null); // output we drop
            }
        }

        void close() {
            if (closed) return;
            closed = true;
            try { send(A_CLSE, local, remote, null); } catch (Exception ignored) {}
        }
    }

    // ---- Messages -------------------------------------------------------------------------------

    private static final class Message {
        int command, arg0, arg1;
        byte[] data;
    }

    private synchronized void send(int command, int arg0, int arg1, byte[] data) throws IOException {
        int len = data == null ? 0 : data.length;
        ByteBuffer b = ByteBuffer.allocate(24 + len).order(ByteOrder.LITTLE_ENDIAN);
        int sum = 0;
        if (data != null) for (byte x : data) sum += x & 0xff;
        b.putInt(command).putInt(arg0).putInt(arg1).putInt(len).putInt(sum).putInt(~command);
        if (data != null) b.put(data);
        out.write(b.array());
        out.flush();
    }

    private Message recv() throws IOException {
        byte[] h = new byte[24];
        in.readFully(h);
        ByteBuffer b = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN);
        Message m = new Message();
        m.command = b.getInt();
        m.arg0 = b.getInt();
        m.arg1 = b.getInt();
        int len = b.getInt();
        b.getInt(); // checksum
        int magic = b.getInt();
        if (magic != ~m.command) throw new IOException("bad adb message");
        m.data = new byte[len];
        in.readFully(m.data);
        return m;
    }

    // ---- The key, in adb's own formats ----------------------------------------------------------

    private byte[] sign(byte[] token) throws Exception {
        // RSA_sign(NID_sha1): type-1 padding by hand, then a raw RSA operation.
        byte[] padded = new byte[256];
        padded[0] = 0;
        padded[1] = 1;
        int fill = 256 - 3 - SHA1_PREFIX.length - token.length;
        for (int i = 0; i < fill; i++) padded[2 + i] = (byte) 0xff;
        padded[2 + fill] = 0;
        System.arraycopy(SHA1_PREFIX, 0, padded, 3 + fill, SHA1_PREFIX.length);
        System.arraycopy(token, 0, padded, 3 + fill + SHA1_PREFIX.length, token.length);
        Cipher c = Cipher.getInstance("RSA/ECB/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, (RSAPrivateKey) key.getPrivate());
        return c.doFinal(padded);
    }

    // adb's RSAPublicKey struct (len, n0inv, n, rr, exponent, little-endian words), base64, a name.
    private byte[] publicKey() throws Exception {
        RSAPublicKey pub = (RSAPublicKey) key.getPublic();
        BigInteger n = pub.getModulus();
        BigInteger r32 = BigInteger.ONE.shiftLeft(32);
        BigInteger n0inv = n.remainder(r32).modInverse(r32).negate().mod(r32);
        BigInteger rr = BigInteger.ONE.shiftLeft(4096).mod(n);
        ByteBuffer b = ByteBuffer.allocate(4 + 4 + 256 + 256 + 4).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(64);
        b.putInt(n0inv.intValue());
        putWords(b, n);
        putWords(b, rr);
        b.putInt(pub.getPublicExponent().intValue());
        String s = Base64.encodeToString(b.array(), Base64.NO_WRAP) + " petcat@tv\0";
        return s.getBytes("UTF-8");
    }

    private static void putWords(ByteBuffer b, BigInteger v) {
        byte[] be = v.toByteArray(); // big-endian, maybe with a leading zero
        byte[] le = new byte[256];
        int k = 0;
        for (int i = be.length - 1; i >= 0 && k < 256; i--) le[k++] = be[i];
        b.put(le);
    }
}
