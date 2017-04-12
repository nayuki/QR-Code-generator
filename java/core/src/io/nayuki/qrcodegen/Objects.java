package io.nayuki.qrcodegen;

/**
 * Created by mariotaku on 2017/4/5.
 */
public class Objects {

    static <T> T requireNonNull(T obj) {
        if (obj == null)
            throw new NullPointerException();
        return obj;
    }

}
