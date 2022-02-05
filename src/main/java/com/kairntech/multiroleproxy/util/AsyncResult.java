package com.kairntech.multiroleproxy.util;

public class AsyncResult<T> {

    private final T result;
    private final Throwable cause;

    public AsyncResult(T result) {
        this.result = result;
        this.cause = null;
    }

    public AsyncResult(Throwable t) {
        this.result = null;
        this.cause = t;
    }

    public boolean success() {
        return cause == null;
    }

    public T result() {
        return result;
    }

    public Throwable cause() {
        return cause;
    }
}
