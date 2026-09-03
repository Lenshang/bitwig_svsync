package svsync;
import com.bitwig.extension.controller.ControllerExtension;
import com.bitwig.extension.controller.ControllerExtensionDefinition;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.Transport;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * 全合一同步扩展,两个入口做同一件事(定位 + 播放):
 *  1. 剪贴板轮询(主通道):发现 SV2 脚本写入的 "SVSYNC:P:<秒>" 后,
 *     恢复原剪贴板文本,然后按当前 BPM 换算成拍,setPosition + play。
 *  2. TCP(调试通道):监听 127.0.0.1:8890,按行接收 "GO <秒>" / "STOP"。
 */
public class SVSyncExtension extends ControllerExtension {
    private static final int PORT = 8890;
    private static final String MARKER = "SVSYNC:P:";
    private static final long POLL_MS = 30;

    private Transport transport;
    private ServerSocket server;
    private String savedClip = "";

    protected SVSyncExtension(
            final ControllerExtensionDefinition definition, final ControllerHost host) {
        super(definition, host);
    }

    @Override
    public void init() {
        transport = getHost().createTransport();
        transport.playPosition().markInterested();
        transport.playPositionInSeconds().markInterested();
        transport.tempo().markInterested();
        startClipboardPoller();
        startServer();
        getHost().println("SV Cursor Sync 已启动(剪贴板 + TCP:" + PORT + ")");
    }

    private void startClipboardPoller() {
        try {
            Win32Clipboard.getText(); // 预热 JNA,失败立即报告
            final Thread t = new Thread(() -> {
                while (true) {
                    try {
                        final String text = Win32Clipboard.getText();
                        if (text != null && text.startsWith(MARKER)) {
                            final double seconds = Double.parseDouble(text.substring(MARKER.length()).trim());
                            Win32Clipboard.setText(savedClip);
                            savedClip = "";
                            syncPlay(seconds);
                        } else if (text != null) {
                            savedClip = text; // 用户复制了新内容,更新备份
                        }
                        Thread.sleep(POLL_MS);
                    } catch (final InterruptedException e) {
                        return;
                    } catch (final Exception ignored) {
                        // 剪贴板被其它进程瞬时占用等情况,下个周期重试
                    }
                }
            }, "svsync-clipboard");
            t.setDaemon(true);
            t.start();
        } catch (final Throwable e) {
            getHost().println("SV Cursor Sync:无法访问系统剪贴板(" + e + "),只剩 TCP 通道");
        }
    }

    private void startServer() {
        try {
            server = new ServerSocket(PORT, 4, java.net.InetAddress.getLoopbackAddress());
            final Thread t = new Thread(() -> {
                while (!server.isClosed()) {
                    try (Socket sock = server.accept()) {
                        sock.setSoTimeout(10_000);
                        final BufferedReader in = new BufferedReader(
                                new InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8));
                        String line;
                        while ((line = in.readLine()) != null) {
                            final String cmd = line.trim();
                            if (cmd.startsWith("GO ")) {
                                syncPlay(Double.parseDouble(cmd.substring(3)));
                            } else if (cmd.equals("STOP")) {
                                transport.stop();
                            }
                        }
                    } catch (final Exception ignored) {
                        // 单个连接异常不影响服务
                    }
                }
            }, "svsync-server");
            t.setDaemon(true);
            t.start();
        } catch (final Exception e) {
            getHost().println("SV Cursor Sync TCP 启动失败: " + e);
        }
    }

    /** 由剪贴板/TCP 线程调用;值对象的读写必须调度回 Bitwig 主线程。 */
    private void syncPlay(final double seconds) {
        getHost().scheduleTask(() -> {
            try {
                final double curBeats = transport.playPosition().get();
                final double curSec = transport.playPositionInSeconds().get();
                double beats;
                double bpm;
                if (curSec > 0.001) {
                    bpm = curBeats / curSec * 60.0;
                } else {
                    // 播放头在原点:用 tempo 归一化值反算(Bitwig tempo 范围 20~666,
                    // 实测校准:BPM=110 时归一化值 0.13931)
                    bpm = 20.0 + transport.tempo().get() * (666.0 - 20.0);
                }
                beats = seconds * bpm / 60.0;
                getHost().println("SV Cursor Sync: target=" + seconds + "s cur=" + curSec + "s/" + curBeats
                        + "b bpm=" + String.format("%.2f", bpm) + " -> " + String.format("%.3f", beats) + "b");
                transport.setPosition(beats);
                transport.play();
            } catch (final Exception e) {
                getHost().println("SV Cursor Sync 定位失败: " + e);
            }
        }, 0);
    }

    @Override
    public void exit() {
        try {
            if (server != null) server.close();
        } catch (final Exception ignored) {
        }
    }

    @Override
    public void flush() {
    }
}
