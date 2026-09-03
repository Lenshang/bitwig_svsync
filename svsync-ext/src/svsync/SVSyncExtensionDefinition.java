package svsync;
import com.bitwig.extension.api.PlatformType;
import com.bitwig.extension.controller.AutoDetectionMidiPortNamesList;
import com.bitwig.extension.controller.ControllerExtension;
import com.bitwig.extension.controller.ControllerExtensionDefinition;
import com.bitwig.extension.controller.api.ControllerHost;

import java.util.UUID;

public class SVSyncExtensionDefinition extends ControllerExtensionDefinition {
    private static final UUID ID = UUID.fromString("5f2a1d3e-8b47-4c6a-9e21-a7c90411b2d3");

    @Override
    public String getName() { return "SV Cursor Sync"; }

    @Override
    public String getAuthor() { return "bitwig_svsync"; }

    @Override
    public String getVersion() { return "1.0"; }

    @Override
    public UUID getId() { return ID; }

    @Override
    public int getRequiredAPIVersion() { return 23; }

    @Override
    public String getHardwareVendor() { return "bitwig_svsync"; }

    @Override
    public String getHardwareModel() { return "SV Cursor Sync"; }

    @Override
    public int getNumMidiInPorts() { return 0; }

    @Override
    public int getNumMidiOutPorts() { return 0; }

    @Override
    public void listAutoDetectionMidiPortNames(
            final AutoDetectionMidiPortNamesList list, final PlatformType platformType) {
        // 无 MIDI 端口,无需自动检测
    }

    @Override
    public ControllerExtension createInstance(final ControllerHost host) {
        return new SVSyncExtension(this, host);
    }
}
