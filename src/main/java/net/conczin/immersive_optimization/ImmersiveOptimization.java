package net.conczin.immersive_optimization;

import net.conczin.immersive_optimization.config.ModConfig;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(
        modid = Tags.MOD_ID,
        name = Tags.MOD_NAME,
        version = Tags.VERSION,
        acceptableRemoteVersions = "*",
        acceptedMinecraftVersions = "[1.12.2]"
)
public class ImmersiveOptimization {
    public static final Logger LOGGER = LogManager.getLogger("ImmersiveOptimization");

    @Mod.Instance(Tags.MOD_ID)
    public static ImmersiveOptimization INSTANCE;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ModConfig.initCache();
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        TickScheduler.setServer(event.getServer());
        event.registerServerCommand(new CommandImmersiveOptimization());
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        TickScheduler.stop();
    }
}
