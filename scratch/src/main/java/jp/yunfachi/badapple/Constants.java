package jp.yunfachi.badapple;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

public interface Constants {
    SocketAddress ADDRESS = new InetSocketAddress("localhost", 25545);

    int VIEW_DISTANCE = 8;
}
