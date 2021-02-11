package com.tools.niviewer;

import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class SocketServer extends Thread {
    private static final String TAG = "SocketServer";
    private static SocketServer server = null;

    private final BlockingQueue<byte[]> colorFrames = new ArrayBlockingQueue<>(2);
    private final BlockingQueue<byte[]> depthFrames = new ArrayBlockingQueue<>(2);

    private OutputStream outputStream;
    private boolean running;
    private Socket socket = null;

    public static SocketServer getServer()  {
        if (server == null) {
            server = new SocketServer();
        }
        return server;
    }

    public void offerColorFrame(final byte[] frame) {
        colorFrames.offer(frame);
    }

    public void offerDepthFrame(final byte[] frame) {
        depthFrames.offer(frame);
    }

    public void run() {
        running = true;

        while (running) {
            try {
                if(isConnected())
                {
                    byte[] colorFrame = colorFrames.poll();
                    if (colorFrame != null) {
                        outputStream.write(colorFrame);
                    }

                    byte[] depthFrame = depthFrames.poll();
                    if (depthFrame != null) {
                        outputStream.write(depthFrame);
                    }
                }
                else
                {
                    initializeSocket();
                }
            } catch (IOException ex) {
                Log.e(TAG, "Failed sending frame: " + ex.getMessage());
                stopSocket();
            }
        }
    }

    public void kill() {
        running = false;
        server = null;
        stopSocket();
    }




    private void initializeSocket() {
        try {
            if(socket == null){
                socket = new Socket();
            }

            if(socket.isClosed() || !socket.isConnected())
            {
                socket.connect(new InetSocketAddress("192.168.26.64", 5000), 5000);
            }

            socket.setSendBufferSize(1310740);
            outputStream = socket.getOutputStream();
        }
        catch (IOException ex) {
            Log.e(TAG, ex.getMessage());
            stopSocket();
        }
    }

    private void stopSocket() {
        try {
            if(outputStream != null){
                outputStream.close();
                outputStream = null;
            }

            if(socket != null && !socket.isClosed()){
                socket.close();
                socket = null;
            }
        }
        catch (IOException ex) {
            Log.e(TAG, ex.getMessage());
        }
    }

    private boolean isConnected(){
        return socket != null && socket.isConnected() && !socket.isClosed() && outputStream != null;
    }
}
