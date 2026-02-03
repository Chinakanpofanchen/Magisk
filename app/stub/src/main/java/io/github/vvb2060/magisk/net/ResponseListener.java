package io.github.vvb2060.magisk.net;

public interface ResponseListener<T> {
    void onResponse(T response);
}
