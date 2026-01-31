package com.kpfc.magisk.net;

public interface ResponseListener<T> {
    void onResponse(T response);
}
