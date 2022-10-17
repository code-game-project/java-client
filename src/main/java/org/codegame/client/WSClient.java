package org.codegame.client;

import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletionStage;

class WSClient implements WebSocket.Listener {
    @FunctionalInterface
    interface OnMessageCallback {
        void onMessage(String message);
    }

    @FunctionalInterface
    interface OnCloseCallback {
        void onClose();
    }

    private OnMessageCallback onMessage;
    private OnCloseCallback onClose;

    public WSClient(OnMessageCallback onMessage, OnCloseCallback onClose) {
        this.onMessage = onMessage;
        this.onClose = onClose;
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence message, boolean last) {
        onMessage.onMessage(message.toString());
        webSocket.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer bytes, boolean last) {
        webSocket.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket arg0, int arg1, String arg2) {
        onClose.onClose();
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable throwable) {
        throwable.printStackTrace();
        onClose.onClose();
    }
}
