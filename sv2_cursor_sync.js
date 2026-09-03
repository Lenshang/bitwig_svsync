// sv2 cursor sync - bind to a keyboard shortcut (e.g. Space).
// Plugin mode (inside a DAW): write the current playhead position (seconds)
//   to the clipboard as "SVSYNC:P:<sec>" for the Bitwig SVCursorSync extension.
// Standalone mode: just start local playback.

function getClientInfo() {
    return {
        name: "sv2 cursor sync",
        category: "Utility",
        author: "bitwig_svsync",
        versionNumber: 1,
        minEditorVersion: 65540
    };
}

function main() {
    var hostName = SV.getHostInfo().hostName || "";
    if (hostName.indexOf("Plugin") !== -1) {
        var pos = SV.getPlayback().getPlayhead();
        SV.setHostClipboard("SVSYNC:P:" + pos.toFixed(3));
    } else {
        var pb = SV.getPlayback();
        var status = pb.getStatus(); // "playing" / "looping" / "stopped"
        if (status === "playing" || status === "looping") {
            pb.pause(); // 停止但保持播放头位置
        } else {
            pb.play();
        }
    }
    SV.finish();
}
